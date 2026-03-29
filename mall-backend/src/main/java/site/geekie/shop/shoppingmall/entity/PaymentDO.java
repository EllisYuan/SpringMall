package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体类
 * 对应数据库表：mall_payment
 */
@Data
public class PaymentDO {

    /**
     * 支付ID（主键）
     */
    private Long id;

    /**
     * 支付流水号（唯一）
     */
    private String paymentNo;

    /**
     * 关联订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付方式（ALIPAY/STRIPE）
     */
    private String paymentMethod;

    /**
     * 支付状态（PENDING/SUCCESS/FAILED/CLOSED）
     */
    private String paymentStatus;

    /**
     * 第三方交易号
     */
    private String tradeNo;

    /**
     * 支付相关数据
     * - 微信 Native 支付：二维码链接
     * - Stripe 支付：client_secret
     */
    private String codeUrl;

    /**
     * 异步通知时间
     */
    private LocalDateTime notifyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
