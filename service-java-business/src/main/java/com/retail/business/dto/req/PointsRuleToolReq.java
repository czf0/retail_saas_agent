package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 积分规则查看/修改工具(points:rule, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class PointsRuleToolReq {

    /** 操作类型 code: get=查看规则(rate 可空) set=修改规则(rate 必填); 可空, 缺省视为 get. */
    private String action;

    /** 积分费率(1 元 = N 积分); set 操作时必填, 正整数; 如 rate=10 表示消费 1 元得 10 积分. */
    private Integer rate;
}
