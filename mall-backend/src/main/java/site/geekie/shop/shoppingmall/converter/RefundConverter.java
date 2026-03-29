package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.geekie.shop.shoppingmall.entity.RefundDO;
import site.geekie.shop.shoppingmall.vo.StripeRefundVO;
import site.geekie.shop.shoppingmall.vo.WxRefundVO;

/**
 * 退款转换器
 * 负责 RefundDO 与各支付渠道退款 VO 之间的转换
 */
@Mapper(componentModel = "spring")
public interface RefundConverter {

    /**
     * RefundDO -> WxRefundVO（同名字段直接映射）
     */
    WxRefundVO toWxRefundVO(RefundDO refund);

    /**
     * RefundDO -> StripeRefundVO
     * tradeNo -> refundId，refundReason -> reason，refundStatus -> status
     */
    @Mapping(source = "tradeNo", target = "refundId")
    @Mapping(source = "refundReason", target = "reason")
    @Mapping(source = "refundStatus", target = "status")
    StripeRefundVO toStripeRefundVO(RefundDO refund);
}
