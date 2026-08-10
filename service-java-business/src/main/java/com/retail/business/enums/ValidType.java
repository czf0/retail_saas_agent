package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 优惠券有效期类型枚举; coupon_template.valid_type 字段.
 * <p>决定用户领取时计算 coupon valid_from / valid_to 的算法:
 * <ul>
 *   <li>RELATIVE(1 领取后 N 天有效): 领取时间戳起 N 天窗口; 需 valid_days 字段; valid_from = claim_time, valid_to = claim_time + valid_days 天.</li>
 *   <li>FIXED(2 固定时间段有效): 模板中绝对起止日期范围; 需 valid_start / valid_end 字段; 仅允许在范围内领取, 无论何时领取均在 valid_end 过期.</li>
 * </ul>
 */
public enum ValidType implements BaseEnum {

    /** 相对 N 天有效期; 优惠券有效窗口从用户 claim_time 起算(valid_from), 持续 valid_days 天(valid_to = claim_time + valid_days); 此类型忽略模板 valid_start/valid_end. */
    RELATIVE(1, "领取后N天有效"),
    /** 固定绝对有效期范围; 优惠券有效窗口直接使用模板 valid_start 和 valid_end 时间值; 仅允许在 claim_start 至 claim_end(独立模板字段)内领取; 无论何时领取都在 valid_end 过期. */
    FIXED(2, "固定时间段有效");

    @EnumValue
    private final Integer code;
    private final String desc;

    ValidType(Integer code, String desc) {
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
