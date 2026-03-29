package site.geekie.shop.shoppingmall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import site.geekie.shop.shoppingmall.dto.LoginDTO;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;
import site.geekie.shop.shoppingmall.entity.UserDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.UserMapper;
import site.geekie.shop.shoppingmall.security.JwtTokenProvider;
import site.geekie.shop.shoppingmall.security.SecurityUser;
import site.geekie.shop.shoppingmall.service.AuthService;
import site.geekie.shop.shoppingmall.util.TokenBlacklistService;
import site.geekie.shop.shoppingmall.util.UserAuthCacheService;
import site.geekie.shop.shoppingmall.vo.LoginVO;
import site.geekie.shop.shoppingmall.vo.UserVO;

import java.util.Date;

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

    /**
     * 用户注册
     * 验证用户信息唯一性后创建新用户账户
     *
     * @param request 注册请求
     * @throws BusinessException 当用户名、邮箱或手机号已存在时抛出
     */
    @Override
    @Transactional
    @LogOperation(value = "用户注册", module = "认证")
    public void register(RegisterDTO request) {
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        if (request.getPhone() != null && userMapper.findByPhone(request.getPhone()) != null) {
            throw new BusinessException(ResultCode.PHONE_ALREADY_EXISTS);
        }

        UserDO user = userConverter.toDO(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus(1);

        userMapper.insert(user);
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
            log.info("【登录尝试】用户名: {}", request.getUsername());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            log.info("【认证成功】用户名: {}", request.getUsername());

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            UserDO user = securityUser.getUser();

            // 生成JWT令牌（包含 id + username + email + createdAt）
            String token = tokenProvider.generateToken(user);
            UserVO userResponse = userConverter.toVO(user);

            // 登录成功后回填认证缓存
            userAuthCacheService.putUser(user);

            log.info("【登录成功】用户名: {}，JWT Token 已生成", request.getUsername());
            log.debug("【JWT Token】{}", token);

            return new LoginVO(token, userResponse);

        } catch (BadCredentialsException e) {
            UserDO user = userMapper.findByUsername(request.getUsername());
            if (user == null) {
                log.warn("【登录失败】用户名: {}，原因: 账号不存在，异常信息: {}",
                        request.getUsername(), e.getMessage());
            } else {
                log.warn("【登录失败】用户名: {}，原因: 密码错误，异常信息: {}",
                        request.getUsername(), e.getMessage());
            }
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);

        } catch (AuthenticationException e) {
            String exceptionType = e.getClass().getSimpleName();
            log.warn("【登录失败】用户名: {}，异常类型: {}，原因: {}，详细信息: {}",
                    request.getUsername(), exceptionType, e.getMessage(), e.toString());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);

        } catch (Exception e) {
            log.error("【系统错误】用户登录异常，用户名: {}，异常类型: {}，详细信息: {}",
                    request.getUsername(), e.getClass().getSimpleName(), e.getMessage(), e);
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
}
