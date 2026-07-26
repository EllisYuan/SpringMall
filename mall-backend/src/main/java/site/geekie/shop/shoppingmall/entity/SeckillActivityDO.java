package site.geekie.shop.shoppingmall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体类
 * 对应数据库表：mall_seckill_activity
 */
@Data
public class SeckillActivityDO {

    /** 活动ID（主键） */
    private Long id;

    /** 关联商品ID */
    private Long productId;

    /** 关联SKU ID，无SKU时为0 */
    private Long skuId;

    /** 活动名称 */
    private String activityName;

    /** 原价（快照） */
    private BigDecimal originalPrice;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀总库存 */
    private Integer seckillStock;

    /** 剩余可用库存（DB乐观锁兜底权威） */
    private Integer availableStock;

    /** 每人限购数量 */
    private Integer limitPerUser;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 状态：0-下线/草稿 1-上线 */
    private Integer status;

    /** 超时释放库存是否允许二次流出：1-允许（回流可再抢） 0-不允许（仅账面回补） */
    private Integer allowRestock;

    /** 预留库存已归还商品库存的幂等标记：0-未归还 1-已归还 */
    private Integer stockReclaimed;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ===== 以下为非持久化展示字段，查询时 LEFT JOIN 商品/SKU 表填充，不参与写入 =====

    /** 关联商品名称（联表查询填充，用于前端列表展示） */
    private String productName;

    /** 关联商品主图（联表查询填充，秒杀页继承商品图片） */
    private String productImage;

    /** 关联SKU规格描述（联表查询填充，无SKU时为空） */
    private String skuSpecDesc;
}
