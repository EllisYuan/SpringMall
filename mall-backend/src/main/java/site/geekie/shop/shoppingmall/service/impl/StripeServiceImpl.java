package site.geekie.shop.shoppingmall.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.PaymentMethod;
import site.geekie.shop.shoppingmall.common.PaymentStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.dto.CreateStripePaymentDTO;
import site.geekie.shop.shoppingmall.dto.StripeRefundDTO;
import site.geekie.shop.shoppingmall.config.StripeConfig;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.entity.RefundDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.RefundMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.mq.producer.PaymentMessageProducer;
import site.geekie.shop.shoppingmall.converter.PaymentConverter;
import site.geekie.shop.shoppingmall.converter.RefundConverter;
import site.geekie.shop.shoppingmall.service.PaymentCloseService;
import site.geekie.shop.shoppingmall.service.StripeService;
import site.geekie.shop.shoppingmall.util.OrderNoGenerator;
import site.geekie.shop.shoppingmall.util.RedisDistributedLock;
import site.geekie.shop.shoppingmall.vo.StripePaymentVO;
import site.geekie.shop.shoppingmall.vo.StripeRefundVO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Stripe 支付服务实现类 - Checkout + Adaptive Pricing 模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final StripeConfig stripeConfig;
    private final RedisDistributedLock redisDistributedLock;
    private final PaymentCloseService paymentCloseService;
    private final PaymentMessageProducer paymentMessageProducer;
    private final PaymentConverter paymentConverter;
    private final RefundConverter refundConverter;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.refund-webhook-secret}")
    private String refundWebhookSecret;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StripePaymentVO createStripe(String orderNo, Long userId) {
        String lockKey = "lock:payment:create:" + orderNo;
        String lockValue = redisDistributedLock.tryLock(lockKey, 60, TimeUnit.SECONDS);
        if (lockValue == null) {
            throw new BusinessException(ResultCode.PAYMENT_LOCK_FAILED);
        }

        try {
            return doCreateStripeIntent(orderNo, userId);
        } finally {
            redisDistributedLock.unlock(lockKey, lockValue);
        }
    }

    /**
     * createPaymentIntent 内部实现，由分布式锁保护
     */
    private StripePaymentVO doCreateStripeIntent(String orderNo, Long userId) {
        Stripe.apiKey = stripeSecretKey;

        // 1. 查询订单
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 2. 验证订单所有权（引入@CurrentUserId之后就不需要检查所有权了）
//        if (!order.getUserId().equals(userId)) {
//            throw new BusinessException(ResultCode.FORBIDDEN);
//        }

        // 3. 验证订单状态
        if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_ORDER_STATUS);
        }

        // 4. 查询订单商品明细
        List<OrderItemDO> orderItems = orderItemMapper.findByOrderId(order.getId());
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(ResultCode.ORDER_ITEM_NOT_FOUND);
        }

        // 5. 关闭其他支付方式的 PENDING 记录（支付方式互斥）
        paymentCloseService.closeAllPendingPayments(orderNo);

        // 6. 检查是否已有 Stripe PENDING 支付记录可复用
        PaymentDO existingPayment = paymentMapper.findPendingByOrderNo(orderNo).stream()
                .filter(p -> PaymentMethod.STRIPE.name().equals(p.getPaymentMethod()))
                .filter(p -> p.getCodeUrl() != null)
                .findFirst()
                .orElse(null);
        if (existingPayment != null) {
            log.info("订单已有 Stripe Checkout Session 记录 - 订单号: {}, 支付流水号: {}",
                    orderNo, existingPayment.getPaymentNo());
            return paymentConverter.toStripePaymentVO(existingPayment);
        }

        // 7. 生成支付流水号
        String paymentNo = OrderNoGenerator.generateOrderNo();

        // 8. 构建 Checkout Session 回调 URL
        String sessionSuccessUrl = successUrl + "?paymentNo=" + paymentNo;
        String sessionCancelUrl = cancelUrl.replace("{ORDER_NO}", orderNo);

        // 9. 构建 Checkout Session 页面商品图片、详情信息
        try {
            //为每个商品创建 line_item
            List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();

            for (OrderItemDO item : orderItems) {
                // 处理商品图片 URL（拼接为完整公网 URL）
                String imageUrl = item.getProductImage();
                if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.startsWith("http")) {
                    imageUrl = stripeConfig.getProductImageBaseUrl() + imageUrl;
                }

                // 构建 product_data
                SessionCreateParams.LineItem.PriceData.ProductData.Builder productDataBuilder =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(item.getProductName())
                        .setDescription("商品编号: " + item.getProductId());

                // 如果有图片 URL，添加到 images
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    productDataBuilder.addImage(imageUrl);
                }

                // 创建 line_item
                lineItems.add(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity((long) item.getQuantity())
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("cny")
                                .setUnitAmount(item.getUnitPrice().multiply(new BigDecimal("100")).longValue())
                                .setProductData(productDataBuilder.build())
                                .build()
                        )
                        .build()
                );
            }

            // 10. 创建 Checkout Session (Adaptive Pricing 模式 + 自定义外观)
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(sessionSuccessUrl)
                .setCancelUrl(sessionCancelUrl)
                .addAllLineItem(lineItems)  // 批量添加所有商品

                // 元数据
                .putMetadata("order_no", orderNo)
                .putMetadata("payment_no", paymentNo)
                .putMetadata("user_id", userId.toString())

                // 会话过期时间（Stripe 最低要求 30 分钟）
                .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).getEpochSecond())

                // 语言本地化（简体中文）
                .setLocale(SessionCreateParams.Locale.ZH)

                // 启用账单地址收集
                .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)

                // 自定义提交按钮文字
                .setSubmitType(SessionCreateParams.SubmitType.PAY)

                // 自定义文本（条款说明等）
                .setCustomText(
                    SessionCreateParams.CustomText.builder()
                        .setSubmit(
                            SessionCreateParams.CustomText.Submit.builder()
                                .setMessage("完成支付后，我们将立即处理您的订单")
                                .build()
                        )
                        .build()
                )

                // 启用客户信息收集
                .setCustomerCreation(SessionCreateParams.CustomerCreation.IF_REQUIRED)

                // 启用电话号码收集
                .setPhoneNumberCollection(
                    SessionCreateParams.PhoneNumberCollection.builder()
                        .setEnabled(true)
                        .build()
                )

                // 启用支付方式（Adaptive Pricing 会自动根据地区显示更多合适的支付方式）
                // 注意：只添加基础的 CARD 类型，其他支付方式由 Stripe 根据用户地区自动启用
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)

                .build();

            Session session = Session.create(params);

            // 11. 创建支付记录
            PaymentDO payment = new PaymentDO();
            payment.setPaymentNo(paymentNo);
            payment.setOrderNo(orderNo);
            payment.setUserId(userId);
            payment.setAmount(order.getPayAmount());
            payment.setPaymentMethod(PaymentMethod.STRIPE.name());
            payment.setPaymentStatus(PaymentStatus.PENDING.name());
            payment.setTradeNo(session.getId());  // 存储 Session ID (cs_xxx)
            payment.setCodeUrl(session.getUrl());  // 存储 Checkout URL

            paymentMapper.insert(payment);

            log.info("创建 Stripe Checkout Session - 支付流水号: {}, 订单号: {}, 金额: {} CNY, Session ID: {}, 商品数量: {}",
                paymentNo, orderNo, order.getPayAmount(), session.getId(), lineItems.size());

            // 事务提交后再发送掉单检查延迟消息，避免事务回滚后消息已发出但支付记录不存在
            final String finalOrderNo = orderNo;
            final Long paymentId = payment.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    paymentMessageProducer.sendPaymentCheckDelayMessage(finalOrderNo, paymentId);
                }
            });

            // 12. 更新订单支付方式
            orderMapper.updatePaymentMethod(orderNo, PaymentMethod.STRIPE.name());

            // 13. 返回支付信息
            return paymentConverter.toStripePaymentVO(payment);

        } catch (StripeException e) {
            log.error("创建 Stripe Checkout Session 失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    @Override
    public StripePaymentVO queryPayment(String paymentNo, Long userId) {
        // 1. 查询支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 验证用户权限
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 返回支付信息
        return paymentConverter.toStripePaymentVO(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWebhook(String payload, String signature) {
        Stripe.apiKey = stripeSecretKey;

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Webhook 签名验证失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.PAYMENT_VERIFY_FAILED);
        }

        String eventType = event.getType();
        log.info("收到 Stripe Webhook 事件: {}", eventType);

        // 处理 Checkout Session 事件
        switch (eventType) {
            case "checkout.session.completed":
                handleCheckoutSessionCompleted(event);
                break;

            case "checkout.session.expired":
                handleCheckoutSessionExpired(event);
                break;

            default:
                log.info("未处理的 Webhook 事件类型: {}", eventType);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StripeRefundVO createRefund(StripeRefundDTO request, Long userId) {
        Stripe.apiKey = stripeSecretKey;

        // 1. 查询支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(request.getPaymentNo());
        if (payment == null) {
            log.error("退款失败 - 支付记录不存在,支付流水号: {}", request.getPaymentNo());
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 验证支付状态
        if (!PaymentStatus.SUCCESS.name().equals(payment.getPaymentStatus())) {
            log.error("退款失败 - 支付状态异常,支付流水号: {}, 支付状态: {}",
                request.getPaymentNo(), payment.getPaymentStatus());
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        // 3. 验证退款金额
        if (request.getRefundAmount().compareTo(payment.getAmount()) > 0) {
            log.error("退款失败 - 退款金额超过支付金额,支付流水号: {}, 支付金额: {}, 退款金额: {}",
                request.getPaymentNo(), payment.getAmount(), request.getRefundAmount());
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        // 4. 检查是否已退款（幂等性检查）
        RefundDO existingRefund = refundMapper.findByPaymentNo(request.getPaymentNo());
        if (existingRefund != null && "SUCCESS".equals(existingRefund.getRefundStatus())) {
            log.warn("支付已退款 - 返回已有退款记录，支付流水号: {}", request.getPaymentNo());
            // 幂等性：返回已有退款记录，而不是抛出异常
            return refundConverter.toStripeRefundVO(existingRefund);
        }

        // 5. 检查支付记录是否已标记为已退款
        if (PaymentStatus.REFUNDED.name().equals(payment.getPaymentStatus())) {
            log.warn("支付记录已标记为已退款 - 支付流水号: {}", request.getPaymentNo());
            // 如果支付已退款但没有退款记录，返回现有信息
            if (existingRefund != null) {
                return refundConverter.toStripeRefundVO(existingRefund);
            }
        }

        // 5. 生成退款流水号
        String refundNo = generateRefundNo();

        try {
            // 6. 获取 PaymentIntent ID
            // handleCheckoutSessionCompleted 已将 trade_no 更新为 PaymentIntent ID（pi_xxx）
            String paymentIntentId = payment.getTradeNo();
            if (paymentIntentId == null || paymentIntentId.isEmpty()) {
                log.error("PaymentIntent ID 为空，无法退款 - 支付流水号: {}", request.getPaymentNo());
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }

            // 7. 调用 Stripe 退款 API
            long refundAmountStripe = request.getRefundAmount().multiply(new BigDecimal("100")).longValue();

            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(refundAmountStripe)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("refund_no", refundNo)
                .putMetadata("reason", request.getReason() != null ? request.getReason() : "用户申请退款")
                .build();

            Refund refund = Refund.create(params);

            // 8. 创建退款记录
            RefundDO refundDO = new RefundDO();
            refundDO.setRefundNo(refundNo);
            refundDO.setOrderNo(payment.getOrderNo());
            refundDO.setPaymentNo(request.getPaymentNo());
            refundDO.setTradeNo(refund.getId());
            refundDO.setRefundAmount(request.getRefundAmount());
            refundDO.setRefundReason(request.getReason() != null ? request.getReason() : "用户申请退款");
            refundDO.setRefundStatus("PROCESSING");

            refundMapper.insert(refundDO);

            log.info("创建 Stripe 退款 - 退款流水号: {}, 支付流水号: {}, 退款金额: {} CNY, Stripe Refund ID: {}",
                refundNo, request.getPaymentNo(), request.getRefundAmount(), refund.getId());

            // 9. 返回退款响应
            return refundConverter.toStripeRefundVO(refundDO);

        } catch (StripeException e) {
            // 处理 Stripe 特定错误
            if ("charge_already_refunded".equals(e.getCode())) {
                // 幂等性：Charge 已经退款，视为成功
                log.warn("Charge 已退款 - 更新退款记录为成功，支付流水号: {}, 错误: {}",
                        request.getPaymentNo(), e.getMessage());

                // 创建或更新退款记录为成功状态
                if (existingRefund != null) {
                    existingRefund.setRefundStatus("SUCCESS");
                    existingRefund.setRefundTime(LocalDateTime.now());
                    refundMapper.updateById(existingRefund);

                    return refundConverter.toStripeRefundVO(existingRefund);
                } else {
                    // 创建新的退款记录（SUCCESS 状态）
                    RefundDO refundDO = new RefundDO();
                    refundDO.setRefundNo(refundNo);
                    refundDO.setOrderNo(payment.getOrderNo());
                    refundDO.setPaymentNo(request.getPaymentNo());
                    refundDO.setTradeNo(payment.getTradeNo()); // 使用 Session ID
                    refundDO.setRefundAmount(request.getRefundAmount());
                    refundDO.setRefundReason(request.getReason() != null ? request.getReason() : "用户申请退款");
                    refundDO.setRefundStatus("SUCCESS");
                    refundDO.setRefundTime(LocalDateTime.now());

                    refundMapper.insert(refundDO);

                    // 更新支付状态为已退款
                    payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
                    paymentMapper.updateById(payment);

                    return refundConverter.toStripeRefundVO(refundDO);
                }
            }

            // 其他 Stripe 错误
            log.error("Stripe 退款失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.REFUND_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefundWebhook(String payload, String signature) {
        Stripe.apiKey = stripeSecretKey;

        Event event;
        try {
            // 验证 Webhook 签名
            event = Webhook.constructEvent(payload, signature, refundWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("退款 Webhook 签名验证失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.PAYMENT_VERIFY_FAILED);
        }

        // 处理退款事件
        String eventType = event.getType();
        log.info("收到 Stripe 退款 Webhook 事件: {}", eventType);

        if ("charge.refunded".equals(eventType)) {
            handleChargeRefunded(event);
        } else {
            log.info("未处理的退款 Webhook 事件类型: {}", eventType);
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 处理 Checkout Session 完成事件
     */
    private void handleCheckoutSessionCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject()
            .orElse(null);

        if (session == null) {
            log.error("无法解析 Checkout Session 对象");
            return;
        }

        String sessionId = session.getId();
        String orderNo = session.getMetadata().get("order_no");
        String paymentNoFromMeta = session.getMetadata().get("payment_no");
        log.info("处理 Checkout Session 完成事件 - Session ID: {}, 订单号: {}, 支付流水号: {}",
                sessionId, orderNo, paymentNoFromMeta);

        // 优先通过 metadata 中的 payment_no 精确定位支付记录，避免多支付记录时 TooManyResultsException
        PaymentDO payment = null;
        if (paymentNoFromMeta != null && !paymentNoFromMeta.isEmpty()) {
            payment = paymentMapper.findByPaymentNo(paymentNoFromMeta);
        }
        if (payment == null) {
            log.error("支付记录不存在 - 订单号: {}, 支付流水号: {}", orderNo, paymentNoFromMeta);
            return;
        }

        // 幂等性检查
        if (PaymentStatus.SUCCESS.name().equals(payment.getPaymentStatus())) {
            log.warn("支付已完成,忽略重复通知 - 支付流水号: {}", payment.getPaymentNo());
            return;
        }

        // CLOSED 状态收到支付成功通知 → 支付单已被关闭但用户仍完成了支付（竞态），需自动退款
        if (PaymentStatus.CLOSED.name().equals(payment.getPaymentStatus())) {
            String paymentIntentIdForRefund = session.getPaymentIntent();
            log.warn("已关闭的 Stripe 支付单收到支付成功通知，将自动退款 - 支付流水号: {}, PaymentIntent: {}",
                    payment.getPaymentNo(), paymentIntentIdForRefund);
            // 更新 trade_no 以便退款使用
            payment.setTradeNo(paymentIntentIdForRefund);
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
            // 调用 Stripe 退款
            autoRefundClosedStripePayment(payment, paymentIntentIdForRefund);
            return;
        }

        // 校验支付状态转换合法性（PENDING->SUCCESS）
        PaymentStatus currentPaymentStatus = PaymentStatus.valueOf(payment.getPaymentStatus());
        currentPaymentStatus.transitTo(PaymentStatus.SUCCESS);

        // 从 Session 获取 PaymentIntent ID，用于后续退款 Webhook 匹配
        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null) {
            log.warn("Session 暂无 PaymentIntent ID（支付可能仍在处理中）- Session ID: {}", sessionId);
            paymentIntentId = sessionId; // 降级：保留 Session ID
        }

        // 更新支付记录，trade_no 改存 PaymentIntent ID（pi_xxx），供退款 Webhook 查询使用
        payment.setPaymentStatus(PaymentStatus.SUCCESS.name());
        payment.setNotifyTime(LocalDateTime.now());
        payment.setTradeNo(paymentIntentId);
        paymentMapper.updateById(payment);

        // 原子性更新订单状态为 PAID（乐观锁防止并发重复）
        int updated = orderMapper.compareAndUpdateStatus(
                orderNo, OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
        if (updated > 0) {
            orderMapper.updatePaymentTime(orderNo);
            log.info("订单支付成功 - 订单号: {}, 支付流水号: {}, PaymentIntent ID: {}",
                orderNo, payment.getPaymentNo(), paymentIntentId);
            // 支付成功后，更新商品销量
            OrderDO order = orderMapper.findByOrderNo(orderNo);
            if (order != null) {
                List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
                for (OrderItemDO item : items) {
                    productMapper.increaseSalesCount(item.getProductId(), item.getQuantity());
                    if (item.getSkuId() != null && item.getSkuId() > 0) {
                        skuMapper.increaseSalesCount(item.getSkuId(), item.getQuantity());
                    }
                }
            }
        } else {
            log.warn("订单状态已被其他线程更新，跳过 - 订单号: {}", orderNo);
        }
    }

    /**
     * 处理 Checkout Session 过期事件
     */
    private void handleCheckoutSessionExpired(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject()
            .orElse(null);

        if (session == null) {
            log.error("无法解析 Checkout Session 对象");
            return;
        }

        String sessionId = session.getId();
        String orderNo = session.getMetadata().get("order_no");
        String paymentNoFromMeta = session.getMetadata().get("payment_no");
        log.info("处理 Checkout Session 过期事件 - Session ID: {}, 订单号: {}, 支付流水号: {}",
                sessionId, orderNo, paymentNoFromMeta);

        // 优先通过 metadata 中的 payment_no 精确定位支付记录，避免多支付记录时 TooManyResultsException
        PaymentDO payment = null;
        if (paymentNoFromMeta != null && !paymentNoFromMeta.isEmpty()) {
            payment = paymentMapper.findByPaymentNo(paymentNoFromMeta);
        }
        if (payment == null) {
            log.error("支付记录不存在 - 订单号: {}, 支付流水号: {}", orderNo, paymentNoFromMeta);
            return;
        }

        // 仅当支付状态为 PENDING 时更新为 FAILED
        if (PaymentStatus.PENDING.name().equals(payment.getPaymentStatus())) {
            payment.setPaymentStatus(PaymentStatus.FAILED.name());
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
            log.info("支付 Session 已过期 - 订单号: {}, 支付流水号: {}", orderNo, payment.getPaymentNo());
        }
    }

    /**
     * 处理退款成功事件
     */
    private void handleChargeRefunded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
            .getObject()
            .orElse(null);

        if (charge == null) {
            log.error("无法解析 Charge 对象");
            return;
        }

        String chargeId = charge.getId();
        log.info("处理退款成功事件 - Charge ID: {}", chargeId);

        // 获取 PaymentIntent ID
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            log.error("Charge 未关联 PaymentIntent - Charge ID: {}", chargeId);
            return;
        }

        // 查询支付记录
        PaymentDO payment = paymentMapper.findByTradeNo(paymentIntentId);
        if (payment == null) {
            log.error("支付记录不存在 - PaymentIntent ID: {}", paymentIntentId);
            return;
        }

        // 查询退款记录
        RefundDO refund = refundMapper.findByPaymentNo(payment.getPaymentNo());
        if (refund == null) {
            log.error("退款记录不存在 - 支付流水号: {}", payment.getPaymentNo());
            return;
        }

        // 更新退款记录
        refund.setRefundStatus("SUCCESS");
        refund.setRefundTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        // 更新支付记录
        payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
        paymentMapper.updateById(payment);

        log.info("退款成功 - 订单号: {}, 退款流水号: {}, 支付流水号: {}",
            payment.getOrderNo(), refund.getRefundNo(), payment.getPaymentNo());
    }

    /**
     * 生成退款流水号
     */
    private String generateRefundNo() {
        return "RF" + System.currentTimeMillis() +
            String.format("%06d", (int) (Math.random() * 1000000));
    }

    @Override
    public boolean queryPaymentStatus(PaymentDO payment) {
        Stripe.apiKey = stripeSecretKey;

        // tradeNo 在创建时存储的是 Session ID（cs_xxx）
        // 支付完成后 handleCheckoutSessionCompleted 会将其更新为 PaymentIntent ID（pi_xxx）
        // 掉单补偿时支付仍为 PENDING，tradeNo 应还是 Session ID
        String tradeNo = payment.getTradeNo();
        if (tradeNo == null || tradeNo.isEmpty()) {
            log.warn("掉单补偿查询 Stripe 支付状态：tradeNo 为空 - 支付流水号: {}", payment.getPaymentNo());
            return false;
        }

        try {
            if (tradeNo.startsWith("pi_")) {
                // 已经是 PaymentIntent ID，直接查询
                PaymentIntent intent = PaymentIntent.retrieve(tradeNo);
                boolean succeeded = "succeeded".equals(intent.getStatus());
                log.info("掉单补偿查询 Stripe PaymentIntent 状态 - 支付流水号: {}, PaymentIntent: {}, 状态: {}",
                        payment.getPaymentNo(), tradeNo, intent.getStatus());
                return succeeded;
            } else {
                // 仍是 Session ID，通过 Session 查询 payment_status
                Session session = Session.retrieve(tradeNo);
                boolean paid = "paid".equals(session.getPaymentStatus());
                log.info("掉单补偿查询 Stripe Session 支付状态 - 支付流水号: {}, Session: {}, payment_status: {}",
                        payment.getPaymentNo(), tradeNo, session.getPaymentStatus());
                if (paid) {
                    String paymentIntentId = session.getPaymentIntent();
                    if (paymentIntentId != null) {
                        payment.setTradeNo(paymentIntentId);
                    }
                }
                return paid;
            }
        } catch (StripeException e) {
            log.error("掉单补偿查询 Stripe 支付状态异常 - 支付流水号: {}, tradeNo: {}",
                    payment.getPaymentNo(), tradeNo, e);
            return false;
        }
    }

    @Override
    public void expireSession(PaymentDO payment) {
        Stripe.apiKey = stripeSecretKey;

        // trade_no 在创建时存的是 Session ID（cs_xxx），Session 完成后会更新为 pi_xxx
        // 互斥场景下，支付单仍是 PENDING，trade_no 应还是 Session ID
        String sessionId = payment.getTradeNo();
        if (sessionId == null || sessionId.isEmpty()) {
            // 无 Session ID，只更新本地状态
            log.warn("Stripe 支付单无 Session ID，仅更新本地状态 - 支付流水号: {}", payment.getPaymentNo());
            payment.setPaymentStatus(PaymentStatus.CLOSED.name());
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
            return;
        }

        try {
            Session session = Session.retrieve(sessionId);
            // 只有 open 状态的 Session 才能 expire
            if ("open".equals(session.getStatus())) {
                session.expire();
                log.info("Stripe Session 已过期 - Session ID: {}, 支付流水号: {}", sessionId, payment.getPaymentNo());
            } else {
                log.info("Stripe Session 已非 open 状态，跳过 expire - Session ID: {}, 状态: {}", sessionId, session.getStatus());
            }
        } catch (StripeException e) {
            log.error("Stripe Session expire 失败（仅记录日志，不阻断流程）- Session ID: {}, 错误: {}",
                    sessionId, e.getMessage());
        }

        // 更新本地支付状态为 CLOSED
        payment.setPaymentStatus(PaymentStatus.CLOSED.name());
        payment.setNotifyTime(LocalDateTime.now());
        paymentMapper.updateById(payment);
        log.info("Stripe 支付单已更新为 CLOSED - 支付流水号: {}", payment.getPaymentNo());
    }

    /**
     * 已关闭的 Stripe 支付单收到支付成功通知时，自动退款。
     * 竞态场景：用户在我们标记 CLOSED 后仍完成了 Stripe Checkout 支付。
     */
    private void autoRefundClosedStripePayment(PaymentDO payment, String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.error("无法自动退款：PaymentIntent ID 为空 - 支付流水号: {}", payment.getPaymentNo());
            return;
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .build();

            Refund stripeRefund = Refund.create(params);
            log.info("已关闭 Stripe 支付单自动退款成功 - 支付流水号: {}, Refund ID: {}",
                    payment.getPaymentNo(), stripeRefund.getId());

            payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
            paymentMapper.updateById(payment);
        } catch (StripeException e) {
            log.error("已关闭 Stripe 支付单自动退款失败，需人工介入 - 支付流水号: {}, PaymentIntent: {}",
                    payment.getPaymentNo(), paymentIntentId, e);
        }
    }
}
