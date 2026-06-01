package site.geekie.shop.shoppingmall.service;

import site.geekie.shop.shoppingmall.dto.ForgotPasswordDTO;
import site.geekie.shop.shoppingmall.dto.LoginDTO;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;
import site.geekie.shop.shoppingmall.dto.ResetPasswordDTO;
import site.geekie.shop.shoppingmall.dto.SendOtpDTO;
import site.geekie.shop.shoppingmall.dto.VerifyOtpDTO;
import site.geekie.shop.shoppingmall.vo.LoginVO;

/**
 * 认证服务接口
 * 提供用户注册和登录功能
 *
 * 主要功能：
 *   - 用户注册：验证用户信息唯一性，创建新用户账户
 *   - 用户登录：验证用户凭证，生成JWT令牌
 *
 */
public interface AuthService {

    /**
     * 用户注册
     * 验证用户信息唯一性，创建新用户账户，并直接颁发 JWT 实现自动登录
     *
     * @param request 注册请求，包含用户名、密码、邮箱等信息
     * @return 登录响应，包含 JWT 令牌和用户信息（与 login 同构，用于注册后免登录直达）
     * @throws site.geekie.shop.shoppingmall.exception.BusinessException
     *         当用户名、邮箱或手机号已存在时抛出
     */
    LoginVO register(RegisterDTO request);

    /**
     * 用户登录
     * 验证用户凭证，生成并返回JWT访问令牌
     *
     * @param request 登录请求，包含用户名和密码
     * @return 登录响应，包含JWT令牌和用户信息
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         当用户名或密码错误时抛出
     */
    LoginVO login(LoginDTO request);

    /**
     * 用户登出
     * 将当前 token 加入黑名单，并清除该用户的认证缓存。
     * 无效 token 也正常返回（幂等）。
     *
     * @param token 当前请求携带的 JWT token（不含 "Bearer " 前缀）
     */
    void logout(String token);

    /**
     * 发送 OTP 验证码
     * 根据 type（email/sms）向 target 发送验证码；注册场景校验目标未被占用，密码找回场景校验用户存在。
     *
     * @param dto 包含 target、type、purpose
     */
    void sendOtp(SendOtpDTO dto);

    /**
     * 校验 OTP 并颁发短期 verificationToken
     * OTP 通过后生成 UUID token 存入 Redis（TTL 10 分钟），用于注册/密码找回的二次凭证。
     *
     * @param dto 包含 target、code、purpose
     * @return verificationToken 字符串
     */
    String verifyOtpAndGetToken(VerifyOtpDTO dto);

    /**
     * 忘记密码 — 发送重置验证码
     *
     * @param dto 包含 target、type
     */
    void forgotPassword(ForgotPasswordDTO dto);

    /**
     * 重置密码
     * 校验 OTP 后直接重置密码，并强制该用户下线。
     *
     * @param dto 包含 target、code、newPassword
     */
    void resetPassword(ResetPasswordDTO dto);
}
