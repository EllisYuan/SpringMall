package site.geekie.shop.shoppingmall.service;

import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.dto.OrderDTO;
import site.geekie.shop.shoppingmall.vo.OrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单服务接口
 * 提供订单的业务逻辑方法
 */
public interface OrderService {

    /**
     * 创建订单（从购物车结算）
     * 1. 验证购物车中有已选中的商品
     * 2. 验证库存充足
     * 3. 扣减库存
     * 4. 生成订单号
     * 5. 创建订单主表和明细表
     * 6. 清空已购买的购物车商品
     *
     * @param request 订单请求（包含收货地址ID和备注）
     * @param userId  当前登录用户ID
     * @return 订单响应
     */
    OrderVO createOrder(OrderDTO request, Long userId);

    /**
     * 获取当前用户的所有订单（分页）
     *
     * @param userId 当前登录用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页订单列表
     */
    PageResult<OrderVO> getMyOrders(Long userId, int page, int size);

    /**
     * 根据状态获取当前用户的订单（分页）
     *
     * @param status 订单状态
     * @param userId 当前登录用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页订单列表
     */
    PageResult<OrderVO> getMyOrdersByStatus(String status, Long userId, int page, int size);

    /**
     * 获取订单详情
     *
     * @param orderNo 订单号
     * @param userId  当前登录用户ID
     * @return 订单详情
     */
    OrderVO getOrderDetail(String orderNo, Long userId);

    /**
     * 取消订单
     * 只有待支付状态的订单可以取消
     * 取消后恢复库存
     *
     * @param orderNo 订单号
     * @param userId  当前登录用户ID
     */
    void cancelOrder(String orderNo, Long userId);

    /**
     * 确认收货
     * 只有已发货状态的订单可以确认收货
     *
     * @param orderNo 订单号
     * @param userId  当前登录用户ID
     */
    void confirmReceipt(String orderNo, Long userId);

    // ===== 管理员方法 =====

    /**
     * 获取所有订单（管理员）
     *
     * @return 所有订单列表
     */
    PageResult<OrderVO> getAllOrders(int page, int size, String sortBy, String sortDir);

    /**
     * 根据状态获取所有订单（管理员）
     *
     * @param status 订单状态
     * @param page   页码
     * @param size   每页大小
     * @return 分页订单列表
     */
    PageResult<OrderVO> getAllOrdersByStatus(String status, int page, int size, String sortBy, String sortDir);

    /**
     * 获取订单详情（管理员）
     * 不验证订单所有权
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    OrderVO getOrderDetailAdmin(String orderNo);

    /**
     * 发货（管理员）
     * 只有已支付状态的订单可以发货
     *
     * @param orderNo 订单号
     */
    void shipOrder(String orderNo);

    /**
     * 取消订单（管理员）
     * 管理员可以取消待支付和待发货状态的订单
     * 取消后恢复库存
     *
     * @param orderNo 订单号
     */
    void cancelOrderByAdmin(String orderNo);

    /**
     * 统计总销售额（管理员）
     * 排除 CANCELLED 状态的订单，汇总所有订单的实付金额
     *
     * @return 总销售额，无数据时返回 0
     */
    BigDecimal getTotalSales();

    /**
     * 获取用户的归档历史订单（分页）
     *
     * @param userId 当前登录用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页订单列表
     */
    PageResult<OrderVO> getArchivedOrders(Long userId, int page, int size);
}
