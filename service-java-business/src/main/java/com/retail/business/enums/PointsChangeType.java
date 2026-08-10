package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 会员积分变动方向类型枚举; points_log.change_type 列.
 * <p>与 PointsBizType(ORDER/COUPON/MANUAL/ACTIVITY/REFUND)组合使用, 完整描述积分变动方向 + 业务来源:
 * <ul>
 *   <li>EARN(1 消费获取): 订单完成获取积分; 正向增量; biz_type 通常为 ORDER.</li>
 *   <li>GIFT(2 活动赠送): 签到 / 活动 / 推荐奖励; 正向增量; biz_type 通常为 ACTIVITY.</li>
 *   <li>EXCHANGE(3 兑换消耗): 积分兑换优惠券/商品; 负向减量; biz_type 通常为 COUPON.</li>
 *   <li>REFUND(4 退款扣减): 订单退款回滚此前获取的积分; 负向减量; biz_type 通常为 ORDER/REFUND.</li>
 *   <li>ADJUST(5 手动调整): 运营手动更正; 正负双向均可; biz_type 通常为 MANUAL.</li>
 * </ul>
 */
public enum PointsChangeType implements BaseEnum {

    /** 消费获取积分; 订单 COMPLETED 时触发正向增量, 数量 = pay_amount * member_level.points_multiplier; biz_id 关联 order_id. */
    EARN(1, "消费获取"),
    /** 活动赠送积分; 每日签到 / 邀好友 / 活动参与奖励; 正向增量; biz_id 关联 activity_id, 系统生成时可为 null. */
    GIFT(2, "活动赠送"),
    /** 积分兑换消耗; 用户在积分商城用积分兑换优惠券 / 实物; 负向减量; 余额不足则报错拒绝. */
    EXCHANGE(3, "兑换消耗"),
    /** 退款扣减; 订单 FULL_REFUND 回滚此前 EARN 获取的积分; 负向减量; biz_id 关联 refund_order_id; 部分退款不扣减积分. */
    REFUND(4, "退款扣减"),
    /** 运营手动调整; 有符号增量(正增负减); extra_json 审计字段中记录运营 user_id 和调整原因. */
    ADJUST(5, "手动调整");

    @EnumValue
    private final Integer code;
    private final String desc;

    PointsChangeType(Integer code, String desc) {
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
