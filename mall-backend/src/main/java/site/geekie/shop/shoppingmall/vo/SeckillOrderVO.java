package site.geekie.shop.shoppingmall.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单 VO（"我的秒杀订单"列表使用）
 *
 * 数据来自 mall_seckill_order 快照 JOIN mall_order（状态/金额）与活动表（活动名）。
 * 支付/取消等操作通过 orderNo 走既有订单接口，因此不下发明细主键 id。
 */
@Data
public class SeckillOrderVO {

    /** 订单号（用于跳转支付/订单详情） */
    private String orderNo;

    /** 秒杀活动ID（雪花 ID，序列化为字符串防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 活动名称 */
    private String activityName;

    /** 商品ID */
    private Long productId;

    /** SKU ID，无SKU时为0 */
    private Long skuId;

    /** 商品名称快照 */
    private String productName;

    /** 商品主图快照 */
    private String productImage;

    /** 规格描述快照 */
    private String specDesc;

    /** 秒杀单价快照 */
    private BigDecimal seckillPrice;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 订单状态：UNPAID/PAID/SHIPPED/COMPLETED/CANCELLED */
    private String orderStatus;

    /** 订单状态描述（中文） */
    private String statusDesc;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
