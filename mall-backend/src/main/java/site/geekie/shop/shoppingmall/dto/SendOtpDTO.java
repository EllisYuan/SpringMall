package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpDTO {
    @NotBlank(message = "目标地址不能为空")
    private String target;

    @NotBlank(message = "发送渠道不能为空")
    @Pattern(regexp = "^(email|sms)$", message = "发送渠道仅支持 email 或 sms")
    private String type;  // "email" | "sms"

    @NotBlank(message = "用途不能为空")
    @Pattern(regexp = "^(register|reset_password)$", message = "用途仅支持 register 或 reset_password")
    private String purpose;  // "register" | "reset_password"
}
