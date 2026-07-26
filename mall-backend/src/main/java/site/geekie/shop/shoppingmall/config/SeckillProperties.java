package site.geekie.shop.shoppingmall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀功能配置属性
 *
 * 对应 application.yml 的 seckill.* 命名空间，所有字段均带安全默认值兜底。
 */
@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProperties {

    /** 库存分桶数量，默认 10（设为 1 即退化为单 key 预扣） */
    private int bucketCount = 10;

    /** 总量闸门倍率 k（闸门上限 = 秒杀库存 × gateMultiplier），默认 5 */
    private int gateMultiplier = 5;

    /** 售罄标记 TTL（秒），默认 3600 (1h) */
    private long soldoutTtl = 3600L;

    /** 消息去重 key TTL（秒），默认 86400 (24h) */
    private long msgDedupTtl = 86400L;

    /** 抢购结果标记 TTL（秒），默认 300 (5min) */
    private long resultTtl = 300L;

    /** 风控配置 */
    private Risk risk = new Risk();

    /** SSE 推送配置 */
    private Sse sse = new Sse();

    @Data
    public static class Risk {
        /** 用户/IP 被拒累计次数阈值（超过判高风险），默认 5 */
        private int failThreshold = 5;

        /** 频率滑动窗口内最大请求次数阈值（超过判高风险），默认 10 */
        private int freqThreshold = 10;

        /** 频率滑动窗口时长（秒），默认 60 */
        private int freqWindowSeconds = 60;
    }

    @Data
    public static class Sse {
        /** SseEmitter 超时时长（毫秒），默认 300000 (5min)，与结果标记 TTL 对齐 */
        private long timeoutMs = 300_000L;

        /** 心跳间隔（秒），防代理层空闲断连，默认 15 */
        private int heartbeatSeconds = 15;
    }
}
