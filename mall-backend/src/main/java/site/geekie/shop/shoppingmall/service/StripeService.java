package site.geekie.shop.shoppingmall.service;

import site.geekie.shop.shoppingmall.dto.CreateStripePaymentDTO;
import site.geekie.shop.shoppingmall.dto.StripeRefundDTO;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.vo.StripePaymentVO;
import site.geekie.shop.shoppingmall.vo.StripeRefundVO;

/**
 * Stripe 支付服务接口
 */
public interface StripeService {

    /**
     * 创建支付意图
     *
     * @param orderNo 支付订单号
     * @param userId 用户ID
     * @return 支付响应（包含 client_secret）
     */
    StripePaymentVO createStripe(String orderNo, Long userId);

    /**
     * 查询支付状态
     *
     * @param paymentNo 支付编号
     * @param userId 用户ID
     * @return 支付信息
     */
    StripePaymentVO queryPayment(String paymentNo, Long userId);

    /**
     * 处理 Webhook 回调
     *
     * @param payload Webhook 请求体
     * @param signature Stripe-Signature 头
     */
    void handleWebhook(String payload, String signature);

    /**
     * 创建退款
     *
     * @param request 退款请求
     * @param userId 用户ID（管理员）
     * @return 退款响应
     */
    StripeRefundVO createRefund(StripeRefundDTO request, Long userId);

    /**
     * 处理退款 Webhook 回调
     *
     * @param payload Webhook 请求体
     * @param signature Stripe-Signature 头
     */
    void handleRefundWebhook(String payload, String signature);

    /**
     * 使 Stripe Checkout Session 过期（用于支付方式互斥）
     *
     * @param payment 待关闭的支付记录
     */
    void expireSession(PaymentDO payment);

    /**
     * 查询 Stripe 第三方实际支付状态（用于掉单补偿）
     *
     * @param payment 支付记录（通过 tradeNo 查询 PaymentIntent）
     * @return true 表示第三方显示已支付成功，false 表示未支付或查询失败
     */
    boolean queryPaymentStatus(PaymentDO payment);
}
