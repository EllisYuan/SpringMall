package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 * 对应数据库表：mall_product
 */
@Data
public class ProductDO {

    /**
     * 商品ID（主键）
     */
    private Long id;

    /**
     * 分类ID（外键）
     */
    private Long categoryId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 副标题/卖点
     */
    private String subtitle;

    /**
     * 主图URL
     */
    private String mainImage;

    /**
     * 图片列表（JSON数组字符串）
     */
    private String images;

    /**
     * 商品详情（HTML）
     */
    private String detail;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 状态
     * 0-下架，1-上架
     */
    private Integer status;

    /**
     * 累计销量
     */
    private Integer salesCount;

    /**
     * 是否启用SKU：0-否 1-是
     */
    private Integer hasSku;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
