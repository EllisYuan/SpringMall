package site.geekie.shop.shoppingmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgotPasswordDTO {
    @NotBlank(message = "目标地址不能为空")
    private String target;

    @NotBlank(message = "发送渠道不能为空")
    @Pattern(regexp = "^(email|sms)$", message = "发送渠道仅支持 email 或 sms")
    private String type;  // "email" | "sms"
}
