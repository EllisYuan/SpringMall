package site.geekie.shop.shoppingmall.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.PaymentMethod;
import site.geekie.shop.shoppingmall.common.PaymentStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.config.AlipayConfig;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.mq.producer.PaymentMessageProducer;
import site.geekie.shop.shoppingmall.converter.PaymentConverter;
import site.geekie.shop.shoppingmall.service.AlipayPaymentService;
import site.geekie.shop.shoppingmall.service.PaymentCloseService;
import site.geekie.shop.shoppingmall.util.OrderNoGenerator;
import site.geekie.shop.shoppingmall.util.RedisDistributedLock;
import site.geekie.shop.shoppingmall.vo.AlipayPaymentVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝支付服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayPaymentServiceImpl implements AlipayPaymentService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final RedisDistributedLock redisDistributedLock;
    private final PaymentCloseService paymentCloseService;
    private final PaymentMessageProducer paymentMessageProducer;
    private final PaymentConverter paymentConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlipayPaymentVO createAlipay(String orderNo, Long userId) {
        String lockKey = "lock:payment:create:" + orderNo;
        String lockValue = redisDistributedLock.tryLock(lockKey, 60, TimeUnit.SECONDS);
        if (lockValue == null) {
            throw new BusinessException(ResultCode.PAYMENT_LOCK_FAILED);
        }

        try {
            return doCreateAlipayIntent(orderNo, userId);
        } finally {
            redisDistributedLock.unlock(lockKey, lockValue);
        }
    }

    /**
     * createPayment 内部实现，由分布式锁保护
     */
    private AlipayPaymentVO doCreateAlipayIntent(String orderNo, Long userId) {
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

        // 6. 检查是否已有支付记录（关闭其他方式后，查看是否还有同方式的 PENDING 记录可复用）
        PaymentDO existingPayment = paymentMapper.findPendingByOrderNo(orderNo).stream()
                .filter(p -> PaymentMethod.ALIPAY.name().equals(p.getPaymentMethod()))
                .findFirst()
                .orElse(null);
        if (existingPayment != null) {
            // 已有待支付记录，返回已有支付信息
            log.info("订单已有待支付记录 - 订单号: {}, 支付流水号: {}", orderNo, existingPayment.getPaymentNo());
            // 重新生成支付表单
            String paymentUrl = doCreatePayment(
                    existingPayment.getPaymentNo(),
                    orderNo,
                    buildPaymentSubject(orderItems, orderNo),
                    buildPaymentBody(orderItems, order.getPayAmount()),
                    order.getPayAmount().toString()
            );

            AlipayPaymentVO existingVO = paymentConverter.toAlipayPaymentVO(existingPayment);
            existingVO.setPaymentUrl(paymentUrl);
            return existingVO;
        }

        // 7. 生成支付流水号
        String paymentNo = OrderNoGenerator.generateOrderNo();

        // 8. 创建支付记录
        PaymentDO payment = new PaymentDO();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmount(order.getPayAmount());
        payment.setPaymentMethod(PaymentMethod.ALIPAY.name());
        payment.setPaymentStatus(PaymentStatus.PENDING.name());

        paymentMapper.insert(payment);

        log.info("创建支付记录 - 支付流水号: {}, 订单号: {}, 金额: {}, 商品: {}",
                paymentNo, orderNo, order.getPayAmount(), buildPaymentSubject(orderItems, orderNo));

        // 事务提交后再发送掉单检查延迟消息，避免事务回滚后消息已发出但支付记录不存在
        final Long paymentId = payment.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paymentMessageProducer.sendPaymentCheckDelayMessage(orderNo, paymentId);
            }
        });

        // 10. 调用支付宝API生成支付表单
        String paymentUrl = doCreatePayment(
                paymentNo,
                orderNo,
                buildPaymentSubject(orderItems, orderNo),
                buildPaymentBody(orderItems, order.getPayAmount()),
                order.getPayAmount().toString()
        );

        // 11. 更新订单支付方式
        orderMapper.updatePaymentMethod(orderNo, PaymentMethod.ALIPAY.name());

        // 12. 返回支付信息
        AlipayPaymentVO newVO = paymentConverter.toAlipayPaymentVO(payment);
        newVO.setPaymentUrl(paymentUrl);
        return newVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params) {
        // 1. 验证签名
        // 注意：rsaCheckV1 会原地修改 params，移除 sign 和 sign_type，
        // 因此在调用前记录完整原始参数，供验签失败时的日志排查使用。
        log.info("支付宝异步通知参数: {}", params);
        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
        } catch (AlipayApiException e) {
            log.error("支付宝异步通知验签异常", e);
            return "failure";
        }

        if (!signVerified) {
            log.error("支付宝异步通知验签失败");
            return "failure";
        }

        // 2. 获取通知参数
        String outTradeNo = params.get("out_trade_no");  // 支付流水号
        String tradeNo = params.get("trade_no");          // 支付宝交易号
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        log.info("支付宝异步通知 - 支付流水号: {}, 交易号: {}, 状态: {}, 金额: {}",
                outTradeNo, tradeNo, tradeStatus, totalAmount);

        // 3. 查询支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(outTradeNo);
        if (payment == null) {
            log.error("支付记录不存在 - 支付流水号: {}", outTradeNo);
            return "failure";
        }

        // 4. 验证金额
        BigDecimal notifyAmount = new BigDecimal(totalAmount);
        if (payment.getAmount().compareTo(notifyAmount) != 0) {
            log.error("支付金额不匹配 - 支付流水号: {}, 订单金额: {}, 通知金额: {}",
                    outTradeNo, payment.getAmount(), notifyAmount);
            return "failure";
        }

        // 5. 幂等性检查
        if (PaymentStatus.SUCCESS.name().equals(payment.getPaymentStatus())) {
            log.warn("支付已完成，忽略重复通知 - 支付流水号: {}", outTradeNo);
            return "success";
        }

        // 5a. CLOSED 状态收到 SUCCESS 通知 → 支付单已被关闭但用户仍完成了支付（竞态），需自动退款
        if (PaymentStatus.CLOSED.name().equals(payment.getPaymentStatus())
                && ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))) {
            log.warn("已关闭的支付单收到支付成功通知，将自动退款 - 支付流水号: {}, 交易号: {}", outTradeNo, tradeNo);
            // 更新 trade_no 以便退款 API 使用
            payment.setTradeNo(tradeNo);
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
            // 调用支付宝退款
            String refundNo = OrderNoGenerator.generateOrderNo();
            boolean refundSuccess = refund(refundNo, tradeNo, payment.getAmount(), "支付单已关闭，自动退款");
            if (refundSuccess) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED.name());
                paymentMapper.updateById(payment);
                log.info("已关闭支付单自动退款成功 - 支付流水号: {}, 退款流水号: {}", outTradeNo, refundNo);
            } else {
                log.error("已关闭支付单自动退款失败，需人工介入 - 支付流水号: {}, 交易号: {}", outTradeNo, tradeNo);
            }
            return "success";
        }

        // 6. 更新支付记录
        PaymentStatus currentPaymentStatus = PaymentStatus.valueOf(payment.getPaymentStatus());
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            // 校验支付状态转换合法性（PENDING->SUCCESS）
            currentPaymentStatus.transitTo(PaymentStatus.SUCCESS);

            payment.setPaymentStatus(PaymentStatus.SUCCESS.name());
            payment.setTradeNo(tradeNo);
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);

            // 7. 原子性更新订单状态为 PAID（乐观锁防止并发重复）
            int updated = orderMapper.compareAndUpdateStatus(
                    payment.getOrderNo(), OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
            if (updated > 0) {
                orderMapper.updatePaymentTime(payment.getOrderNo());
                log.info("订单支付成功 - 订单号: {}, 支付流水号: {}, 交易号: {}",
                        payment.getOrderNo(), outTradeNo, tradeNo);
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
            } else {
                log.warn("订单状态已被其他线程更新，跳过 - 订单号: {}", payment.getOrderNo());
            }

            return "success";
        } else if ("TRADE_CLOSED".equals(tradeStatus)) {
            // 校验支付状态转换合法性（PENDING->CLOSED）
            currentPaymentStatus.transitTo(PaymentStatus.CLOSED);

            payment.setPaymentStatus(PaymentStatus.CLOSED.name());
            payment.setTradeNo(tradeNo);
            payment.setNotifyTime(LocalDateTime.now());
            paymentMapper.updateById(payment);

            log.info("支付已关闭 - 支付流水号: {}", outTradeNo);
            return "success";
        } else {
            log.warn("未处理的交易状态 - 状态: {}", tradeStatus);
            return "failure";
        }
    }

    @Override
    public AlipayPaymentVO queryPayment(String paymentNo, Long userId) {
        // 1. 查询支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 验证支付记录所有权
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 如果是待支付状态，查询支付宝实时状态
        if (PaymentStatus.PENDING.name().equals(payment.getPaymentStatus())) {
            String tradeStatus = doQueryPaymentStatus(paymentNo);
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 支付成功，更新记录
                payment.setPaymentStatus(PaymentStatus.SUCCESS.name());
                paymentMapper.updateById(payment);
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                // 交易关闭
                payment.setPaymentStatus(PaymentStatus.CLOSED.name());
                paymentMapper.updateById(payment);
            }
        }

        // 4. 返回支付信息
        return paymentConverter.toAlipayPaymentVO(payment);
    }

    @Override
    public boolean refund(String refundNo, String tradeNo, BigDecimal refundAmount, String refundReason) {
        try {
            // 创建API请求对象
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

            // 创建业务请求参数模型
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setTradeNo(tradeNo);                         // 支付宝交易号
            model.setRefundAmount(refundAmount.toString());    // 退款金额
            model.setRefundReason(refundReason);               // 退款原因
            model.setOutRequestNo(refundNo);                   // 退款请求号（退款流水号）

            // 设置业务参数
            request.setBizModel(model);

            // 调用SDK申请退款
            AlipayTradeRefundResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                log.info("支付宝退款成功 - 退款流水号: {}, 支付宝交易号: {}, 退款金额: {}",
                        refundNo, tradeNo, refundAmount);
                return true;
            } else {
                log.error("支付宝退款失败 - 错误码: {}, 错误信息: {}, 退款流水号: {}, 支付宝交易号: {}",
                        response.getCode(), response.getMsg(), refundNo, tradeNo);
                return false;
            }

        } catch (AlipayApiException e) {
            log.error("支付宝退款异常 - 退款流水号: {}, 支付宝交易号: {}", refundNo, tradeNo, e);
            return false;
        }
    }

    /**
     * 调用支付宝SDK生成支付表单
     *
     * @param paymentNo 支付流水号
     * @param orderNo 订单号
     * @param subject 支付标题
     * @param body 支付描述
     * @param totalAmount 支付金额
     * @return 支付表单HTML
     */
    private String doCreatePayment(String paymentNo, String orderNo, String subject, String body, String totalAmount) {
        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
            request.setReturnUrl(alipayConfig.getReturnUrl());

            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(paymentNo);
            model.setTotalAmount(totalAmount);
            model.setSubject(subject);
            model.setBody(body);
            model.setProductCode("FAST_INSTANT_TRADE_PAY");

            request.setBizModel(model);

            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            if (response.isSuccess()) {
                log.info("支付宝支付表单生成成功 - 支付流水号: {}, 订单号: {}, 商品: {}", paymentNo, orderNo, subject);
                return response.getBody();
            } else {
                log.error("支付宝支付表单生成失败 - 错误码: {}, 错误信息: {}",
                        response.getCode(), response.getMsg());
                throw new BusinessException(ResultCode.PAYMENT_FAILED);
            }

        } catch (AlipayApiException e) {
            log.error("支付宝API调用异常 - 支付流水号: {}", paymentNo, e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    /**
     * 调用支付宝SDK查询支付状态
     *
     * @param paymentNo 支付流水号
     * @return 支付状态字符串，查询失败时返回 null
     */
    private String doQueryPaymentStatus(String paymentNo) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(paymentNo);

            request.setBizModel(model);

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                log.info("支付宝支付状态查询成功 - 支付流水号: {}, 状态: {}", paymentNo, tradeStatus);
                return tradeStatus;
            } else {
                log.error("支付宝支付状态查询失败 - 错误码: {}, 错误信息: {}",
                        response.getCode(), response.getMsg());
                return null;
            }

        } catch (AlipayApiException e) {
            log.error("支付宝支付状态查询异常 - 支付流水号: {}", paymentNo, e);
            return null;
        }
    }

    /**
     * 调用支付宝SDK关闭支付
     *
     * @param paymentNo 支付流水号
     */
    /**
     * 关单时可视为"已关闭"的支付宝子错误码：
     * - ACQ.TRADE_NOT_EXIST：交易在支付宝端从未创建（用户生成了支付链接但未跳转）
     * - ACQ.TRADE_HAS_CLOSE：交易已经被关闭过
     * - ACQ.REASON_TRADE_STATUS_INVALID：交易状态不允许关闭（已退款等终态）
     */
    private static final java.util.Set<String> CLOSE_IGNORABLE_SUB_CODES = java.util.Set.of(
            "ACQ.TRADE_NOT_EXIST",
            "ACQ.TRADE_HAS_CLOSE",
            "ACQ.REASON_TRADE_STATUS_INVALID"
    );

    private void doClosePayment(String paymentNo) {
        try {
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(paymentNo);

            request.setBizModel(model);

            AlipayTradeCloseResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                log.info("支付宝支付关闭成功 - 支付流水号: {}", paymentNo);
            } else {
                String subCode = response.getSubCode();
                if (subCode != null && CLOSE_IGNORABLE_SUB_CODES.contains(subCode)) {
                    log.info("支付宝支付无需关闭（{}） - 支付流水号: {}", subCode, paymentNo);
                } else {
                    log.error("支付宝支付关闭失败 - 错误码: {}, 子错误码: {}, 错误信息: {}",
                            response.getCode(), subCode, response.getSubMsg());
                    throw new BusinessException(ResultCode.PAYMENT_CLOSE_FAILED);
                }
            }

        } catch (AlipayApiException e) {
            log.error("支付宝支付关闭异常 - 支付流水号: {}", paymentNo, e);
            throw new BusinessException(ResultCode.PAYMENT_CLOSE_FAILED);
        }
    }

    /**
     * 构建支付标题
     * 格式：
     * - 单商品："{商品名称} x{数量} - 订单{订单号}"
     * - 多商品："{首个商品名称}等{商品总数}件 - 订单{订单号}"
     *
     * @param orderItems 订单商品列表
     * @param orderNo 订单号
     * @return 支付标题（最长 256 字符）
     */
    private String buildPaymentSubject(List<OrderItemDO> orderItems, String orderNo) {
        StringBuilder subject = new StringBuilder();

        if (orderItems.size() == 1) {
            // 单商品订单
            OrderItemDO item = orderItems.get(0);
            String productName = item.getProductName() != null ? item.getProductName() : "商品";
            subject.append(productName)
                    .append(" x")
                    .append(item.getQuantity());
        } else {
            // 多商品订单
            OrderItemDO firstItem = orderItems.get(0);
            String productName = firstItem.getProductName() != null ? firstItem.getProductName() : "商品";
            subject.append(productName)
                    .append("等")
                    .append(orderItems.size())
                    .append("件商品");
        }

        subject.append(" - 订单").append(orderNo);

        // 支付宝 subject 限制：最长 256 字符
        String result = subject.toString();
        if (result.length() > 256) {
            result = result.substring(0, 253) + "...";
        }

        return result;
    }

    /**
     * 构建支付描述
     * 格式："{商品1名称} x{数量}、{商品2名称} x{数量}，共计¥{金额}"
     *
     * @param orderItems 订单商品列表
     * @param totalAmount 订单总金额
     * @return 支付描述（最长 128 字符）
     */
    private String buildPaymentBody(List<OrderItemDO> orderItems, BigDecimal totalAmount) {
        StringBuilder body = new StringBuilder();

        for (int i = 0; i < orderItems.size(); i++) {
            OrderItemDO item = orderItems.get(i);
            if (i > 0) {
                body.append("、");
            }
            String productName = item.getProductName() != null ? item.getProductName() : "商品";
            body.append(productName)
                    .append(" x")
                    .append(item.getQuantity());

            // 如果描述过长，提前截断
            if (body.length() > 100) {
                body.setLength(0);
                body.append(orderItems.get(0).getProductName() != null ? orderItems.get(0).getProductName() : "商品")
                        .append("等")
                        .append(orderItems.size())
                        .append("件商品");
                break;
            }
        }

        body.append("，共计¥").append(totalAmount);

        // 支付宝 body 限制：最长 128 字符
        String result = body.toString();
        if (result.length() > 128) {
            result = result.substring(0, 125) + "...";
        }

        return result;
    }

    @Override
    public void closePayment(PaymentDO payment) {
        // 如果 trade_no 为空，说明用户从未在支付宝端发起支付，无需调用支付宝关单 API
        if (payment.getTradeNo() != null && !payment.getTradeNo().isBlank()) {
            doClosePayment(payment.getPaymentNo());
        } else {
            log.info("支付宝支付单无 trade_no（用户未发起支付），跳过远程关单 - 支付流水号: {}", payment.getPaymentNo());
        }
        // 更新本地支付状态为 CLOSED
        payment.setPaymentStatus(PaymentStatus.CLOSED.name());
        payment.setNotifyTime(LocalDateTime.now());
        paymentMapper.updateById(payment);
        log.info("支付宝支付单已关闭并更新本地状态 - 支付流水号: {}", payment.getPaymentNo());
    }

    @Override
    public boolean queryPaymentStatus(PaymentDO payment) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            // 使用支付流水号（out_trade_no）查询
            model.setOutTradeNo(payment.getPaymentNo());

            request.setBizModel(model);

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                log.info("掉单补偿查询支付宝支付状态 - 支付流水号: {}, 状态: {}",
                        payment.getPaymentNo(), tradeStatus);
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    payment.setTradeNo(response.getTradeNo());
                    return true;
                }
                return false;
            } else {
                String subCode = response.getSubCode();
                if ("ACQ.TRADE_NOT_EXIST".equals(subCode)) {
                    log.info("支付宝交易不存在（可能用户未完成支付页面跳转） - 支付流水号: {}", payment.getPaymentNo());
                } else {
                    log.warn("掉单补偿查询支付宝支付状态失败 - 支付流水号: {}, 错误码: {}, 子错误码: {}, 错误信息: {}",
                            payment.getPaymentNo(), response.getCode(), subCode, response.getSubMsg());
                }
                return false;
            }
        } catch (AlipayApiException e) {
            log.error("掉单补偿查询支付宝支付状态异常 - 支付流水号: {}", payment.getPaymentNo(), e);
            return false;
        }
    }
}
