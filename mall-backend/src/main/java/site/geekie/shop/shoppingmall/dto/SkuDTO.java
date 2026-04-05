package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU DTO
 */
@Data
public class SkuDTO {

    /**
     * SKU编码（可选）
     */
    private String skuCode;

    /**
     * 规格值ID列表（对应 mall_product_spec_value.id）
     */
    @NotEmpty(message = "规格值ID不能为空")
    private List<Long> specValueIds;

    /**
     * SKU价格
     */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    /**
     * SKU库存
     */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    /**
     * SKU图片（可选）
     */
    private String image;

    /**
     * 是否默认SKU
     */
    private Boolean isDefault;
}
