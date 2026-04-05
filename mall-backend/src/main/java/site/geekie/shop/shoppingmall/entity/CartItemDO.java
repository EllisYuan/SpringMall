package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车项实体类
 * 对应数据库表：mall_cart_item
 */
@Data
public class CartItemDO {

    /**
     * 购物车项ID（主键）
     */
    private Long id;

    /**
     * 用户ID（外键）
     */
    private Long userId;

    /**
     * 商品ID（外键）
     */
    private Long productId;

    /**
     * SKU ID（无SKU商品为0）
     */
    private Long skuId = 0L;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 是否选中
     * 0-未选中，1-已选中
     */
    private Integer checked;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
