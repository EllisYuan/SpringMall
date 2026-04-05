package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.ProductSpecValueDO;

import java.util.List;

/**
 * 商品规格选项值Mapper接口
 */
@Mapper
public interface ProductSpecValueMapper {

    /**
     * 根据商品ID查询所有规格选项值
     *
     * @param productId 商品ID
     * @return 规格选项值列表
     */
    List<ProductSpecValueDO> findByProductId(@Param("productId") Long productId);

    /**
     * 根据规格维度ID查询所有规格选项值
     *
     * @param specId 规格维度ID
     * @return 规格选项值列表
     */
    List<ProductSpecValueDO> findBySpecId(@Param("specId") Long specId);

    /**
     * 根据ID列表批量查询规格选项值
     *
     * @param ids 规格值ID列表
     * @return 规格选项值列表
     */
    List<ProductSpecValueDO> findByIds(@Param("ids") List<Long> ids);

    /**
     * 批量插入规格选项值
     *
     * @param values 规格选项值列表
     * @return 影响行数
     */
    int batchInsert(@Param("values") List<ProductSpecValueDO> values);

    /**
     * 根据商品ID删除所有规格选项值
     *
     * @param productId 商品ID
     * @return 影响行数
     */
    int deleteByProductId(@Param("productId") Long productId);
}
