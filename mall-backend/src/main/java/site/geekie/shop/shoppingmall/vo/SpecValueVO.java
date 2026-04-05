package site.geekie.shop.shoppingmall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规格选项值VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecValueVO {

    /**
     * 规格值ID
     */
    private Long id;

    /**
     * 规格值（如红色、XL）
     */
    private String value;

    /**
     * 排序值
     */
    private Integer sortOrder;
}
