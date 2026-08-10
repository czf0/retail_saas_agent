package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

import java.time.LocalDateTime;

/**
 * 促销活动状态枚举; code = 1 未开始, 2 进行中, 3 已结束.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>PENDING(1) → ACTIVE(2): 系统定时任务 now >= startTime 触发; 活动生效, 优惠引擎查询结果中开始返回.</li>
 *   <li>ACTIVE(2) → EXPIRED(3): 系统定时任务 now >= endTime 触发; 活动自动终止; EXPIRED 为不可逆终态.</li>
 *   <li>PENDING(1) → 暂停(概念): 运营在开始前手动暂停; 数据库保留 ACTIVE 标记但引擎跳过(使用 manual flag 实现).</li>
 *   <li>ACTIVE(2) → 暂停(概念): 运营活动期间手动暂停; 优惠引擎跳过直至运营手动恢复.</li>
 * </ol>
 * <p>同商品命中多个促销时的叠加规则: 按 priority DESC + excludeGroup 互斥组, 由 PromotionEngine 统一处理.
 */
public enum PromotionStatus implements BaseEnum {

    /** 未开始(当前时间早于 startTime); 活动已配置但未生效; 优惠引擎跳过; 开始前运营可编辑. */
    PENDING(1, "未开始"),
    /** 进行中(当前时间位于 [startTime, endTime] 区间内); 活动生效; 优惠引擎纳入匹配结果; 引擎应用叠加/优先级规则. */
    ACTIVE(2, "进行中"),
    /** 已结束(终态, 当前时间晚于 endTime); 活动自动失效; 运营不可重新激活; 仅用于历史查询. */
    EXPIRED(3, "已结束");

    @EnumValue
    private final Integer code;
    private final String desc;

    PromotionStatus(Integer code, String desc) {
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

    /**
     * 根据当前时间在 [startTime, endTime] 区间内的位置, 自动推算促销状态.
     *
     * @param startTime 活动开始时间
     * @param endTime   活动结束时间
     * @param now       当前时间
     * @return 推算后的促销状态
     */
    public static PromotionStatus calculateStatus(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (now.isBefore(startTime)) {
            return PENDING;
        }
        if (now.isAfter(endTime)) {
            return EXPIRED;
        }
        return ACTIVE;
    }
}
