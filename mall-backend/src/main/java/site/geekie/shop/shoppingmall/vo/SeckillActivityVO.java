package site.geekie.shop.shoppingmall.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动 VO（返回给前端）
 *
 * activityStatus 为运行时计算字段（未开始/进行中/已结束/已售罄），不落库。
 */
@Data
public class SeckillActivityVO {

    /** 活动ID（雪花 ID，序列化为字符串防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联商品ID */
    private Long productId;

    /** 关联SKU ID，无SKU时为0 */
    private Long skuId;

    /** 关联商品名称（联表查询填充，前端列表展示用；提交仍用 productId） */
    private String productName;

    /** 关联商品主图（联表查询填充，秒杀列表/详情继承商品图片） */
    private String productImage;

    /** 关联SKU规格描述（联表查询填充，无SKU时为空） */
    private String skuSpecDesc;

    /** 活动名称 */
    private String activityName;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀总库存 */
    private Integer seckillStock;

    /** 剩余可用库存 */
    private Integer availableStock;

    /** 每人限购数量 */
    private Integer limitPerUser;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 管理状态：0-下线/草稿 1-上线 */
    private Integer status;

    /** 超时释放库存是否允许二次流出：1-允许 0-不允许 */
    private Integer allowRestock;

    /** 预留库存是否已归还商品库存：0-未归还 1-已归还（管理端展示用） */
    private Integer stockReclaimed;

    /**
     * 运行时活动状态（前端展示用）：
     * NOT_STARTED — 未开始
     * IN_PROGRESS — 进行中
     * ENDED       — 已结束
     * SOLD_OUT    — 已售罄
     */
    private String activityStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 服务器时间
     * 列表和详情接口均下发，前端以此计算倒计时偏移量，避免客户端时钟不准导致的抢购时间错位
     */
    private LocalDateTime serverTime;
}
