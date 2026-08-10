package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 订单来源渠道枚举; code = 1 线上, 2 Agent, 3 手工.
 * <p>表示订单原始下单入口; 用于渠道维度销售统计和佣金计算:
 * <ul>
 *   <li>ONLINE(1 线上): 用户自助从 H5 / 小程序 / APP 前台结算; 标准订单流程.</li>
 *   <li>AGENT(2 Agent): AI 智能助手通过自然语言会话生成的订单; order_channel=AGENT 用于 Agent KPI 考核.</li>
 *   <li>MANUAL(3 手工): 运营后台手工创建订单(电话下单 / 线下补录); 需运营权限码.</li>
 * </ul>
 */
public enum OrderChannel implements BaseEnum {

    /** 线上用户自助订单; 用户从 H5 / 小程序 / APP 前台下单; 带支付网关的标准结算流程. */
    ONLINE(1, "线上"),
    /** AI Agent 助手生成订单; 通过智能体自然会话创建; 标记用于 Agent 销售 KPI 与渠道归因分析. */
    AGENT(2, "Agent"),
    /** 运营后台手工订单; 电话 / 线下到店订单在后台补录; 需 rbac:order:manual_add 权限. */
    MANUAL(3, "手工");

    @EnumValue
    private final Integer code;
    private final String desc;

    OrderChannel(Integer code, String desc) {
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
