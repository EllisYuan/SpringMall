package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import site.geekie.shop.shoppingmall.annotation.SensitiveField;
import site.geekie.shop.shoppingmall.annotation.SensitiveType;

/**
 * 用户登录请求DTO
 * 支持用户名或邮箱双模式登录
 *
 */
@Data
public class LoginDTO {

    /**
     * 账号（用户名或邮箱）
     * 验证规则：不能为空
     */
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 密码
     * 验证规则：不能为空
     */
    @NotBlank(message = "密码不能为空")
    @SensitiveField(SensitiveType.PASSWORD)
    private String password;

    /**
     * Cloudflare Turnstile 前端挑战 token
     * 验证规则：不能为空
     */
    @NotBlank(message = "人机验证不能为空")
    private String cfToken;
}
