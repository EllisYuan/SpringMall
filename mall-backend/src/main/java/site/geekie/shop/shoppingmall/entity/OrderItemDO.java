package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体类
 * 对应数据库表：mall_order_item
 */
@Data
public class OrderItemDO {

    /**
     * 订单明细ID（主键）
     */
    private Long id;

    /**
     * 订单ID（外键）
     */
    private Long orderId;

    /**
     * 商品ID（外键）
     */
    private Long productId;

    /**
     * SKU ID（无SKU商品为0）
     */
    private Long skuId = 0L;

    /**
     * 商品名称（冗余，防止商品信息变更）
     */
    private String productName;

    /**
     * 商品图片（冗余）
     */
    private String productImage;

    /**
     * 规格描述快照（如"红色,XL"），无SKU商品为NULL
     */
    private String specDesc;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 总价（单价 × 数量）
     */
    private BigDecimal totalPrice;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
