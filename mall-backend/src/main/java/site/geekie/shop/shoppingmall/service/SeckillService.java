package site.geekie.shop.shoppingmall.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.dto.SeckillActivityDTO;
import site.geekie.shop.shoppingmall.dto.SeckillOrderDTO;
import site.geekie.shop.shoppingmall.vo.SeckillActivityVO;
import site.geekie.shop.shoppingmall.vo.SeckillOrderVO;
import site.geekie.shop.shoppingmall.vo.SeckillResultVO;

import java.util.List;

/**
 * 秒杀业务接口
 */
public interface SeckillService {

    // ======================== 用户端 ========================

    /**
     * 查询进行中或即将开始的活动列表
     */
    List<SeckillActivityVO> listActiveActivities();

    /**
     * 查询活动详情（含实时状态与倒计时基准时间）
     * 仅返回已上线（status=1）活动；草稿/下线活动与不存在同响应，不泄露存在性
     *
     * @param activityId 活动ID
     */
    SeckillActivityVO getActivityDetail(Long activityId);

    /**
     * 发起秒杀抢购
     * 活动校验 → 频率计数 → 风控 step-up → 总量闸门 → 售罄快失败
     * → Redis 分桶原子预扣 → MQ 异步落单 → 立即返回排队中
     *
     * @param request  包含 activityId、userId、quantity、skuId、addressId、cfToken（可选）
     * @param clientIp 客户端 IP（用于风险计数和 Turnstile 验证）
     * @return 抢购结果（QUEUING 表示已入队，最终结果经 SSE 推送）
     */
    SeckillResultVO seckill(SeckillOrderDTO request, String clientIp);

    /**
     * 查询抢购结果（SSE 未建立或连接中断时的降级一次性查询兜底）
     *
     * @param activityId 活动ID
     * @param userId     当前用户ID
     * @return QUEUING / SUCCESS（含订单号）/ FAIL（含原因）/ NOT_FOUND
     */
    SeckillResultVO getSeckillResult(Long activityId, Long userId);

    /**
     * 订阅抢购结果 SSE 流
     * 注册当前用户的 SseEmitter；若结果已是最终态（SUCCESS/FAIL）则立即推送并关闭连接
     *
     * @param activityId 活动ID
     * @param userId     当前用户ID
     */
    SseEmitter subscribeResult(Long activityId, Long userId);

    /**
     * 查询用户秒杀订单列表（"我的秒杀订单"）
     */
    PageResult<SeckillOrderVO> getMySeckillOrders(Long userId, int page, int size);

    // ======================== 管理端 ========================

    /**
     * 管理后台查询活动详情（不过滤 status，草稿/下线活动均可查看）
     */
    SeckillActivityVO adminGetActivityDetail(Long activityId);

    /**
     * 管理后台分页查询活动列表（含下线状态）
     */
    PageResult<SeckillActivityVO> adminListActivities(int page, int size);

    /**
     * 创建秒杀活动（含业务校验，默认草稿态 status=0）
     */
    SeckillActivityVO createActivity(SeckillActivityDTO dto);

    /**
     * 更新秒杀活动（仅草稿/下线态允许编辑）
     */
    SeckillActivityVO updateActivity(Long activityId, SeckillActivityDTO dto);

    /**
     * 上线活动：同事务从商品库存预留 seckill_stock（联动商品 DB/Redis 库存，
     * 不足整体回滚），提交后同步完成 Redis 分桶预热 + 闸门初始化
     */
    SeckillActivityVO activateActivity(Long activityId);

    /**
     * 下线活动：归还未售 available_stock 回商品库存（幂等），清活动 Redis 键
     */
    SeckillActivityVO offlineActivity(Long activityId);

    /**
     * 删除活动（物理删除，仅草稿/下线态允许）
     */
    void deleteActivity(Long activityId);

    /**
     * 手动重新预热：以最新 available_stock 覆盖写 Redis 分桶 + 初始化闸门
     * （上线动作内部同步预热复用同一逻辑；此接口供开抢前刷新库存快照兜底）
     */
    void preheatActivity(Long activityId);

    // ======================== 结束归还（Job 调用） ========================

    /**
     * 查询已结束且预留库存尚未归还的活动ID列表（结束归还 Job 扫描用）
     */
    List<Long> listEndedUnreclaimedIds();

    /**
     * 归还单个活动的未售库存回商品库存（幂等，可重跑）
     * 供结束归还 Job 逐个调用，单活动独立事务
     */
    void reclaimActivityStock(Long activityId);
}
