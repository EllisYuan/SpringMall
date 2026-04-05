package site.geekie.shop.shoppingmall.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.PaymentMethod;
import site.geekie.shop.shoppingmall.common.PaymentStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.service.AlipayPaymentService;
import site.geekie.shop.shoppingmall.service.PaymentCloseService;
import site.geekie.shop.shoppingmall.service.StripeService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付关闭服务实现类
 * 在创建新支付前关闭同一订单所有 PENDING 历史支付，实现支付方式互斥。
 * 关闭前会先向第三方查询实际支付状态，若发现第三方已支付则执行补单并阻断新支付创建。
 */
@Slf4j
@Service
public class PaymentCloseServiceImpl implements PaymentCloseService {

    private final PaymentMapper paymentMapper;
    private final AlipayPaymentService alipayPaymentService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;

    /**
     * StripeService 与本类存在循环依赖，使用 @Lazy 延迟注入以打破循环
     */
    private final StripeService stripeService;

    @Autowired
    public PaymentCloseServiceImpl(PaymentMapper paymentMapper, @Lazy AlipayPaymentService alipayPaymentService, @Lazy StripeService stripeService, OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper, SkuMapper skuMapper) {
        this.paymentMapper = paymentMapper;
        this.alipayPaymentService = alipayPaymentService;
        this.stripeService = stripeService;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    public void closeAllPendingPayments(String orderNo) {
        List<PaymentDO> pendingPayments = paymentMapper.findPendingByOrderNo(orderNo);
        if (pendingPayments == null || pendingPayments.isEmpty()) {
            log.debug("订单无 PENDING 支付记录，跳过关闭 - 订单号: {}", orderNo);
            return;
        }

        log.info("开始关闭订单 PENDING 支付记录 - 订单号: {}, 数量: {}", orderNo, pendingPayments.size());

        for (PaymentDO payment : pendingPayments) {
            closeOnePayment(payment);
        }

        log.info("关闭订单 PENDING 支付记录完毕 - 订单号: {}", orderNo);
    }

    /**
     * 关闭单条支付记录（含第三方状态查询 + 第三方关单 + 本地状态更新）
     * 若第三方已支付，则执行补单并抛出 BusinessException 阻断新支付创建
     */
    private void closeOnePayment(PaymentDO payment) {
        String method = payment.getPaymentMethod();
        log.info("关闭支付记录 - 支付流水号: {}, 支付方式: {}", payment.getPaymentNo(), method);

        // 先查询第三方实际支付状态
        boolean isPaidAtThirdParty = queryThirdPartyStatus(payment);

        if (isPaidAtThirdParty) {
            // 第三方已支付：执行补单，然后抛异常阻断新支付创建
            log.warn("关单前发现第三方已支付，执行补单 - 支付流水号: {}, 订单号: {}",
                    payment.getPaymentNo(), payment.getOrderNo());
            payment.setPaymentStatus(PaymentStatus.SUCCESS.name());
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);

            // 原子性更新订单状态为 PAID（乐观锁防止并发重复）
            int updated = orderMapper.compareAndUpdateStatus(
                    payment.getOrderNo(), OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
            if (updated > 0) {
                orderMapper.updatePaymentTime(payment.getOrderNo());
                // 支付成功后，更新商品销量
                OrderDO order = orderMapper.findByOrderNo(payment.getOrderNo());
                if (order != null) {
                    List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
                    for (OrderItemDO item : items) {
                        productMapper.increaseSalesCount(item.getProductId(), item.getQuantity());
                        if (item.getSkuId() != null && item.getSkuId() > 0) {
                            skuMapper.increaseSalesCount(item.getSkuId(), item.getQuantity());
                        }
                    }
                }
            }

            throw new BusinessException(ResultCode.PAYMENT_ALREADY_PAID_BY_OTHER_METHOD);
        }

        // 第三方未支付：执行关单
        if (PaymentMethod.ALIPAY.name().equals(method)) {
            // 调用支付宝关单接口（内部已更新本地状态）
            alipayPaymentService.closePayment(payment);

        } else if (PaymentMethod.STRIPE.name().equals(method)) {
            // 调用 Stripe Session expire 接口（内部已更新本地状态）
            stripeService.expireSession(payment);

        } else {
            // 其他支付方式（如 WECHAT）：仅更新本地状态为 CLOSED
            log.info("不支持第三方关单的支付方式，仅更新本地状态 - 支付流水号: {}, 支付方式: {}",
                    payment.getPaymentNo(), method);
            payment.setPaymentStatus(PaymentStatus.CLOSED.name());
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }
    }

    /**
     * 查询第三方实际支付状态
     *
     * @param payment 支付记录
     * @return true 表示第三方已完成支付
     */
    private boolean queryThirdPartyStatus(PaymentDO payment) {
        String method = payment.getPaymentMethod();
        try {
            if (PaymentMethod.ALIPAY.name().equals(method)) {
                return alipayPaymentService.queryPaymentStatus(payment);
            } else if (PaymentMethod.STRIPE.name().equals(method)) {
                return stripeService.queryPaymentStatus(payment);
            }
        } catch (Exception e) {
            log.error("查询第三方支付状态异常，为安全起见视为可能已支付 - 支付流水号: {}, 错误: {}",
                    payment.getPaymentNo(), e.getMessage(), e);
            // 查询异常时抛出异常阻断新支付创建（宁可阻断也不冒双重支付风险，按已支付处理）
            throw new BusinessException(ResultCode.PAYMENT_ALREADY_PAID_BY_OTHER_METHOD);
        }
        return false;
    }
}
