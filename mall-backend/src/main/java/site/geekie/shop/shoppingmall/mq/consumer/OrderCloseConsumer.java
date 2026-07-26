package site.geekie.shop.shoppingmall.mq.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.config.RabbitMQConfig;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.service.StockRestoreService;

import java.io.IOException;
import java.util.List;

/**
 * 订单超时关闭消费者
 * 消费 order.close.queue 队列中的消息，执行订单超时自动关闭逻辑
 *
 * 消费逻辑：
 * 1. 根据 orderNo 查询订单
 * 2. 幂等检查：只处理 UNPAID 状态的订单
 * 3. 查询是否存在 PENDING 支付记录：
 *    - 有 PENDING 记录 → 说明掉单补偿（PaymentCheckConsumer）尚未完成，本消费者跳过，由其负责关单
 *    - 无 PENDING 记录 → 执行完整关单：按订单来源分流恢复库存 + 取消订单
 * 4. 库存回补统一委托 StockRestoreService，按订单 source 分流普通/秒杀链路（口径详见该服务注释）
 *
 * 事务策略：使用 TransactionTemplate 编程式事务，确保数据库操作提交成功后才 ack，
 * 避免 @Transactional 与手动 ack 混用导致事务未提交但消息已被确认的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseConsumer {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final TransactionTemplate transactionTemplate;
    private final StockRestoreService stockRestoreService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CLOSE_QUEUE)
    public void handleOrderClose(String orderNo,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到订单超时关闭消息 - 订单号: {}", orderNo);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 1. 查询订单
                OrderDO order = orderMapper.findByOrderNo(orderNo);
                if (order == null) {
                    log.warn("订单不存在，跳过关闭 - 订单号: {}", orderNo);
                    return;
                }

                // 2. 幂等性检查：只处理 UNPAID 状态的订单
                if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
                    log.info("订单已非 UNPAID 状态，跳过超时关闭 - 订单号: {}, 当前状态: {}",
                            orderNo, order.getStatus());
                    return;
                }

                // 3. 查询是否存在 PENDING 支付记录
                List<PaymentDO> pendingPayments = paymentMapper.findPendingByOrderNo(orderNo);
                if (pendingPayments != null && !pendingPayments.isEmpty()) {
                    // 有 PENDING 支付记录，说明掉单补偿消费者尚在处理中，本消费者跳过
                    log.info("订单存在 PENDING 支付记录，跳过超时关闭，由 PaymentCheckConsumer 负责 - 订单号: {}, PENDING 支付数量: {}",
                            orderNo, pendingPayments.size());
                    return;
                }

                // 4. 无 PENDING 支付记录：回补库存（统一回补服务，按订单来源分流普通/秒杀链路）
                List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
                stockRestoreService.restoreForClosedOrder(order, items, "超时关单");

                // 5. 更新订单状态为已取消
                orderMapper.updateStatus(orderNo, OrderStatus.CANCELLED.getCode());

                log.info("订单超时自动取消成功 - 订单号: {}", orderNo);
            });

            // 事务成功提交后 ack
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理订单超时关闭消息异常 - 订单号: {}", orderNo, e);
            // 出现异常时拒绝消息且不重新入队，防止死循环
            // 实际生产中可按需改为 nack + requeue=true 并配置重试次数
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
