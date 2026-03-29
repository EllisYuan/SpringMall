package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.geekie.shop.shoppingmall.entity.PaymentDO;
import site.geekie.shop.shoppingmall.vo.AlipayPaymentVO;
import site.geekie.shop.shoppingmall.vo.PaymentVO;
import site.geekie.shop.shoppingmall.vo.StripePaymentVO;
import site.geekie.shop.shoppingmall.vo.WxPaymentVO;

/**
 * 支付转换器
 * 负责 PaymentDO 与各支付渠道 VO 之间的转换
 */
@Mapper(componentModel = "spring")
public interface PaymentConverter {

    /**
     * PaymentDO -> WxPaymentVO（同名字段直接映射）
     */
    WxPaymentVO toWxPaymentVO(PaymentDO payment);

    /**
     * PaymentDO -> StripePaymentVO
     * codeUrl -> sessionUrl，tradeNo -> sessionId，paymentStatus -> status
     */
    @Mapping(source = "codeUrl", target = "sessionUrl")
    @Mapping(source = "tradeNo", target = "sessionId")
    @Mapping(source = "paymentStatus", target = "status")
    StripePaymentVO toStripePaymentVO(PaymentDO payment);

    /**
     * PaymentDO -> AlipayPaymentVO
     * paymentUrl 字段在 DO 中不存在，需在调用方单独 set
     */
    @Mapping(target = "paymentUrl", ignore = true)
    AlipayPaymentVO toAlipayPaymentVO(PaymentDO payment);

    /**
     * PaymentDO -> PaymentVO
     * paymentUrl 字段在 DO 中不存在，ignore
     */
    @Mapping(target = "paymentUrl", ignore = true)
    PaymentVO toPaymentVO(PaymentDO payment);
}
