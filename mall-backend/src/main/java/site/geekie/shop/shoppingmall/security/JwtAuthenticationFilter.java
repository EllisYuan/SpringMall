package site.geekie.shop.shoppingmall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.util.TokenBlacklistService;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 在每个HTTP请求中验证JWT Token，并设置Spring Security认证上下文
 *
 * 工作流程：
 *   - 从请求头中提取JWT Token
 *   - 解析Token获取用户名
 *   - 加载用户详情
 *   - 验证Token有效性
 *   - 设置Security认证上下文
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // JWT Token提供者
    private final JwtTokenProvider tokenProvider;

    // 用户详情服务
    private final UserDetailsService userDetailsService;

    // JSON 序列化器，用于写出 401 响应体
    private final ObjectMapper objectMapper;

    // Token 黑名单服务（Redis）
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 过滤器内部处理逻辑
     * 每个请求执行一次
     *
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求中提取JWT Token
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // 检查 token 是否在黑名单中
                if (tokenBlacklistService.isBlacklisted(jwt)) {
                    log.debug("JWT token 已被列入黑名单");
                    sendUnauthorizedResponse(response, "登录凭证已失效，请重新登录");
                    return;
                }

                // 从Token中获取用户名
                String username = tokenProvider.getUsernameFromToken(jwt);

                // 检查用户是否被强制登出
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                long issuedAt = tokenProvider.getIssuedAtFromToken(jwt);
                if (tokenBlacklistService.isForceLoggedOut(userId, issuedAt)) {
                    log.debug("用户 {} 已被强制登出，token 签发于强制登出之前", userId);
                    sendUnauthorizedResponse(response, "账号已被强制下线，请重新登录");
                    return;
                }

                // 加载用户详情
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 验证Token
                SecurityUser securityUser = (SecurityUser) userDetails;
                if (tokenProvider.validateToken(jwt, securityUser.getUser())) {
                    // 创建认证对象
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置到Security上下文
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token 已过期: {}", ex.getMessage());
            sendUnauthorizedResponse(response, "登录已过期，请重新登录");
            return;
        } catch (JwtException ex) {
            log.warn("无效的 JWT token: {}", ex.getMessage());
            sendUnauthorizedResponse(response, "无效的登录凭证");
            return;
        } catch (Exception ex) {
            log.error("JWT 认证过程发生意外错误", ex);
            // 非 JWT 异常不拦截，交给 AuthenticationEntryPoint 兜底
        }

        // 继续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 直接向响应写出 HTTP 401 + Result JSON，并短路过滤器链。
     *
     * @param response HTTP 响应
     * @param message  返回给前端的错误消息
     * @throws IOException 写响应时的 IO 异常
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED, message);
        objectMapper.writeValue(response.getWriter(), result);
    }

    /**
     * 从HTTP请求中提取JWT Token
     * Token格式：Authorization: Bearer <token>
     *
     * @param request HTTP请求
     * @return JWT Token字符串，如果不存在则返回null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
