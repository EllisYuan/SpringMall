package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀下单请求 DTO
 * 同时用作 MQ 消息体（实现 Serializable），随消息传递给 SeckillOrderConsumer
 *
 * requestId（UUID）由 Service 层在 Redis 预扣成功后生成，随消息入 MQ 实现幂等去重；
 * cfToken（可选）由前端在触发 step-up 人机验证后携带。
 */
@Data
public class SeckillOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀活动ID（来自路径参数 @PathVariable，由 Controller 注入，不在请求体中） */
    private Long activityId;

    /** 当前用户ID（由 @CurrentUserId 注入，不在请求体中） */
    private Long userId;

    /**
     * 购买数量（前端请求体字段）
     * 秒杀为单商品单数量直接抢购（design Non-Goal），上限锁定为 1：
     * 多数量跨桶预扣会在"分桶碎片化但总量有余"时误置售罄标记，连坐阻塞其他用户
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量最少为1")
    @Max(value = 1, message = "秒杀每单限购买1件")
    private Integer quantity;

    /** SKU ID（可选，商品无 SKU 时为 null 或 0） */
    private Long skuId;

    /** 收货地址ID */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /**
     * Cloudflare Turnstile 验证 token（可选）
     * 仅在后端判定为高风险并返回 SECKILL_CAPTCHA_REQUIRED 后，
     * 前端完成验证挑战再次提交时携带。正常请求无需此字段。
     */
    private String cfToken;

    /**
     * 请求幂等 ID（由 Service 层生成 UUID，随消息传递给 MQ 消费者）
     * 消费者通过 SETNX seckill:msg:done:{requestId} + mall_seckill_order.request_id
     * 唯一索引双重去重，防止重复落单
     */
    private String requestId;
}
