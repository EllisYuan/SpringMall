package site.geekie.shop.shoppingmall.service;

public interface TwilioVerifyService {
    void sendOtp(String target, String channel);
    boolean verifyOtp(String target, String code);
}
