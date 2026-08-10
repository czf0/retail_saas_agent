package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 用户优惠券状态枚举; code = 1 未使用, 2 已使用, 3 已过期, 4 已退.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>UNUSED(1) → USED(2): 用户结算时核销优惠券; Service 层原子 CAS 校验 status=1 同时 SET status=2 + redeem_time; 并发下仅 1 次成功.</li>
 *   <li>UNUSED(1) → EXPIRED(3): 定时任务每日扫描 valid_to < today; 批量更新, 不可逆向回 UNUSED.</li>
 *   <li>UNUSED(1) → REFUNDED(4): 订单全额退款触发优惠券退回(优惠券仍在有效期内); 部分退款不退回优惠券.</li>
 *   <li>USED(2) → REFUNDED(4): 订单退款且优惠券已使用; 若退款原因为商家责任, 由运营手动以新券实例退回(不复用当前 coupon_id).</li>
 * </ol>
 * <p>终态(不可逆): USED / EXPIRED / REFUNDED; UNUSED 为用户领取后的初始状态.
 */
public enum CouponStatus implements BaseEnum {

    /** 未使用(用户领取后初始态); 结算时可用于优惠抵扣; 核销需满足 valid_from <= now <= valid_to. */
    UNUSED(1, "未使用"),
    /** 已使用(结算时已核销); Service 层原子 CAS 设置并记录 redeem_time; 绝不允许 USED→UNUSED 逆向, 防止重复核销. */
    USED(2, "已使用"),
    /** 已过期(终态); 定时任务每日 00:10 扫描 valid_to < today 批量更新为 EXPIRED; 不可使用也不可退回. */
    EXPIRED(3, "已过期"),
    /** 已退(订单全额退款后优惠券退回); 仅订单全额退款 + 优惠券仍在有效期内触发; 部分退款不退回优惠券. */
    REFUNDED(4, "已退");

    @EnumValue
    private final Integer code;
    private final String desc;

    CouponStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
