package site.geekie.shop.shoppingmall.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.annotation.LogOperation;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.converter.UserConverter;
import site.geekie.shop.shoppingmall.dto.ForgotPasswordDTO;
import site.geekie.shop.shoppingmall.dto.LoginDTO;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;
import site.geekie.shop.shoppingmall.dto.ResetPasswordDTO;
import site.geekie.shop.shoppingmall.dto.SendOtpDTO;
import site.geekie.shop.shoppingmall.dto.VerifyOtpDTO;
import site.geekie.shop.shoppingmall.entity.UserDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.UserMapper;
import site.geekie.shop.shoppingmall.security.JwtTokenProvider;
import site.geekie.shop.shoppingmall.security.SecurityUser;
import site.geekie.shop.shoppingmall.service.AuthService;
import site.geekie.shop.shoppingmall.service.TurnstileService;
import site.geekie.shop.shoppingmall.service.TwilioVerifyService;
import site.geekie.shop.shoppingmall.util.TokenBlacklistService;
import site.geekie.shop.shoppingmall.util.UserAuthCacheService;
import site.geekie.shop.shoppingmall.vo.LoginVO;
import site.geekie.shop.shoppingmall.vo.UserVO;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类
 * 实现用户注册、登录、登出的业务逻辑
 *
 * 核心功能：
 *   - 用户注册：验证唯一性约束，加密密码，创建用户账户
 *   - 用户登录：Spring Security认证，生成JWT令牌，回填认证缓存
 *   - 用户登出：token 加黑名单，清除认证缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserConverter userConverter;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserAuthCacheService userAuthCacheService;
    private final TurnstileService turnstileService;
    private final HttpServletRequest httpServletRequest;

    @Autowired
    private TwilioVerifyService twilioVerifyService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String VERIFY_TOKEN_PREFIX = "auth:verify-token:";
    private static final long VERIFY_TOKEN_TTL_MINUTES = 10;

    /**
     * 用户注册
     * 验证用户信息唯一性后创建新用户账户，并直接颁发 JWT 实现注册即登录
     *
     * @param request 注册请求
     * @return 登录响应，包含 JWT 令牌和用户信息
     * @throws BusinessException 当用户名、邮箱或手机号已存在时抛出
     */
    @Override
    @Transactional
    @LogOperation(value = "用户注册", module = "认证")
    public LoginVO register(RegisterDTO request) {
        // 人机验证（在业务逻辑之前执行）
        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        String clientIp = (xForwardedFor != null && !xForwardedFor.isBlank())
                ? xForwardedFor.split(",")[0].trim()
                : httpServletRequest.getRemoteAddr();
        turnstileService.verify(request.getCfToken(), clientIp);

        // 验证 OTP verificationToken
        String redisKey = VERIFY_TOKEN_PREFIX + request.getVerificationToken();
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisValue == null) {
            throw new BusinessException(ResultCode.VERIFY_TOKEN_INVALID);
        }
        // 格式: "target:purpose"
        String[] parts = redisValue.split(":", 2);
        if (parts.length != 2 || !"register".equals(parts[1])) {
            throw new BusinessException(ResultCode.VERIFY_TOKEN_INVALID);
        }
        String verifiedTarget = parts[0];
        // 验证 target 与表单中的 email 或 phone 一致（邮箱忽略大小写）
        boolean targetMatches = verifiedTarget.equalsIgnoreCase(request.getEmail()) || verifiedTarget.equals(request.getPhone());
        if (!targetMatches) {
            throw new BusinessException(ResultCode.VERIFY_TOKEN_INVALID);
        }
        // 验证通过后删除（防重用）
        stringRedisTemplate.delete(redisKey);

        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        if (request.getPhone() != null && userMapper.findByPhone(request.getPhone()) != null) {
            throw new BusinessException(ResultCode.PHONE_ALREADY_EXISTS);
        }

        UserDO user = userConverter.toDO(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus(1);
        // 显式设置创建/更新时间，保证 insert 后内存对象与 DB 行一致
        // （insert 仅回填自增 id，不会回读 DB 生成的时间字段，否则注册即登录生成 JWT 时 createdAt 为 null）
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        userMapper.insert(user);

        // 注册即登录：颁发 JWT 并回填认证缓存（与 login() 流程一致）
        String token = tokenProvider.generateToken(user);
        UserVO userResponse = userConverter.toVO(user);
        userAuthCacheService.putUser(user);

        log.info("【注册成功】账号: {}，JWT Token 已生成", user.getUsername());
        return new LoginVO(token, userResponse);
    }

    /**
     * 用户登录
     * 通过Spring Security验证用户凭证并生成JWT令牌
     *
     * @param request 登录请求
     * @return 登录响应，包含JWT令牌和用户信息
     * @throws BusinessException 当用户名或密码错误时抛出
     */
    @Override
    @LogOperation(value = "用户登录", module = "认证")
    public LoginVO login(LoginDTO request) {
        try {
            log.info("【登录尝试】账号: {}", request.getAccount());

            // 人机验证（在密码校验之前执行）
            // 优先取 X-Forwarded-For 真实客户端 IP（Nginx 反代场景），回退到直连 IP
            String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
            String clientIp = (xForwardedFor != null && !xForwardedFor.isBlank())
                    ? xForwardedFor.split(",")[0].trim()
                    : httpServletRequest.getRemoteAddr();
            turnstileService.verify(request.getCfToken(), clientIp);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getAccount(),
                            request.getPassword()
                    )
            );

            log.info("【认证成功】账号: {}", request.getAccount());

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            UserDO user = securityUser.getUser();

            // 生成JWT令牌（包含 id + username + email + createdAt）
            String token = tokenProvider.generateToken(user);
            UserVO userResponse = userConverter.toVO(user);

            // 登录成功后回填认证缓存
            userAuthCacheService.putUser(user);

            log.info("【登录成功】账号: {}，JWT Token 已生成", request.getAccount());
            log.debug("【JWT Token】{}", token);

            return new LoginVO(token, userResponse);

        } catch (BadCredentialsException e) {
            UserDO user = userMapper.findByAccount(request.getAccount());
            if (user == null) {
                log.warn("【登录失败】账号: {}，原因: 账号不存在，异常信息: {}",
                        request.getAccount(), e.getMessage());
            } else {
                log.warn("【登录失败】账号: {}，原因: 密码错误，异常信息: {}",
                        request.getAccount(), e.getMessage());
            }
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);

        } catch (AuthenticationException e) {
            String exceptionType = e.getClass().getSimpleName();
            log.warn("【登录失败】账号: {}，异常类型: {}，原因: {}，详细信息: {}",
                    request.getAccount(), exceptionType, e.getMessage(), e.toString());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);

        } catch (BusinessException e) {
            // 透传业务异常（如 TURNSTILE_FAILED），不覆盖为 INTERNAL_SERVER_ERROR
            throw e;

        } catch (Exception e) {
            log.error("【系统错误】用户登录异常，账号: {}，异常类型: {}，详细信息: {}",
                    request.getAccount(), e.getClass().getSimpleName(), e.getMessage(), e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 用户登出
     * 将 token 加入黑名单并清除认证缓存，无效 token 也正常返回（幂等）
     *
     * @param token 当前请求携带的 JWT token（不含 "Bearer " 前缀）
     */
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Date expiresAt = tokenProvider.getExpirationDateFromToken(token);
            Long userId = tokenProvider.getUserIdFromToken(token);

            // 将 token 加入黑名单，TTL = 剩余有效期
            tokenBlacklistService.blacklist(token, expiresAt);
            // 清除该用户的认证缓存
            userAuthCacheService.evictUser(userId);

            log.info("【登出成功】userId: {}", userId);
        } catch (Exception e) {
            // token 无效（已过期/签名错误）时也正常返回，接口保持幂等
            log.debug("【登出】token 无效或已过期，忽略处理: {}", e.getMessage());
        }
    }

    /**
     * 发送 OTP 验证码
     * 注册场景：校验目标（邮箱/手机号）未被占用
     * 密码找回场景：校验用户存在（用户不存在时静默返回，不暴露枚举信息）
     */
    @Override
    public void sendOtp(SendOtpDTO dto) {
        String target = dto.getTarget();
        String type = dto.getType();
        String purpose = dto.getPurpose();

        // 根据 type 校验 target 格式，并对邮箱做大小写归一化
        if ("email".equals(type)) {
            if (!target.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new BusinessException(ResultCode.PARAM_INVALID);
            }
            target = target.toLowerCase();
            dto.setTarget(target);
        } else if ("sms".equals(type)) {
            // E.164 国际格式：+ 国家码（1-3位）+ 本地号（6-14位）
            if (!target.matches("^\\+[1-9]\\d{6,14}$")) {
                throw new BusinessException(ResultCode.PARAM_INVALID);
            }
        }

        if ("register".equals(purpose)) {
            if ("email".equals(type)) {
                if (userMapper.findByEmail(target) != null) {
                    throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
                }
            } else if ("sms".equals(type)) {
                if (userMapper.findByPhone(target) != null) {
                    throw new BusinessException(ResultCode.PHONE_ALREADY_EXISTS);
                }
            }
        } else if ("reset_password".equals(purpose)) {
            // 静默处理：不暴露用户是否存在，防止用户枚举攻击
            UserDO user;
            if ("email".equals(type)) {
                user = userMapper.findByEmail(target);
            } else {
                user = userMapper.findByPhone(target);
            }
            if (user == null) {
                log.info("sendOtp reset_password: target not found, silently returning");
                return;
            }
        }

        // target 维度限流：每小时最多 5 次，每天最多 15 次
        checkOtpRateLimit(target);

        twilioVerifyService.sendOtp(target, "email".equals(type) ? "email" : "sms");
    }

    /**
     * 校验 OTP 并颁发短期 verificationToken
     * OTP 通过后生成 UUID token 存入 Redis（TTL 10 分钟）
     */
    @Override
    public String verifyOtpAndGetToken(VerifyOtpDTO dto) {
        boolean approved = twilioVerifyService.verifyOtp(dto.getTarget(), dto.getCode());
        if (!approved) {
            throw new BusinessException(ResultCode.OTP_INVALID);
        }
        String token = UUID.randomUUID().toString();
        String redisValue = dto.getTarget() + ":" + dto.getPurpose();
        stringRedisTemplate.opsForValue().set(
                VERIFY_TOKEN_PREFIX + token,
                redisValue,
                VERIFY_TOKEN_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return token;
    }

    /**
     * 忘记密码 — 发送重置验证码
     * 用户不存在时静默返回（不暴露用户是否注册，防止用户枚举攻击）
     */
    @Override
    public void forgotPassword(ForgotPasswordDTO dto) {
        String target = dto.getTarget();
        String type = dto.getType();
        UserDO user;
        if ("email".equals(type)) {
            user = userMapper.findByEmail(target.toLowerCase());
        } else {
            user = userMapper.findByPhone(target);
        }
        // 用户不存在时静默返回（不暴露用户是否注册）
        if (user == null) {
            log.info("forgotPassword: target not found, silently returning");
            return;
        }
        twilioVerifyService.sendOtp(target, "email".equals(type) ? "email" : "sms");
    }

    /**
     * 重置密码（二步流程第二步）
     * 通过 verificationToken（由 verifyOtpAndGetToken 颁发，purpose=reset_password）验证身份，
     * 验证通过后重置密码并强制该用户下线
     */
    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        // 从 Redis 验证 verificationToken
        String redisKey = VERIFY_TOKEN_PREFIX + dto.getVerificationToken();
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisValue == null) {
            throw new BusinessException(ResultCode.VERIFY_TOKEN_INVALID);
        }
        // 格式: "target:purpose"
        String[] parts = redisValue.split(":", 2);
        if (parts.length != 2 || !"reset_password".equals(parts[1])) {
            throw new BusinessException(ResultCode.VERIFY_TOKEN_INVALID);
        }
        String target = parts[0];
        // 删除 token（防重用）
        stringRedisTemplate.delete(redisKey);

        // 根据 target 查找用户（含 @ 视为邮箱，+ 开头视为 E.164 手机号）
        UserDO user;
        if (target.contains("@")) {
            user = userMapper.findByEmail(target.toLowerCase());
        } else {
            user = userMapper.findByPhone(target);
        }
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 更新密码并强制用户下线
        String encoded = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(user.getId(), encoded);
        userAuthCacheService.evictUser(user.getId());
        tokenBlacklistService.forceLogoutUser(user.getId());
    }

    /**
     * target 维度 OTP 发送限流
     * 每小时最多 5 次，每天最多 15 次
     */
    private void checkOtpRateLimit(String target) {
        String hourKey = "auth:otp-rate:hour:" + target;
        String dayKey  = "auth:otp-rate:day:"  + target;

        Long hourCount = stringRedisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1L) {
            stringRedisTemplate.expire(hourKey, 1, java.util.concurrent.TimeUnit.HOURS);
        }
        if (hourCount != null && hourCount > 5) {
            throw new BusinessException(ResultCode.OTP_TOO_FREQUENT);
        }

        Long dayCount = stringRedisTemplate.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1L) {
            stringRedisTemplate.expire(dayKey, 1, java.util.concurrent.TimeUnit.DAYS);
        }
        if (dayCount != null && dayCount > 15) {
            throw new BusinessException(ResultCode.OTP_TOO_FREQUENT);
        }
    }
}
