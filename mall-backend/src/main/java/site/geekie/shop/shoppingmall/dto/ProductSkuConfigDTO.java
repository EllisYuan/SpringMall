package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 商品SKU配置DTO
 * 管理员一次性保存商品的规格和SKU配置
 */
@Data
public class ProductSkuConfigDTO {

    /**
     * 规格维度列表（如：颜色-[红,蓝]，尺码-[S,M,L]）
     */
    @NotEmpty(message = "规格列表不能为空")
    @Valid
    private List<ProductSpecDTO> specs;

    /**
     * SKU列表（每个SKU对应一种规格组合）
     */
    @NotEmpty(message = "SKU列表不能为空")
    @Valid
    private List<SkuDTO> skuList;
}
