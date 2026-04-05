package site.geekie.shop.shoppingmall.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.annotation.LogOperation;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.converter.CartItemConverter;
import site.geekie.shop.shoppingmall.dto.CartItemDTO;
import site.geekie.shop.shoppingmall.entity.CartItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.vo.CartItemVO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.CartItemMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.service.CartService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CartItemConverter cartItemConverter;


    /**
     * 验证购物车项所有权
     */
    private void validateCartItemOwnership(Long cartItemId, Long userId) {
        CartItemDO cartItem = cartItemMapper.findById(cartItemId);
        if (cartItem == null) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        if (!cartItem.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    @Override
    public List<CartItemVO> getCartItems(Long userId) {
        List<CartItemDO> cartItems = cartItemMapper.findByUserId(userId);
        return cartItemConverter.toVOList(cartItems, productMapper, skuMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogOperation(value = "添加购物车", module = "购物车")
    public CartItemVO addToCart(CartItemDTO request, Long userId) {
        // 验证商品是否存在且可售
        ProductDO product = productMapper.findById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() != 1) {
            throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
        }

        // 根据是否启用 SKU 分支处理
        if (Integer.valueOf(1).equals(product.getHasSku())) {
            // === 有 SKU 商品 ===
            Long skuId = request.getSkuId();
            if (skuId == null) {
                // 尝试使用默认 SKU
                SkuDO defaultSku = skuMapper.findDefaultByProductId(product.getId());
                if (defaultSku == null) {
                    throw new BusinessException(ResultCode.SKU_REQUIRED);
                }
                skuId = defaultSku.getId();
            }

            // 查询并校验 SKU
            SkuDO sku = skuMapper.findById(skuId);
            if (sku == null || !sku.getProductId().equals(request.getProductId())) {
                throw new BusinessException(ResultCode.SKU_NOT_FOUND);
            }
            if (!Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
            }
            if (sku.getStock() < request.getQuantity()) {
                throw new BusinessException(ResultCode.SKU_OUT_OF_STOCK);
            }

            // 按 userId + productId + skuId 查重
            CartItemDO existingItem = cartItemMapper.findByUserIdAndProductIdAndSkuId(
                    userId, request.getProductId(), skuId);

            if (existingItem != null) {
                int newQuantity = existingItem.getQuantity() + request.getQuantity();
                if (sku.getStock() < newQuantity) {
                    throw new BusinessException(ResultCode.SKU_OUT_OF_STOCK);
                }
                cartItemMapper.updateQuantity(existingItem.getId(), newQuantity);
                existingItem.setQuantity(newQuantity);
                return cartItemConverter.toVO(existingItem, productMapper, skuMapper);
            } else {
                CartItemDO cartItem = cartItemConverter.toDO(request);
                cartItem.setUserId(userId);
                cartItem.setSkuId(skuId);
                cartItem.setChecked(1);
                cartItemMapper.insert(cartItem);
                return cartItemConverter.toVO(cartItem, productMapper, skuMapper);
            }
        } else {
            // === 无 SKU 商品（原有逻辑） ===
            if (product.getStock() < request.getQuantity()) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
            }

            // 按 userId + productId（sku_id = 0）查重
            CartItemDO existingItem = cartItemMapper.findByUserIdAndProductId(userId, request.getProductId());

            if (existingItem != null) {
                int newQuantity = existingItem.getQuantity() + request.getQuantity();
                if (product.getStock() < newQuantity) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
                }
                cartItemMapper.updateQuantity(existingItem.getId(), newQuantity);
                existingItem.setQuantity(newQuantity);
                return cartItemConverter.toVO(existingItem, productMapper, skuMapper);
            } else {
                CartItemDO cartItem = cartItemConverter.toDO(request);
                cartItem.setUserId(userId);
                cartItem.setSkuId(0L);
                cartItem.setChecked(1);
                cartItemMapper.insert(cartItem);
                return cartItemConverter.toVO(cartItem, productMapper, skuMapper);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartItemVO updateQuantity(Long id, Integer quantity, Long userId) {
        // 验证所有权
        validateCartItemOwnership(id, userId);

        if (quantity < 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        // 获取购物车项
        CartItemDO cartItem = cartItemMapper.findById(id);

        // 验证库存
        if (cartItem.getSkuId() != null && cartItem.getSkuId() > 0) {
            // 有 SKU：验证 SKU 库存
            SkuDO sku = skuMapper.findById(cartItem.getSkuId());
            if (sku == null || sku.getStock() < quantity) {
                throw new BusinessException(ResultCode.SKU_OUT_OF_STOCK);
            }
        } else {
            // 无 SKU：验证商品库存
            ProductDO product = productMapper.findById(cartItem.getProductId());
            if (product == null) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
            }
            if (product.getStock() < quantity) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
            }
        }

        // 更新数量
        cartItemMapper.updateQuantity(id, quantity);
        cartItem.setQuantity(quantity);

        return cartItemConverter.toVO(cartItem, productMapper, skuMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChecked(Long id, Integer checked, Long userId) {
        // 验证所有权
        validateCartItemOwnership(id, userId);

        if (checked != 0 && checked != 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        cartItemMapper.updateChecked(id, checked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAllChecked(Integer checked, Long userId) {
        if (checked != 0 && checked != 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        cartItemMapper.updateAllChecked(userId, checked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartItem(Long id, Long userId) {
        // 验证所有权
        validateCartItemOwnership(id, userId);

        cartItemMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartItems(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER);
        }

        // 批量验证所有购物车项的所有权
        int ownedCount = cartItemMapper.countByIdsAndUserId(ids, userId);
        if (ownedCount != ids.size()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        cartItemMapper.deleteByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartItemMapper.deleteByUserId(userId);
    }

    @Override
    public BigDecimal getCartTotal(Long userId) {
        List<CartItemDO> checkedItems = cartItemMapper.findCheckedByUserId(userId);
        if (checkedItems.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Long> productIds = checkedItems.stream()
                .map(CartItemDO::getProductId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ProductDO> productMap = productMapper.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductDO::getId, p -> p));

        BigDecimal total = BigDecimal.ZERO;
        for (CartItemDO item : checkedItems) {
            BigDecimal effectivePrice = null;

            // 有 SKU 的购物车项使用 SKU 价格
            if (item.getSkuId() != null && item.getSkuId() > 0) {
                SkuDO sku = skuMapper.findById(item.getSkuId());
                if (sku != null) {
                    effectivePrice = sku.getPrice();
                }
            }

            // 无 SKU 或 SKU 查询失败，使用商品价格
            if (effectivePrice == null) {
                ProductDO product = productMap.get(item.getProductId());
                if (product != null) {
                    effectivePrice = product.getPrice();
                }
            }

            if (effectivePrice != null) {
                total = total.add(effectivePrice.multiply(new BigDecimal(item.getQuantity())));
            }
        }

        return total;
    }

    @Override
    public int getCartCount(Long userId) {
        return cartItemMapper.countByUserId(userId);
    }
}
