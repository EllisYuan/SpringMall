package site.geekie.shop.shoppingmall.service.impl;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.service.TwilioVerifyService;

@Slf4j
@Service
public class TwilioVerifyServiceImpl implements TwilioVerifyService {

    @Value("${twilio.account-sid:mock}")
    private String accountSid;

    @Value("${twilio.auth-token:mock}")
    private String authToken;

    @Value("${twilio.verify.service-sid:mock}")
    private String serviceSid;

    @Value("${twilio.mock.enabled:false}")
    private boolean mockEnabled;

    @PostConstruct
    public void init() {
        if (mockEnabled) {
            log.warn("[Twilio Mock] Mock 模式已启用，所有 OTP 发送将跳过，验证码固定为 000000");
            return;
        }
        if ("mock".equals(accountSid) || "mock".equals(authToken)) {
            log.warn("[Twilio] 凭据未配置（TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN），发送 OTP 时将失败。如需本地调试，请设置 TWILIO_MOCK_ENABLED=true");
            return;
        }
        Twilio.init(accountSid, authToken);
        log.info("[Twilio] 初始化成功，serviceSid={}", serviceSid);
    }

    @Override
    public void sendOtp(String target, String channel) {
        if (mockEnabled) {
            log.warn("[Twilio Mock] OTP 发送跳过 — target={}, channel={}，请使用固定验证码 000000", target, channel);
            return;
        }
        try {
            Verification.creator(serviceSid, target, channel).create();
        } catch (ApiException e) {
            String detail = String.format("code=%d, status=%d, msg=%s", e.getCode(), e.getStatusCode(), e.getMessage());
            if (e.getCode() != null) {
                switch (e.getCode()) {
                    case 60203 -> {
                        log.warn("[Twilio] 发送频控 — {}", detail);
                        throw new BusinessException(ResultCode.OTP_TOO_FREQUENT);
                    }
                    case 60200 -> {
                        log.error("[Twilio] 号码/邮箱格式无效 — {}", detail);
                        throw new BusinessException(ResultCode.PARAM_INVALID);
                    }
                    case 20003 -> {
                        log.error("[Twilio] 凭据认证失败，请检查 TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN — {}", detail);
                        throw new BusinessException(ResultCode.OTP_SEND_FAILED);
                    }
                    case 20404 -> {
                        log.error("[Twilio] Verify Service 不存在，请检查 TWILIO_VERIFY_SERVICE_SID — {}", detail);
                        throw new BusinessException(ResultCode.OTP_SEND_FAILED);
                    }
                    default -> {
                        log.error("[Twilio] 发送失败 — {}", detail);
                        throw new BusinessException(ResultCode.OTP_SEND_FAILED);
                    }
                }
            }
            log.error("[Twilio] 未知异常 — {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.OTP_SEND_FAILED);
        }
    }

    @Override
    public boolean verifyOtp(String target, String code) {
        if (mockEnabled) {
            boolean pass = "000000".equals(code);
            log.warn("[Twilio Mock] OTP 验证 — target={}, code={}, result={}", target, code, pass ? "approved" : "rejected");
            return pass;
        }
        try {
            VerificationCheck check = VerificationCheck.creator(serviceSid)
                    .setTo(target)
                    .setCode(code)
                    .create();
            return "approved".equals(check.getStatus().toString());
        } catch (ApiException e) {
            log.error("[Twilio] OTP 验证异常 — code={}, msg={}", e.getCode(), e.getMessage());
            return false;
        }
    }
}
