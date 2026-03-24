package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.geekie.shop.shoppingmall.entity.CartItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
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
     * 将 CartItemDO 转换为 CartItemVO（基础映射）
     * 6个基础字段直接映射，商品关联字段和subtotal通过default方法处理
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
    CartItemVO toVO(CartItemDO cartItem);

    /**
     * 将 CartItemDO 转换为 CartItemVO（带商品信息查询）
     * 查询商品信息并填充到VO的5个product相关字段，同时计算subtotal
     *
     * @param cartItem 购物车项DO
     * @param productMapper 商品Mapper
     * @return 完整的购物车项VO（包含商品信息和小计）
     */
    default CartItemVO toVO(CartItemDO cartItem, ProductMapper productMapper) {
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
                vo.setProductImage(product.getMainImage());
                vo.setProductPrice(product.getPrice());
                vo.setProductStock(product.getStock());

                // 计算小计: productPrice × quantity
                BigDecimal subtotal = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
                vo.setSubtotal(subtotal);
            }
        }

        return vo;
    }

    /**
     * 批量转换购物车项列表（优化版本，避免N+1查询）
     * 先批量查询所有商品ID，构建Map缓存，然后逐个填充
     *
     * @param cartItems 购物车项DO列表
     * @param productMapper 商品Mapper
     * @return 完整的购物车项VO列表
     */
    default List<CartItemVO> toVOList(List<CartItemDO> cartItems, ProductMapper productMapper) {
        if (cartItems == null || cartItems.isEmpty()) {
            return List.of();
        }

        // 收集所有商品ID
        List<Long> productIds = cartItems.stream()
                .map(CartItemDO::getProductId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询所有商品，构建Map缓存
        Map<Long, ProductDO> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.findByIds(productIds).stream()
                        .collect(Collectors.toMap(ProductDO::getId, p -> p));

        // 转换列表，使用缓存的商品数据
        return cartItems.stream()
                .map(cartItem -> {
                    CartItemVO vo = toVO(cartItem);

                    // 从缓存中获取商品信息
                    ProductDO product = productMap.get(cartItem.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getName());
                        vo.setProductSubtitle(product.getSubtitle());
                        vo.setProductImage(product.getMainImage());
                        vo.setProductPrice(product.getPrice());
                        vo.setProductStock(product.getStock());

                        // 计算小计
                        BigDecimal subtotal = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
                        vo.setSubtotal(subtotal);
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }
}
