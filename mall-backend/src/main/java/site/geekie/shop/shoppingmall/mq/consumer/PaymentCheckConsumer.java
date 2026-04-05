package site.geekie.shop.shoppingmall.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.PaymentMethod;
import site.geekie.shop.shoppingmall.common.PaymentStatus;
import site.geekie.shop.shoppingmall.config.RabbitMQConfig;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.service.AlipayPaymentService;
import site.geekie.shop.shoppingmall.util.StockRedisService;
import site.geekie.shop.shoppingmall.service.PaymentCloseService;
import site.geekie.shop.shoppingmall.service.PaymentService;
import site.geekie.shop.shoppingmall.service.StripeService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 掉单补偿消费者
 * !!! 未作重复支付测试 !!!
 * 消费 payment.check.queue 队列中的消息，主动向第三方查询支付状态，
 * 补偿因网络抖动等原因导致回调未到达的情况（掉单）
 *
 * 消费逻辑：
 * 1. 解析消息获取 orderNo 和 paymentId
 * 2. 若支付状态仍为 PENDING，向第三方查询实际状态
 * 3. 第三方已支付（6a）→ 事务内更新本地支付和订单为成功；事务外检测多重支付并退款
 * 4. 第三方未支付/已关闭（6b）→ 执行完整关单：FAILED + 恢复库存 + 取消订单
 *
 * 事务策略：使用 TransactionTemplate 编程式事务，确保数据库操作提交成功后才 ack，
 * 避免 @Transactional 与手动 ack 混用导致事务未提交但消息已被确认的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCheckConsumer {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final AlipayPaymentService alipayPaymentService;
    private final StripeService stripeService;
    private final PaymentCloseService paymentCloseService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final StockRedisService stockRedisService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_CHECK_QUEUE)
    public void handlePaymentCheck(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到掉单补偿检查消息: {}", message);

        String orderNo = null;
        Long paymentId = null;

        try {
            // 1. 解析消息（在事务外执行，解析失败直接 nack）
            Map<?, ?> payload = objectMapper.readValue(message, Map.class);
            orderNo = (String) payload.get("orderNo");
            Object paymentIdObj = payload.get("paymentId");
            if (paymentIdObj instanceof Number) {
                paymentId = ((Number) paymentIdObj).longValue();
            }

            if (orderNo == null || paymentId == null) {
                log.error("掉单补偿消息格式错误，丢弃消息: {}", message);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // 2. 查询支付记录和订单（在事务外读取，减少事务持锁时间）
            final PaymentDO payment = paymentMapper.findById(paymentId);
            if (payment == null) {
                log.warn("掉单补偿：支付记录不存在，跳过 - 支付ID: {}", paymentId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 幂等性检查：只处理 PENDING 状态的支付
            if (!PaymentStatus.PENDING.name().equals(payment.getPaymentStatus())) {
                log.info("掉单补偿：支付已非 PENDING 状态，跳过 - 支付流水号: {}, 当前状态: {}",
                        payment.getPaymentNo(), payment.getPaymentStatus());

                // 3a. 兜底：扫描该订单下所有支付记录，检测是否存在多重支付（重复 SUCCESS）
                detectAndRefundDuplicatePayments(orderNo);

                channel.basicAck(deliveryTag, false);
                return;
            }

            // 4. 再次确认订单状态（若订单已取消，则无需补偿）
            final OrderDO order = orderMapper.findByOrderNo(orderNo);
            if (order == null || OrderStatus.CANCELLED.getCode().equals(order.getStatus())) {
                log.info("掉单补偿：订单已取消或不存在，跳过 - 订单号: {}, 当前状态: {}",
                        orderNo, order == null ? "不存在" : order.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. 向第三方查询实际支付状态（在事务外执行，避免长事务持锁）
            final boolean isPaid = queryThirdPartyPaymentStatus(payment);
            final String finalOrderNo = orderNo;

            if (isPaid) {
                // 6a. 第三方显示已支付：在事务中更新本地状态
                log.info("掉单补偿：第三方确认已支付，更新本地状态 - 订单号: {}, 支付流水号: {}",
                        orderNo, payment.getPaymentNo());

                transactionTemplate.executeWithoutResult(status -> {
                    payment.setPaymentStatus(PaymentStatus.SUCCESS.name());
                    payment.setNotifyTime(LocalDateTime.now());
                    paymentMapper.updateById(payment);

                    // 原子性更新订单状态为 PAID（乐观锁防止并发重复）
                    int updated = orderMapper.compareAndUpdateStatus(
                            finalOrderNo, OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
                    if (updated > 0) {
                        orderMapper.updatePaymentTime(finalOrderNo);
                        log.info("掉单补偿成功 - 订单号: {}, 支付流水号: {}",
                                finalOrderNo, payment.getPaymentNo());
                        // 支付成功后，更新商品销量
                        List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
                        for (OrderItemDO item : items) {
                            productMapper.increaseSalesCount(item.getProductId(), item.getQuantity());
                            if (item.getSkuId() != null && item.getSkuId() > 0) {
                                skuMapper.increaseSalesCount(item.getSkuId(), item.getQuantity());
                            }
                        }
                    } else {
                        log.warn("掉单补偿：订单状态已被其他线程更新，跳过 - 订单号: {}",
                                finalOrderNo);
                    }
                });

                // 补单后多重支付检测（在事务外执行，因为 refundOrder 有自己的事务）
                detectAndRefundDuplicatePayments(finalOrderNo);

            } else {
                // 6b. 第三方显示未支付/已关闭：执行完整关单
                log.info("掉单补偿：第三方确认未支付，执行完整关单 - 订单号: {}, 支付流水号: {}",
                        orderNo, payment.getPaymentNo());

                // 第一步（事务内）：更新支付状态 + 恢复库存 + 取消订单
                boolean needCloseOtherPayments = Boolean.TRUE.equals(
                    transactionTemplate.execute(status -> {
                        // 更新当前支付状态 → FAILED
                        payment.setPaymentStatus(PaymentStatus.FAILED.name());
                        payment.setNotifyTime(LocalDateTime.now());
                        paymentMapper.updateById(payment);

                        // 查询订单状态，若仍为 UNPAID 则完整关单
                        OrderDO latestOrder = orderMapper.findByOrderNo(finalOrderNo);
                        if (latestOrder != null && OrderStatus.UNPAID.getCode().equals(latestOrder.getStatus())) {
                            // 恢复库存（区分 SKU 商品和普通商品）
                            List<OrderItemDO> orderItems = orderItemMapper.findByOrderId(latestOrder.getId());
                            List<OrderItemDO> noSkuItems = new java.util.ArrayList<>();
                            for (OrderItemDO item : orderItems) {
                                if (item.getSkuId() != null && item.getSkuId() > 0) {
                                    skuMapper.increaseStock(item.getSkuId(), item.getQuantity());
                                    log.debug("掉单补偿恢复SKU库存 - SKU_ID: {}, 商品ID: {}, 数量: {}",
                                            item.getSkuId(), item.getProductId(), item.getQuantity());
                                } else {
                                    productMapper.increaseStock(item.getProductId(), item.getQuantity());
                                    noSkuItems.add(item);
                                    log.debug("掉单补偿恢复商品库存 - 商品ID: {}, 数量: {}",
                                            item.getProductId(), item.getQuantity());
                                }
                            }

                            // 恢复 Redis 库存（仅无 SKU 商品）
                            if (!noSkuItems.isEmpty()) {
                                try {
                                    stockRedisService.batchRestoreStock(noSkuItems);
                                } catch (Exception e) {
                                    log.warn("掉单补偿恢复 Redis 库存异常 - 订单号: {}", finalOrderNo, e);
                                }
                            }

                            // 更新订单状态 → CANCELLED
                            orderMapper.updateStatus(finalOrderNo, OrderStatus.CANCELLED.getCode());
                            log.info("掉单补偿：订单关单成功（库存已恢复、订单已取消）- 订单号: {}", finalOrderNo);
                            return true; // 标记需要关闭其他 PENDING 支付
                        }
                        return false;
                    })
                );

                // 第二步（事务外）：关闭其他 PENDING 支付记录（涉及第三方 HTTP 调用，不能在事务内执行）
                if (needCloseOtherPayments) {
                    try {
                        paymentCloseService.closeAllPendingPayments(finalOrderNo);
                    } catch (Exception closeEx) {
                        log.error("掉单补偿：关闭其他 PENDING 支付失败（订单已取消，不影响主流程）- 订单号: {}, 错误: {}",
                                finalOrderNo, closeEx.getMessage(), closeEx);
                    }
                }
            }

            // 事务成功提交后 ack
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理掉单补偿消息异常 - 订单号: {}, 支付ID: {}", orderNo, paymentId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 根据支付方式调用对应的第三方查询接口
     *
     * @param payment 支付记录
     * @return true 表示第三方确认已支付
     */
    private boolean queryThirdPartyPaymentStatus(PaymentDO payment) {
        String method = payment.getPaymentMethod();

        if (PaymentMethod.ALIPAY.name().equals(method)) {
            return alipayPaymentService.queryPaymentStatus(payment);
        } else if (PaymentMethod.STRIPE.name().equals(method)) {
            return stripeService.queryPaymentStatus(payment);
        } else {
            log.warn("掉单补偿：不支持的支付方式，跳过查询 - 支付方式: {}, 支付流水号: {}",
                    method, payment.getPaymentNo());
            return false;
        }
    }

    /**
     * 扫描订单下所有支付记录，检测多重支付并自动退款。
     * 保留最新的 SUCCESS 记录（按 created_at ASC 排序，保留最后一条），对其余 SUCCESS 记录发起退款。
     *
     * @param orderNo 订单号
     */
    private void detectAndRefundDuplicatePayments(String orderNo) {
        try {
            List<PaymentDO> successPayments = paymentMapper.findSuccessListByOrderNo(orderNo);
            if (successPayments == null || successPayments.size() <= 1) {
                return;
            }

            log.warn("检测到多重支付！订单号: {}, SUCCESS 支付数量: {}", orderNo, successPayments.size());
            // 保留最后一条（按 created_at ASC，最后一条为最新），对其余发起退款
            for (int i = 0; i < successPayments.size() - 1; i++) {
                PaymentDO duplicatePayment = successPayments.get(i);
                log.warn("对重复支付发起退款 - 支付流水号: {}, 订单号: {}", duplicatePayment.getPaymentNo(), orderNo);
                try {
                    paymentService.refundByPaymentNo(duplicatePayment.getPaymentNo(), "多重支付自动退款");
                } catch (Exception ex) {
                    log.error("多重支付退款失败，需人工介入 - 支付流水号: {}, 错误: {}",
                            duplicatePayment.getPaymentNo(), ex.getMessage(), ex);
                }
            }
        } catch (Exception e) {
            log.error("多重支付检测异常 - 订单号: {}, 错误: {}", orderNo, e.getMessage(), e);
        }
    }
}
