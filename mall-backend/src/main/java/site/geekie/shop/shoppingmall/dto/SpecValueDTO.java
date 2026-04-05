package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 规格选项值DTO
 * 嵌套在 ProductSpecDTO 中使用
 */
@Data
public class SpecValueDTO {

    /**
     * 规格值（如红色、XL）
     */
    @NotBlank(message = "规格值不能为空")
    private String value;

    /**
     * 排序值
     */
    private Integer sortOrder;
}
