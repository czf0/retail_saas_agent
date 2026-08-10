package com.retail.business.dto.req;

import lombok.Data;

/**
 * 会员主动领取优惠券请求.
 * <p>storeId 仅作上下文标识;user_coupon.store_id 由门店拦截器自动注入,代码不主动赋值.
 */
@Data
public class CouponReceiveReq {
    /** 优惠券模板 ID */
    private Long couponId;
    /** 会员 ID */
    private Long memberId;
    /** 门店 ID(可空,仅标识上下文;实际写入由拦截器注入) */
    private Long storeId;
}
