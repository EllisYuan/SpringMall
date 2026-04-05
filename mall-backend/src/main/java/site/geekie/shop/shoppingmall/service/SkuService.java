package site.geekie.shop.shoppingmall.service;

import site.geekie.shop.shoppingmall.dto.ProductSkuConfigDTO;

/**
 * SKU 服务接口
 * 管理商品的规格和SKU配置
 */
public interface SkuService {

    /**
     * 保存商品SKU配置（全量替换：先删后插）
     * 同时同步更新 product.price（最低SKU价）和 product.stock（SKU库存总和）
     * 同时将 product.hasSku 设为 1
     *
     * @param productId 商品ID
     * @param config    SKU配置DTO
     */
    void saveProductSkuConfig(Long productId, ProductSkuConfigDTO config);

    /**
     * 获取商品SKU配置
     *
     * @param productId 商品ID
     * @return SKU配置DTO
     */
    ProductSkuConfigDTO getProductSkuConfig(Long productId);

    /**
     * 删除商品所有SKU配置
     * 删除后将 product.hasSku 设为 0
     *
     * @param productId 商品ID
     */
    void deleteProductSkuConfig(Long productId);

    /**
     * 同步商品价格和库存（根据SKU重新计算最低价和库存总和）
     *
     * @param productId 商品ID
     */
    void syncProductPriceAndStock(Long productId);
}
