package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 商品规格维度DTO
 */
@Data
public class ProductSpecDTO {

    /**
     * 规格名称（如颜色、尺码）
     */
    @NotBlank(message = "规格名称不能为空")
    private String name;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 规格选项值列表
     */
    @NotEmpty(message = "规格值不能为空")
    @Valid
    private List<SpecValueDTO> values;
}
