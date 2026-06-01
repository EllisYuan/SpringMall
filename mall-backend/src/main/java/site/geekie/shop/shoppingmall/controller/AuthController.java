package site.geekie.shop.shoppingmall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import site.geekie.shop.shoppingmall.annotation.RateLimiter;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.dto.ForgotPasswordDTO;
import site.geekie.shop.shoppingmall.dto.LoginDTO;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;
import site.geekie.shop.shoppingmall.dto.ResetPasswordDTO;
import site.geekie.shop.shoppingmall.dto.SendOtpDTO;
import site.geekie.shop.shoppingmall.dto.VerifyOtpDTO;
import site.geekie.shop.shoppingmall.service.AuthService;
import site.geekie.shop.shoppingmall.vo.LoginVO;

/**
 * 认证控制器
 * 处理用户注册、登录、登出等认证相关接口
 *
 * 接口路径前缀：/api/v1/auth
 */
@Tag(name = "Authentication", description = "认证接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册接口
     * 注册成功后直接颁发 JWT，实现注册即登录（前端无需再调用 login）
     *
     * @param request 注册请求，包含用户名、密码、邮箱等信息
     * @return 包含 JWT 令牌和用户信息的统一响应对象
     */
    @Operation(summary = "用户注册")
    @RateLimiter(count = 5, period = 60)
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO request) {
        LoginVO response = authService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录接口
     *
     * @param request 登录请求，包含用户名和密码
     * @return 包含JWT令牌和用户信息的统一响应对象
     */
    @Operation(summary = "用户登录")
    @RateLimiter(count = 5, period = 60)
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        LoginVO response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 用户登出接口
     * 将当前 token 加入黑名单并清除认证缓存。
     * 无效 token 也返回成功（幂等设计）。
     *
     * @param httpRequest HTTP请求，用于提取 Authorization header
     * @return 统一响应对象
     */
    @Operation(summary = "用户登出")
    @RateLimiter(count = 5, period = 60)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        String token = extractToken(httpRequest);
        authService.logout(token);
        return Result.success("登出成功", null);
    }

    /**
     * 发送 OTP 验证码
     */
    @Operation(summary = "发送OTP验证码")
    @RateLimiter(count = 5, period = 60)
    @PostMapping("/send-otp")
    public Result<Void> sendOtp(@Valid @RequestBody SendOtpDTO otpdto) {
        authService.sendOtp(otpdto);
        return Result.success("验证码已发送", null);
    }

    /**
     * 校验 OTP 并获取 verificationToken
     */
    @Operation(summary = "校验OTP验证码并获取凭证")
    @RateLimiter(count = 5, period = 60)
    @PostMapping("/verify-otp")
    public Result<String> verifyOtp(@Valid @RequestBody VerifyOtpDTO dto) {
        String token = authService.verifyOtpAndGetToken(dto);
        return Result.success(token);
    }

    /**
     * 忘记密码 — 发送重置验证码
     */
    @Operation(summary = "忘记密码-发送验证码")
    @RateLimiter(count = 3, period = 60)
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        authService.forgotPassword(dto);
        return Result.success("验证码已发送", null);
    }

    /**
     * 重置密码
     */
    @Operation(summary = "重置密码")
    @RateLimiter(count = 3, period = 60)
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success("密码重置成功", null);
    }

    /**
     * 从请求头提取 JWT token（去掉 "Bearer " 前缀）
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
