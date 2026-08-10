package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 会员积分汇总查询工具(points:summary, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(单条汇总查询).
 * <p>会员定位: 支持 memberId/memberName/phone 多维自然语言解析, 不只依赖 memberId(铁律 20).
 */
@Data
public class PointsSummaryToolReq {

    /** 目标会员 id, 对应 member.id; 可选, 优先使用; 否则用 memberName/phone 反查. */
    private Long memberId;

    private String memberName;

    private String phone;
}
