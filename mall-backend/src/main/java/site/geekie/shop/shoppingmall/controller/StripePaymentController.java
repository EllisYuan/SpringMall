package site.geekie.shop.shoppingmall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.geekie.shop.shoppingmall.annotation.CurrentUserId;
import site.geekie.shop.shoppingmall.annotation.RateLimiter;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.dto.CreateStripePaymentDTO;
import site.geekie.shop.shoppingmall.dto.StripeRefundDTO;
import site.geekie.shop.shoppingmall.service.StripeService;
import site.geekie.shop.shoppingmall.vo.StripePaymentVO;
import site.geekie.shop.shoppingmall.vo.StripeRefundVO;

/**
 * Stripe 支付控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe 支付", description = "Stripe 支付相关接口")
public class StripePaymentController {

    private final StripeService stripeService;

    @PostMapping("/create")
    @Operation(summary = "创建 Stripe 支付", description = "创建 Stripe 支付意图，返回 client_secret 用于前端确认支付")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(count = 10, period = 60)
    public Result<StripePaymentVO> createPayment(
            @Valid @RequestBody CreateStripePaymentDTO request,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        StripePaymentVO payment = stripeService.createStripe(request.getOrderNo(), userId);
        return Result.success("创建支付成功", payment);
    }

    @GetMapping("/{paymentNo}")
    @Operation(summary = "查询 Stripe 支付状态", description = "根据支付编号查询支付状态")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('USER')")
    public Result<StripePaymentVO> queryPayment(
            @PathVariable String paymentNo,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        StripePaymentVO payment = stripeService.queryPayment(paymentNo, userId);
        return Result.success(payment);
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe Webhook 回调", description = "处理 Stripe 支付事件回调（payment_intent.succeeded 等）")
    @RateLimiter(count = 50, period = 60, key = "stripe_webhook")
    public String handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        try {
            stripeService.handleWebhook(payload, signature);
            return "success";
        } catch (Exception e) {
            log.error("Webhook 处理失败: {}", e.getMessage());
            // Stripe 要求返回 200，避免重试
            return "success";
        }
    }

    @PostMapping("/refund")
    @Operation(summary = "创建 Stripe 退款", description = "管理员创建退款（全额/部分退款）")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimiter(count = 20, period = 60)
    public Result<StripeRefundVO> createRefund(
            @Valid @RequestBody StripeRefundDTO request,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        StripeRefundVO refund = stripeService.createRefund(request, userId);
        return Result.success("退款申请成功", refund);
    }

    @PostMapping("/refund/webhook")
    @Operation(summary = "Stripe 退款 Webhook 回调", description = "处理 Stripe 退款事件回调（charge.refunded）")
    @RateLimiter(count = 50, period = 60, key = "stripe_refund_webhook")
    public String handleRefundWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        try {
            stripeService.handleRefundWebhook(payload, signature);
            return "success";
        } catch (Exception e) {
            log.error("退款 Webhook 处理失败: {}", e.getMessage());
            return "success";
        }
    }
}
