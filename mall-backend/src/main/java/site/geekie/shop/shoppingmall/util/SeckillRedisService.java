package site.geekie.shop.shoppingmall.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.config.SeckillProperties;
import site.geekie.shop.shoppingmall.config.SeckillProperties.Risk;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀 Redis 操作封装（分桶版）
 *
 * Key 命名空间（独立于商品库存缓存 stock:product:）：
 *   seckill:stock:{activityId}:{bucket}       — 分桶库存（bucket=0..N-1），String 类型
 *   seckill:limit:{activityId}:{userId}       — 用户已抢数量（全局，不随分桶变化），String 类型
 *   seckill:soldout:{activityId}              — 售罄快速失败标记，带 TTL
 *   seckill:gate:{activityId}                 — 总量闸门计数器
 *   seckill:result:{activityId}:{userId}      — 异步结果标记，String 类型，带 TTL
 *   seckill:risk:fail:{activityId}:{userId}   — 用户失败累计计数（风险信号）
 *   seckill:risk:fail:{activityId}:ip:{ip}    — IP 失败累计计数（风险信号）
 *   seckill:risk:freq:{activityId}:{identity} — 频率滑动窗口计数
 *   seckill:msg:done:{requestId}              — 消息去重标记，带 TTL
 *
 * 结果标记格式：
 *   QUEUING           — 已入队，等待消费者处理
 *   SUCCESS:{orderNo} — 抢购成功，订单号附后
 *   FAIL:{reason}     — 抢购失败，原因附后
 *
 * 使用说明：
 *   tryDeductStockInBucket 对单个桶执行原子 Lua：返回 1=成功 / -1=桶未预热 / -2=桶空 / -3=超限购
 *   restoreStock 回补库存到任意存在的桶（总量守恒），同步扣回用户限购计数并清除售罄标记
 *   preloadStock 按桶均摊写入，幂等（SETNX），并清除售罄标记（懒加载兜底用）
 *   setStock 强制覆盖所有桶（上线预热 / 管理端手动重新预热），并清除售罄标记
 *   removeStock 删除所有桶及售罄标记、闸门（下线/结束清理）
 *   rollbackGate 回退闸门额度（超时释放库存重新放出时调用）
 *   Redis 整体不可用时，异常向上抛出，由 SeckillService 捕获后拒绝请求（不放行，防超卖）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillProperties seckillProperties;

    // ======================== Key 前缀常量 ========================

    private static final String STOCK_KEY_PREFIX   = "seckill:stock:";
    private static final String LIMIT_KEY_PREFIX   = "seckill:limit:";
    private static final String SOLDOUT_KEY_PREFIX = "seckill:soldout:";
    private static final String GATE_KEY_PREFIX    = "seckill:gate:";
    private static final String RESULT_KEY_PREFIX  = "seckill:result:";
    private static final String RISK_FAIL_USER_PREFIX = "seckill:risk:fail:";
    private static final String RISK_FAIL_IP_INFIX  = ":ip:";
    private static final String RISK_FREQ_PREFIX    = "seckill:risk:freq:";
    private static final String MSG_DONE_PREFIX     = "seckill:msg:done:";

    // ======================== Lua 脚本 ========================

    /**
     * 分桶原子扣减脚本（针对单个桶 + 全局限购 key）
     *
     * KEYS[1] = seckill:stock:{activityId}:{bucket}
     * KEYS[2] = seckill:limit:{activityId}:{userId}
     * ARGV[1] = quantity（本次购买数量）
     * ARGV[2] = limitPerUser（该活动每人限购上限）
     * ARGV[3] = ttlSeconds（key TTL，秒）
     *
     * 返回值：
     *   1  — 成功，已扣减桶库存并累加用户计数
     *  -1  — 桶 key 不存在（未预热）
     *  -2  — 桶库存不足（桶空，需换桶重试）
     *  -3  — 用户已购数量 + 本次 > limitPerUser（超过限购）
     */
    private static final DefaultRedisScript<Long> SECKILL_DEDUCT_SCRIPT;

    /**
     * 回补脚本（回补到指定桶）
     * 仅在桶 key 存在时才执行回补、限购计数扣回和售罄清除（三者绑定同一条件分支，保持原子）；
     * 桶不存在返回 0，由调用方轮询下一桶。
     *
     * KEYS[1] = seckill:stock:{activityId}:{bucket}
     * KEYS[2] = seckill:limit:{activityId}:{userId}
     * KEYS[3] = seckill:soldout:{activityId}
     * ARGV[1] = quantity（回补数量）
     * ARGV[2] = ttlSeconds（key TTL，秒）
     */
    private static final DefaultRedisScript<Long> SECKILL_RESTORE_SCRIPT;

    /**
     * 闸门 INCR 检查脚本（仅首次创建时设置 TTL）
     *
     * KEYS[1] = seckill:gate:{activityId}
     * ARGV[1] = gateLimit（上限，保留参数位）
     * ARGV[2] = ttlSeconds（初次设置 TTL，仅当 key 不存在时生效）
     *
     * 返回值：INCR 后的当前值（> gateLimit 表示被拒绝）
     */
    private static final DefaultRedisScript<Long> GATE_INCR_SCRIPT;

    /**
     * 闸门回退脚本：DECRBY 并下限归零（key 不存在时不创建）
     *
     * KEYS[1] = seckill:gate:{activityId}
     * ARGV[1] = quantity（回退额度）
     */
    private static final DefaultRedisScript<Long> GATE_ROLLBACK_SCRIPT;

    /**
     * 用户限购计数扣回脚本：减少已购计数并下限归零（key 不存在时不创建）
     * 用于"不允许二次流出"的超时回补——限购额度释放但库存不回分桶
     *
     * KEYS[1] = seckill:limit:{activityId}:{userId}
     * ARGV[1] = quantity
     * ARGV[2] = ttlSeconds
     */
    private static final DefaultRedisScript<Long> LIMIT_RESTORE_SCRIPT;

    static {
        // 分桶原子扣减脚本
        SECKILL_DEDUCT_SCRIPT = new DefaultRedisScript<>();
        SECKILL_DEDUCT_SCRIPT.setScriptText(
                "local stock = tonumber(redis.call('get', KEYS[1]))\n" +
                "if stock == nil then return -1 end\n" +
                "local qty = tonumber(ARGV[1])\n" +
                "if stock < qty then return -2 end\n" +
                "local limit = tonumber(ARGV[2])\n" +
                "local bought = tonumber(redis.call('get', KEYS[2])) or 0\n" +
                "if bought + qty > limit then return -3 end\n" +
                "redis.call('decrby', KEYS[1], qty)\n" +
                "redis.call('expire', KEYS[1], ARGV[3])\n" +
                "redis.call('incrby', KEYS[2], qty)\n" +
                "redis.call('expire', KEYS[2], ARGV[3])\n" +
                "return 1"
        );
        SECKILL_DEDUCT_SCRIPT.setResultType(Long.class);

        // 回补脚本：桶不存在时直接 return 0，不扣 limit 也不清 soldout（脚本内单次原子 exists，无 TOCTOU）
        SECKILL_RESTORE_SCRIPT = new DefaultRedisScript<>();
        SECKILL_RESTORE_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[1]) == 0 then\n" +
                "    return 0\n" +
                "end\n" +
                "redis.call('incrby', KEYS[1], ARGV[1])\n" +
                "redis.call('expire', KEYS[1], ARGV[2])\n" +
                "if redis.call('exists', KEYS[2]) == 1 then\n" +
                "    local bought = tonumber(redis.call('get', KEYS[2])) or 0\n" +
                "    local qty = tonumber(ARGV[1])\n" +
                "    local newVal = bought - qty\n" +
                "    if newVal < 0 then newVal = 0 end\n" +
                "    redis.call('set', KEYS[2], newVal)\n" +
                "    redis.call('expire', KEYS[2], ARGV[2])\n" +
                "end\n" +
                "redis.call('del', KEYS[3])\n" +
                "return 1"
        );
        SECKILL_RESTORE_SCRIPT.setResultType(Long.class);

        // 闸门 INCR 脚本（仅首次设置 TTL）
        GATE_INCR_SCRIPT = new DefaultRedisScript<>();
        GATE_INCR_SCRIPT.setScriptText(
                "local current = redis.call('incr', KEYS[1])\n" +
                "if current == 1 then\n" +
                "    redis.call('expire', KEYS[1], ARGV[2])\n" +
                "end\n" +
                "return current"
        );
        GATE_INCR_SCRIPT.setResultType(Long.class);

        // 闸门回退脚本
        GATE_ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        GATE_ROLLBACK_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[1]) == 0 then return 0 end\n" +
                "local v = redis.call('decrby', KEYS[1], ARGV[1])\n" +
                "if v < 0 then redis.call('set', KEYS[1], 0) end\n" +
                "return 1"
        );
        GATE_ROLLBACK_SCRIPT.setResultType(Long.class);

        // 用户限购计数扣回脚本
        LIMIT_RESTORE_SCRIPT = new DefaultRedisScript<>();
        LIMIT_RESTORE_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[1]) == 0 then return 0 end\n" +
                "local bought = tonumber(redis.call('get', KEYS[1])) or 0\n" +
                "local newVal = bought - tonumber(ARGV[1])\n" +
                "if newVal < 0 then newVal = 0 end\n" +
                "redis.call('set', KEYS[1], newVal)\n" +
                "redis.call('expire', KEYS[1], ARGV[2])\n" +
                "return 1"
        );
        LIMIT_RESTORE_SCRIPT.setResultType(Long.class);
    }

    // ======================== 核心扣减（分桶） ========================

    /**
     * 对单个桶执行原子扣减（分桶版）
     *
     * @param activityId   活动ID
     * @param bucket       桶编号（0..bucketCount-1）
     * @param userId       用户ID
     * @param quantity     本次购买数量
     * @param limitPerUser 该活动每人限购上限
     * @param ttlSeconds   key TTL（秒）
     * @return 1=成功 / -1=未预热 / -2=桶空 / -3=超限购
     */
    public Long tryDeductStockInBucket(Long activityId, int bucket, Long userId,
                                       int quantity, int limitPerUser, long ttlSeconds) {
        String stockKey = bucketKey(activityId, bucket);
        String limitKey = limitKey(activityId, userId);

        Long result = stringRedisTemplate.execute(
                SECKILL_DEDUCT_SCRIPT,
                List.of(stockKey, limitKey),
                String.valueOf(quantity),
                String.valueOf(limitPerUser),
                String.valueOf(ttlSeconds)
        );
        log.debug("秒杀分桶扣减 - activityId: {}, bucket: {}, userId: {}, qty: {}, result: {}",
                activityId, bucket, userId, quantity, result);
        return result;
    }

    /**
     * 回补库存到任意存在的桶（总量守恒），同步扣回用户限购计数并清除售罄标记
     *
     * 从随机起始桶顺序轮询，由 Lua 脚本内单次原子 exists 决定是否回补（无 TOCTOU 窗口）；
     * 所有桶均不存在时（缓存整体失效）跳过 Redis，DB available_stock 是最终兜底。
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param quantity   回补数量
     * @param ttlSeconds key TTL（秒）
     */
    public void restoreStock(Long activityId, Long userId, int quantity, long ttlSeconds) {
        int bucketCount = seckillProperties.getBucketCount();
        String limitKey = limitKey(activityId, userId);
        String soldoutKey = soldoutKey(activityId);

        try {
            int startBucket = ThreadLocalRandom.current().nextInt(bucketCount);
            for (int i = 0; i < bucketCount; i++) {
                int bucket = (startBucket + i) % bucketCount;
                String bucketStockKey = bucketKey(activityId, bucket);
                Long result = stringRedisTemplate.execute(
                        SECKILL_RESTORE_SCRIPT,
                        List.of(bucketStockKey, limitKey, soldoutKey),
                        String.valueOf(quantity),
                        String.valueOf(ttlSeconds)
                );
                if (Long.valueOf(1L).equals(result)) {
                    log.debug("秒杀回补库存成功 - activityId: {}, bucket: {}, userId: {}, qty: {}",
                            activityId, bucket, userId, quantity);
                    return;
                }
                // result == 0：桶 key 不存在，继续尝试下一桶
            }
            log.debug("秒杀回补：所有桶 key 不存在（缓存已失效），依赖 DB 兜底 - activityId: {}", activityId);
        } catch (Exception e) {
            // 回补失败不影响主流程，记录日志由运维处理（DB available_stock 是最终兜底）
            log.warn("秒杀回补 Redis 库存失败 - activityId: {}, userId: {}, qty: {}",
                    activityId, userId, quantity, e);
        }
    }

    /**
     * 仅扣回用户限购计数，不回补分桶库存
     * 用于"不允许二次流出"（allow_restock=0）活动的超时回补：
     * 限购额度随订单取消释放（与 DB SUM 排除 CANCELLED 对齐），但库存不重新放出。
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param quantity   扣回数量
     * @param ttlSeconds key TTL（秒）
     */
    public void restoreUserLimit(Long activityId, Long userId, int quantity, long ttlSeconds) {
        try {
            stringRedisTemplate.execute(
                    LIMIT_RESTORE_SCRIPT,
                    List.of(limitKey(activityId, userId)),
                    String.valueOf(quantity),
                    String.valueOf(ttlSeconds)
            );
        } catch (Exception e) {
            log.warn("扣回用户限购计数失败 - activityId: {}, userId: {}", activityId, userId, e);
        }
    }

    /**
     * 预热秒杀库存（SETNX，幂等，不覆盖已存在 key）
     * 按桶均摊：余数前几桶多分配 1 个库存。同时清除售罄标记。
     * 用于抢购链路的懒加载兜底（未预热时首次抢购自动填充一次）。
     *
     * @param activityId     活动ID
     * @param availableStock 当前可用库存
     * @param ttlSeconds     key TTL（秒）
     */
    public void preloadStock(Long activityId, int availableStock, long ttlSeconds) {
        int bucketCount = seckillProperties.getBucketCount();
        int base = availableStock / bucketCount;
        int remainder = availableStock % bucketCount;

        // 清除旧的售罄标记
        stringRedisTemplate.delete(soldoutKey(activityId));

        int loaded = 0;
        for (int i = 0; i < bucketCount; i++) {
            int bucketStock = base + (i < remainder ? 1 : 0);
            String key = bucketKey(activityId, i);
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(bucketStock), ttlSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                loaded++;
            }
        }
        if (loaded > 0) {
            log.info("秒杀库存预热（懒加载）- activityId: {}, totalStock: {}, buckets: {}, loadedBuckets: {}",
                    activityId, availableStock, bucketCount, loaded);
        } else {
            log.debug("秒杀库存预热跳过（所有桶 key 已存在）- activityId: {}", activityId);
        }
    }

    /**
     * 强制设置秒杀库存（覆盖所有桶）
     * 用于上线动作的同步预热与管理端手动重新预热（总是以最新 available_stock 覆盖）。
     * 同时清除售罄标记。
     *
     * @param activityId     活动ID
     * @param availableStock 当前可用库存
     * @param ttlSeconds     key TTL（秒）
     */
    public void setStock(Long activityId, int availableStock, long ttlSeconds) {
        int bucketCount = seckillProperties.getBucketCount();
        int base = availableStock / bucketCount;
        int remainder = availableStock % bucketCount;

        for (int i = 0; i < bucketCount; i++) {
            int bucketStock = base + (i < remainder ? 1 : 0);
            String key = bucketKey(activityId, i);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(bucketStock), ttlSeconds, TimeUnit.SECONDS);
        }
        // 清除售罄标记
        stringRedisTemplate.delete(soldoutKey(activityId));
        log.info("秒杀库存覆盖写入 - activityId: {}, totalStock: {}, buckets: {}",
                activityId, availableStock, bucketCount);
    }

    /**
     * 删除秒杀库存缓存（活动下线/删除/结束归还时调用）
     * 删除所有桶 + 售罄标记 + 闸门
     *
     * @param activityId 活动ID
     */
    public void removeStock(Long activityId) {
        int bucketCount = seckillProperties.getBucketCount();
        for (int i = 0; i < bucketCount; i++) {
            stringRedisTemplate.delete(bucketKey(activityId, i));
        }
        stringRedisTemplate.delete(soldoutKey(activityId));
        stringRedisTemplate.delete(gateKey(activityId));
        log.debug("秒杀库存缓存已清理 - activityId: {}", activityId);
    }

    // ======================== 售罄标记 ========================

    /**
     * 查询售罄标记是否存在（快速失败检测）
     */
    public boolean isSoldOut(Long activityId) {
        Boolean exists = stringRedisTemplate.hasKey(soldoutKey(activityId));
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 设置售罄标记
     *
     * @param activityId 活动ID
     * @param ttlSeconds 售罄标记 TTL（秒）
     */
    public void markSoldOut(Long activityId, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(soldoutKey(activityId), "1", ttlSeconds, TimeUnit.SECONDS);
        log.info("秒杀售罄标记已设置 - activityId: {}", activityId);
    }

    /**
     * 清除售罄标记（回补时主动清除，避免假性售罄）
     */
    public void clearSoldOut(Long activityId) {
        stringRedisTemplate.delete(soldoutKey(activityId));
    }

    // ======================== 总量闸门 ========================

    /**
     * 初始化闸门计数器（预热时调用，重置为 0）
     *
     * @param activityId 活动ID
     * @param gateLimit  上限（= 秒杀库存 × gateMultiplier）
     * @param ttlSeconds 闸门 TTL（秒），应覆盖活动时长
     */
    public void initGate(Long activityId, long gateLimit, long ttlSeconds) {
        String key = gateKey(activityId);
        stringRedisTemplate.opsForValue().set(key, "0", ttlSeconds, TimeUnit.SECONDS);
        log.info("秒杀闸门初始化 - activityId: {}, limit: {}", activityId, gateLimit);
    }

    /**
     * 闸门 INCR：请求计数 +1，返回当前值，超过 gateLimit 表示被拒
     *
     * @param activityId 活动ID
     * @param gateLimit  上限
     * @param ttlSeconds 首次创建时的 TTL（兜底，已有 key 不会重置 TTL）
     * @return INCR 后的当前计数
     */
    public long incrementGate(Long activityId, long gateLimit, long ttlSeconds) {
        Long current = stringRedisTemplate.execute(
                GATE_INCR_SCRIPT,
                List.of(gateKey(activityId)),
                String.valueOf(gateLimit),
                String.valueOf(ttlSeconds)
        );
        return current == null ? 1L : current;
    }

    /**
     * 闸门回退：超时释放的库存重新放出时按回补量退还闸门额度
     * （不退闸门，回流库存会被闸门防线挡住，永远无人能抢到）
     *
     * @param activityId 活动ID
     * @param quantity   回退额度
     */
    public void rollbackGate(Long activityId, int quantity) {
        try {
            stringRedisTemplate.execute(
                    GATE_ROLLBACK_SCRIPT,
                    List.of(gateKey(activityId)),
                    String.valueOf(quantity)
            );
        } catch (Exception e) {
            log.warn("秒杀闸门回退失败 - activityId: {}, qty: {}", activityId, quantity, e);
        }
    }

    // ======================== 风险信号 ========================

    /**
     * 用户/IP 失败计数 +1（被拒绝时调用）
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param clientIp   客户端 IP
     * @param ttlSeconds 计数 key TTL（秒）
     */
    public void incrementRiskFail(Long activityId, Long userId, String clientIp, long ttlSeconds) {
        String userKey = riskFailUserKey(activityId, userId);
        Long userCnt = stringRedisTemplate.opsForValue().increment(userKey);
        if (userCnt != null && userCnt == 1L) {
            stringRedisTemplate.expire(userKey, ttlSeconds, TimeUnit.SECONDS);
        }
        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = riskFailIpKey(activityId, clientIp);
            Long ipCnt = stringRedisTemplate.opsForValue().increment(ipKey);
            if (ipCnt != null && ipCnt == 1L) {
                stringRedisTemplate.expire(ipKey, ttlSeconds, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 频率计数：滑动窗口请求数 +1，返回当前计数（INCR + 首次设 expire）
     *
     * @param activityId    活动ID
     * @param identity      userId 或 ip 字符串（统一标识）
     * @param windowSeconds 窗口时长（秒）
     * @return 当前窗口内请求计数
     */
    public long incrementRiskFreq(Long activityId, String identity, int windowSeconds) {
        String key = riskFreqKey(activityId, identity);
        Long cnt = stringRedisTemplate.opsForValue().increment(key);
        if (cnt != null && cnt == 1L) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return cnt == null ? 1L : cnt;
    }

    /**
     * 判断是否需要人机验证（任一风险信号超阈值）
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param clientIp   客户端 IP
     * @param riskCfg    风控阈值配置（failThreshold / freqThreshold）
     * @return true 表示需要验证码
     */
    public boolean needCaptcha(Long activityId, Long userId, String clientIp, Risk riskCfg) {
        int failThreshold = riskCfg.getFailThreshold();
        int freqThreshold = riskCfg.getFreqThreshold();
        // 检查用户失败计数
        String userFailVal = stringRedisTemplate.opsForValue().get(riskFailUserKey(activityId, userId));
        if (userFailVal != null && Integer.parseInt(userFailVal) >= failThreshold) {
            return true;
        }
        // 检查 IP 失败计数
        if (clientIp != null && !clientIp.isBlank()) {
            String ipFailVal = stringRedisTemplate.opsForValue().get(riskFailIpKey(activityId, clientIp));
            if (ipFailVal != null && Integer.parseInt(ipFailVal) >= failThreshold) {
                return true;
            }
        }
        // 检查频率（INCR 已在入口完成，此处仅 GET 判断）
        String userFreqVal = stringRedisTemplate.opsForValue().get(riskFreqKey(activityId, String.valueOf(userId)));
        if (userFreqVal != null && Integer.parseInt(userFreqVal) >= freqThreshold) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        String ipFreqVal = stringRedisTemplate.opsForValue().get(riskFreqKey(activityId, clientIp));
        return ipFreqVal != null && Integer.parseInt(ipFreqVal) >= freqThreshold;
    }

    /**
     * 清零 userId 维度的风险计数（Turnstile 验证通过后调用，避免正常用户反复被要求验证）
     *
     * 验证通过仅清零 userId 维度，IP 维度保留不清零：
     * 一个真人过验证不应漂白整个 IP 的风险记录——若该 IP 同时有其他恶意用户，
     * 清零 IP 计数会导致 IP 维度的风控失效。
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param clientIp   客户端 IP（保留但不操作，签名兼容）
     */
    public void clearRiskCounters(Long activityId, Long userId, String clientIp) {
        stringRedisTemplate.delete(riskFailUserKey(activityId, userId));
        stringRedisTemplate.delete(riskFreqKey(activityId, String.valueOf(userId)));
        log.debug("秒杀用户维度风险计数已清零（IP 维度保留）- activityId: {}, userId: {}", activityId, userId);
    }

    // ======================== 消息去重 ========================

    /**
     * 原子抢占幂等位（SETNX 语义）：消费入口调用，仅首次成功
     *
     * setIfAbsent（SETNX + TTL 原子完成），首次返回 true，重复消费返回 false。
     *
     * @param requestId  请求 ID
     * @param ttlSeconds 去重 key TTL（秒）
     * @return true=抢占成功，继续处理；false=重复消费，跳过
     */
    public boolean tryClaimMessage(String requestId, long ttlSeconds) {
        Boolean claimed = stringRedisTemplate.opsForValue()
                .setIfAbsent(msgDoneKey(requestId), "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(claimed);
    }

    // ======================== 结果标记读写 ========================

    /**
     * 写入抢购结果标记
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param result     结果字符串（"QUEUING" / "SUCCESS:{orderNo}" / "FAIL:{reason}"）
     */
    public void setResult(Long activityId, Long userId, String result) {
        String key = resultKey(activityId, userId);
        stringRedisTemplate.opsForValue().set(key, result, seckillProperties.getResultTtl(), TimeUnit.SECONDS);
        log.debug("写入秒杀结果 - activityId: {}, userId: {}, result: {}", activityId, userId, result);
    }

    /**
     * 读取抢购结果标记
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 结果字符串，key 不存在或已过期时返回 null
     */
    public String getResult(Long activityId, Long userId) {
        return stringRedisTemplate.opsForValue().get(resultKey(activityId, userId));
    }

    // ======================== 工具方法 ========================

    /**
     * 随机获取起始桶编号（ThreadLocalRandom，无竞争）
     */
    public int randomBucket() {
        return ThreadLocalRandom.current().nextInt(seckillProperties.getBucketCount());
    }

    /**
     * 获取配置的桶数量
     */
    public int getBucketCount() {
        return seckillProperties.getBucketCount();
    }

    /**
     * 计算活动相关 key 的 TTL（秒）：活动剩余时长 + 1h 兜底，且不低于售罄标记 TTL
     *
     * @param endTime 活动结束时间
     */
    public long calcActivityTtlSeconds(LocalDateTime endTime) {
        long remaining = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(remaining + 3600L, seckillProperties.getSoldoutTtl());
    }

    // ======================== Key 构建私有方法 ========================

    private String bucketKey(Long activityId, int bucket) {
        return STOCK_KEY_PREFIX + activityId + ":" + bucket;
    }

    private String limitKey(Long activityId, Long userId) {
        return LIMIT_KEY_PREFIX + activityId + ":" + userId;
    }

    private String soldoutKey(Long activityId) {
        return SOLDOUT_KEY_PREFIX + activityId;
    }

    private String gateKey(Long activityId) {
        return GATE_KEY_PREFIX + activityId;
    }

    private String resultKey(Long activityId, Long userId) {
        return RESULT_KEY_PREFIX + activityId + ":" + userId;
    }

    private String riskFailUserKey(Long activityId, Long userId) {
        return RISK_FAIL_USER_PREFIX + activityId + ":" + userId;
    }

    private String riskFailIpKey(Long activityId, String clientIp) {
        return RISK_FAIL_USER_PREFIX + activityId + RISK_FAIL_IP_INFIX + clientIp;
    }

    private String riskFreqKey(Long activityId, String identity) {
        return RISK_FREQ_PREFIX + activityId + ":" + identity;
    }

    private String msgDoneKey(String requestId) {
        return MSG_DONE_PREFIX + requestId;
    }
}
