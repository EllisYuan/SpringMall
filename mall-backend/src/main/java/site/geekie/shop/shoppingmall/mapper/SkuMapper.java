package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.SkuDO;

import java.util.List;

/**
 * SKU Mapper接口
 */
@Mapper
public interface SkuMapper {

    /**
     * 根据ID查询SKU
     *
     * @param id SKU ID
     * @return SKU DO，不存在返回null
     */
    SkuDO findById(@Param("id") Long id);

    /**
     * 根据ID列表批量查询SKU
     *
     * @param ids SKU ID列表
     * @return SKU列表
     */
    List<SkuDO> findByIds(@Param("ids") List<Long> ids);

    /**
     * 根据商品ID查询所有SKU
     *
     * @param productId 商品ID
     * @return SKU列表
     */
    List<SkuDO> findByProductId(@Param("productId") Long productId);

    /**
     * 批量插入SKU
     *
     * @param skuList SKU列表
     * @return 影响行数
     */
    int batchInsert(@Param("skuList") List<SkuDO> skuList);

    /**
     * 根据商品ID删除所有SKU
     *
     * @param productId 商品ID
     * @return 影响行数
     */
    int deleteByProductId(@Param("productId") Long productId);

    /**
     * 根据商品ID查询默认SKU
     *
     * @param productId 商品ID
     * @return 默认SKU，不存在返回null
     */
    SkuDO findDefaultByProductId(@Param("productId") Long productId);

    /**
     * 扣减SKU库存（乐观锁，防止超卖）
     *
     * @param id       SKU ID
     * @param quantity 扣减数量
     * @return 影响行数（0表示库存不足）
     */
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 增加SKU库存（取消订单时恢复）
     *
     * @param id       SKU ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 增加SKU销量
     *
     * @param id       SKU ID
     * @param quantity 销量增量
     * @return 影响行数
     */
    int increaseSalesCount(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 扣减SKU销量（最小为0）
     *
     * @param id       SKU ID
     * @param quantity 销量减量
     * @return 影响行数
     */
    int decreaseSalesCount(@Param("id") Long id, @Param("quantity") Integer quantity);
}
