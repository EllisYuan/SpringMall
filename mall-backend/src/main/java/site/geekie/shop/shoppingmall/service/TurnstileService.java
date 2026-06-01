package site.geekie.shop.shoppingmall.service;

/**
 * Cloudflare Turnstile 人机验证服务接口
 */
public interface TurnstileService {

    /**
     * 验证 Turnstile token
     *
     * @param token    前端挑战通过后返回的 token
     * @param remoteIp 客户端 IP 地址
     * @throws site.geekie.shop.shoppingmall.exception.BusinessException 验证失败时抛出
     */
    void verify(String token, String remoteIp);
}
