package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import site.geekie.shop.shoppingmall.annotation.SensitiveField;
import site.geekie.shop.shoppingmall.annotation.SensitiveType;

@Data
public class ResetPasswordDTO {
    @NotBlank(message = "请先完成验证")
    private String verificationToken;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20位")
    @SensitiveField(SensitiveType.PASSWORD)
    private String newPassword;
}
