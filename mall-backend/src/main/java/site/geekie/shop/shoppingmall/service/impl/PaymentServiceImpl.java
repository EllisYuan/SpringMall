package site.geekie.shop.shoppingmall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.annotation.LogOperation;
import site.geekie.shop.shoppingmall.common.PaymentMethod;
import site.geekie.shop.shoppingmall.common.PaymentStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.entity.RefundDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.RefundMapper;
import site.geekie.shop.shoppingmall.converter.PaymentConverter;
import site.geekie.shop.shoppingmall.service.AlipayPaymentService;
import site.geekie.shop.shoppingmall.service.PaymentService;
import site.geekie.shop.shoppingmall.service.StripeService;
import site.geekie.shop.shoppingmall.service.WxPayService;
import site.geekie.shop.shoppingmall.vo.PaymentVO;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 支付服务实现类
 * 模拟支付功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final AlipayPaymentService alipayPaymentService;
    private final StripeService stripeService;
    private final ObjectProvider<WxPayService> wxPayServiceProvider;
    private final PaymentConverter paymentConverter;

    @Override
    public PaymentVO getPaymentByNo(String paymentNo, Long userId) {
        PaymentDO payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 触发对应支付通道的状态同步逻辑，确保返回最新状态。
        String paymentMethod = payment.getPaymentMethod();
        if (PaymentMethod.ALIPAY.name().equals(paymentMethod)) {
            alipayPaymentService.queryPayment(paymentNo, userId);
        } else if (PaymentMethod.STRIPE.name().equals(paymentMethod)) {
            stripeService.queryPayment(paymentNo, userId);
        } else if (PaymentMethod.WECHAT.name().equals(paymentMethod)) {
            WxPayService wxPayService = wxPayServiceProvider.getIfAvailable();
            if (wxPayService == null) {
                log.error("查询支付状态失败 - 微信支付未启用，支付流水号: {}", paymentNo);
                throw new BusinessException(ResultCode.PAYMENT_FAILED, "WeChat Pay is not enabled");
            }
            wxPayService.queryPayment(paymentNo, userId);
        } else {
            log.error("查询支付状态失败 - 不支持的支付方式: {}, 支付流水号: {}", paymentMethod, paymentNo);
            throw new BusinessException(ResultCode.PAYMENT_FAILED, "Unsupported payment method");
        }

        PaymentDO refreshed = paymentMapper.findByPaymentNo(paymentNo);
        if (refreshed == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        return paymentConverter.toPaymentVO(refreshed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogOperation(value = "创建支付退款", module = "支付")
    public void refundOrder(String orderNo, String refundReason) {
        // 1. 查询订单的 SUCCESS 支付记录（退款只能对已成功支付的记录操作）
        PaymentDO payment = paymentMapper.findSuccessByOrderNo(orderNo);
        if (payment == null) {
            log.error("退款失败 - 未找到已成功支付的记录，订单号: {}", orderNo);
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 校验支付状态转换合法性（SUCCESS->REFUNDED）
        PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getPaymentStatus());
        currentStatus.transitTo(PaymentStatus.REFUNDED);

        // 4. 检查是否已有退款记录
        RefundDO existingRefund = refundMapper.findByOrderNo(orderNo);
        if (existingRefund != null && "SUCCESS".equals(existingRefund.getRefundStatus())) {
            log.warn("退款失败 - 订单已退款，订单号: {}", orderNo);
            throw new BusinessException(ResultCode.PAYMENT_ALREADY_REFUNDED);
        }

        // 5. 生成退款流水号
        String refundNo = generateRefundNo();

        // 6. 根据支付方式调用对应的退款接口
        boolean refundSuccess = false;
        if (PaymentMethod.ALIPAY.name().equals(payment.getPaymentMethod())) {
            // 支付宝退款
            if (payment.getTradeNo() == null || payment.getTradeNo().isEmpty()) {
                log.error("退款失败 - 支付宝交易号为空，订单号: {}", orderNo);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }

            refundSuccess = alipayPaymentService.refund(
                    refundNo,
                    payment.getTradeNo(),
                    payment.getAmount(),
                    refundReason
            );
        } else if (PaymentMethod.STRIPE.name().equals(payment.getPaymentMethod())) {
            // Stripe 退款
            if (payment.getTradeNo() == null || payment.getTradeNo().isEmpty()) {
                log.error("退款失败 - Stripe Session ID 为空，订单号: {}", orderNo);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }

            // 从支付记录获取 userId，避免依赖 SecurityContextHolder（在 MQ 消费者线程中会 NPE）
            Long userId = payment.getUserId();

            try {
                // 构建 Stripe 退款请求
                site.geekie.shop.shoppingmall.dto.StripeRefundDTO refundRequest = new site.geekie.shop.shoppingmall.dto.StripeRefundDTO();
                refundRequest.setPaymentNo(payment.getPaymentNo());
                refundRequest.setRefundAmount(payment.getAmount());
                refundRequest.setReason(refundReason != null ? refundReason : "订单取消");

                // 调用 Stripe 退款服务（全额退款）
                stripeService.createRefund(refundRequest, userId);
                refundSuccess = true;
                log.info("Stripe 退款请求已提交 - 订单号: {}, 退款流水号: {}, 金额: {}",
                        orderNo, refundNo, payment.getAmount());
            } catch (Exception e) {
                log.error("Stripe 退款失败 - 订单号: {}, 错误: {}", orderNo, e.getMessage(), e);
                refundSuccess = false;
            }
        } else {
            // 未来可以扩展其他支付方式
            log.error("退款失败 - 不支持的支付方式: {}", payment.getPaymentMethod());
            throw new BusinessException(ResultCode.REFUND_FAILED);
        }

        // 7. 创建退款记录
        RefundDO refund = new RefundDO();
        refund.setRefundNo(refundNo);
        refund.setOrderNo(orderNo);
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setTradeNo(payment.getTradeNo());
        refund.setRefundAmount(payment.getAmount());
        refund.setRefundReason(refundReason);

        if (refundSuccess) {
            // 退款成功
            refund.setRefundStatus("SUCCESS");
            refund.setRefundTime(LocalDateTime.now());

            refundMapper.insert(refund);

            // 更新支付记录状态为已退款
            payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
            paymentMapper.updateById(payment);

            log.info("退款成功 - 订单号: {}, 退款流水号: {}, 金额: {}",
                     orderNo, refundNo, payment.getAmount());
        } else {
            // 退款失败
            refund.setRefundStatus("FAILED");
            refundMapper.insert(refund);

            log.error("退款失败 - 订单号: {}, 退款流水号: {}", orderNo, refundNo);
            throw new BusinessException(ResultCode.REFUND_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundByPaymentNo(String paymentNo, String refundReason) {
        // 1. 根据支付流水号精确查找支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null) {
            log.error("退款失败 - 支付记录不存在，支付流水号: {}", paymentNo);
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 校验支付状态（只能对 SUCCESS 记录退款）
        if (!PaymentStatus.SUCCESS.name().equals(payment.getPaymentStatus())) {
            log.warn("退款跳过 - 支付状态非 SUCCESS，支付流水号: {}, 当前状态: {}",
                    paymentNo, payment.getPaymentStatus());
            return;
        }

        // 3. 校验支付状态转换合法性（SUCCESS->REFUNDED）
        PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getPaymentStatus());
        currentStatus.transitTo(PaymentStatus.REFUNDED);

        // 4. 检查是否已有退款记录（按 paymentNo 查）
        RefundDO existingRefund = refundMapper.findByPaymentNo(paymentNo);
        if (existingRefund != null && "SUCCESS".equals(existingRefund.getRefundStatus())) {
            log.warn("退款跳过 - 该支付记录已退款，支付流水号: {}", paymentNo);
            return;
        }

        // 5. 生成退款流水号
        String refundNo = generateRefundNo();

        // 6. 根据支付方式调用对应的退款接口
        boolean refundSuccess = false;
        if (PaymentMethod.ALIPAY.name().equals(payment.getPaymentMethod())) {
            if (payment.getTradeNo() == null || payment.getTradeNo().isEmpty()) {
                log.error("退款失败 - 支付宝交易号为空，支付流水号: {}", paymentNo);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }
            refundSuccess = alipayPaymentService.refund(
                    refundNo, payment.getTradeNo(), payment.getAmount(), refundReason);
        } else if (PaymentMethod.STRIPE.name().equals(payment.getPaymentMethod())) {
            if (payment.getTradeNo() == null || payment.getTradeNo().isEmpty()) {
                log.error("退款失败 - Stripe PaymentIntent ID 为空，支付流水号: {}", paymentNo);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }
            try {
                site.geekie.shop.shoppingmall.dto.StripeRefundDTO refundRequest = new site.geekie.shop.shoppingmall.dto.StripeRefundDTO();
                refundRequest.setPaymentNo(payment.getPaymentNo());
                refundRequest.setRefundAmount(payment.getAmount());
                refundRequest.setReason(refundReason != null ? refundReason : "多重支付自动退款");
                stripeService.createRefund(refundRequest, payment.getUserId());
                refundSuccess = true;
            } catch (Exception e) {
                log.error("Stripe 退款失败 - 支付流水号: {}, 错误: {}", paymentNo, e.getMessage(), e);
                refundSuccess = false;
            }
        } else {
            log.error("退款失败 - 不支持的支付方式: {}", payment.getPaymentMethod());
            throw new BusinessException(ResultCode.REFUND_FAILED);
        }

        // 7. 创建退款记录
        RefundDO refund = new RefundDO();
        refund.setRefundNo(refundNo);
        refund.setOrderNo(payment.getOrderNo());
        refund.setPaymentNo(paymentNo);
        refund.setTradeNo(payment.getTradeNo());
        refund.setRefundAmount(payment.getAmount());
        refund.setRefundReason(refundReason);

        if (refundSuccess) {
            refund.setRefundStatus("SUCCESS");
            refund.setRefundTime(LocalDateTime.now());
            refundMapper.insert(refund);
            payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
            paymentMapper.updateById(payment);
            log.info("多重支付退款成功 - 支付流水号: {}, 退款流水号: {}, 金额: {}",
                    paymentNo, refundNo, payment.getAmount());
        } else {
            refund.setRefundStatus("FAILED");
            refundMapper.insert(refund);
            log.error("多重支付退款失败 - 支付流水号: {}, 退款流水号: {}", paymentNo, refundNo);
            throw new BusinessException(ResultCode.REFUND_FAILED);
        }
    }

    /**
     * 生成退款流水号
     * 格式：RF + 时间戳 + 6位随机数
     *
     * @return 退款流水号
     */
    private String generateRefundNo() {
        return "RF" + Instant.now().toEpochMilli() +
               String.format("%06d", (int) (Math.random() * 1000000));
    }
}
