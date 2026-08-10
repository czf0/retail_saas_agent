package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券详情响应("我的优惠券"卡片详情 / 订单结算页选择优惠券弹窗详情);聚合券模板快照 + 用户持有状态 + 有效期 + 核销订单号.
 * <p>Controller: GET /api/v1/user-coupons/{id:\\d+};{id} 正则守卫;仅本人或后台管理员可查.
 */
@Data
public class UserCouponResp {
    private Long id;
    private Long couponId;
    private String couponName;
    /** 类型枚举 code:1=满减(FULLCUT) / 2=折扣(DISCOUNT) / 3=代金券(CASH) */
    private Integer couponType;
    private Long memberId;
    /** 状态枚举 code:1=未使用(UNUSED) / 2=已使用(USED) / 3=已过期(EXPIRED) / 4=已退(REFUNDED),见 CouponStatus */
    private Integer status;
    private BigDecimal faceValue;
    private BigDecimal threshold;
    private LocalDateTime receiveTime;
    private LocalDateTime usedTime;
    private LocalDateTime expireTime;
    private String orderNo;
}
