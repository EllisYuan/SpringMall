package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动创建/更新 DTO（管理后台使用）
 *
 * 活动状态不由本 DTO 控制：创建后默认草稿（status=0），
 * 上线/下线走独立的 activate / offline 接口（上线伴随库存预留与预热）。
 */
@Data
public class SeckillActivityDTO {

    /** 关联商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 关联SKU ID（可选，无SKU时传0或不传） */
    private Long skuId;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 200, message = "活动名称不能超过200字符")
    private String activityName;

    /** 原价 */
    @NotNull(message = "原价不能为空")
    @DecimalMin(value = "0.01", message = "原价必须大于0")
    private BigDecimal originalPrice;

    /** 秒杀价 */
    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于0")
    private BigDecimal seckillPrice;

    /** 秒杀总库存 */
    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存至少为1")
    private Integer seckillStock;

    /** 每人限购数量 */
    @NotNull(message = "限购数量不能为空")
    @Min(value = 1, message = "限购数量至少为1")
    private Integer limitPerUser;

    /** 活动开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 活动结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 超时释放库存是否允许二次流出：1-允许 0-不允许（不传默认1） */
    @Min(value = 0, message = "allowRestock 取值为 0 或 1")
    @Max(value = 1, message = "allowRestock 取值为 0 或 1")
    private Integer allowRestock;
}
