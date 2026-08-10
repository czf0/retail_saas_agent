package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 积分兑换工具(points:redeem, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 * <p>会员定位: 优先使用 memberPhone(手机号反查), memberId 仅作兜底(铁律 20).
 */
@Data
public class PointsRedeemToolReq {

    /** 目标会员 id, 对应 member.id; 可空, memberPhone 反查命中时忽略. */
    private Long memberId;

    private String memberPhone;

    /** 兑换积分数量; 正整数; 内部转换为负数扣减, Service 层校验积分余额充足. */
    private Integer points;

    private String reason;
}
