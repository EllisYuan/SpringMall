package site.geekie.shop.shoppingmall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.geekie.shop.shoppingmall.entity.SeckillActivityDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动 Mapper
 */
@Mapper
public interface SeckillActivityMapper {

    /**
     * 根据ID查询活动（联表带商品名/主图/规格，供详情与编辑回显）
     */
    SeckillActivityDO findById(@Param("id") Long id);

    /**
     * 查询进行中或即将开始的活动列表（status=1，end_time > now）
     * 用于用户端活动列表展示
     */
    List<SeckillActivityDO> findActiveAndUpcoming();

    /**
     * 管理后台查询全量活动（含下线状态），配合 PageHelper 分页
     */
    List<SeckillActivityDO> findAllForAdmin();

    /**
     * 插入活动
     */
    int insert(SeckillActivityDO activity);

    /**
     * 更新活动（动态字段）
     */
    int updateById(SeckillActivityDO activity);

    /**
     * 上线活动：status 0→1，重置 available_stock 与归还标记
     * 带 status=0 守卫，防止并发重复上线（重复上线会重复预留商品库存）
     *
     * @param id             活动ID
     * @param availableStock 上线时的可用库存（= seckill_stock）
     * @return 影响行数：1=成功，0=活动不存在或已上线
     */
    int activateById(@Param("id") Long id, @Param("availableStock") Integer availableStock);

    /**
     * 下线活动（status 改为 0）
     */
    int offlineById(@Param("id") Long id);

    /**
     * 置"预留库存已归还"幂等标记
     * 带 stock_reclaimed=0 守卫：影响行数 1 表示本次抢占归还权成功，0 表示已归还过（幂等跳过）
     */
    int markReclaimed(@Param("id") Long id);

    /**
     * 删除活动（物理删除，仅限草稿/下线状态）
     */
    int deleteById(@Param("id") Long id);

    /**
     * SELECT ... FOR UPDATE：对指定活动行加写锁，串行化同一活动的并发写者
     * （消费者扣减、超时回补、下线/结束归还均先持此锁，保证库存账面串行变更）
     * 调用方须在事务内使用，锁在事务提交/回滚后释放。
     *
     * @param id 活动ID
     * @return 活动实体（含最新 available_stock），未找到时返回 null
     */
    SeckillActivityDO selectForUpdate(@Param("id") Long id);

    /**
     * 乐观锁扣减 available_stock
     * 仿 ProductMapper.decreaseStock，防超卖的最后一道 DB 防线
     *
     * @param id       活动ID
     * @param quantity 扣减数量
     * @return 影响行数：1=成功，0=库存不足或记录不存在
     */
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 回补 available_stock（超时取消或消费者失败回补时调用）
     *
     * @param id       活动ID
     * @param quantity 回补数量
     */
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 查询已结束且预留库存尚未归还的上线活动（结束归还 Job 扫描用）
     *
     * @param now 当前时间
     */
    List<SeckillActivityDO> findEndedUnreclaimed(@Param("now") LocalDateTime now);
}
