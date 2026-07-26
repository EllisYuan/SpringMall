package site.geekie.shop.shoppingmall.mq.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.config.RabbitMQConfig;
import site.geekie.shop.shoppingmall.dto.SeckillOrderDTO;

/**
 * 秒杀消息生产者
 * 负责将 Redis 预扣成功的抢购请求投递到 seckill.order.queue，由消费者异步串行落库
 *
 * 序列化说明：消息体为 SeckillOrderDTO（Serializable），依赖 RabbitMQConfig 中
 * 注册的 SimpleMessageConverter 反序列化白名单（dto 包已放行），否则消费端会被
 * Spring AMQP 的允许类检查以 SecurityException 拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀抢购落单消息
     * 投递失败时异常向上抛出，由调用方回补 Redis 库存并写失败结果
     *
     * @param message 抢购消息体（activityId、userId、quantity、skuId、addressId、requestId）
     */
    public void sendSeckillMessage(SeckillOrderDTO message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    message
            );
            log.info("秒杀消息已投递 - activityId: {}, userId: {}, qty: {}, requestId: {}",
                    message.getActivityId(), message.getUserId(), message.getQuantity(), message.getRequestId());
        } catch (Exception e) {
            log.error("秒杀消息投递失败 - activityId: {}, userId: {}",
                    message.getActivityId(), message.getUserId(), e);
            throw e;
        }
    }
}
