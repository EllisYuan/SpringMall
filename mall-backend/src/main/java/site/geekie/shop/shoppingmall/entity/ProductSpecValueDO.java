package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品规格选项值实体类
 * 对应数据库表：mall_product_spec_value
 */
@Data
public class ProductSpecValueDO {

    /**
     * 规格值ID（主键）
     */
    private Long id;

    /**
     * 规格维度ID（外键）
     */
    private Long specId;

    /**
     * 商品ID（冗余，便于查询）
     */
    private Long productId;

    /**
     * 规格值（如红色、XL）
     */
    private String value;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
