package site.geekie.shop.shoppingmall.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.config.SeckillProperties;
import site.geekie.shop.shoppingmall.config.SeckillProperties.Risk;
import site.geekie.shop.shoppingmall.converter.SeckillConverter;
import site.geekie.shop.shoppingmall.dto.SeckillActivityDTO;
import site.geekie.shop.shoppingmall.dto.SeckillOrderDTO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.entity.SeckillActivityDO;
import site.geekie.shop.shoppingmall.entity.SeckillOrderDO;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SeckillActivityMapper;
import site.geekie.shop.shoppingmall.mapper.SeckillOrderMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.mq.producer.SeckillMessageProducer;
import site.geekie.shop.shoppingmall.service.SeckillService;
import site.geekie.shop.shoppingmall.service.TurnstileService;
import site.geekie.shop.shoppingmall.util.SeckillRedisService;
import site.geekie.shop.shoppingmall.util.SeckillSseService;
import site.geekie.shop.shoppingmall.util.SnowflakeIdGenerator;
import site.geekie.shop.shoppingmall.util.StockRedisService;
import site.geekie.shop.shoppingmall.vo.SeckillActivityVO;
import site.geekie.shop.shoppingmall.vo.SeckillOrderVO;
import site.geekie.shop.shoppingmall.vo.SeckillResultVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 秒杀业务实现
 *
 * 抢购防线（按顺序）：
 *   1. 活动状态/时间校验（不消费库存与闸门额度）
 *   2. 频率计数记录（风险信号预处理，与判断分离）
 *   3. 风险检测 + step-up Turnstile（needCaptcha -> 要求验证 / 验证通过清零用户维度计数）
 *   4. 总量闸门 INCR（超 库存×k 快速拒绝防过载）
 *   5. 售罄标记快速失败（isSoldOut）
 *   6. Redis 分桶原子预扣（随机起桶 + 跨桶重试 + 未预热懒加载）
 *   7. MQ 异步落库（真实 mall_order），立即返回排队中，结果经 SSE 推送
 *   8. MQ 投递失败回补 Redis + 写失败结果
 *
 * 库存生命周期（账面守恒）：
 *   上线   product.stock -= seckill_stock（联动商品 Redis）→ available_stock = seckill_stock，
 *          提交后同步预热分桶 + 初始化闸门（D11，无独立定时任务）
 *   抢购   Redis 分桶预扣 → 消费者 DB 乐观锁扣 available_stock
 *   超时   OrderCloseConsumer 按 source + allow_restock 分流回补
 *   归还   下线/删除/结束（Job）：product.stock += 未售 available_stock，置已归还幂等标记
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final SeckillRedisService seckillRedisService;
    private final StockRedisService stockRedisService;
    private final SeckillMessageProducer seckillMessageProducer;
    private final SeckillConverter seckillConverter;
    private final TurnstileService turnstileService;
    private final SeckillSseService seckillSseService;
    private final SeckillProperties seckillProperties;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    // ======================== 用户端 ========================

    @Override
    public List<SeckillActivityVO> listActiveActivities() {
        List<SeckillActivityDO> activities = seckillActivityMapper.findActiveAndUpcoming();
        return seckillConverter.toVOList(activities);
    }

    @Override
    public SeckillActivityVO getActivityDetail(Long activityId) {
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        // 草稿/下线活动对用户端与"不存在"同响应：不泄露未上线活动（未来营销信息）的存在性
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    public SeckillResultVO seckill(SeckillOrderDTO request, String clientIp) {
        Long activityId = request.getActivityId();
        Long userId = request.getUserId();
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();

        // 1. 校验活动存在、已上线且在时间窗口内（不满足时快速失败，不消费库存/闸门）
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.SECKILL_NOT_STARTED);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }

        // 2. 频率计数（每次抢购入口都记录，与风险判断分离）
        Risk riskCfg = seckillProperties.getRisk();
        seckillRedisService.incrementRiskFreq(activityId, String.valueOf(userId), riskCfg.getFreqWindowSeconds());
        if (clientIp != null && !clientIp.isBlank()) {
            seckillRedisService.incrementRiskFreq(activityId, clientIp, riskCfg.getFreqWindowSeconds());
        }

        // 3. 风险检测 + step-up Turnstile（在扣库存之前，未触发风险时零额外开销）
        boolean isHighRisk = seckillRedisService.needCaptcha(activityId, userId, clientIp, riskCfg);
        if (isHighRisk) {
            String cfToken = request.getCfToken();
            if (cfToken == null || cfToken.isBlank()) {
                // 高风险且未带 token -> 要求前端弹验证码，不消费库存/闸门
                log.info("秒杀风险拦截，要求验证码 - activityId: {}, userId: {}", activityId, userId);
                throw new BusinessException(ResultCode.SECKILL_CAPTCHA_REQUIRED);
            }
            // 带了 token -> 验证（失败抛 TURNSTILE_FAILED）
            turnstileService.verify(cfToken, clientIp);
            // 验证通过 -> 清零该用户维度风险计数（IP 维度保留）
            seckillRedisService.clearRiskCounters(activityId, userId, clientIp);
            log.info("秒杀 Turnstile 验证通过，用户风险计数已清零 - activityId: {}, userId: {}", activityId, userId);
        }

        // 4. 总量闸门（防过载：只允许 库存×k 个请求进入后续流程）
        long gateLimit = (long) activity.getSeckillStock() * seckillProperties.getGateMultiplier();
        long ttlSeconds = seckillRedisService.calcActivityTtlSeconds(activity.getEndTime());
        long gateCurrent = seckillRedisService.incrementGate(activityId, gateLimit, ttlSeconds);
        if (gateCurrent > gateLimit) {
            log.info("秒杀总量闸门拒绝 - activityId: {}, gateCount: {}, limit: {}", activityId, gateCurrent, gateLimit);
            incrementRiskOnReject(activityId, userId, clientIp, ttlSeconds);
            throw new BusinessException(ResultCode.SECKILL_GATE_REJECTED);
        }

        // 5. 售罄标记快速失败
        if (seckillRedisService.isSoldOut(activityId)) {
            log.debug("秒杀售罄标记命中，快速拒绝 - activityId: {}", activityId);
            incrementRiskOnReject(activityId, userId, clientIp, ttlSeconds);
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }

        // 6. 写入排队中状态（SSE 建连前的初始状态，降级查询也可见）
        seckillRedisService.setResult(activityId, userId, "QUEUING");

        // 7. 分桶原子扣减（随机起始桶，-2 时跨桶重试，-1 时懒加载后重试一次）
        Long luaResult = tryDeductWithBucketRetry(activityId, userId, quantity,
                activity.getLimitPerUser(), activity.getAvailableStock(), ttlSeconds);

        if (luaResult == null || luaResult == -1L) {
            // 懒加载后仍未预热：兜底视为服务不可用
            seckillRedisService.setResult(activityId, userId, "FAIL:服务繁忙，请稍后重试");
            throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
        }
        if (luaResult == -2L) {
            // 所有桶均空：真实售罄，置售罄标记
            seckillRedisService.markSoldOut(activityId, seckillProperties.getSoldoutTtl());
            seckillRedisService.setResult(activityId, userId, "FAIL:售罄");
            incrementRiskOnReject(activityId, userId, clientIp, ttlSeconds);
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }
        if (luaResult == -3L) {
            seckillRedisService.setResult(activityId, userId, "FAIL:超过限购");
            incrementRiskOnReject(activityId, userId, clientIp, ttlSeconds);
            throw new BusinessException(ResultCode.SECKILL_LIMIT_EXCEEDED);
        }

        // 8. 生成幂等请求 ID 并投递 MQ；投递失败回补 Redis + 写失败结果
        request.setRequestId(UUID.randomUUID().toString());
        try {
            seckillMessageProducer.sendSeckillMessage(request);
        } catch (Exception e) {
            log.error("秒杀 MQ 投递失败，回补 Redis - activityId: {}, userId: {}", activityId, userId, e);
            seckillRedisService.restoreStock(activityId, userId, quantity, ttlSeconds);
            seckillRedisService.setResult(activityId, userId, "FAIL:服务繁忙，请稍后重试");
            throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
        }

        // 9. 立即返回排队中，前端随即建立 SSE 连接等待最终结果
        return SeckillResultVO.queuing(activityId);
    }

    /**
     * 分桶选桶 + 跨桶重试逻辑
     *
     * 随机起始桶，扣减返回 -2（桶空）时顺序尝试其余桶；
     * 返回 -1（未预热）时先懒加载再重试一次（仅首次）；
     * 所有桶皆空返回 -2；超限购返回 -3（全局键，换桶无意义）；成功返回 1。
     */
    private Long tryDeductWithBucketRetry(Long activityId, Long userId, int quantity,
                                          int limitPerUser, int availableStock, long ttlSeconds) {
        int bucketCount = seckillRedisService.getBucketCount();
        int startBucket = seckillRedisService.randomBucket();
        boolean lazyLoaded = false;

        for (int i = 0; i < bucketCount; i++) {
            int bucket = (startBucket + i) % bucketCount;
            Long result;
            try {
                result = seckillRedisService.tryDeductStockInBucket(
                        activityId, bucket, userId, quantity, limitPerUser, ttlSeconds);
            } catch (Exception e) {
                // Redis 不可用时拒绝请求（不放行，防超卖）
                log.error("秒杀 Redis 不可用 - activityId: {}, bucket: {}", activityId, bucket, e);
                throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
            }

            if (result == null) {
                throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
            }

            if (result == 1L) {
                return 1L;
            }
            if (result == -1L && !lazyLoaded) {
                // 未预热：懒加载后只重试一次（仅在首次 -1 时）
                log.info("秒杀库存未预热，懒加载 - activityId: {}", activityId);
                seckillRedisService.preloadStock(activityId, availableStock, ttlSeconds);
                lazyLoaded = true;
                try {
                    result = seckillRedisService.tryDeductStockInBucket(
                            activityId, bucket, userId, quantity, limitPerUser, ttlSeconds);
                } catch (Exception e) {
                    log.error("秒杀重试扣减 Redis 异常 - activityId: {}", activityId, e);
                    throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
                }
                if (result == null) {
                    throw new BusinessException(ResultCode.SECKILL_SERVICE_UNAVAILABLE);
                }
                if (result == 1L) {
                    return 1L;
                }
                if (result == -3L) {
                    return -3L;
                }
                // 懒加载后仍 -1/-2，继续下一个桶
                continue;
            }
            if (result == -3L) {
                // 超限购：全局 key 控制，换桶无意义
                return -3L;
            }
            // -2（桶空）或 -1（未预热，已懒加载过）：继续下一个桶
        }

        // 所有桶都试完，无库存
        return -2L;
    }

    /**
     * 被拒绝时 INCR 风险失败计数（售罄/超限购/闸门拒）
     * TTL 使用活动剩余时长（活动结束后计数自然过期）
     */
    private void incrementRiskOnReject(Long activityId, Long userId, String clientIp, long ttlSeconds) {
        try {
            seckillRedisService.incrementRiskFail(activityId, userId, clientIp, ttlSeconds);
        } catch (Exception e) {
            log.warn("更新风险失败计数异常 - activityId: {}, userId: {}", activityId, userId, e);
        }
    }

    @Override
    public SeckillResultVO getSeckillResult(Long activityId, Long userId) {
        String raw = seckillRedisService.getResult(activityId, userId);
        return parseResult(activityId, raw);
    }

    /**
     * 解析 Redis 结果标记为 VO
     */
    private SeckillResultVO parseResult(Long activityId, String raw) {
        if (raw == null) {
            return SeckillResultVO.notFound(activityId);
        }
        if ("QUEUING".equals(raw)) {
            return SeckillResultVO.queuing(activityId);
        }
        if (raw.startsWith("SUCCESS:")) {
            return SeckillResultVO.success(activityId, raw.substring("SUCCESS:".length()));
        }
        if (raw.startsWith("FAIL:")) {
            return SeckillResultVO.fail(activityId, raw.substring("FAIL:".length()));
        }
        return SeckillResultVO.notFound(activityId);
    }

    @Override
    public SseEmitter subscribeResult(Long activityId, Long userId) {
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        // 先注册连接，再检查已有结果：避免"消费者在建连间隙完成推送"导致结果丢失
        SseEmitter emitter = seckillSseService.register(activityId, userId);
        String raw = seckillRedisService.getResult(activityId, userId);
        if (raw != null && (raw.startsWith("SUCCESS:") || raw.startsWith("FAIL:"))) {
            // 结果已是最终态（消费者先于建连完成）：立即推送并关闭连接
            seckillSseService.pushResult(activityId, userId, parseResult(activityId, raw));
        }
        return emitter;
    }

    @Override
    public PageResult<SeckillOrderVO> getMySeckillOrders(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<SeckillOrderDO> orders = seckillOrderMapper.findByUserId(userId);
        PageInfo<SeckillOrderDO> pageInfo = new PageInfo<>(orders);
        List<SeckillOrderVO> voList = seckillConverter.toOrderVOList(orders);
        return new PageResult<>(voList, pageInfo.getTotal(), page, size);
    }

    // ======================== 管理端 ========================

    @Override
    public SeckillActivityVO adminGetActivityDetail(Long activityId) {
        // 管理端不过滤 status：草稿/下线活动均可查看（编辑回显）
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    public PageResult<SeckillActivityVO> adminListActivities(int page, int size) {
        PageHelper.startPage(page, size);
        List<SeckillActivityDO> activities = seckillActivityMapper.findAllForAdmin();
        PageInfo<SeckillActivityDO> pageInfo = new PageInfo<>(activities);
        List<SeckillActivityVO> voList = seckillConverter.toVOList(activities);
        return new PageResult<>(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivityVO createActivity(SeckillActivityDTO dto) {
        validateActivityDTO(dto);

        SeckillActivityDO activity = seckillConverter.toDO(dto);
        // 主键为应用层雪花 ID（防对外暴露可枚举自增 ID），insert 前显式赋值
        activity.setId(snowflakeIdGenerator.nextId());
        // available_stock 初始等于 seckill_stock（草稿态未预留，账面占位）
        activity.setAvailableStock(dto.getSeckillStock());
        // 创建默认草稿态，上线走 activate 接口（伴随库存预留与预热）
        activity.setStatus(0);
        // 无 SKU 商品 skuId 取 0（列为 NOT NULL DEFAULT 0，显式传 null 会违约）
        if (activity.getSkuId() == null) {
            activity.setSkuId(0L);
        }
        // 二次流出开关默认允许
        if (activity.getAllowRestock() == null) {
            activity.setAllowRestock(1);
        }

        seckillActivityMapper.insert(activity);
        log.info("创建秒杀活动成功 - id: {}, name: {}", activity.getId(), activity.getActivityName());
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivityVO updateActivity(Long activityId, SeckillActivityDTO dto) {
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(activity.getStatus())) {
            // D11：不提供"上线中静默改库存"的能力，需先下线（归还预留）再编辑
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "活动已上线，请先下线后再编辑");
        }
        validateActivityDTO(dto);

        seckillConverter.updateDOFromDTO(dto, activity);
        // 草稿态未预留库存，可用库存跟随秒杀总库存
        activity.setAvailableStock(activity.getSeckillStock());
        if (activity.getSkuId() == null) {
            activity.setSkuId(0L);
        }
        seckillActivityMapper.updateById(activity);
        log.info("更新秒杀活动成功 - id: {}", activityId);
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivityVO activateActivity(Long activityId) {
        // 行写锁串行化并发上线操作
        SeckillActivityDO activity = seckillActivityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "活动已上线");
        }
        if (!LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }
        ProductDO product = productMapper.findById(activity.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
        }

        int seckillStock = activity.getSeckillStock();

        // 1. 从商品库存真正预留（乐观锁 stock >= qty，不足整体回滚，不产生负库存）
        int reserved = productMapper.decreaseStock(activity.getProductId(), seckillStock);
        if (reserved == 0) {
            throw new BusinessException(ResultCode.SECKILL_STOCK_EXCEEDED);
        }

        // 2. 活动置上线：available_stock 重置为 seckill_stock，清归还标记（status=0 守卫防重复预留）
        int activated = seckillActivityMapper.activateById(activityId, seckillStock);
        if (activated == 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "活动已上线");
        }

        // 3. 同事务联动扣减商品 Redis 库存（失败回滚时自动恢复）
        deductProductRedis(activity.getProductId(), seckillStock);

        // 4. 预热并入上线动作（D11）：DB 预留提交后同步完成分桶灌入 + 闸门初始化
        final Long productId = activity.getProductId();
        final LocalDateTime endTime = activity.getEndTime();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    doPreheat(activityId, seckillStock, seckillStock, endTime);
                } catch (Exception e) {
                    // 预热失败不影响上线结果：抢购链路有懒加载兜底，管理端可手动重新预热
                    log.error("上线后预热失败（可手动重新预热或依赖懒加载兜底）- activityId: {}, productId: {}",
                            activityId, productId, e);
                }
            }
        });

        log.info("秒杀活动上线成功，已预留商品库存 - activityId: {}, productId: {}, reserved: {}",
                activityId, activity.getProductId(), seckillStock);

        activity.setStatus(1);
        activity.setAvailableStock(seckillStock);
        activity.setStockReclaimed(0);
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivityVO offlineActivity(Long activityId) {
        // 行写锁冻结 available_stock，与消费者扣减/超时回补串行
        SeckillActivityDO activity = seckillActivityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "活动未上线");
        }

        // 归还未售库存（markReclaimed 带 stock_reclaimed=0 守卫，幂等：结束 Job 已归还时跳过）
        int claimed = seckillActivityMapper.markReclaimed(activityId);
        if (claimed == 1 && activity.getAvailableStock() != null && activity.getAvailableStock() > 0) {
            productMapper.increaseStock(activity.getProductId(), activity.getAvailableStock());
            restoreProductRedis(activity.getProductId(), activity.getAvailableStock());
            log.info("下线归还未售库存 - activityId: {}, productId: {}, returned: {}",
                    activityId, activity.getProductId(), activity.getAvailableStock());
        }

        seckillActivityMapper.offlineById(activityId);

        // 提交后清理活动 Redis 键（分桶/售罄标记/闸门）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    seckillRedisService.removeStock(activityId);
                } catch (Exception e) {
                    log.warn("下线清理活动 Redis 键失败 - activityId: {}", activityId, e);
                }
            }
        });

        log.info("秒杀活动已下线 - activityId: {}", activityId);
        activity.setStatus(0);
        activity.setStockReclaimed(1);
        return seckillConverter.toVOWithStatus(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long activityId) {
        SeckillActivityDO activity = seckillActivityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(activity.getStatus())) {
            // 上线态持有预留库存，必须先下线（触发归还）再删除
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "活动已上线，请先下线再删除");
        }
        seckillActivityMapper.deleteById(activityId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    seckillRedisService.removeStock(activityId);
                } catch (Exception e) {
                    log.warn("删除清理活动 Redis 键失败 - activityId: {}", activityId, e);
                }
            }
        });
        log.info("秒杀活动已删除 - activityId: {}", activityId);
    }

    @Override
    public void preheatActivity(Long activityId) {
        SeckillActivityDO activity = seckillActivityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED, "活动未上线，无法预热");
        }
        // 以最新 available_stock 覆盖写分桶 + 初始化闸门（管理端手动重新预热总是覆盖）
        doPreheat(activityId, activity.getAvailableStock(), activity.getSeckillStock(), activity.getEndTime());
        log.info("手动重新预热秒杀库存 - activityId: {}, stock: {}", activityId, activity.getAvailableStock());
    }

    /**
     * 预热核心逻辑：availableStock 覆盖写入分桶（清售罄标记）+ 初始化闸门
     * 供上线动作内部调用与管理端手动重新预热复用
     */
    private void doPreheat(Long activityId, int availableStock, int seckillStock, LocalDateTime endTime) {
        long ttlSeconds = seckillRedisService.calcActivityTtlSeconds(endTime);
        seckillRedisService.setStock(activityId, availableStock, ttlSeconds);
        long gateLimit = (long) seckillStock * seckillProperties.getGateMultiplier();
        seckillRedisService.initGate(activityId, gateLimit, ttlSeconds);
    }

    // ======================== 结束归还（Job 调用） ========================

    @Override
    public List<Long> listEndedUnreclaimedIds() {
        return seckillActivityMapper.findEndedUnreclaimed(LocalDateTime.now()).stream()
                .map(SeckillActivityDO::getId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reclaimActivityStock(Long activityId) {
        // 行写锁冻结 available_stock，与消费者扣减/超时回补/下线归还串行
        SeckillActivityDO activity = seckillActivityMapper.selectForUpdate(activityId);
        if (activity == null) {
            return;
        }
        // 幂等守卫：仅处理已上线、未归还且已过结束时间的活动
        if (!Integer.valueOf(1).equals(activity.getStatus())
                || Integer.valueOf(1).equals(activity.getStockReclaimed())
                || LocalDateTime.now().isBefore(activity.getEndTime())) {
            return;
        }
        int claimed = seckillActivityMapper.markReclaimed(activityId);
        if (claimed == 0) {
            return;
        }
        if (activity.getAvailableStock() != null && activity.getAvailableStock() > 0) {
            productMapper.increaseStock(activity.getProductId(), activity.getAvailableStock());
            restoreProductRedis(activity.getProductId(), activity.getAvailableStock());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    seckillRedisService.removeStock(activityId);
                } catch (Exception e) {
                    log.warn("结束归还清理活动 Redis 键失败 - activityId: {}", activityId, e);
                }
            }
        });
        log.info("活动结束归还未售库存 - activityId: {}, productId: {}, returned: {}",
                activityId, activity.getProductId(), activity.getAvailableStock());
    }

    // ======================== 私有方法 ========================

    /**
     * 创建/更新活动的业务校验
     */
    private void validateActivityDTO(SeckillActivityDTO dto) {
        // 时间校验：结束时间必须晚于开始时间
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_TIME_INVALID);
        }
        // 秒杀价校验：不得高于原价
        if (dto.getSeckillPrice().compareTo(dto.getOriginalPrice()) > 0) {
            throw new BusinessException(ResultCode.SECKILL_PRICE_INVALID);
        }
        // 商品存在且上架校验
        ProductDO product = productMapper.findById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
        }
        // 秒杀库存不能超过商品当前库存（上线预留时以乐观锁再次兜底）
        if (dto.getSeckillStock() > product.getStock()) {
            throw new BusinessException(ResultCode.SECKILL_STOCK_EXCEEDED);
        }
        // 指定 SKU 时校验其存在、归属与状态
        if (dto.getSkuId() != null && dto.getSkuId() > 0) {
            SkuDO sku = skuMapper.findById(dto.getSkuId());
            if (sku == null || !sku.getProductId().equals(dto.getProductId())) {
                throw new BusinessException(ResultCode.SKU_NOT_FOUND);
            }
            if (!Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BusinessException(ResultCode.SKU_UNAVAILABLE);
            }
        }
    }

    /**
     * 同事务联动扣减商品 Redis 库存（上线预留时调用）
     * 扣减成功则注册回滚补偿；Redis 与 DB 漂移（-2）时删除缓存强制下次懒加载重读 DB；
     * 缓存未加载（-1）无需处理（后续懒加载会读到预留后的 DB 值）
     */
    private void deductProductRedis(Long productId, int quantity) {
        OrderItemDO item = new OrderItemDO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        final List<OrderItemDO> items = List.of(item);
        try {
            Long result = stockRedisService.batchDeductStock(items);
            if (Long.valueOf(1L).equals(result)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                stockRedisService.batchRestoreStock(items);
                            } catch (Exception e) {
                                log.error("上线回滚后恢复商品 Redis 库存异常 - productId: {}", productId, e);
                            }
                        }
                    }
                });
            } else if (Long.valueOf(-2L).equals(result)) {
                // Redis 账面不足但 DB 预留成功：缓存漂移，删除缓存以 DB 为准
                stockRedisService.removeStock(productId);
                log.warn("商品 Redis 库存与 DB 漂移，已删除缓存 - productId: {}", productId);
            }
            // -1：缓存未加载，无需处理
        } catch (Exception e) {
            log.warn("上线联动扣减商品 Redis 库存异常，删除缓存以 DB 为准 - productId: {}", productId, e);
            try {
                stockRedisService.removeStock(productId);
            } catch (Exception ex) {
                log.warn("删除商品 Redis 库存缓存失败 - productId: {}", productId, ex);
            }
        }
    }

    /**
     * 联动恢复商品 Redis 库存（下线/结束归还时调用）
     * 仅对已存在的 key 执行 INCRBY，key 不存在说明缓存已失效、懒加载会重读 DB
     */
    private void restoreProductRedis(Long productId, int quantity) {
        OrderItemDO item = new OrderItemDO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        try {
            stockRedisService.batchRestoreStock(List.of(item));
        } catch (Exception e) {
            log.warn("归还联动恢复商品 Redis 库存异常 - productId: {}", productId, e);
        }
    }
}
