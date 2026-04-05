package site.geekie.shop.shoppingmall.service.impl;

import com.wechat.pay.java.core.exception.HttpException;
import com.wechat.pay.java.core.exception.MalformedMessageException;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.config.WxPayConfig;
import site.geekie.shop.shoppingmall.dto.CreateWxPaymentDTO;
import site.geekie.shop.shoppingmall.dto.WxRefundDTO;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.entity.RefundDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.exception.WxPayException;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;
import site.geekie.shop.shoppingmall.mapper.PaymentMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.mapper.RefundMapper;
import site.geekie.shop.shoppingmall.converter.PaymentConverter;
import site.geekie.shop.shoppingmall.converter.RefundConverter;
import site.geekie.shop.shoppingmall.service.WxPayService;
import site.geekie.shop.shoppingmall.util.OrderNoGenerator;
import site.geekie.shop.shoppingmall.vo.WxPaymentVO;
import site.geekie.shop.shoppingmall.vo.WxRefundVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 微信支付服务实现类
 * 使用官方 wechatpay-java SDK
 *
 * 注意：只有在 wxpay.enabled=true 时才会注册此 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
public class WxPayServiceImpl implements WxPayService {

    private final NativePayService nativePayService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;
    private final WxPayConfig wxPayConfig;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final PaymentConverter paymentConverter;
    private final RefundConverter refundConverter;

    @Override
    @Transactional
    public WxPaymentVO createNativePayment(CreateWxPaymentDTO dto, Long userId) {
        log.info("创建微信Native支付订单，订单号：{}，金额：{}", dto.getOrderNo(), dto.getAmount());

        // 1. 查询订单
        OrderDO order = orderMapper.findByOrderNo(dto.getOrderNo());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 2. 验证订单属于当前用户
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }

        // 3. 验证订单状态（必须是待支付）
        if (!"UNPAID".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_ORDER_STATUS, "订单状态不是待支付，无法创建支付");
        }

        // 4. 检查是否已存在 SUCCESS 支付记录
        PaymentDO existingPayment = paymentMapper.findSuccessByOrderNo(dto.getOrderNo());
        if (existingPayment != null) {
            throw new BusinessException(ResultCode.PAYMENT_FAILED, "订单已支付");
        }

        // 5. 生成支付流水号
        String paymentNo = OrderNoGenerator.generateOrderNo();

        // 6. 构建请求参数
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        // 金额转换：元 -> 分
        amount.setTotal((int) (dto.getAmount().multiply(new BigDecimal("100")).longValue()));
        request.setAmount(amount);
        request.setAppid(wxPayConfig.getAppId());
        request.setMchid(wxPayConfig.getMchId());
        request.setDescription(dto.getDescription());
        request.setNotifyUrl(wxPayConfig.getNotifyUrl());
        request.setOutTradeNo(paymentNo);

        try {
            // 7. 调用官方SDK下单
            PrepayResponse response = nativePayService.prepay(request);

            // 8. 保存支付记录到数据库
            PaymentDO payment = new PaymentDO();
            payment.setPaymentNo(paymentNo);
            payment.setOrderNo(dto.getOrderNo());
            payment.setUserId(userId);
            payment.setAmount(dto.getAmount());
            payment.setPaymentMethod("WECHAT");
            payment.setPaymentStatus("PENDING");
            payment.setCodeUrl(response.getCodeUrl());

            if (existingPayment != null) {
                // 更新已有记录
                payment.setId(existingPayment.getId());
                paymentMapper.updateById(payment);
            } else {
                // 插入新记录
                paymentMapper.insert(payment);
            }

            // 9. 更新订单的支付方式
            orderMapper.updatePaymentMethod(dto.getOrderNo(), "WECHAT");

            log.info("微信Native支付订单创建成功，支付单号：{}，二维码：{}",
                paymentNo, response.getCodeUrl());

            // 10. 转换为VO返回
            return paymentConverter.toWxPaymentVO(payment);

        } catch (HttpException e) {
            log.error("微信支付HTTP请求失败", e);
            throw new WxPayException(ResultCode.PAYMENT_FAILED, "微信支付系统繁忙，请稍后重试");
        } catch (ServiceException e) {
            log.error("微信支付下单失败，错误码：{}，错误信息：{}",
                e.getErrorCode(), e.getErrorMessage());
            throw new WxPayException(ResultCode.PAYMENT_FAILED, "微信支付下单失败：" + e.getErrorMessage());
        } catch (MalformedMessageException e) {
            log.error("微信支付响应解析失败", e);
            throw new WxPayException(ResultCode.PAYMENT_FAILED, "微信支付响应异常");
        }
    }

    @Override
    public WxPaymentVO queryPayment(String paymentNo, Long userId) {
        PaymentDO payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 验证支付记录属于当前用户
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此支付记录");
        }

        return paymentConverter.toWxPaymentVO(payment);
    }

    @Override
    @Transactional
    public void handlePaymentNotify(String requestBody, Map<String, String> headers) {
        log.info("接收到微信支付回调通知");

        // 1. 构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
            .serialNumber(headers.get("Wechatpay-Serial"))
            .nonce(headers.get("Wechatpay-Nonce"))
            .signature(headers.get("Wechatpay-Signature"))
            .timestamp(headers.get("Wechatpay-Timestamp"))
            .body(requestBody)
            .build();

        try {
            // 2. 自动验签、解密，返回强类型对象
            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);

            // 3. 验证交易状态
            if (!"SUCCESS".equals(transaction.getTradeState().name())) {
                log.warn("支付未成功，状态：{}", transaction.getTradeState());
                return;
            }

            String outTradeNo = transaction.getOutTradeNo();

            // 4. 查询支付记录（幂等性检查）
            PaymentDO payment = paymentMapper.findByPaymentNo(outTradeNo);
            if (payment == null) {
                log.error("支付记录不存在，支付单号：{}", outTradeNo);
                throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
            }

            // 5. 幂等性：如果已经处理过，直接返回
            if ("SUCCESS".equals(payment.getPaymentStatus())) {
                log.info("支付已处理过，支付单号：{}", outTradeNo);
                return;
            }

            // 6. 更新支付记录
            paymentMapper.updateStatus(outTradeNo, "SUCCESS", transaction.getTransactionId());

            // 7. 原子性更新订单状态为 PAID（乐观锁防止并发重复）
            int updated = orderMapper.compareAndUpdateStatus(
                    payment.getOrderNo(), OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
            if (updated > 0) {
                orderMapper.updatePaymentTime(payment.getOrderNo());
                // 支付成功后，更新商品销量
                OrderDO paidOrder = orderMapper.findByOrderNo(payment.getOrderNo());
                if (paidOrder != null) {
                    List<OrderItemDO> items = orderItemMapper.findByOrderId(paidOrder.getId());
                    for (OrderItemDO item : items) {
                        productMapper.increaseSalesCount(item.getProductId(), item.getQuantity());
                        if (item.getSkuId() != null && item.getSkuId() > 0) {
                            skuMapper.increaseSalesCount(item.getSkuId(), item.getQuantity());
                        }
                    }
                }
            }

            log.info("微信支付回调处理成功，支付单号：{}，微信订单号：{}",
                outTradeNo, transaction.getTransactionId());

        } catch (ValidationException e) {
            log.error("微信支付回调验签失败", e);
            throw e;
        } catch (MalformedMessageException e) {
            log.error("微信支付回调数据解析失败", e);
            throw new BusinessException(ResultCode.PAYMENT_VERIFY_FAILED, "回调数据格式错误");
        }
    }

    @Override
    @Transactional
    public WxRefundVO createRefund(WxRefundDTO dto, Long userId) {
        log.info("创建微信退款，支付单号：{}，退款金额：{}", dto.getPaymentNo(), dto.getRefundAmount());

        // 1. 查询支付记录
        PaymentDO payment = paymentMapper.findByPaymentNo(dto.getPaymentNo());
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 验证支付记录属于当前用户（防止横向越权）
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此支付记录");
        }

        // 3. 验证支付状态
        if (!"SUCCESS".equals(payment.getPaymentStatus())) {
            throw new BusinessException(ResultCode.REFUND_FAILED, "支付未成功，无法退款");
        }

        // 4. 验证退款金额
        if (dto.getRefundAmount().compareTo(payment.getAmount()) > 0) {
            throw new BusinessException(ResultCode.REFUND_FAILED, "退款金额不能大于支付金额");
        }

        // 5. 检查是否已退款
        RefundDO existingRefund = refundMapper.findByPaymentNo(dto.getPaymentNo());
        if (existingRefund != null && "SUCCESS".equals(existingRefund.getRefundStatus())) {
            throw new BusinessException(ResultCode.PAYMENT_ALREADY_REFUNDED);
        }

        // 6. 生成退款流水号
        String refundNo = OrderNoGenerator.generateOrderNo();

        // 7. 构建退款请求
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(payment.getPaymentNo());
        request.setOutRefundNo(refundNo);
        request.setReason(dto.getReason());
        request.setNotifyUrl(wxPayConfig.getRefundNotifyUrl());

        AmountReq amountReq = new AmountReq();
        // 金额转换：元 -> 分
        amountReq.setRefund(dto.getRefundAmount().multiply(new BigDecimal("100")).longValue());
        amountReq.setTotal(payment.getAmount().multiply(new BigDecimal("100")).longValue());
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);

        try {
            // 8. 调用官方SDK申请退款
            Refund response = refundService.create(request);

            // 9. 保存退款记录
            RefundDO refund = new RefundDO();
            refund.setRefundNo(refundNo);
            refund.setOrderNo(payment.getOrderNo());
            refund.setPaymentNo(payment.getPaymentNo());
            refund.setTradeNo(payment.getTradeNo());
            refund.setRefundAmount(dto.getRefundAmount());
            refund.setRefundReason(dto.getReason());
            refund.setRefundStatus("PROCESSING");

            if (existingRefund != null) {
                refund.setId(existingRefund.getId());
                refundMapper.updateById(refund);
            } else {
                refundMapper.insert(refund);
            }

            log.info("微信退款申请成功，退款单号：{}，微信退款单号：{}",
                refundNo, response.getRefundId());

            return refundConverter.toWxRefundVO(refund);

        } catch (HttpException e) {
            log.error("微信退款HTTP请求失败", e);
            throw new WxPayException(ResultCode.REFUND_FAILED, "微信退款系统繁忙，请稍后重试");
        } catch (ServiceException e) {
            log.error("微信退款申请失败，错误码：{}，错误信息：{}",
                e.getErrorCode(), e.getErrorMessage());
            throw new WxPayException(ResultCode.REFUND_FAILED, "微信退款失败：" + e.getErrorMessage());
        } catch (MalformedMessageException e) {
            log.error("微信退款响应解析失败", e);
            throw new WxPayException(ResultCode.REFUND_FAILED, "微信退款响应异常");
        }
    }

    @Override
    @Transactional
    public void handleRefundNotify(String requestBody, Map<String, String> headers) {
        log.info("接收到微信退款回调通知");

        // 1. 构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
            .serialNumber(headers.get("Wechatpay-Serial"))
            .nonce(headers.get("Wechatpay-Nonce"))
            .signature(headers.get("Wechatpay-Signature"))
            .timestamp(headers.get("Wechatpay-Timestamp"))
            .body(requestBody)
            .build();

        try {
            // 2. 自动验签、解密
            RefundNotification refundNotification = notificationParser.parse(
                requestParam, RefundNotification.class);

            // 3. 验证退款状态
            if (!"SUCCESS".equals(refundNotification.getRefundStatus().name())) {
                log.warn("退款未成功，状态：{}", refundNotification.getRefundStatus());
                return;
            }

            String outRefundNo = refundNotification.getOutRefundNo();

            // 4. 查询退款记录
            RefundDO refund = refundMapper.findByRefundNo(outRefundNo);
            if (refund == null) {
                log.error("退款记录不存在，退款单号：{}", outRefundNo);
                return;
            }

            // 5. 幂等性检查
            if ("SUCCESS".equals(refund.getRefundStatus())) {
                log.info("退款已处理过，退款单号：{}", outRefundNo);
                return;
            }

            // 6. 更新退款状态
            refundMapper.updateStatus(outRefundNo, "SUCCESS", LocalDateTime.now());

            log.info("微信退款回调处理成功，退款单号：{}，微信退款单号：{}",
                outRefundNo, refundNotification.getRefundId());

        } catch (ValidationException e) {
            log.error("微信退款回调验签失败", e);
            throw e;
        } catch (MalformedMessageException e) {
            log.error("微信退款回调数据解析失败", e);
            throw new BusinessException(ResultCode.PAYMENT_VERIFY_FAILED, "回调数据格式错误");
        }
    }

}
