package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单明细快照实体类
 * 对应数据库表：mall_seckill_order
 *
 * 与 mall_order 通过 order_id 1:1 关联：真实订单语义（状态/金额/支付）由 mall_order 承载，
 * 本表专门承载秒杀维度的明细快照（活动ID、秒杀价、商品名/规格/主图）与幂等/限购统计
 * （request_id 唯一索引兜底消息去重，activity_id + user_id 维度 SUM 做限购校验）。
 */
@Data
public class SeckillOrderDO {

    /** 主键ID */
    private Long id;

    /** 关联 mall_order.id（1:1） */
    private Long orderId;

    /** 订单号（冗余，便于秒杀维度查询直达支付） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 秒杀活动ID */
    private Long activityId;

    /** 商品ID */
    private Long productId;

    /** SKU ID，无SKU时为0 */
    private Long skuId;

    /** 商品名称快照 */
    private String productName;

    /** 商品主图快照 */
    private String productImage;

    /** 规格描述快照（如"红色,XL"），无SKU时为NULL */
    private String specDesc;

    /** 秒杀单价快照 */
    private BigDecimal seckillPrice;

    /** 购买数量 */
    private Integer quantity;

    /** 请求幂等ID（UUID，配合唯一索引防重复消费） */
    private String requestId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ===== 以下为非持久化展示字段，查询时 JOIN mall_order / mall_seckill_activity 填充 =====

    /** 订单状态（JOIN mall_order 填充：UNPAID/PAID/SHIPPED/COMPLETED/CANCELLED） */
    private String orderStatus;

    /** 订单总金额（JOIN mall_order 填充） */
    private BigDecimal totalAmount;

    /** 实付金额（JOIN mall_order 填充） */
    private BigDecimal payAmount;

    /** 活动名称（JOIN mall_seckill_activity 填充） */
    private String activityName;
}
