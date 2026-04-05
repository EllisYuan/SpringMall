package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品规格维度实体类
 * 对应数据库表：mall_product_spec
 */
@Data
public class ProductSpecDO {

    /**
     * 规格ID（主键）
     */
    private Long id;

    /**
     * 商品ID（外键）
     */
    private Long productId;

    /**
     * 规格名称（如颜色、尺码）
     */
    private String name;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
