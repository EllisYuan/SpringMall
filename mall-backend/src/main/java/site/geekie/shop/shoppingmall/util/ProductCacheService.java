package site.geekie.shop.shoppingmall.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.entity.ProductDO;

import java.util.concurrent.TimeUnit;

/**
 * 商品缓存服务
 *
 * Key 格式：
 * - 正常缓存：cache:product:{productId}，存储 JSON 序列化的 ProductDO
 * - 空值缓存：cache:product:null:{productId}，防缓存穿透
 *
 * TTL：正常缓存 30 分钟，空值缓存 5 分钟
 *
 * 所有方法不对外抛出异常，由调用方 catch 后降级处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "cache:product:";
    private static final String NULL_PREFIX = "cache:product:null:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final long NULL_TTL_MINUTES = 5;

    /**
     * 从缓存获取商品
     * 优先检查空值缓存（防穿透），再检查正常缓存。
     * 缓存未命中或反序列化失败均返回 null，由调用方回源查 DB。
     *
     * @param productId 商品ID
     * @return 商品对象；空值缓存命中或缓存未命中均返回 null
     */
    public ProductDO getProduct(Long productId) {
        // 先检查空值缓存（防穿透）
        String nullKey = NULL_PREFIX + productId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey))) {
            log.debug("空值缓存命中，商品不存在 - productId: {}", productId);
            return null;
        }

        String key = CACHE_PREFIX + productId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null; // 缓存未命中
        }

        try {
            return objectMapper.readValue(json, ProductDO.class);
        } catch (Exception e) {
            log.warn("反序列化商品缓存异常 - productId: {}", productId, e);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入商品缓存
     *
     * @param product 商品对象（不能为 null）
     */
    public void putProduct(ProductDO product) {
        String key = CACHE_PREFIX + product.getId();
        try {
            String json = objectMapper.writeValueAsString(product);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("写入商品缓存成功 - productId: {}", product.getId());
        } catch (Exception e) {
            log.warn("写入商品缓存异常 - productId: {}", product.getId(), e);
        }
    }

    /**
     * 缓存空值，防止缓存穿透
     *
     * @param productId 商品ID
     */
    public void putNull(Long productId) {
        String nullKey = NULL_PREFIX + productId;
        stringRedisTemplate.opsForValue().set(nullKey, "1", NULL_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("写入空值缓存 - productId: {}", productId);
    }

    /**
     * 清除商品缓存（商品变更时调用）
     * <p>
     * 同时清除正常缓存和空值缓存。
     *
     * @param productId 商品ID
     */
    public void evictProduct(Long productId) {
        stringRedisTemplate.delete(CACHE_PREFIX + productId);
        stringRedisTemplate.delete(NULL_PREFIX + productId);
        log.debug("清除商品缓存 - productId: {}", productId);
    }
}
