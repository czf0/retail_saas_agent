package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 批量发放优惠券请求(运营侧主动发券给指定会员).
 * <p>storeId 仅作上下文标识;user_coupon.store_id 由门店拦截器自动注入,代码不主动赋值.
 */
@Data
public class CouponIssueReq {
    /** 优惠券模板 ID */
    private Long couponId;
    /** 待发放会员 ID 列表(memberIds 为空时按会员姓名/手机号/等级反查会员) */
    private List<Long> memberIds;
    /** 会员姓名(memberIds 为空时按此定位,可空) */
    private String memberName;
    /** 会员手机号(memberIds 为空时按此定位,可空) */
    private String memberPhone;
    /** 会员等级(memberIds 为空时按此定位,可空;MemberLevel:1普通/2银卡/3金卡/4钻石) */
    private Integer memberLevel;
    /** 门店 ID(可空,仅标识上下文;实际写入由拦截器注入) */
    private Long storeId;
}
