package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.CartItemDO;

import java.util.List;

/**
 * 购物车Mapper接口
 * 提供购物车的数据访问方法
 */
@Mapper
public interface CartItemMapper {

    /**
     * 根据ID查询购物车项
     *
     * @param id 购物车项ID
     * @return 购物车项信息，不存在返回null
     */
    CartItemDO findById(@Param("id") Long id);

    /**
     * 根据用户ID查询所有购物车项
     * 按创建时间倒序排列
     *
     * @param userId 用户ID
     * @return 购物车项列表
     */
    List<CartItemDO> findByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和商品ID查询购物车项（仅适用于无SKU商品，sku_id IS NULL）
     * 用于检查无SKU商品是否已在购物车中
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return 购物车项信息，不存在返回null
     */
    CartItemDO findByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 根据用户ID、商品ID和SKU ID查询购物车项（适用于有SKU商品）
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param skuId     SKU ID
     * @return 购物车项信息，不存在返回null
     */
    CartItemDO findByUserIdAndProductIdAndSkuId(@Param("userId") Long userId, @Param("productId") Long productId, @Param("skuId") Long skuId);

    /**
     * 根据用户ID查询已选中的购物车项
     *
     * @param userId 用户ID
     * @return 已选中的购物车项列表
     */
    List<CartItemDO> findCheckedByUserId(@Param("userId") Long userId);

    /**
     * 插入新购物车项
     *
     * @param cartItem 购物车项信息
     * @return 影响行数
     */
    int insert(CartItemDO cartItem);

    /**
     * 更新购物车项数量
     *
     * @param id 购物车项ID
     * @param quantity 新数量
     * @return 影响行数
     */
    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 更新购物车项选中状态
     *
     * @param id 购物车项ID
     * @param checked 选中状态（0-未选中，1-已选中）
     * @return 影响行数
     */
    int updateChecked(@Param("id") Long id, @Param("checked") Integer checked);

    /**
     * 批量更新用户的所有购物车项选中状态
     * 用于全选/取消全选功能
     *
     * @param userId 用户ID
     * @param checked 选中状态（0-未选中，1-已选中）
     * @return 影响行数
     */
    int updateAllChecked(@Param("userId") Long userId, @Param("checked") Integer checked);

    /**
     * 根据ID删除购物车项
     *
     * @param id 购物车项ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据用户ID删除所有购物车项
     * 用于清空购物车
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 批量删除购物车项
     *
     * @param ids 购物车项ID列表
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 统计用户购物车中的商品种类数
     *
     * @param userId 用户ID
     * @return 商品种类数
     */
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计指定ID列表中属于指定用户的购物车项数量
     * 用于批量验证购物车项所有权，避免 N+1 查询
     *
     * @param ids    购物车项ID列表
     * @param userId 用户ID
     * @return 属于该用户的购物车项数量
     */
    int countByIdsAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
