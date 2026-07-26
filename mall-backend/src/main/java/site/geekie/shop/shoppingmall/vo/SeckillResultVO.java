package site.geekie.shop.shoppingmall.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 秒杀抢购结果 VO
 * 用于抢购接口的同步返回、SSE 结果事件推送，以及降级一次性查询接口
 *
 * status 取值：
 *   QUEUING           — 排队中，等待 SSE 推送最终结果
 *   SUCCESS           — 抢购成功（orderNo 有值）
 *   FAIL              — 抢购失败（failReason 有值）
 *   NOT_FOUND         — 未找到结果（超时或从未参与）
 */
@Data
public class SeckillResultVO {

    /** 活动ID（雪花 ID，序列化为字符串防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 结果状态：QUEUING / SUCCESS / FAIL / NOT_FOUND */
    private String status;

    /** 订单号（仅 SUCCESS 时有值） */
    private String orderNo;

    /** 失败原因（仅 FAIL 时有值） */
    private String failReason;

    // ======================== 静态工厂方法 ========================

    public static SeckillResultVO queuing(Long activityId) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setActivityId(activityId);
        vo.setStatus("QUEUING");
        return vo;
    }

    public static SeckillResultVO success(Long activityId, String orderNo) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setActivityId(activityId);
        vo.setStatus("SUCCESS");
        vo.setOrderNo(orderNo);
        return vo;
    }

    public static SeckillResultVO fail(Long activityId, String reason) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setActivityId(activityId);
        vo.setStatus("FAIL");
        vo.setFailReason(reason);
        return vo;
    }

    public static SeckillResultVO notFound(Long activityId) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setActivityId(activityId);
        vo.setStatus("NOT_FOUND");
        return vo;
    }
}
