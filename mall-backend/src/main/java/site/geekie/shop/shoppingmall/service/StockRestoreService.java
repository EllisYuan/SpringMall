package site.geekie.shop.shoppingmall.service;

import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;

import java.util.List;

/**
 * 订单关闭/取消时的库存回补服务
 *
 * 统一收口四个关单场景的库存回补口径：超时关单（OrderCloseConsumer）、
 * 用户取消 / 管理员取消（OrderServiceImpl）、掉单补偿（PaymentCheckConsumer）。
 * 回补口径镜像下单扣减侧：有 SKU 明细回 SKU 库存（纯 DB），无 SKU 明细回商品库存 + Redis；
 * 秒杀订单回补活动库存池并按活动配置二次分流。
 */
public interface StockRestoreService {

    /**
     * 订单关闭/取消时回补库存，内部按 order.source 分流普通/秒杀链路。
     * 必须在调用方事务内执行（实现标注 Propagation.MANDATORY，事务外调用直接抛异常）。
     *
     * @param order 订单（需含 orderNo、source、userId、seckillActivityId）
     * @param items 订单明细列表
     * @param scene 场景标签，仅用于日志（如"超时关单"/"用户取消"/"管理员取消"/"掉单补偿"）
     */
    void restoreForClosedOrder(OrderDO order, List<OrderItemDO> items, String scene);
}
