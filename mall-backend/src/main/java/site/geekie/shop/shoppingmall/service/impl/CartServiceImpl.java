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
import site.geekie.shop.shoppingmall.vo.CartItemVO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.CartItemMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
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
        return cartItemConverter.toVOList(cartItems, productMapper);
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

        // 验证库存是否充足
        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
        }

        // 检查商品是否已在购物车中
        CartItemDO existingItem = cartItemMapper.findByUserIdAndProductId(userId, request.getProductId());

        if (existingItem != null) {
            // 商品已存在，增加数量
            int newQuantity = existingItem.getQuantity() + request.getQuantity();

            // 再次验证库存
            if (product.getStock() < newQuantity) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
            }

            cartItemMapper.updateQuantity(existingItem.getId(), newQuantity);
            existingItem.setQuantity(newQuantity);
            return cartItemConverter.toVO(existingItem, productMapper);
        } else {
            // 新增购物车项
            CartItemDO cartItem = new CartItemDO();
            cartItem.setUserId(userId);
            cartItem.setProductId(request.getProductId());
            cartItem.setQuantity(request.getQuantity());
            cartItem.setChecked(1); // 默认选中

            cartItemMapper.insert(cartItem);
            return cartItemConverter.toVO(cartItem, productMapper);
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

        // 验证商品库存
        ProductDO product = productMapper.findById(cartItem.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStock() < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
        }

        // 更新数量
        cartItemMapper.updateQuantity(id, quantity);
        cartItem.setQuantity(quantity);

        return cartItemConverter.toVO(cartItem, productMapper);
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
            ProductDO product = productMap.get(item.getProductId());
            if (product != null) {
                BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(item.getQuantity()));
                total = total.add(itemTotal);
            }
        }

        return total;
    }

    @Override
    public int getCartCount(Long userId) {
        return cartItemMapper.countByUserId(userId);
    }
}
