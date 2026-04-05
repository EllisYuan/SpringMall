package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品SKU实体类
 * 对应数据库表：mall_sku
 */
@Data
public class SkuDO {

    /**
     * SKU ID（主键）
     */
    private Long id;

    /**
     * 商品ID（外键）
     */
    private Long productId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 规格值ID组合（逗号分隔，升序排列，如"1,3,5"）
     */
    private String specValueIds;

    /**
     * 规格描述冗余（如"红色,XL"）
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
     * 是否默认SKU：0-否 1-是
     */
    private Integer isDefault;

    /**
     * SKU销量
     */
    private Integer salesCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
