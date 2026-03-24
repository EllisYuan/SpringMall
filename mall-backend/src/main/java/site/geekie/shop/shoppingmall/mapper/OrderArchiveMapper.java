package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.OrderDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单归档Mapper接口
 */
@Mapper
public interface OrderArchiveMapper {

    /**
     * 根据订单号查询归档订单
     */
    OrderDO findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 批量插入归档订单
     */
    int batchInsert(@Param("orders") List<OrderDO> orders);

    /**
     * 根据用户ID分页查询归档订单
     */
    List<OrderDO> findByUserId(@Param("userId") Long userId);
}
