package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpDTO {
    @NotBlank(message = "目标地址不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码必须为6位")
    private String code;

    @NotBlank(message = "用途不能为空")
    private String purpose;  // "register" | "reset_password"
}
