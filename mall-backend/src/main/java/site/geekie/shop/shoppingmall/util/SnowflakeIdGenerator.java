package site.geekie.shop.shoppingmall.util;

import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.config.SnowflakeProperties;

/**
 * 雪花 ID 生成器（全局通用，单机部署版）
 *
 * 64 位结构：1 位符号（恒 0）+ 41 位时间戳（自定义纪元 2026-01-01）+ 10 位 workerId + 12 位序列。
 * 41 位毫秒时间戳自纪元起可用约 69 年；同一毫秒内单机最多 4096 个 ID，耗尽则自旋等下一毫秒。
 *
 * 时钟回拨策略：小幅回拨（≤ {@link #MAX_BACKWARD_WAIT_MS}）自旋等待时钟追上；
 * 大幅回拨直接抛异常拒绝生成，避免发出重复 ID（单机部署，不引入外部协调）。
 *
 * 生成的 ID 对外序列化必须转字符串（VO 字段加 ToStringSerializer），
 * 因为数值超出 JS Number 安全整数范围（2^53-1），直接返回会精度丢失。
 */
@Component
public class SnowflakeIdGenerator {

    /** 自定义纪元：2026-01-01T00:00:00Z */
    private static final long EPOCH = 1767225600000L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 可容忍并等待追平的最大时钟回拨（毫秒），超过则拒绝生成 */
    private static final long MAX_BACKWARD_WAIT_MS = 5L;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(SnowflakeProperties snowflakeCfg) {
        long workerId = snowflakeCfg.getWorkerId();
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "snowflake.worker-id 必须在 0-" + MAX_WORKER_ID + " 范围内，当前值: " + workerId);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个全局唯一 ID（线程安全）
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_BACKWARD_WAIT_MS) {
                throw new IllegalStateException("检测到时钟回拨 " + offset + "ms，超过容忍上限，拒绝生成雪花 ID");
            }
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒 4096 个序列号耗尽，等待下一毫秒
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 自旋等待系统时钟到达目标毫秒（用于小幅回拨追平与序列耗尽跨毫秒）
     */
    private long waitUntil(long targetTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < targetTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
