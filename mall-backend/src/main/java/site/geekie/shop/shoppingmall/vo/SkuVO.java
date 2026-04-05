package site.geekie.shop.shoppingmall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuVO {

    /**
     * SKU ID
     */
    private Long id;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 规格值ID列表（前端用列表形式）
     */
    private List<Long> specValueIds;

    /**
     * 规格描述（如"红色,XL"）
     */
    private String specDesc;

    /**
     * SKU价格
     */
    private BigDecimal price;

    /**
     * SKU库存
     */
    private Integer stock;

    /**
     * SKU图片
     */
    private String image;

    /**
     * 状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 是否默认SKU
     */
    private Boolean isDefault;
}
