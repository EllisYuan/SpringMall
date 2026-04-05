package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.geekie.shop.shoppingmall.dto.CartItemDTO;
import site.geekie.shop.shoppingmall.entity.CartItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.vo.CartItemVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CartItem 转换器接口
 * 处理 CartItemDO -> CartItemVO 转换（包含商品关联查询和小计计算）
 */
@Mapper(componentModel = "spring")
public interface CartItemConverter {

    /**
     * 将 CartItemDTO 转换为 CartItemDO（新增场景）
     * id、userId、checked、createdAt、updatedAt 由 Service 层或数据库处理
     * skuId 由 MapStruct 自动从 dto.skuId 映射
     *
     * @param dto 购物车请求DTO
     * @return 购物车项DO（userId 和 checked 需 Service 层单独赋值）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "checked", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CartItemDO toDO(CartItemDTO dto);

    /**
     * 将 CartItemDO 转换为 CartItemVO（基础映射）
     * 基础字段直接映射，商品关联字段、SKU相关字段和subtotal通过default方法处理
     * skuId 由 MapStruct 自动从 DO.skuId 映射
     *
     * @param cartItem 购物车项DO
     * @return 购物车项VO（商品字段和subtotal未填充）
     */
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productSubtitle", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "productPrice", ignore = true)
    @Mapping(target = "productStock", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "specDesc", ignore = true)
    @Mapping(target = "skuImage", ignore = true)
    CartItemVO toVO(CartItemDO cartItem);

    /**
     * 将 CartItemDO 转换为 CartItemVO（带商品信息和SKU信息查询）
     * 查询商品信息并填充到VO，如果有SKU则填充SKU价格、规格描述、SKU图片
     *
     * @param cartItem      购物车项DO
     * @param productMapper 商品Mapper
     * @param skuMapper     SKU Mapper
     * @return 完整的购物车项VO（包含商品信息和小计）
     */
    default CartItemVO toVO(CartItemDO cartItem, ProductMapper productMapper, SkuMapper skuMapper) {
        if (cartItem == null) {
            return null;
        }

        // 先执行基础映射
        CartItemVO vo = toVO(cartItem);

        // 查询商品详情并填充
        if (cartItem.getProductId() != null) {
            ProductDO product = productMapper.findById(cartItem.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductSubtitle(product.getSubtitle());
                vo.setProductPrice(product.getPrice());
                vo.setProductStock(product.getStock());

                BigDecimal effectivePrice = product.getPrice();

                // 如果有SKU，查询SKU信息填充规格描述、SKU图片和SKU价格
                if (cartItem.getSkuId() != null && cartItem.getSkuId() > 0 && skuMapper != null) {
                    SkuDO sku = skuMapper.findById(cartItem.getSkuId());
                    if (sku != null) {
                        vo.setSpecDesc(sku.getSpecDesc());
                        effectivePrice = sku.getPrice();
                        vo.setProductPrice(sku.getPrice());
                        if (sku.getImage() != null) {
                            vo.setSkuImage(sku.getImage());
                            vo.setProductImage(sku.getImage());
                        } else {
                            vo.setProductImage(product.getMainImage());
                        }
                    } else {
                        vo.setProductImage(product.getMainImage());
                    }
                } else {
                    vo.setProductImage(product.getMainImage());
                }

                // 计算小计: effectivePrice × quantity
                BigDecimal subtotal = effectivePrice.multiply(new BigDecimal(cartItem.getQuantity()));
                vo.setSubtotal(subtotal);
            }
        }

        return vo;
    }

    /**
     * 批量转换购物车项列表（优化版本，避免N+1查询）
     *
     * @param cartItems     购物车项DO列表
     * @param productMapper 商品Mapper
     * @param skuMapper     SKU Mapper
     * @return 完整的购物车项VO列表
     */
    default List<CartItemVO> toVOList(List<CartItemDO> cartItems, ProductMapper productMapper, SkuMapper skuMapper) {
        if (cartItems == null || cartItems.isEmpty()) {
            return List.of();
        }

        // 批量查询商品
        List<Long> productIds = cartItems.stream()
                .map(CartItemDO::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductDO> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.findByIds(productIds).stream()
                        .collect(Collectors.toMap(ProductDO::getId, p -> p));

        // 批量查询有SKU的购物车项对应的SKU信息
        List<Long> skuIds = cartItems.stream()
                .map(CartItemDO::getSkuId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SkuDO> skuMap = Collections.emptyMap();
        if (!skuIds.isEmpty() && skuMapper != null) {
            skuMap = skuMapper.findByIds(skuIds).stream()
                    .collect(Collectors.toMap(SkuDO::getId, s -> s));
        }

        final Map<Long, SkuDO> finalSkuMap = skuMap;

        return cartItems.stream()
                .map(cartItem -> {
                    CartItemVO vo = toVO(cartItem);

                    ProductDO product = productMap.get(cartItem.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getName());
                        vo.setProductSubtitle(product.getSubtitle());
                        vo.setProductPrice(product.getPrice());
                        vo.setProductStock(product.getStock());

                        BigDecimal effectivePrice = product.getPrice();

                        if (cartItem.getSkuId() != null && cartItem.getSkuId() > 0) {
                            SkuDO sku = finalSkuMap.get(cartItem.getSkuId());
                            if (sku != null) {
                                vo.setSpecDesc(sku.getSpecDesc());
                                effectivePrice = sku.getPrice();
                                vo.setProductPrice(sku.getPrice());
                                if (sku.getImage() != null) {
                                    vo.setSkuImage(sku.getImage());
                                    vo.setProductImage(sku.getImage());
                                } else {
                                    vo.setProductImage(product.getMainImage());
                                }
                            } else {
                                vo.setProductImage(product.getMainImage());
                            }
                        } else {
                            vo.setProductImage(product.getMainImage());
                        }

                        BigDecimal subtotal = effectivePrice.multiply(new BigDecimal(cartItem.getQuantity()));
                        vo.setSubtotal(subtotal);
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }
}
