package site.geekie.shop.shoppingmall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付信息响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVO {

    /**
     * 支付流水号
     */
    private String paymentNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 支付状态
     */
    private String paymentStatus;

    /**
     * 第三方交易号
     */
    private String tradeNo;

    /**
     * 支付URL或表单HTML
     */
    private String paymentUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
