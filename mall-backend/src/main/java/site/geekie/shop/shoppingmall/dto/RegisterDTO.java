package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import site.geekie.shop.shoppingmall.annotation.SensitiveField;
import site.geekie.shop.shoppingmall.annotation.SensitiveType;
import site.geekie.shop.shoppingmall.validation.ContactRequired;

/**
 * 用户注册请求DTO
 * 包含用户注册所需的所有字段和验证规则
 *
 */
@Data
@ContactRequired
public class RegisterDTO {

    /**
     * 用户名
     * 验证规则：
     * - 不能为空
     * - 长度3-20个字符
     * - 只能包含字母、数字和下划线
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /**
     * 密码
     * 验证规则：
     * - 不能为空
     * - 长度6-20个字符
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    @SensitiveField(SensitiveType.PASSWORD)
    private String password;

    /**
     * 邮箱（可选，与手机号至少填写一项）
     * 验证规则：
     * - 如果提供，必须符合邮箱格式
     */
    @Email(message = "邮箱格式不正确")
    @SensitiveField(SensitiveType.EMAIL)
    private String email;

    /**
     * 手机号（可选，与邮箱至少填一项）
     * 使用 E.164 国际格式，例如 +8613800138000
     */
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "手机号请使用 E.164 国际格式，如 +8613800138000")
    @SensitiveField(SensitiveType.PHONE)
    private String phone;

    /**
     * Cloudflare Turnstile 前端挑战 token
     * 验证规则：不能为空
     */
    @NotBlank(message = "人机验证不能为空")
    private String cfToken;

    /**
     * OTP 验证成功后服务端颁发的短期凭证
     * 用于证明邮箱或手机号已通过验证
     */
    @NotBlank(message = "请先完成邮箱或手机验证")
    private String verificationToken;
}
