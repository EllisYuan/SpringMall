package site.geekie.shop.shoppingmall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品规格维度VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecVO {

    /**
     * 规格维度ID
     */
    private Long id;

    /**
     * 规格名称（如颜色、尺码）
     */
    private String name;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 规格选项值列表
     */
    private List<SpecValueVO> values;
}
