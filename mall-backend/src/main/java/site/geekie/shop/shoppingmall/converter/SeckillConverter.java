package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import site.geekie.shop.shoppingmall.common.OrderStatus;
import site.geekie.shop.shoppingmall.dto.SeckillActivityDTO;
import site.geekie.shop.shoppingmall.entity.SeckillActivityDO;
import site.geekie.shop.shoppingmall.entity.SeckillOrderDO;
import site.geekie.shop.shoppingmall.vo.SeckillActivityVO;
import site.geekie.shop.shoppingmall.vo.SeckillOrderVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀数据转换器
 * SeckillActivityDTO -> DO、SeckillActivityDO -> VO、SeckillOrderDO -> SeckillOrderVO
 */
@Mapper(componentModel = "spring")
public interface SeckillConverter {

    /**
     * SeckillActivityDTO -> SeckillActivityDO（新增场景）
     * id / 时间戳 / availableStock（Service 设为 seckillStock）/ status / stockReclaimed 由 Service 处理；
     * 联表展示字段不参与写入。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "availableStock", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "stockReclaimed", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "skuSpecDesc", ignore = true)
    SeckillActivityDO toDO(SeckillActivityDTO dto);

    /**
     * SeckillActivityDTO 就地更新 SeckillActivityDO（编辑场景，仅草稿态允许）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "availableStock", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "stockReclaimed", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "skuSpecDesc", ignore = true)
    void updateDOFromDTO(SeckillActivityDTO dto, @MappingTarget SeckillActivityDO activity);

    /**
     * SeckillActivityDO -> SeckillActivityVO（不含运行时 activityStatus / serverTime）
     * activityStatus 和 serverTime 由 toVOWithStatus 填充
     */
    @Mapping(target = "activityStatus", ignore = true)
    @Mapping(target = "serverTime", ignore = true)
    SeckillActivityVO toVO(SeckillActivityDO activity);

    /**
     * SeckillActivityDO -> SeckillActivityVO（含运行时 activityStatus 和 serverTime）
     */
    default SeckillActivityVO toVOWithStatus(SeckillActivityDO activity) {
        if (activity == null) {
            return null;
        }
        SeckillActivityVO vo = toVO(activity);
        LocalDateTime now = LocalDateTime.now();
        vo.setActivityStatus(calcActivityStatus(activity, now));
        vo.setServerTime(now);
        return vo;
    }

    /**
     * 批量转换（含运行时状态）
     */
    default List<SeckillActivityVO> toVOList(List<SeckillActivityDO> activities) {
        if (activities == null) {
            return null;
        }
        return activities.stream()
                .map(this::toVOWithStatus)
                .collect(Collectors.toList());
    }

    /**
     * SeckillOrderDO -> SeckillOrderVO
     * statusDesc 由 toOrderVOComplete 填充
     */
    @Mapping(target = "statusDesc", ignore = true)
    SeckillOrderVO toOrderVO(SeckillOrderDO order);

    /**
     * SeckillOrderDO -> SeckillOrderVO（含状态中文描述）
     */
    default SeckillOrderVO toOrderVOComplete(SeckillOrderDO order) {
        if (order == null) {
            return null;
        }
        SeckillOrderVO vo = toOrderVO(order);
        if (order.getOrderStatus() != null) {
            try {
                vo.setStatusDesc(OrderStatus.fromCode(order.getOrderStatus()).getDescription());
            } catch (Exception e) {
                vo.setStatusDesc(order.getOrderStatus());
            }
        }
        return vo;
    }

    /**
     * 批量秒杀订单转换
     */
    default List<SeckillOrderVO> toOrderVOList(List<SeckillOrderDO> orders) {
        if (orders == null) {
            return null;
        }
        return orders.stream()
                .map(this::toOrderVOComplete)
                .collect(Collectors.toList());
    }

    // ======================== 私有工具方法 ========================

    /**
     * 运行时计算活动状态（传入 now 避免同一次转换中重复调用 LocalDateTime.now()）
     * NOT_STARTED / IN_PROGRESS / SOLD_OUT / ENDED
     */
    private static String calcActivityStatus(SeckillActivityDO activity, LocalDateTime now) {
        if (now.isBefore(activity.getStartTime())) {
            return "NOT_STARTED";
        }
        if (now.isAfter(activity.getEndTime())) {
            return "ENDED";
        }
        if (activity.getAvailableStock() != null && activity.getAvailableStock() <= 0) {
            return "SOLD_OUT";
        }
        return "IN_PROGRESS";
    }
}
