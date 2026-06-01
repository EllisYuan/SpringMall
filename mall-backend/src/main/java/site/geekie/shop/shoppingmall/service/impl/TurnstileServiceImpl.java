package site.geekie.shop.shoppingmall.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.service.TurnstileService;

import java.time.Duration;
import java.util.Map;

/**
 * Cloudflare Turnstile 人机验证服务实现
 * 向 Cloudflare 验证端点发送 POST 请求，校验前端挑战 token 的真实性
 */
@Slf4j
@Service
public class TurnstileServiceImpl implements TurnstileService {

    private final String secretKey;
    private final String verifyUrl;
    private final RestTemplate restTemplate;

    public TurnstileServiceImpl(
            @Value("${cloudflare.turnstile.secret-key}") String secretKey,
            @Value("${cloudflare.turnstile.verify-url}") String verifyUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void verify(String token, String remoteIp) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);
            body.add("response", token);
            if (remoteIp != null && !remoteIp.isBlank()) {
                body.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(verifyUrl, request, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                log.warn("【Turnstile验证失败】remoteIp: {}, errorCodes: {}",
                        remoteIp, response != null ? response.get("error-codes") : "null response");
                throw new BusinessException(ResultCode.TURNSTILE_FAILED);
            }

            log.debug("【Turnstile验证通过】remoteIp: {}", remoteIp);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【Turnstile验证异常】remoteIp: {}, error: {}", remoteIp, e.getMessage());
            throw new BusinessException(ResultCode.TURNSTILE_FAILED);
        }
    }
}
