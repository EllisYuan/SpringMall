package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.ProductSpecDO;

import java.util.List;

/**
 * 商品规格维度Mapper接口
 */
@Mapper
public interface ProductSpecMapper {

    /**
     * 根据商品ID查询所有规格维度
     *
     * @param productId 商品ID
     * @return 规格维度列表
     */
    List<ProductSpecDO> findByProductId(@Param("productId") Long productId);

    /**
     * 插入规格维度
     *
     * @param spec 规格维度DO
     * @return 影响行数
     */
    int insert(ProductSpecDO spec);

    /**
     * 根据商品ID删除所有规格维度
     *
     * @param productId 商品ID
     * @return 影响行数
     */
    int deleteByProductId(@Param("productId") Long productId);
}
