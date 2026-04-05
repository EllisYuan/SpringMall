package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购物车请求DTO
 * 用于添加商品到购物车
 */
@Data
public class CartItemDTO {

    /**
     * 商品ID
     * 验证规则：不能为null，必须>0
     */
    @NotNull(message = "商品ID不能为空")
    @Min(value = 1, message = "商品ID必须大于0")
    private Long productId;

    /**
     * 数量
     * 验证规则：不能为null，必须>=1
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于等于1")
    private Integer quantity;

    /**
     * SKU ID（可选，有SKU商品必传）
     */
    private Long skuId;
}
