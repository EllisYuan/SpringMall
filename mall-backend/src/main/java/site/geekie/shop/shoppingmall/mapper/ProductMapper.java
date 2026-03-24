package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.ProductDO;

import java.util.List;
import java.util.Map;

/**
 * 商品Mapper接口
 * 提供商品的数据访问方法
 */
@Mapper
public interface ProductMapper {

    /**
     * 根据ID查询商品
     *
     * @param id 商品ID
     * @return 商品信息，不存在返回null
     */
    ProductDO findById(@Param("id") Long id);

    /**
     * 查询所有商品
     * 按创建时间倒序排列
     *
     * @return 所有商品列表
     */
    List<ProductDO> findAll();

    /**
     * 带过滤条件查询所有商品
     * keyword/categoryId/status 均为 null 时等价于 findAll
     *
     * @param keyword 搜索关键词（匹配商品名称或副标题）
     * @param categoryId 分类ID
     * @param status 状态（0-下架，1-上架）
     * @param sortColumn 排序列（白名单校验后传入）
     * @param sortDir 排序方向（ASC/DESC）
     * @return 商品列表
     */
    List<ProductDO> findAllWithFilter(@Param("keyword") String keyword,
                                     @Param("categoryId") Long categoryId,
                                     @Param("status") Integer status,
                                     @Param("sortColumn") String sortColumn,
                                     @Param("sortDir") String sortDir);

    /**
     * 根据分类ID查询商品列表
     * 按创建时间倒序排列
     *
     * @param categoryId 分类ID
     * @return 商品列表
     */
    List<ProductDO> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 根据关键词搜索商品
     * 在商品名称和副标题中模糊查询
     * 按创建时间倒序排列
     *
     * @param keyword 搜索关键词
     * @return 商品列表
     */
    List<ProductDO> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 插入新商品
     *
     * @param product 商品信息
     * @return 影响行数
     */
    int insert(ProductDO product);

    /**
     * 根据ID更新商品信息
     * 使用动态SQL，只更新非null字段
     *
     * @param product 商品信息
     * @return 影响行数
     */
    int updateById(ProductDO product);

    /**
     * 根据ID删除商品
     *
     * @param id 商品ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计指定分类下的商品数量
     *
     * @param categoryId 分类ID
     * @return 商品数量
     */
    int countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 根据ID列表批量查询商品
     *
     * @param ids 商品ID列表
     * @return 商品列表
     */
    List<ProductDO> findByIds(@Param("ids") List<Long> ids);

    /**
     * 按分类ID列表批量统计商品数量
     * 返回列表中每个 Map 包含 categoryId 和 cnt 两个 key
     *
     * @param categoryIds 分类ID列表
     * @return 每个分类对应的商品数量列表
     */
    List<Map<String, Object>> countGroupByCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    /**
     * 扣减库存
     * 使用乐观锁，确保库存不为负
     *
     * @param id 商品ID
     * @param quantity 扣减数量
     * @return 影响行数，0表示库存不足
     */
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 增加库存
     *
     * @param id 商品ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 增加销量
     *
     * @param id 商品ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    int increaseSalesCount(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 扣减销量（最小为0）
     *
     * @param id 商品ID
     * @param quantity 扣减数量
     * @return 影响行数
     */
    int decreaseSalesCount(@Param("id") Long id, @Param("quantity") Integer quantity);
}
