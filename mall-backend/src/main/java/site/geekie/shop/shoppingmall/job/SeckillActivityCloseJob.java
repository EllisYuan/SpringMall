package site.geekie.shop.shoppingmall.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.geekie.shop.shoppingmall.service.SeckillService;

import java.util.List;

/**
 * 秒杀活动结束归还定时任务
 * 扫描已过 endTime 且预留库存尚未归还的上线活动，将未售 available_stock 归还商品库存
 * 并置"已归还"幂等标记（重复执行不重复归还，可安全重跑）
 *
 * 建议调度频率：每分钟一次（与 XXL-Job 现有节奏对齐）
 * 兜底关系：管理员手动下线也会触发归还（同一幂等标记），Job 故障时可人工下线补救
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillActivityCloseJob {

    private final SeckillService seckillService;

    /**
     * 结束归还扫描（由 XXL-JOB 调度，Handler: seckillActivityCloseJobHandler）
     * 每个活动独立事务归还，单个失败不影响其余活动
     */
    @XxlJob("seckillActivityCloseJobHandler")
    public void reclaimEndedActivities() {
        List<Long> activityIds = seckillService.listEndedUnreclaimedIds();
        if (activityIds.isEmpty()) {
            XxlJobHelper.log("无待归还的已结束秒杀活动");
            return;
        }

        XxlJobHelper.log("扫描到 {} 个已结束待归还的秒杀活动: {}", activityIds.size(), activityIds);
        log.info("秒杀结束归还扫描 - 待归还活动数: {}", activityIds.size());

        int succeeded = 0;
        int failed = 0;
        for (Long activityId : activityIds) {
            try {
                seckillService.reclaimActivityStock(activityId);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("秒杀活动结束归还失败（下次调度重试）- activityId: {}", activityId, e);
                XxlJobHelper.log("活动 {} 归还失败: {}", activityId, e.getMessage());
            }
        }

        XxlJobHelper.log("秒杀结束归还完成 - 成功: {}, 失败: {}", succeeded, failed);
        if (failed > 0) {
            XxlJobHelper.handleFail("部分活动归还失败，失败数: " + failed);
        }
    }
}
