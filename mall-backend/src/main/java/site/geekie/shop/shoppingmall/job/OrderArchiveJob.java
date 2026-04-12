package site.geekie.shop.shoppingmall.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.entity.OrderDO;
import site.geekie.shop.shoppingmall.entity.OrderItemDO;
import site.geekie.shop.shoppingmall.mapper.OrderArchiveMapper;
import site.geekie.shop.shoppingmall.mapper.OrderItemArchiveMapper;
import site.geekie.shop.shoppingmall.mapper.OrderItemMapper;
import site.geekie.shop.shoppingmall.mapper.OrderMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单归档定时任务
 * 每天凌晨2点将3个月前的已完成/已取消订单迁移到归档表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderArchiveJob {

    private static final int BATCH_SIZE = 500;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderArchiveMapper orderArchiveMapper;
    private final OrderItemArchiveMapper orderItemArchiveMapper;

    /**
     * 每天凌晨2点执行归档（由 XXL-JOB 调度，Handler: orderArchiveJobHandler）
     * 分批处理，每批500条，避免长事务和锁表
     */
    @XxlJob("orderArchiveJobHandler")
    public void archiveOldOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(3);
        int totalArchived = 0;

        XxlJobHelper.log("开始归档订单，阈值时间: {}", threshold);
        log.info("开始归档订单，阈值时间: {}", threshold);

        while (true) {
            try {
                int archived = archiveBatch(threshold);
                if (archived == 0) break;
                totalArchived += archived;
                XxlJobHelper.log("累计已归档 {} 条", totalArchived);
            } catch (Exception e) {
                log.error("归档批次执行失败，已归档 {} 条", totalArchived, e);
                XxlJobHelper.handleFail("归档中断，已归档 " + totalArchived + " 条: " + e.getMessage());
                return;
            }
        }

        XxlJobHelper.log("归档完成，共归档 {} 条订单", totalArchived);
        log.info("归档完成，共归档 {} 条订单", totalArchived);
    }

    /**
     * 归档一批订单（独立事务，失败不影响已归档数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public int archiveBatch(LocalDateTime threshold) {
        // 1. 查询一批待归档订单
        List<OrderDO> batch = orderMapper.findArchiveCandidates(threshold, BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }

        List<Long> orderIds = batch.stream().map(OrderDO::getId).collect(Collectors.toList());

        // 2. 归档订单明细
        List<OrderItemDO> items = orderItemMapper.findByOrderIds(orderIds);
        if (!items.isEmpty()) {
            orderItemArchiveMapper.batchInsert(items);
            orderItemMapper.deleteByOrderIds(orderIds);
        }

        // 3. 归档订单主表
        orderArchiveMapper.batchInsert(batch);
        orderMapper.deleteByIds(orderIds);

        log.debug("本批归档 {} 条订单", batch.size());
        return batch.size();
    }
}
