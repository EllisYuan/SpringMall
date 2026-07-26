package site.geekie.shop.shoppingmall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.SeckillActivityDO;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.SeckillActivityMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.service.StockRestoreService;
import site.geekie.shop.shoppingmall.util.SeckillRedisService;
import site.geekie.shop.shoppingmall.util.StockRedisService;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存回补服务实现
 *
 * 分流规则（与下单扣减侧镜像）：
 * - source=0 普通订单 → SKU 感知回补：有 SKU 明细回 SKU 库存（下单时 SKU 走纯 DB 扣减，无 Redis），
 *   无 SKU 明细回商品库存 + Redis（下单时经 stockRedisService.batchDeductStock 预扣）
 * - source=1 秒杀订单 → 一律回补活动 available_stock 账面，再按活动 allow_restock 二次分流：
 *     允许二次流出 → 同步回补秒杀 Redis 分桶（含扣回限购计数、清售罄标记）+ 回退闸门额度
 *     不允许       → 仅账面回补（不回分桶，活动期内不再放出），限购计数扣回与 DB SUM 口径对齐
 *   活动缺失或预留库存已归还（结束 Job 已回收）时回退为商品维度回补，避免账面滞留
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRestoreServiceImpl implements StockRestoreService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final StockRedisService stockRedisService;
    private final SeckillRedisService seckillRedisService;

    /**
     * MANDATORY：秒杀链路依赖 selectForUpdate 行锁串行化，行锁必须依附调用方事务存活，
     * 强制约束不允许在事务外调用（事务外调用抛 IllegalTransactionStateException）。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForClosedOrder(OrderDO order, List<OrderItemDO> items, String scene) {
        if (Integer.valueOf(1).equals(order.getSource())) {
            restoreSeckillStock(order, items, scene);
        } else {
            restoreProductStockSkuAware(order.getOrderNo(), items, scene);
        }
    }

    /**
     * 普通订单库存回补（SKU 感知）：有 SKU 明细回 SKU 库存，无 SKU 明细回商品库存 + Redis
     */
    private void restoreProductStockSkuAware(String orderNo, List<OrderItemDO> items, String scene) {
        List<OrderItemDO> noSkuItems = new ArrayList<>();
        for (OrderItemDO item : items) {
            if (item.getSkuId() != null && item.getSkuId() > 0) {
                // 有 SKU：恢复 SKU 库存（下单时 SKU 走纯 DB 扣减，无需回补 Redis）
                skuMapper.increaseStock(item.getSkuId(), item.getQuantity());
                log.debug("[{}] 恢复 SKU 库存 - SKU_ID: {}, 商品ID: {}, 数量: {}",
                        scene, item.getSkuId(), item.getProductId(), item.getQuantity());
            } else {
                // 无 SKU：恢复商品库存
                productMapper.increaseStock(item.getProductId(), item.getQuantity());
                noSkuItems.add(item);
                log.debug("[{}] 恢复商品库存 - 商品ID: {}, 数量: {}",
                        scene, item.getProductId(), item.getQuantity());
            }
        }
        // 恢复 Redis 库存（仅无 SKU 商品，与下单时的 Redis 预扣口径镜像）
        if (!noSkuItems.isEmpty()) {
            try {
                stockRedisService.batchRestoreStock(noSkuItems);
            } catch (Exception e) {
                log.warn("[{}] 恢复 Redis 库存异常 - 订单号: {}", scene, orderNo, e);
            }
        }
    }

    /**
     * 商品维度库存回补（不做 SKU 分流），仅供秒杀回退链路使用。
     *
     * 秒杀活动池创建时从商品维度预留（productMapper.decreaseStock），下线/结束归还也回商品维度；
     * 因此活动缺失或预留库存已归还时，回补必须回商品维度、不做 SKU 分流——即使秒杀明细带 skuId。
     * 这是与普通链路刻意的不对称，勿"修复"为 SKU 感知版。
     */
    private void restoreProductStockProductDimension(String orderNo, List<OrderItemDO> items, String scene) {
        for (OrderItemDO item : items) {
            productMapper.increaseStock(item.getProductId(), item.getQuantity());
            log.debug("[{}] 恢复商品库存（秒杀回退）- 商品ID: {}, 数量: {}",
                    scene, item.getProductId(), item.getQuantity());
        }
        try {
            stockRedisService.batchRestoreStock(items);
        } catch (Exception e) {
            log.warn("[{}] 恢复 Redis 库存异常 - 订单号: {}", scene, orderNo, e);
        }
    }

    /**
     * 秒杀订单库存回补：回补秒杀库存池（而非商品库存），按活动 allow_restock 决定是否重新放出
     *
     * 持活动行写锁串行化，与消费者扣减/下线归还/结束归还互斥，保证账面守恒：
     * - 活动缺失或 stock_reclaimed=1（预留库存已归还商品库存）：回补量直接回商品库存，
     *   否则加到已归还活动的 available_stock 上会永久滞留、账面丢失
     * - allow_restock=1：回补 available_stock + 秒杀 Redis 分桶（含扣回限购计数、清售罄标记）
     *   + 回退闸门额度，库存重新公开放出、先到先得
     * - allow_restock=0：仅回补 available_stock 账面（活动期内不再放出，待下线/结束随归还回商品库存），
     *   限购计数照常扣回（与 DB SUM 排除 CANCELLED 口径一致）
     */
    private void restoreSeckillStock(OrderDO order, List<OrderItemDO> items, String scene) {
        Long activityId = order.getSeckillActivityId();
        int quantity = items.stream().mapToInt(OrderItemDO::getQuantity).sum();

        SeckillActivityDO activity = activityId == null ? null : seckillActivityMapper.selectForUpdate(activityId);
        if (activity == null || Integer.valueOf(1).equals(activity.getStockReclaimed())) {
            // 活动缺失或预留库存已归还：直接回补商品库存（商品维度，见方法注释），避免账面滞留
            log.info("[{}] 秒杀活动缺失或库存已归还，回补商品库存 - 订单号: {}, activityId: {}",
                    scene, order.getOrderNo(), activityId);
            restoreProductStockProductDimension(order.getOrderNo(), items, scene);
            return;
        }

        // 一律回补 available_stock 账面（DB 权威）
        seckillActivityMapper.increaseStock(activityId, quantity);

        long ttlSeconds = seckillRedisService.calcActivityTtlSeconds(activity.getEndTime());
        if (Integer.valueOf(1).equals(activity.getAllowRestock())) {
            // 允许二次流出：回补分桶（含扣回限购计数、清售罄标记）+ 回退闸门额度
            try {
                seckillRedisService.restoreStock(activityId, order.getUserId(), quantity, ttlSeconds);
                seckillRedisService.rollbackGate(activityId, quantity);
            } catch (Exception e) {
                log.warn("[{}] 秒杀回补 Redis 异常（DB 账面已回补）- 订单号: {}, activityId: {}",
                        scene, order.getOrderNo(), activityId, e);
            }
            log.info("[{}] 秒杀订单回补（允许二次流出）- 订单号: {}, activityId: {}, qty: {}",
                    scene, order.getOrderNo(), activityId, quantity);
        } else {
            // 不允许二次流出：仅账面回补，不回分桶、售罄标记与闸门保持不变；限购计数扣回
            seckillRedisService.restoreUserLimit(activityId, order.getUserId(), quantity, ttlSeconds);
            log.info("[{}] 秒杀订单回补（不允许二次流出，仅账面）- 订单号: {}, activityId: {}, qty: {}",
                    scene, order.getOrderNo(), activityId, quantity);
        }
    }
}
