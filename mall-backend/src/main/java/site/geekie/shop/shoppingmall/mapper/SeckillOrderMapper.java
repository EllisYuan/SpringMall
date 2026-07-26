package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.SeckillOrderDO;

import java.util.List;

/**
 * 秒杀订单明细快照 Mapper
 * 真实订单状态/金额在 mall_order，本表仅承载秒杀维度快照与幂等/限购统计
 */
@Mapper
public interface SeckillOrderMapper {

    /**
     * 插入秒杀订单明细快照（含 request_id，DB 唯一索引 uk_request_id 兜底幂等去重）
     */
    int insert(SeckillOrderDO order);

    /**
     * 用户在某活动下的未取消订单累计购买量（DB 权威限购校验，须在活动行写锁内调用）
     * JOIN mall_order 排除 CANCELLED 状态（超时取消后限购额度释放）
     *
     * @return 累计数量，无记录时返回 null
     */
    Integer sumQuantityByActivityAndUser(@Param("activityId") Long activityId,
                                         @Param("userId") Long userId);

    /**
     * 根据用户ID查询秒杀订单列表（"我的秒杀订单"），配合 PageHelper 分页
     * JOIN mall_order 填充订单状态/金额，LEFT JOIN 活动表填充活动名称
     */
    List<SeckillOrderDO> findByUserId(@Param("userId") Long userId);
}
