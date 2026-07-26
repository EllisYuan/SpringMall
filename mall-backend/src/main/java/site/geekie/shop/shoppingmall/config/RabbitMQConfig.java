package site.geekie.shop.shoppingmall.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RabbitMQ 队列配置
 *
 * 采用死信队列（DLQ）模式实现延迟消息：
 *
 * 订单超时关闭（TTL=15min）：
 *   order.delay.exchange --[order.delay]--> order.delay.queue
 *     (TTL 到期后) --[DLX]--> order.process.exchange --[order.close]--> order.close.queue
 *
 * 掉单补偿（TTL=5min）：
 *   payment.delay.exchange --[payment.delay]--> payment.delay.queue
 *     (TTL 到期后) --[DLX]--> payment.process.exchange --[payment.check]--> payment.check.queue
 *
 * 秒杀异步落单（即时消费，无延迟）：
 *   seckill.order.exchange --[seckill.order]--> seckill.order.queue --> SeckillOrderConsumer
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器：Java 序列化 + 反序列化白名单。
     *
     * 秒杀异步下单通过 MQ 传递 SeckillOrderDTO 对象，Spring AMQP 默认的允许类检查
     * 只信任标准类型，会以 SecurityException 拒绝反序列化自定义类（导致消费者落库失败、抢购失败）。
     * 这里显式放行本项目 dto 包及常用 JDK 类型；String 等标准消息不受影响。
     * Spring Boot 会自动将此 MessageConverter 装配到 RabbitTemplate（生产者）与监听容器工厂（消费者）。
     */
    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of(
                "site.geekie.shop.shoppingmall.dto.*",
                "java.util.*",
                "java.lang.*",
                "java.time.*",
                "java.math.*"
        ));
        return converter;
    }

    // ======================== 常量定义 ========================

    // 订单超时关闭
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    public static final String ORDER_PROCESS_EXCHANGE = "order.process.exchange";
    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    // 掉单补偿
    public static final String PAYMENT_DELAY_EXCHANGE = "payment.delay.exchange";
    public static final String PAYMENT_DELAY_QUEUE = "payment.delay.queue";
    public static final String PAYMENT_DELAY_ROUTING_KEY = "payment.delay";

    public static final String PAYMENT_PROCESS_EXCHANGE = "payment.process.exchange";
    public static final String PAYMENT_CHECK_QUEUE = "payment.check.queue";
    public static final String PAYMENT_CHECK_ROUTING_KEY = "payment.check";

    // 秒杀异步落单
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    // TTL（毫秒）
    private static final int ORDER_TTL_MS = 15 * 60 * 1000;   // 15 分钟：下单后未支付自动取消
    private static final int PAYMENT_TTL_MS = 5 * 60 * 1000;   // 5 分钟：支付创建后掉单补偿检查（回调通常秒级到达，5min 足够判断掉单）

    // ======================== 订单超时关闭 ========================

    /**
     * 订单延迟 Exchange
     */
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    /**
     * 订单延迟队列（TTL=15min，超时后消息转发到 order.process.exchange/order.close）
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", ORDER_TTL_MS)
                .withArgument("x-dead-letter-exchange", ORDER_PROCESS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    /**
     * 订单延迟队列绑定
     */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    /**
     * 订单处理 Exchange
     */
    @Bean
    public DirectExchange orderProcessExchange() {
        return new DirectExchange(ORDER_PROCESS_EXCHANGE);
    }

    /**
     * 订单关闭队列（消费者监听此队列，执行关闭逻辑）
     */
    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(ORDER_CLOSE_QUEUE).build();
    }

    /**
     * 订单关闭队列绑定
     */
    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue())
                .to(orderProcessExchange())
                .with(ORDER_CLOSE_ROUTING_KEY);
    }

    // ======================== 掉单补偿 ========================

    /**
     * 支付延迟 Exchange（掉单检查消息入口）
     */
    @Bean
    public DirectExchange paymentDelayExchange() {
        return new DirectExchange(PAYMENT_DELAY_EXCHANGE);
    }

    /**
     * 支付延迟队列（TTL=5min，超时后消息转发到 payment.process.exchange/payment.check）
     */
    @Bean
    public Queue paymentDelayQueue() {
        return QueueBuilder.durable(PAYMENT_DELAY_QUEUE)
                .withArgument("x-message-ttl", PAYMENT_TTL_MS)
                .withArgument("x-dead-letter-exchange", PAYMENT_PROCESS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_CHECK_ROUTING_KEY)
                .build();
    }

    /**
     * 支付延迟队列绑定
     */
    @Bean
    public Binding paymentDelayBinding() {
        return BindingBuilder.bind(paymentDelayQueue())
                .to(paymentDelayExchange())
                .with(PAYMENT_DELAY_ROUTING_KEY);
    }

    /**
     * 支付处理 Exchange（死信转入后的处理 Exchange）
     */
    @Bean
    public DirectExchange paymentProcessExchange() {
        return new DirectExchange(PAYMENT_PROCESS_EXCHANGE);
    }

    /**
     * 支付检查队列（消费者监听此队列，执行掉单补偿逻辑）
     */
    @Bean
    public Queue paymentCheckQueue() {
        return QueueBuilder.durable(PAYMENT_CHECK_QUEUE).build();
    }

    /**
     * 支付检查队列绑定
     */
    @Bean
    public Binding paymentCheckBinding() {
        return BindingBuilder.bind(paymentCheckQueue())
                .to(paymentProcessExchange())
                .with(PAYMENT_CHECK_ROUTING_KEY);
    }

    // ======================== 秒杀异步落单 ========================

    /**
     * 秒杀订单 Exchange（幂等声明，不影响现有队列）
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SECKILL_ORDER_EXCHANGE);
    }

    /**
     * 秒杀订单队列（Redis 预扣成功后的落单消息，消费者串行落库削峰）
     * 超时关单复用现有 order.delay 延迟链路，此处不新增延迟队列
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).build();
    }

    /**
     * 秒杀订单队列绑定
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }
}
