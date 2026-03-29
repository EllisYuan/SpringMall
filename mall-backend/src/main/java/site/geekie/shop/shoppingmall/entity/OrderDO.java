package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 对应数据库表：mall_order
 */
@Data
public class OrderDO {

    /**
     * 订单ID（主键）
     */
    private Long id;

    /**
     * 订单号（唯一）
     */
    private String orderNo;

    /**
     * 用户ID（外键）
     */
    private Long userId;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 实付金额
     */
    private BigDecimal payAmount;

    /**
     * 运费
     */
    private BigDecimal freight;

    /**
     * 订单状态
     * UNPAID-待支付, PAID-已支付, SHIPPED-已发货, COMPLETED-已完成, CANCELLED-已取消
     */
    private String status;

    /**
     * 支付单号（来自mall_payment表，通过JOIN查询获取）
     */
    private String paymentNo;

    /**
     * 支付方式（ALIPAY/STRIPE）
     */
    private String paymentMethod;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;

    /**
     * 发货时间
     */
    private LocalDateTime shipTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 收货人姓名
     */
    private String receiverName;

    /**
     * 收货人电话
     */
    private String receiverPhone;

    /**
     * 收货地址
     */
    private String receiverAddress;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
