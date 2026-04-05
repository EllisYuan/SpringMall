package site.geekie.shop.shoppingmall.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.geekie.shop.shoppingmall.annotation.LogOperation;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.converter.OrderConverter;
import site.geekie.shop.shoppingmall.converter.OrderItemConverter;
import site.geekie.shop.shoppingmall.dto.OrderDTO;
import site.geekie.shop.shoppingmall.vo.OrderVO;
import site.geekie.shop.shoppingmall.entity.*;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.mapper.*;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.security.SecurityUser;
import site.geekie.shop.shoppingmall.mq.producer.OrderMessageProducer;
import site.geekie.shop.shoppingmall.service.OrderService;
import site.geekie.shop.shoppingmall.service.PaymentService;
import site.geekie.shop.shoppingmall.util.OrderNoGenerator;
import site.geekie.shop.shoppingmall.util.RedisDistributedLock;
import site.geekie.shop.shoppingmall.util.StockRedisService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    // 排序列白名单，防止动态 ORDER BY 注入
    private static final Map<String, String> SORT_COLUMN_WHITELIST = Map.of(
            "orderNo", "ord.order_no",
            "createdAt", "ord.created_at",
            "paymentTime", "ord.payment_time"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderArchiveMapper orderArchiveMapper;
    private final OrderItemArchiveMapper orderItemArchiveMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final AddressMapper addressMapper;
    private final OrderConverter orderConverter;
    private final OrderItemConverter orderItemConverter;
    private final PaymentService paymentService;
    private final OrderMessageProducer orderMessageProducer;
    private final StockRedisService stockRedisService;
    private final RedisDistributedLock redisDistributedLock;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogOperation(value = "创建订单", module = "订单")
    public OrderVO createOrder(OrderDTO request, Long userId) {
        // 防重复下单锁：同一用户在锁有效期内只允许一个下单请求执行
        String lockKey = "lock:order:create:" + userId;
        String lockValue = null;
        boolean redisAvailable = true;

        try {
            lockValue = redisDistributedLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("获取下单锁时 Redis 异常，降级放行 - userId: {}", userId, e);
            redisAvailable = false;
        }

        if (lockValue == null && redisAvailable) {
            // tryLock 正常返回 null：锁被占用，说明存在并发重复下单
            throw new BusinessException(ResultCode.ORDER_CREATE_TOO_FREQUENT);
        }

        try {
            return doCreateOrder(request, userId);
        } finally {
            if (lockValue != null) {
                redisDistributedLock.unlock(lockKey, lockValue);
            }
        }
    }

    /**
     * 创建订单核心逻辑
     *
     * 由 createOrder 在事务内调用，不单独标注 @Transactional（复用外层事务）。
     * 流程：查购物车 → 验证地址 → 验证商品状态/计算总价 → Redis 预扣库存 → DB 乐观锁扣减 →
     * 注册事务回滚回调（自动恢复 Redis 库存）→ 生成订单 → 清购物车 → 发延迟消息
     */
    private OrderVO doCreateOrder(OrderDTO request, Long userId) {
        // 1. 查询购物车中已选中的商品
        List<CartItemDO> checkedItems = cartItemMapper.findCheckedByUserId(userId);
        if (checkedItems == null || checkedItems.isEmpty()) {
            throw new BusinessException(ResultCode.NO_CHECKED_CART_ITEMS);
        }

        // 2. 验证地址
        AddressDO address = addressMapper.findById(request.getAddressId());
        if (address == null) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND);
        }
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 批量查询商品信息，验证状态并计算总价
        List<Long> productIds = checkedItems.stream()
                .map(CartItemDO::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductDO> productMap = productMapper.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductDO::getId, p -> p));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemDO> orderItems = new ArrayList<>();
        // 记录有 SKU 的 orderItem 对应的 SkuDO，用于 DB 扣减
        List<SkuDO> skuItemList = new ArrayList<>();

        for (CartItemDO cartItem : checkedItems) {
            ProductDO product = productMap.get(cartItem.getProductId());
            if (product == null) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != 1) {
                throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
            }

            BigDecimal unitPrice;
            String productImage = product.getMainImage();
            SkuDO sku = null;

            if (cartItem.getSkuId() != null && cartItem.getSkuId() > 0) {
                // 有 SKU：使用 SKU 价格和库存
                sku = skuMapper.findById(cartItem.getSkuId());
                if (sku == null || !sku.getProductId().equals(cartItem.getProductId())) {
                    throw new BusinessException(ResultCode.SKU_NOT_FOUND);
                }
                if (!Integer.valueOf(1).equals(sku.getStatus())) {
                    throw new BusinessException(ResultCode.PRODUCT_UNAVAILABLE);
                }
                if (sku.getStock() < cartItem.getQuantity()) {
                    throw new BusinessException(ResultCode.SKU_OUT_OF_STOCK);
                }
                unitPrice = sku.getPrice();
                if (sku.getImage() != null) {
                    productImage = sku.getImage();
                }
            } else {
                // 无 SKU：使用商品价格和库存
                if (product.getStock() < cartItem.getQuantity()) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
                }
                unitPrice = product.getPrice();
            }

            // 计算小计
            BigDecimal itemTotal = unitPrice.multiply(new BigDecimal(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            // 准备订单明细（稍后插入）
            OrderItemDO orderItem = new OrderItemDO();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(productImage);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setTotalPrice(itemTotal);
            if (sku != null) {
                orderItem.setSkuId(sku.getId());
                orderItem.setSpecDesc(sku.getSpecDesc());
            }
            orderItems.add(orderItem);
            skuItemList.add(sku); // null 表示无 SKU
        }

        // 4. Redis 批量预扣库存（仅无 SKU 商品走 product 维度；有 SKU 商品降级走 DB）
        // 为保持 Redis 预扣的原子性，此处仅对无 SKU 的 orderItems 执行 Redis 预扣
        List<OrderItemDO> noSkuItems = new ArrayList<>();
        for (int i = 0; i < orderItems.size(); i++) {
            if (skuItemList.get(i) == null) {
                noSkuItems.add(orderItems.get(i));
            }
        }

        boolean redisDeducted = false;
        if (!noSkuItems.isEmpty()) {
            try {
                Long result = stockRedisService.batchDeductStock(noSkuItems);
                if (Long.valueOf(-1L).equals(result)) {
                    // 缓存未加载，懒加载后重试一次
                    stockRedisService.loadStocksIfAbsent(noSkuItems);
                    result = stockRedisService.batchDeductStock(noSkuItems);
                }
                if (Long.valueOf(-2L).equals(result)) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
                }
                if (Long.valueOf(1L).equals(result)) {
                    redisDeducted = true;
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis 库存预扣异常，降级为纯 DB 模式", e);
            }
        }

        // 5. DB 乐观锁扣减库存（兜底保证）
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItemDO orderItem = orderItems.get(i);
            SkuDO sku = skuItemList.get(i);

            if (sku != null) {
                // 有 SKU：扣减 SKU 库存
                int dbResult = skuMapper.decreaseStock(sku.getId(), orderItem.getQuantity());
                if (dbResult == 0) {
                    if (redisDeducted) {
                        try {
                            stockRedisService.batchRestoreStock(noSkuItems);
                        } catch (Exception ex) {
                            log.error("DB SKU扣减失败后恢复 Redis 库存异常", ex);
                        }
                    }
                    throw new BusinessException(ResultCode.SKU_OUT_OF_STOCK);
                }
            } else {
                // 无 SKU：扣减商品库存
                int dbResult = productMapper.decreaseStock(orderItem.getProductId(), orderItem.getQuantity());
                if (dbResult == 0) {
                    if (redisDeducted) {
                        try {
                            stockRedisService.batchRestoreStock(noSkuItems);
                        } catch (Exception ex) {
                            log.error("DB 扣减失败后恢复 Redis 库存异常", ex);
                        }
                    }
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
                }
            }
        }

        // 6. 注册事务回滚回调：事务异常回滚时自动恢复 Redis 库存
        if (redisDeducted) {
            final List<OrderItemDO> finalNoSkuItems = new ArrayList<>(noSkuItems);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try {
                            stockRedisService.batchRestoreStock(finalNoSkuItems);
                            log.info("事务回滚后已恢复 Redis 库存，共 {} 个商品", finalNoSkuItems.size());
                        } catch (Exception e) {
                            log.error("事务回滚后恢复 Redis 库存异常", e);
                        }
                    }
                }
            });
        }

        // 7. 生成订单号
        String orderNo = OrderNoGenerator.generateOrderNo();

        // 8. 创建订单主表
        OrderDO order = new OrderDO();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount); // 实付金额暂时等于总金额（未来可加优惠券等）
        order.setFreight(BigDecimal.ZERO); // 运费暂时为0
        order.setStatus(OrderStatus.UNPAID.getCode());
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(address.getProvince() + address.getCity() +
                address.getDistrict() + address.getDetailAddress());
        order.setRemark(request.getRemark());

        orderMapper.insert(order);

        // 9. 创建订单明细
        for (OrderItemDO item : orderItems) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.batchInsert(orderItems);

        // 10. 清空已购买的购物车商品
        List<Long> cartItemIds = checkedItems.stream()
                .map(CartItemDO::getId)
                .collect(Collectors.toList());
        cartItemMapper.deleteByIds(cartItemIds);

        // 11. 事务提交后发送订单超时关闭延迟消息（15 分钟后自动取消未支付订单）
        final String finalOrderNo = orderNo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderMessageProducer.sendOrderCloseDelayMessage(finalOrderNo);
            }
        });

        // 12. 返回订单信息
        return orderConverter.toVOWithItems(order, orderItemMapper, orderItemConverter);
    }

    @Override
    public PageResult<OrderVO> getMyOrders(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<OrderDO> orders = orderMapper.findByUserId(userId);
        PageInfo<OrderDO> pageInfo = new PageInfo<>(orders);
        List<OrderVO> voList = buildOrderVOListWithItems(orders);
        return new PageResult<>(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    public PageResult<OrderVO> getMyOrdersByStatus(String status, Long userId, int page, int size) {
        // 验证状态是否合法
        try {
            OrderStatus.fromCode(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.INVALID_ORDER_STATUS);
        }

        PageHelper.startPage(page, size);
        List<OrderDO> orders = orderMapper.findByUserIdAndStatus(userId, status);
        PageInfo<OrderDO> pageInfo = new PageInfo<>(orders);
        List<OrderVO> voList = buildOrderVOListWithItems(orders);
        return new PageResult<>(voList, pageInfo.getTotal(), page, size);
    }

    /**
     * 批量构建带明细的订单 VO 列表（避免 N+1 查询）
     *
     * @param orders 订单DO列表
     * @return 带订单明细的订单VO列表
     */
    private List<OrderVO> buildOrderVOListWithItems(List<OrderDO> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        // 批量查询所有订单明细
        List<Long> orderIds = orders.stream()
                .map(OrderDO::getId)
                .collect(Collectors.toList());
        Map<Long, List<OrderItemDO>> itemsMap = orderItemMapper.findByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemDO::getOrderId));

        // 逐个组装 VO
        return orders.stream().map(order -> {
            OrderVO vo = orderConverter.toVOComplete(order);
            List<OrderItemDO> items = itemsMap.getOrDefault(order.getId(), List.of());
            vo.setItems(items.stream()
                    .map(orderItemConverter::toVO)
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public OrderVO getOrderDetail(String orderNo, Long userId) {
        // 先查热表
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        boolean fromArchive = false;

        if (order == null) {
            // 降级查归档表
            order = orderArchiveMapper.findByOrderNo(orderNo);
            fromArchive = true;
        }

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 验证订单所有权
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        if (fromArchive) {
            // 从归档表加载明细
            OrderVO vo = orderConverter.toVOComplete(order);
            List<OrderItemDO> items = orderItemArchiveMapper.findByOrderIds(List.of(order.getId()));
            vo.setItems(items.stream().map(orderItemConverter::toVO).collect(Collectors.toList()));
            return vo;
        }

        return orderConverter.toVOWithItems(order, orderItemMapper, orderItemConverter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogOperation(value = "取消订单", module = "订单")
    public void cancelOrder(String orderNo, Long userId) {
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 校验状态转换合法性（UNPAID->CANCELLED 或 PAID->CANCELLED）
        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());
        currentStatus.transitTo(OrderStatus.CANCELLED);

        // 如果订单已支付，先退款
        if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
            try {
                paymentService.refundOrder(orderNo, "用户取消订单");
            } catch (Exception e) {
                log.error("订单退款失败 - 订单号: {}", orderNo, e);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }
        }

        // 恢复库存
        List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
        List<OrderItemDO> noSkuItems = new ArrayList<>();
        for (OrderItemDO item : items) {
            if (item.getSkuId() != null && item.getSkuId() > 0) {
                // 有 SKU：恢复 SKU 库存
                skuMapper.increaseStock(item.getSkuId(), item.getQuantity());
            } else {
                // 无 SKU：恢复商品库存
                productMapper.increaseStock(item.getProductId(), item.getQuantity());
                noSkuItems.add(item);
            }
        }
        // 恢复 Redis 库存（仅无 SKU 商品）
        if (!noSkuItems.isEmpty()) {
            try {
                stockRedisService.batchRestoreStock(noSkuItems);
            } catch (Exception e) {
                log.warn("取消订单时恢复 Redis 库存异常 - 订单号: {}", orderNo, e);
            }
        }

        // 已付款订单取消时，扣减销量
        if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
            for (OrderItemDO item : items) {
                if (item.getSkuId() != null && item.getSkuId() > 0) {
                    skuMapper.decreaseSalesCount(item.getSkuId(), item.getQuantity());
                }
                productMapper.decreaseSalesCount(item.getProductId(), item.getQuantity());
            }
        }

        // 更新订单状态为已取消
        orderMapper.updateStatus(orderNo, OrderStatus.CANCELLED.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(String orderNo, Long userId) {
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 校验状态转换合法性（SHIPPED->COMPLETED）
        OrderStatus.fromCode(order.getStatus()).transitTo(OrderStatus.COMPLETED);

        // 更新订单状态为已完成
        orderMapper.updateStatus(orderNo, OrderStatus.COMPLETED.getCode());
        orderMapper.updateCompleteTime(orderNo);
    }

    // ===== 管理员方法实现 =====

    @Override
    public PageResult<OrderVO> getAllOrders(int page, int size, String sortBy, String sortDir) {
        String sortColumn = SORT_COLUMN_WHITELIST.getOrDefault(sortBy, "ord.created_at");
        String dir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        PageHelper.startPage(page, size);
        List<OrderDO> orders = orderMapper.findAll(sortColumn, dir);
        PageInfo<OrderDO> pageInfo = new PageInfo<>(orders);
        List<OrderVO> list = orderConverter.toVOList(orders);
        return new PageResult<>(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public PageResult<OrderVO> getAllOrdersByStatus(String status, int page, int size, String sortBy, String sortDir) {
        // 验证状态是否合法
        try {
            OrderStatus.fromCode(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.INVALID_ORDER_STATUS);
        }

        String sortColumn = SORT_COLUMN_WHITELIST.getOrDefault(sortBy, "ord.created_at");
        String dir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        PageHelper.startPage(page, size);
        List<OrderDO> orders = orderMapper.findAllByStatus(status, sortColumn, dir);
        PageInfo<OrderDO> pageInfo = new PageInfo<>(orders);
        List<OrderVO> list = orderConverter.toVOList(orders);
        return new PageResult<>(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public OrderVO getOrderDetailAdmin(String orderNo) {
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        boolean fromArchive = false;

        if (order == null) {
            order = orderArchiveMapper.findByOrderNo(orderNo);
            fromArchive = true;
        }

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if (fromArchive) {
            OrderVO vo = orderConverter.toVOComplete(order);
            List<OrderItemDO> items = orderItemArchiveMapper.findByOrderIds(List.of(order.getId()));
            vo.setItems(items.stream().map(orderItemConverter::toVO).collect(Collectors.toList()));
            return vo;
        }

        // 管理员查看订单详情不需要验证所有权
        return orderConverter.toVOWithItems(order, orderItemMapper, orderItemConverter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(String orderNo) {
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 校验状态转换合法性（PAID->SHIPPED）
        OrderStatus.fromCode(order.getStatus()).transitTo(OrderStatus.SHIPPED);

        // 更新订单状态为已发货
        orderMapper.updateStatus(orderNo, OrderStatus.SHIPPED.getCode());
        orderMapper.updateShipTime(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByAdmin(String orderNo) {
        OrderDO order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 校验状态转换合法性（UNPAID->CANCELLED 或 PAID->CANCELLED）
        OrderStatus currentStatusAdmin = OrderStatus.fromCode(order.getStatus());
        currentStatusAdmin.transitTo(OrderStatus.CANCELLED);

        // 如果订单已支付，先退款
        if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
            try {
                paymentService.refundOrder(orderNo, "管理员取消订单");
            } catch (Exception e) {
                log.error("订单退款失败 - 订单号: {}", orderNo, e);
                throw new BusinessException(ResultCode.REFUND_FAILED);
            }
        }

        // 恢复库存
        List<OrderItemDO> items = orderItemMapper.findByOrderId(order.getId());
        List<OrderItemDO> noSkuItemsAdmin = new ArrayList<>();
        for (OrderItemDO item : items) {
            if (item.getSkuId() != null && item.getSkuId() > 0) {
                skuMapper.increaseStock(item.getSkuId(), item.getQuantity());
            } else {
                productMapper.increaseStock(item.getProductId(), item.getQuantity());
                noSkuItemsAdmin.add(item);
            }
        }
        // 恢复 Redis 库存（仅无 SKU 商品）
        if (!noSkuItemsAdmin.isEmpty()) {
            try {
                stockRedisService.batchRestoreStock(noSkuItemsAdmin);
            } catch (Exception e) {
                log.warn("管理员取消订单时恢复 Redis 库存异常 - 订单号: {}", orderNo, e);
            }
        }

        // 已付款订单取消时，扣减销量
        if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
            for (OrderItemDO item : items) {
                if (item.getSkuId() != null && item.getSkuId() > 0) {
                    skuMapper.decreaseSalesCount(item.getSkuId(), item.getQuantity());
                }
                productMapper.decreaseSalesCount(item.getProductId(), item.getQuantity());
            }
        }

        // 更新订单状态为已取消
        orderMapper.updateStatus(orderNo, OrderStatus.CANCELLED.getCode());
    }

    @Override
    public BigDecimal getTotalSales() {
        String cacheKey = "stats:order:total_sales";
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return new BigDecimal(cached);
            }
        } catch (Exception e) {
            log.warn("读取销售额缓存失败: {}", e.getMessage());
        }

        BigDecimal totalSales = orderMapper.sumPayAmountExcludeCancelled();

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, totalSales.toPlainString(), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入销售额缓存失败: {}", e.getMessage());
        }

        return totalSales;
    }

    @Override
    public PageResult<OrderVO> getArchivedOrders(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<OrderDO> orders = orderArchiveMapper.findByUserId(userId);
        PageInfo<OrderDO> pageInfo = new PageInfo<>(orders);
        List<OrderVO> voList = buildArchiveOrderVOListWithItems(orders);
        return new PageResult<>(voList, pageInfo.getTotal(), page, size);
    }

    /**
     * 批量构建带明细的归档订单 VO 列表（避免 N+1 查询）
     */
    private List<OrderVO> buildArchiveOrderVOListWithItems(List<OrderDO> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(OrderDO::getId)
                .collect(Collectors.toList());
        Map<Long, List<OrderItemDO>> itemsMap = orderItemArchiveMapper.findByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemDO::getOrderId));

        return orders.stream().map(order -> {
            OrderVO vo = orderConverter.toVOComplete(order);
            List<OrderItemDO> items = itemsMap.getOrDefault(order.getId(), List.of());
            vo.setItems(items.stream()
                    .map(orderItemConverter::toVO)
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }
}
