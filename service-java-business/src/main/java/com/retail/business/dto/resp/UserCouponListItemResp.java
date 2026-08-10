package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券列表页行项(我的优惠券列表 / 运营后台按会员查券列表,返回 20/页;点击行进入详情 UserCouponResp).
 * <p>列表页 faceValue / threshold 为模板快照冗余(LEFT JOIN coupon_template 回填);用于前端卡片展示且订单算价时本地计算,减少详情 N+1.
 * <p>LEFT JOIN member.name 回填 memberName,消除前端 "会员 #id" 数据孤岛问题(MPS 约定禁止前端根据 #id 自查用户中心).
 */
@Data
public class UserCouponListItemResp {
    private Long id;

    /** 券模板ID */
    private Long couponId;

    private String couponName;

    /** 类型:fullcut/discount/cash */
    private String couponType;

    /** 会员ID */
    private Long memberId;

    /** 会员名称(LEFT JOIN member.name 带出,消除数据孤岛) */
    private String memberName;

    /** 状态:unused/used/expired/refunded */
    private Integer status;

    /** 核销订单ID */
    private Long orderId;

    /** 冗余订单号 */
    private String orderNo;

    /** 冗余面额 */
    private BigDecimal faceValue;

    /** 使用门槛:满 threshold 才可用(0=无门槛).B-25 修复:补回此前缺失的字段,前端需展示与算价 */
    private BigDecimal threshold;

    /** 领取时间 */
    private LocalDateTime receiveTime;

    /** 使用时间 */
    private LocalDateTime usedTime;

    /** 过期时间 */
    private LocalDateTime expireTime;
}
