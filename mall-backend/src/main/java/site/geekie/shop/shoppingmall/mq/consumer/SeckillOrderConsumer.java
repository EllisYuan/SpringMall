package site.geekie.shop.shoppingmall.mq.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.config.RabbitMQConfig;
import site.geekie.shop.shoppingmall.config.SeckillProperties;
import site.geekie.shop.shoppingmall.dto.SeckillOrderDTO;
import site.geekie.shop.shoppingmall.entity.AddressDO;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.entity.SeckillActivityDO;
import site.geekie.shop.shoppingmall.entity.SeckillOrderDO;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.mapper.AddressMapper;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SeckillActivityMapper;
import site.geekie.shop.shoppingmall.mapper.SeckillOrderMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.mq.producer.OrderMessageProducer;
import site.geekie.shop.shoppingmall.util.OrderNoGenerator;
import site.geekie.shop.shoppingmall.util.SeckillRedisService;
import site.geekie.shop.shoppingmall.util.SeckillSseService;
import site.geekie.shop.shoppingmall.util.SnowflakeIdGenerator;
import site.geekie.shop.shoppingmall.vo.SeckillResultVO;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 秒杀订单消费者
 * 监听 seckill.order.queue，将 Redis 预扣成功的抢购请求串行落库为真实订单
 *
 * 消费逻辑：
 * 1. 消费入口 tryClaimMessage（SETNX 原子抢占幂等位），抢占失败直接 ack 跳过
 * 2. 对 mall_seckill_activity 行加 SELECT FOR UPDATE 写锁，串行化同一活动的并发消费者
 * 3. 持锁后做 DB 权威限购 SUM 校验（查 mall_seckill_order JOIN mall_order 排除 CANCELLED）
 * 4. 持锁状态下乐观锁扣减 available_stock（防超卖 DB 兜底）
 * 5. 校验地址归属（防越权）
 * 6. 生成真实 mall_order（UNPAID, source=1, seckill_activity_id）+ 单条 order_item
 *    + mall_seckill_order 快照（含 request_id，DB 唯一索引兜底去重）——三写同一事务
 * 7. insert 冲突（DuplicateKeyException）视为重复消费，安全跳过且不回补
 * 8. afterCommit：复用 15 分钟超时关单延迟消息 + 写 result=SUCCESS:{orderNo} + SSE 推送
 * 9. 业务失败：回补 Redis 分桶 + 写 FAIL + SSE 推送；异常：NACK 不重入队防死循环
 *
 * 幂等边界：消费侧幂等仅依赖 SETNX + uk_request_id 双保险。
 * seckill:result:{activityId}:{userId} 是按用户共享的展示状态键（last-writer-wins），
 * 同一用户并发多请求时后到失败请求的 FAIL 会覆盖先到请求的 QUEUING，
 * 因此绝不能作为消费侧"结果已定"的跳过依据——误跳过会导致已预扣分桶库存永久流失。
 *
 * 事务策略：TransactionTemplate 编程式事务 + 手动 ACK，
 * 确保 DB 操作提交成功后才发关单延迟消息并 ack（与 OrderCloseConsumer 范式一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final AddressMapper addressMapper;
    private final TransactionTemplate transactionTemplate;
    private final SeckillRedisService seckillRedisService;
    private final SeckillSseService seckillSseService;
    private final OrderMessageProducer orderMessageProducer;
    private final SeckillProperties seckillProperties;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderDTO message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long activityId = message.getActivityId();
        Long userId = message.getUserId();
        int quantity = message.getQuantity() == null ? 1 : message.getQuantity();
        String requestId = message.getRequestId();

        log.info("收到秒杀下单消息 - activityId: {}, userId: {}, qty: {}, requestId: {}",
                activityId, userId, quantity, requestId);

        // 用于在事务外写结果与推送
        final String[] orderNoHolder = new String[1];
        final boolean[] successHolder = new boolean[]{false};
        final String[] failReasonHolder = new String[]{null};
        // 标记是否为重复消费（DuplicateKey），不需要回补 Redis
        final boolean[] isDuplicateHolder = new boolean[]{false};
        // 活动结束时间快照（回补 Redis 时计算 TTL 用）
        final SeckillActivityDO[] activityHolder = new SeckillActivityDO[1];

        try {
            // 1. 消费入口原子 SETNX 抢占幂等位，重复消费直接 ack 跳过
            if (requestId != null && !requestId.isBlank()) {
                boolean claimed = seckillRedisService.tryClaimMessage(requestId, seckillProperties.getMsgDedupTtl());
                if (!claimed) {
                    log.info("秒杀消息已被处理（SETNX 幂等跳过）- requestId: {}, activityId: {}, userId: {}",
                            requestId, activityId, userId);
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            }

            transactionTemplate.executeWithoutResult(status -> {

                // 注意：不做"result 已定"的快速跳过——result 键按 userId 共享（last-writer-wins），
                // 并发多请求时会被后到失败请求覆盖，误跳过将导致已预扣库存永久流失；
                // 重复消费由入口 SETNX 与下方 uk_request_id 唯一索引兜底

                // 2. 活动行写锁：串行化同一活动的并发消费者，冻结 available_stock 账面
                SeckillActivityDO activity = seckillActivityMapper.selectForUpdate(activityId);
                if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())) {
                    log.warn("秒杀活动不存在或已下线 - activityId: {}", activityId);
                    failReasonHolder[0] = "活动不存在或已结束";
                    status.setRollbackOnly();
                    return;
                }
                // 预留库存已归还（结束 Job 已回收）：此时扣减会双重售卖，必须拒绝
                if (Integer.valueOf(1).equals(activity.getStockReclaimed())) {
                    log.warn("秒杀活动库存已归还，拒绝落单 - activityId: {}", activityId);
                    failReasonHolder[0] = "活动已结束";
                    status.setRollbackOnly();
                    return;
                }
                activityHolder[0] = activity;

                // 3. 持锁状态下 DB 权威限购校验（SUM 在 FOR UPDATE 之后串行执行）
                Integer bought = seckillOrderMapper.sumQuantityByActivityAndUser(activityId, userId);
                int alreadyBought = bought == null ? 0 : bought;
                if (alreadyBought + quantity > activity.getLimitPerUser()) {
                    log.warn("DB 限购校验失败 - activityId: {}, userId: {}, alreadyBought: {}, qty: {}, limit: {}",
                            activityId, userId, alreadyBought, quantity, activity.getLimitPerUser());
                    failReasonHolder[0] = "超过限购";
                    status.setRollbackOnly();
                    return;
                }

                // 4. 持锁状态下乐观锁扣减 available_stock（防超卖 DB 兜底）
                int affected = seckillActivityMapper.decreaseStock(activityId, quantity);
                if (affected == 0) {
                    log.warn("DB 乐观锁扣减失败，库存不足 - activityId: {}, qty: {}", activityId, quantity);
                    failReasonHolder[0] = "售罄";
                    status.setRollbackOnly();
                    return;
                }

                // 5. 校验地址归属（防越权使用他人地址）
                AddressDO address = addressMapper.findById(message.getAddressId());
                if (address == null || !userId.equals(address.getUserId())) {
                    log.warn("地址不存在或不属于当前用户 - addressId: {}, userId: {}", message.getAddressId(), userId);
                    failReasonHolder[0] = "收货地址无效";
                    status.setRollbackOnly();
                    return;
                }

                // 6. 商品/SKU 快照（订单明细冗余，防后续商品信息变更）
                ProductDO product = productMapper.findById(activity.getProductId());
                if (product == null) {
                    log.warn("秒杀关联商品不存在 - activityId: {}, productId: {}", activityId, activity.getProductId());
                    failReasonHolder[0] = "商品不可用";
                    status.setRollbackOnly();
                    return;
                }
                String productImage = product.getMainImage();
                String specDesc = null;
                long skuId = activity.getSkuId() == null ? 0L : activity.getSkuId();
                if (skuId > 0) {
                    SkuDO sku = skuMapper.findById(skuId);
                    if (sku != null) {
                        specDesc = sku.getSpecDesc();
                        if (sku.getImage() != null) {
                            productImage = sku.getImage();
                        }
                    }
                }

                // 7. 生成真实订单（普通订单号体系，无前缀，支付/退款/关单零改动复用）
                String orderNo = OrderNoGenerator.generateOrderNo();
                BigDecimal totalAmount = activity.getSeckillPrice().multiply(BigDecimal.valueOf(quantity));

                // OrderDO/OrderItemDO 按多数据源例外允许手动构建（backend-java 规约）
                OrderDO order = new OrderDO();
                order.setOrderNo(orderNo);
                order.setUserId(userId);
                order.setTotalAmount(totalAmount);
                order.setPayAmount(totalAmount);
                order.setFreight(BigDecimal.ZERO);
                order.setStatus(OrderStatus.UNPAID.getCode());
                order.setSource(1);
                order.setSeckillActivityId(activityId);
                order.setReceiverName(address.getReceiverName());
                order.setReceiverPhone(address.getPhone());
                order.setReceiverAddress(buildFullAddress(address));
                orderMapper.insert(order);

                OrderItemDO orderItem = new OrderItemDO();
                orderItem.setOrderId(order.getId());
                orderItem.setProductId(activity.getProductId());
                orderItem.setSkuId(skuId);
                orderItem.setProductName(product.getName());
                orderItem.setProductImage(productImage);
                orderItem.setSpecDesc(specDesc);
                orderItem.setUnitPrice(activity.getSeckillPrice());
                orderItem.setQuantity(quantity);
                orderItem.setTotalPrice(totalAmount);
                orderItemMapper.insert(orderItem);

                // 8. 同事务写入秒杀明细快照（request_id 唯一索引兜底去重；主键为应用层雪花 ID）
                SeckillOrderDO seckillOrder = new SeckillOrderDO();
                seckillOrder.setId(snowflakeIdGenerator.nextId());
                seckillOrder.setOrderId(order.getId());
                seckillOrder.setOrderNo(orderNo);
                seckillOrder.setUserId(userId);
                seckillOrder.setActivityId(activityId);
                seckillOrder.setProductId(activity.getProductId());
                seckillOrder.setSkuId(skuId);
                seckillOrder.setProductName(product.getName());
                seckillOrder.setProductImage(productImage);
                seckillOrder.setSpecDesc(specDesc);
                seckillOrder.setSeckillPrice(activity.getSeckillPrice());
                seckillOrder.setQuantity(quantity);
                seckillOrder.setRequestId(requestId);
                try {
                    seckillOrderMapper.insert(seckillOrder);
                } catch (DuplicateKeyException e) {
                    // DB 兜底：request_id 唯一索引冲突 → 重复消费，安全跳过且不回补
                    log.warn("秒杀订单 insert 冲突（重复消费），安全跳过 - requestId: {}, activityId: {}, userId: {}",
                            requestId, activityId, userId);
                    isDuplicateHolder[0] = true;
                    status.setRollbackOnly();
                    return;
                }

                orderNoHolder[0] = orderNo;
                successHolder[0] = true;
                log.info("秒杀订单落库成功 - orderNo: {}, activityId: {}, userId: {}", orderNo, activityId, userId);
            });

            // 事务提交后的后续操作
            // M1 收口：提交后只做通知/回补类收尾，每步独立 try-catch 兜住（失败仅告警），
            // 绝不允许异常逃逸到外层 catch —— 否则会对已提交的订单执行误回补 + 写 FAIL + NACK
            if (successHolder[0] && orderNoHolder[0] != null) {
                // 复用现有 15 分钟超时关单延迟链路
                try {
                    orderMessageProducer.sendOrderCloseDelayMessage(orderNoHolder[0]);
                } catch (Exception e) {
                    log.warn("发送秒杀订单关单延迟消息失败 - orderNo: {}", orderNoHolder[0], e);
                }
                // 写成功结果 + SSE 推送；失败仅告警：订单已落库，用户可经降级查询/我的订单看到结果
                try {
                    seckillRedisService.setResult(activityId, userId, "SUCCESS:" + orderNoHolder[0]);
                    seckillSseService.pushResult(activityId, userId,
                            SeckillResultVO.success(activityId, orderNoHolder[0]));
                } catch (Exception e) {
                    log.warn("秒杀成功结果写入/推送失败（订单已落库，由降级查询兜底）- orderNo: {}, activityId: {}, userId: {}",
                            orderNoHolder[0], activityId, userId, e);
                }
            } else if (isDuplicateHolder[0]) {
                // 重复消费：幂等跳过，首次消费已完成扣减与落单，不回补
                log.info("秒杀重复消费，幂等跳过无需回补 - requestId: {}, activityId: {}, userId: {}",
                        requestId, activityId, userId);
            } else if (failReasonHolder[0] != null) {
                // 业务校验失败：回补 Redis 分桶（restoreStock 内部自捕获异常，不会抛出）
                long ttl = calcRestoreTtl(activityHolder[0]);
                seckillRedisService.restoreStock(activityId, userId, quantity, ttl);
                // 写失败结果 + SSE 推送；失败仅告警，防止逃逸到外层 catch 造成重复回补
                try {
                    seckillRedisService.setResult(activityId, userId, "FAIL:" + failReasonHolder[0]);
                    seckillSseService.pushResult(activityId, userId,
                            SeckillResultVO.fail(activityId, failReasonHolder[0]));
                } catch (Exception e) {
                    log.warn("秒杀失败结果写入/推送失败（库存已回补，不再重复回补）- activityId: {}, userId: {}",
                            activityId, userId, e);
                }
                log.info("秒杀下单失败，已回补 Redis - activityId: {}, userId: {}, reason: {}",
                        activityId, userId, failReasonHolder[0]);
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理秒杀下单消息异常 - activityId: {}, userId: {}", activityId, userId, e);

            // M1 收口：事务结论已定（成功落单/重复消费/失败已回补）时还能走到这里，
            // 只剩 basicAck 自身抛错一种可能 —— 绝不能再回补或改写结果（会造成 Redis
            // 多账、用户已有待付订单却被告知失败），仅重试 ack 收尾
            if (successHolder[0] || isDuplicateHolder[0] || failReasonHolder[0] != null) {
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (Exception ackEx) {
                    log.error("秒杀消息事务提交后 ack 失败（消息可能重投，由 SETNX + 唯一索引幂等兜底）- requestId: {}",
                            requestId, ackEx);
                }
                return;
            }

            // 事务阶段异常（未提交已回滚）：回补 Redis 并通知失败（防止预扣库存永久丢失）
            try {
                seckillRedisService.restoreStock(activityId, userId, quantity, calcRestoreTtl(activityHolder[0]));
                seckillRedisService.setResult(activityId, userId, "FAIL:系统异常");
                seckillSseService.pushResult(activityId, userId, SeckillResultVO.fail(activityId, "系统异常"));
            } catch (Exception ex) {
                log.warn("秒杀异常回补 Redis 失败", ex);
            }
            // 拒绝且不重新入队，防死循环消费
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 计算回补 Redis 的 key TTL：能取到活动结束时间则按剩余时长，否则用售罄标记 TTL 兜底
     */
    private long calcRestoreTtl(SeckillActivityDO activity) {
        if (activity != null && activity.getEndTime() != null) {
            return seckillRedisService.calcActivityTtlSeconds(activity.getEndTime());
        }
        return seckillProperties.getSoldoutTtl();
    }

    /**
     * 拼接完整收货地址字符串（与 OrderServiceImpl 下单口径一致）
     */
    private String buildFullAddress(AddressDO address) {
        StringBuilder sb = new StringBuilder();
        if (address.getProvince() != null) sb.append(address.getProvince());
        if (address.getCity() != null) sb.append(address.getCity());
        if (address.getDistrict() != null) sb.append(address.getDistrict());
        if (address.getDetailAddress() != null) sb.append(address.getDetailAddress());
        return sb.toString();
    }
}
