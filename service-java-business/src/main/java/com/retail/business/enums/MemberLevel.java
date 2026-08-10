package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 会员等级枚举; code = 1 普通, 2 银卡, 3 金卡, 4 钻石, 5 黑卡.
 * <p>升级触发条件 = 累计消费金额 OR 累计积分, 任意一项先达到即升级(Service 异步计算):
 * <ul>
 *   <li>NORMAL(1 普通): 用户注册时默认初始等级; 折扣率 100%; 积分倍率 1x; 无生日券.</li>
 *   <li>SILVER(2 银卡): 累计消费 >= 1000 元 OR 积分 >= 5000; 折扣率 98%; 积分倍率 1.2x; 生日 5 元券.</li>
 *   <li>GOLD(3 金卡): 累计消费 >= 5000 元 OR 积分 >= 25000; 折扣率 95%; 积分倍率 1.5x; 生日 20 元券.</li>
 *   <li>DIAMOND(4 钻石): 累计消费 >= 20000 元 OR 积分 >= 100000; 折扣率 90%; 积分倍率 2x; 生日 100 元券.</li>
 *   <li>BLACK(5 黑卡): 邀请制(租户管理员授予, 消费 >= 10 万元); 折扣率 85%; 积分倍率 3x; 生日 500 元券 + 专属客服.</li>
 * </ul>
 * <p>降级规则: 连续 180 天无任何消费则降一级; NORMAL 为底级(不可再降).
 */
public enum MemberLevel implements BaseEnum {

    /** 普通会员(注册默认); 基础折扣 100%(无折扣); 积分倍率 1.0x; 无生日券权益. */
    NORMAL(1, "普通"),
    /** 银卡会员(累计消费 >= 1000 元 OR 积分 >= 5000); 98% 折扣; 积分倍率 1.2x; 自动升级; 生日前 7 天自动发放 5 元券. */
    SILVER(2, "银卡"),
    /** 金卡会员(累计消费 >= 5000 元 OR 积分 >= 25000); 95% 折扣; 积分倍率 1.5x; 生日 20 元券; 售后优先处理. */
    GOLD(3, "金卡"),
    /** 钻石会员(累计消费 >= 20000 元 OR 积分 >= 100000); 90% 折扣; 积分倍率 2.0x; 生日 100 元券 + 专属包装 + 客服专线. */
    DIAMOND(4, "钻石"),
    /** 黑卡会员(邀请制 OR 累计消费 >= 10 万元); 85% 折扣; 积分倍率 3.0x; 生日 500 元券 + 1 对 1 客户经理; 由租户管理员手动授予. */
    BLACK(5, "黑卡");

    @EnumValue
    private final Integer code;
    private final String desc;

    MemberLevel(Integer code, String desc) {
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
