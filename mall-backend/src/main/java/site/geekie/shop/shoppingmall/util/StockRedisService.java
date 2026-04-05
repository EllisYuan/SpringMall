package site.geekie.shop.shoppingmall.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存 Redis 操作封装
 *
 * Key 格式：stock:product:{productId}，String 类型，值为库存整数。
 *
 * 使用说明：
 *   atchDeductStock 返回 1=成功 / -1=缓存未加载（调用方需懒加载后重试）/ -2=库存不足
 *   batchRestoreStock 仅对 key 存在的条目执行 INCRBY，key 不存在说明缓存已失效，无需恢复
 *   所有方法不吞噬 Redis 异常，由调用方负责降级逻辑</li>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductMapper productMapper;

    private static final String STOCK_KEY_PREFIX = "stock:product:";
    private static final long STOCK_TTL_HOURS = 24;

    /**
     * 批量预扣库存 Lua 脚本
     *
     * 两轮循环：第一轮全部校验（有库存不足或缓存未加载则立即返回），
     * 第二轮全部扣减，保证原子性。
     *
     * 返回值：1=成功，-1=缓存未加载，-2=库存不足
     */
    private static final DefaultRedisScript<Long> BATCH_DEDUCT_SCRIPT;

    /**
     * 批量恢复库存 Lua 脚本
     * 仅当 key 存在时执行 INCRBY，key 不存在说明缓存已失效，无需恢复。
     */
    private static final DefaultRedisScript<Long> BATCH_RESTORE_SCRIPT;

    static {
        BATCH_DEDUCT_SCRIPT = new DefaultRedisScript<>();
        BATCH_DEDUCT_SCRIPT.setScriptText(
                "local n = #KEYS\n" +
                "for i = 1, n do\n" +
                "    local stock = tonumber(redis.call('get', KEYS[i]))\n" +
                "    if stock == nil then return -1 end\n" +
                "    if stock < tonumber(ARGV[i]) then return -2 end\n" +
                "end\n" +
                "for i = 1, n do\n" +
                "    redis.call('decrby', KEYS[i], ARGV[i])\n" +
                "    redis.call('expire', KEYS[i], 86400)\n" +
                "end\n" +
                "return 1"
        );
        BATCH_DEDUCT_SCRIPT.setResultType(Long.class);

        BATCH_RESTORE_SCRIPT = new DefaultRedisScript<>();
        BATCH_RESTORE_SCRIPT.setScriptText(
                "for i = 1, #KEYS do\n" +
                "    if redis.call('exists', KEYS[i]) == 1 then\n" +
                "        redis.call('incrby', KEYS[i], ARGV[i])\n" +
                "        redis.call('expire', KEYS[i], 86400)\n" +
                "    end\n" +
                "end\n" +
                "return 1"
        );
        BATCH_RESTORE_SCRIPT.setResultType(Long.class);
    }

    /**
     * 批量预扣库存（原子操作）
     * 先全部校验再全部扣减，任意一个商品缓存未加载或库存不足时整体回滚（Lua 原子性保证）。
     *
     * @param items 订单明细列表
     * @return 1=成功，-1=缓存未加载（需懒加载后重试），-2=库存不足
     */
    public Long batchDeductStock(List<OrderItemDO> items) {
        List<String> keys = items.stream()
                .map(item -> STOCK_KEY_PREFIX + item.getProductId())
                .toList();
        String[] args = items.stream()
                .map(item -> String.valueOf(item.getQuantity()))
                .toArray(String[]::new);

        Long result = stringRedisTemplate.execute(BATCH_DEDUCT_SCRIPT, keys, args);
        log.debug("批量预扣库存 - keys: {}, args: {}, result: {}", keys, args, result);
        return result;
    }

    /**
     * 批量恢复库存（原子操作）
     * 仅对 Redis 中已存在的 key 执行 INCRBY，不存在的 key 跳过（缓存已失效，无需恢复）。
     * 通常在订单取消、支付超时或下单失败回滚时调用。
     *
     * @param items 订单明细列表
     */
    public void batchRestoreStock(List<OrderItemDO> items) {
        List<String> keys = items.stream()
                .map(item -> STOCK_KEY_PREFIX + item.getProductId())
                .toList();
        String[] args = items.stream()
                .map(item -> String.valueOf(item.getQuantity()))
                .toArray(String[]::new);

        stringRedisTemplate.execute(BATCH_RESTORE_SCRIPT, keys, args);
        log.debug("批量恢复库存 - keys: {}", keys);
    }

    /**
     * 懒加载单个商品库存到 Redis
     * 使用 SETNX（setIfAbsent）防止并发加载覆盖已有值。
     * 商品不存在或状态非上架（status != 1）时不加载。
     *
     * @param productId 商品ID
     */
    public void loadStockIfAbsent(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        ProductDO product = productMapper.findById(productId);
        if (product == null) {
            log.warn("懒加载库存跳过 - 商品不存在, productId: {}", productId);
            return;
        }
        if (!Integer.valueOf(1).equals(product.getStatus())) {
            log.warn("懒加载库存跳过 - 商品已下架, productId: {}", productId);
            return;
        }
        Boolean loaded = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(product.getStock()), STOCK_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(loaded)) {
            log.debug("懒加载库存成功 - productId: {}, stock: {}", productId, product.getStock());
        } else {
            log.debug("懒加载库存跳过 - key 已存在, productId: {}", productId);
        }
    }

    /**
     * 批量懒加载商品库存到 Redis
     * 遍历订单明细，对每个商品调用 {@link #loadStockIfAbsent(Long)}。
     *
     * @param items 订单明细列表
     */
    public void loadStocksIfAbsent(List<OrderItemDO> items) {
        for (OrderItemDO item : items) {
            loadStockIfAbsent(item.getProductId());
        }
    }

    /**
     * 直接设置商品库存值（覆盖旧值）
     * 管理员在后台修改库存后调用，使缓存与数据库保持一致。
     *
     * @param productId 商品ID
     * @param stock     最新库存值
     */
    public void setStock(Long productId, Integer stock) {
        String key = STOCK_KEY_PREFIX + productId;
        stringRedisTemplate.opsForValue().set(key, String.valueOf(stock), STOCK_TTL_HOURS, TimeUnit.HOURS);
        log.debug("直接设置库存 - productId: {}, stock: {}", productId, stock);
    }

    /**
     * 删除商品库存缓存
     * 商品下架或删除时调用，避免缓存中残留过期数据。
     *
     * @param productId 商品ID
     */
    public void removeStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        stringRedisTemplate.delete(key);
        log.debug("删除库存缓存 - productId: {}", productId);
    }

}
