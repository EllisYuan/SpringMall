package site.geekie.shop.shoppingmall.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.geekie.shop.shoppingmall.config.SeckillProperties;
import site.geekie.shop.shoppingmall.vo.SeckillResultVO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀抢购结果 SSE 推送服务
 *
 * 管理用户 SSE 连接（SseEmitter）的注册/移除/超时，消费者落库完成后通过本服务
 * 将最终结果（SUCCESS/FAIL）主动推送给在线用户；用户不在线（未建连或已断开）时
 * 推送返回 false，前端降级调用一次性查询接口兜底。
 *
 * 事件契约（前端对接）：
 *   event: result   data: SeckillResultVO JSON（status=SUCCESS/FAIL，推送后连接即完成关闭）
 *   注释行心跳（": ping"）按 seckill.sse.heartbeat-seconds 周期发送，防代理层空闲断连
 *
 * 说明：SseEmitter 存活于当前应用实例内存，单实例部署下消费者可直接内存转发；
 * 多实例水平扩展时需补充 Redis Pub/Sub 桥接（见 design.md D10 Risks）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillSseService {

    private final SeckillProperties seckillProperties;

    /** key = {activityId}:{userId} -> 该用户在该活动下的 SSE 连接 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 心跳调度器（单线程足够：仅发送极小的注释帧） */
    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    public void startHeartbeat() {
        int interval = seckillProperties.getSse().getHeartbeatSeconds();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seckill-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, interval, interval, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        emitters.values().forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 关闭阶段的连接异常无需处理
            }
        });
        emitters.clear();
    }

    /**
     * 注册用户在指定活动下的 SSE 连接
     * 同一用户重复建连时，旧连接被替换并关闭（以最新连接为准）
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 新建的 SseEmitter（由 Controller 直接返回给前端）
     */
    public SseEmitter register(Long activityId, Long userId) {
        String key = emitterKey(activityId, userId);
        SseEmitter emitter = new SseEmitter(seckillProperties.getSse().getTimeoutMs());

        emitter.onCompletion(() -> emitters.remove(key, emitter));
        emitter.onTimeout(() -> emitters.remove(key, emitter));
        emitter.onError(e -> emitters.remove(key, emitter));

        SseEmitter old = emitters.put(key, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
                // 旧连接可能已断开
            }
        }
        log.debug("SSE 连接已注册 - activityId: {}, userId: {}, 当前连接数: {}", activityId, userId, emitters.size());
        return emitter;
    }

    /**
     * 向指定用户推送最终抢购结果并关闭连接
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @param result     最终结果（SUCCESS/FAIL）
     * @return true=已推送；false=用户不在线（前端降级查询兜底）
     */
    public boolean pushResult(Long activityId, Long userId, SeckillResultVO result) {
        String key = emitterKey(activityId, userId);
        SseEmitter emitter = emitters.get(key);
        if (emitter == null) {
            log.debug("SSE 推送跳过（用户未建连）- activityId: {}, userId: {}", activityId, userId);
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name("result").data(result));
            emitter.complete();
            log.debug("SSE 结果已推送 - activityId: {}, userId: {}, status: {}",
                    activityId, userId, result.getStatus());
            return true;
        } catch (Exception e) {
            log.warn("SSE 推送失败（连接可能已断开）- activityId: {}, userId: {}", activityId, userId, e);
            emitters.remove(key, emitter);
            return false;
        }
    }

    /**
     * 向所有在线连接发送心跳注释帧，发送失败的连接视为已断开并移除
     */
    private void sendHeartbeat() {
        emitters.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(key, emitter);
            }
        });
    }

    private String emitterKey(Long activityId, Long userId) {
        return activityId + ":" + userId;
    }
}
