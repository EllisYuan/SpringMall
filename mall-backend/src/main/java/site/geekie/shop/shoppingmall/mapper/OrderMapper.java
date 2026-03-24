package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.OrderDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单Mapper接口
 * 提供订单的数据访问方法
 */
@Mapper
public interface OrderMapper {

    /**
     * 根据订单号查询订单
     *
     * @param orderNo 订单号
     * @return 订单信息，不存在返回null
     */
    OrderDO findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据用户ID查询所有订单
     * 按创建时间倒序排列
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    List<OrderDO> findByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和订单状态查询订单
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @return 订单列表
     */
    List<OrderDO> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 查询所有订单（管理员用）
     *
     * @param sortColumn 排序列（白名单校验后传入）
     * @param sortDir 排序方向（ASC/DESC）
     * @return 订单列表
     */
    List<OrderDO> findAll(@Param("sortColumn") String sortColumn, @Param("sortDir") String sortDir);

    /**
     * 根据订单状态查询所有订单（管理员用）
     *
     * @param status 订单状态
     * @param sortColumn 排序列（白名单校验后传入）
     * @param sortDir 排序方向（ASC/DESC）
     * @return 订单列表
     */
    List<OrderDO> findAllByStatus(@Param("status") String status,
                                  @Param("sortColumn") String sortColumn,
                                  @Param("sortDir") String sortDir);

    /**
     * 插入订单
     *
     * @param order 订单信息
     * @return 影响行数
     */
    int insert(OrderDO order);

    /**
     * 更新订单状态
     *
     * @param orderNo 订单号
     * @param status 新状态
     * @return 影响行数
     */
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") String status);

    /**
     * 原子性更新订单状态（乐观锁）
     * 仅当当前状态等于 expectedStatus 时才更新为 newStatus
     *
     * @param orderNo 订单号
     * @param expectedStatus 期望的当前状态
     * @param newStatus 目标状态
     * @return 影响行数（0 表示状态已被其他线程更新）
     */
    int compareAndUpdateStatus(@Param("orderNo") String orderNo,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("newStatus") String newStatus);

    /**
     * 更新支付方式
     *
     * @param orderNo 订单号
     * @param paymentMethod 支付方式
     * @return 影响行数
     */
    int updatePaymentMethod(@Param("orderNo") String orderNo, @Param("paymentMethod") String paymentMethod);

    /**
     * 更新支付时间
     *
     * @param orderNo 订单号
     * @return 影响行数
     */
    int updatePaymentTime(@Param("orderNo") String orderNo);

    /**
     * 更新发货时间
     *
     * @param orderNo 订单号
     * @return 影响行数
     */
    int updateShipTime(@Param("orderNo") String orderNo);

    /**
     * 更新完成时间
     *
     * @param orderNo 订单号
     * @return 影响行数
     */
    int updateCompleteTime(@Param("orderNo") String orderNo);

    /**
     * 统计用户订单数
     *
     * @param userId 用户ID
     * @return 订单数
     */
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计所有订单数（管理员用）
     *
     * @return 订单数
     */
    int countAll();

    /**
     * 统计非取消订单的实付金额总和（管理员用）
     * 排除 CANCELLED 状态的订单
     *
     * @return 总销售额，无数据时返回 0
     */
    BigDecimal sumPayAmountExcludeCancelled();

    /**
     * 查询待归档的订单（已完成或已取消且创建时间早于阈值）
     *
     * @param threshold 时间阈值
     * @param limit 每批数量
     * @return 待归档订单列表
     */
    List<OrderDO> findArchiveCandidates(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);

    /**
     * 根据ID列表批量删除订单
     *
     * @param ids 订单ID列表
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") List<Long> ids);
}
