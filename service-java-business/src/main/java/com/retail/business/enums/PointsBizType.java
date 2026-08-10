package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 会员积分业务来源类型枚举; 标识触发积分变动的业务场景; points_log.biz_type 列.
 * <p>每个 biz_type 可与 PointsChangeType(EARN/GIFT/EXCHANGE/REFUND/ADJUST)组合使用, 完整描述积分变动:
 * <ul>
 *   <li>ORDER(1 订单): 订单完成 → 获取积分(EARN); 订单退款 → 扣减积分(REFUND); change_type 通常为 EARN/REFUND.</li>
 *   <li>COUPON(2 优惠券): 积分兑换优惠券(EXCHANGE); change_type = EXCHANGE.</li>
 *   <li>MANUAL(3 手动调整): 运营手动调整(ADJUST); change_type = ADJUST; 审计记录运营 user_id.</li>
 *   <li>ACTIVITY(4 活动): 签到 / 邀请 / 活动奖励(GIFT); change_type = GIFT.</li>
 *   <li>REFUND(5 退款): 退款相关直接积分扣减; change_type = REFUND.</li>
 * </ul>
 */
public enum PointsBizType implements BaseEnum {

    /** 订单业务触发; 订单 COMPLETED 状态按 pay_amount * 等级倍率获取积分(EARN); 订单 FULL_REFUND 扣减等额积分(REFUND), 写入 points_log. */
    ORDER(1, "订单"),
    /** 优惠券兑换触发; 用户在积分商城兑换优惠券; 积分扣减(EXCHANGE), 成功则发放至 user_coupon. */
    COUPON(2, "优惠券"),
    /** 运营手动调整; ADJUST change_type; 有符号 delta 值(正增负减); points_log.extra_json 中必填运营 user_id 和原因. */
    MANUAL(3, "手动调整"),
    /** 活动促销奖励; 每日签到 / 邀好友 / 活动参与; GIFT change_type 正向增量; points_log.extra_json 记录 activity_id 用于追溯. */
    ACTIVITY(4, "活动"),
    /** 退款单积分扣减; 退款时回滚此前发放的积分; REFUND change_type 负向减量; points_log.extra_json 关联 refund_order_id. */
    REFUND(5, "退款");

    @EnumValue
    private final Integer code;
    private final String desc;

    PointsBizType(Integer code, String desc) {
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
