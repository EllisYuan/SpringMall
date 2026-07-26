package site.geekie.shop.shoppingmall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.geekie.shop.shoppingmall.annotation.CurrentUserId;
import site.geekie.shop.shoppingmall.annotation.RateLimiter;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.dto.SeckillOrderDTO;
import site.geekie.shop.shoppingmall.service.SeckillService;
import site.geekie.shop.shoppingmall.util.IpUtils;
import site.geekie.shop.shoppingmall.vo.SeckillActivityVO;
import site.geekie.shop.shoppingmall.vo.SeckillOrderVO;
import site.geekie.shop.shoppingmall.vo.SeckillResultVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀控制器（用户端）
 *
 * 基础路径：/api/v1/seckill，所有接口需要 USER 角色（JWT 认证）
 *
 * SSE 说明：/activities/{id}/stream 返回 text/event-stream 流（非 Result 包装）。
 * 原生 EventSource 无法携带 Authorization 头，前端须使用 fetch-event-source
 * （如 @microsoft/fetch-event-source）以标准 Bearer 头建连。
 */
@Tag(name = "Seckill", description = "秒杀接口")
@RestController
@RequestMapping("/api/v1/seckill")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Validated
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "获取秒杀活动列表（进行中或即将开始）")
    @GetMapping("/activities")
    public Result<List<SeckillActivityVO>> listActivities() {
        return Result.success(seckillService.listActiveActivities());
    }

    @Operation(summary = "获取秒杀活动详情（含实时状态与服务器时间）")
    @GetMapping("/activities/{id}")
    public Result<SeckillActivityVO> getActivityDetail(@PathVariable Long id) {
        return Result.success(seckillService.getActivityDetail(id));
    }

    @Operation(summary = "参与秒杀（发起抢购，立即返回排队中，结果经 SSE 推送）")
    @RateLimiter(count = 5, period = 10)
    @PostMapping("/activities/{id}/orders")
    public Result<SeckillResultVO> participate(
            @PathVariable Long id,
            @Valid @RequestBody SeckillOrderDTO request,
            @Parameter(hidden = true) @CurrentUserId Long userId,
            HttpServletRequest httpRequest) {
        request.setActivityId(id);
        request.setUserId(userId);
        String clientIp = IpUtils.getClientIp(httpRequest);
        return Result.success(seckillService.seckill(request, clientIp));
    }

    @Operation(summary = "订阅抢购结果 SSE 流（消费者完成后推送最终结果；需以 Bearer 头建连）")
    @GetMapping(value = "/activities/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeckillResult(
            @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        return seckillService.subscribeResult(id, userId);
    }

    @Operation(summary = "查询抢购结果（SSE 未建立或断线时的一次性降级查询）")
    @GetMapping("/activities/{id}/result")
    public Result<SeckillResultVO> getSeckillResult(
            @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        return Result.success(seckillService.getSeckillResult(id, userId));
    }

    @Operation(summary = "查询我的秒杀订单列表")
    @GetMapping("/orders")
    public Result<PageResult<SeckillOrderVO>> getMySeckillOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        return Result.success(seckillService.getMySeckillOrders(userId, page, size));
    }

    @Operation(summary = "获取服务器当前时间（倒计时校准基准）")
    @GetMapping("/time")
    public Result<LocalDateTime> getServerTime() {
        return Result.success(LocalDateTime.now());
    }
}
