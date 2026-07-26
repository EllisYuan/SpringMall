package site.geekie.shop.shoppingmall.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.dto.SeckillActivityDTO;
import site.geekie.shop.shoppingmall.service.SeckillService;
import site.geekie.shop.shoppingmall.vo.SeckillActivityVO;

/**
 * 管理员秒杀活动管理接口
 *
 * 基础路径：/api/v1/admin/seckill，所有接口需要 ADMIN 角色
 *
 * 生命周期：创建（草稿）→ 上线（预留商品库存 + 自动预热）→ 下线（归还未售库存）→ 删除（仅草稿/下线态）
 * 手动重新预热用于已上线活动开抢前刷新 Redis 库存快照的兜底场景（上线时已自动预热）。
 */
@Tag(name = "AdminSeckill", description = "管理员秒杀活动管理接口")
@RestController
@RequestMapping("/api/v1/admin/seckill")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "分页查询秒杀活动列表（含草稿/下线状态）")
    @GetMapping("/activities")
    public Result<PageResult<SeckillActivityVO>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size) {
        return Result.success(seckillService.adminListActivities(page, size));
    }

    @Operation(summary = "获取秒杀活动详情")
    @GetMapping("/activities/{id}")
    public Result<SeckillActivityVO> getActivityDetail(@PathVariable Long id) {
        return Result.success(seckillService.adminGetActivityDetail(id));
    }

    @Operation(summary = "创建秒杀活动（默认草稿态，上线走 activate 接口）")
    @PostMapping("/activities")
    public Result<SeckillActivityVO> createActivity(@Valid @RequestBody SeckillActivityDTO request) {
        return Result.success(seckillService.createActivity(request));
    }

    @Operation(summary = "更新秒杀活动（仅草稿/下线态允许编辑）")
    @PutMapping("/activities/{id}")
    public Result<SeckillActivityVO> updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody SeckillActivityDTO request) {
        return Result.success(seckillService.updateActivity(id, request));
    }

    @Operation(summary = "上线活动（同事务预留商品库存，提交后自动完成 Redis 预热与闸门初始化）")
    @PostMapping("/activities/{id}/activate")
    public Result<SeckillActivityVO> activateActivity(@PathVariable Long id) {
        return Result.success(seckillService.activateActivity(id));
    }

    @Operation(summary = "下线活动（归还未售库存回商品库存，清活动 Redis 键）")
    @PostMapping("/activities/{id}/offline")
    public Result<SeckillActivityVO> offlineActivity(@PathVariable Long id) {
        return Result.success(seckillService.offlineActivity(id));
    }

    @Operation(summary = "删除秒杀活动（物理删除，仅草稿/下线态允许）")
    @DeleteMapping("/activities/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        seckillService.deleteActivity(id);
        return Result.success();
    }

    @Operation(summary = "手动重新预热（以最新可用库存覆盖写 Redis 分桶并重置闸门）")
    @PostMapping("/activities/{id}/preheat")
    public Result<Void> preheatActivity(@PathVariable Long id) {
        seckillService.preheatActivity(id);
        return Result.success();
    }
}
