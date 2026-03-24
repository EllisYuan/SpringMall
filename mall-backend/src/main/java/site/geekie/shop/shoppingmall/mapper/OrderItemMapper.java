package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;

import java.util.List;

/**
 * 订单明细Mapper接口
 * 提供订单明细的数据访问方法
 */
@Mapper
public interface OrderItemMapper {

    /**
     * 根据订单ID查询所有订单明细
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    List<OrderItemDO> findByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单号查询所有订单明细
     *
     * @param orderNo 订单号
     * @return 订单明细列表
     */
    List<OrderItemDO> findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 批量插入订单明细
     *
     * @param items 订单明细列表
     * @return 影响行数
     */
    int batchInsert(@Param("items") List<OrderItemDO> items)
;

    /**
     * 插入单个订单明细
     *
     * @param item 订单明细
     * @return 影响行数
     */
    int insert(OrderItemDO item);

    /**
     * 根据订单ID列表批量查询所有订单明细
     * 用于避免 N+1 查询，一次性加载多个订单的明细
     *
     * @param orderIds 订单ID列表
     * @return 订单明细列表，按 order_id, id 排序
     */
    List<OrderItemDO> findByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 根据订单ID列表批量删除订单明细
     *
     * @param orderIds 订单ID列表
     * @return 影响行数
     */
    int deleteByOrderIds(@Param("orderIds") List<Long> orderIds);
}
