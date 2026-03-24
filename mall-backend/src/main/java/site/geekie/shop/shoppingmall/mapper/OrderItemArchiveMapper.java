package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;

import java.util.List;

/**
 * 订单明细归档Mapper接口
 */
@Mapper
public interface OrderItemArchiveMapper {

    /**
     * 批量插入归档订单明细
     */
    int batchInsert(@Param("items") List<OrderItemDO> items);

    /**
     * 根据订单ID批量查询归档明细
     */
    List<OrderItemDO> findByOrderIds(@Param("orderIds") List<Long> orderIds);
}
