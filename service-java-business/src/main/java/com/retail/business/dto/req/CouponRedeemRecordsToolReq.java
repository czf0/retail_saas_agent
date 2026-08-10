package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 优惠券核销记录明细查询工具(coupon:redeem-records, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>优惠券模板定位: 支持 couponId/name 多维定位(CouponAgentToolService 内部 translate 为 ID), 不只依赖 couponId(铁律 20).
 */
@Data
public class CouponRedeemRecordsToolReq {

    /** 优惠券模板 id, 对应 coupon_template.id; 可选, 优先使用; 否则用 name 反查. */
    private Long couponId;

    private String name;

    /** 目标会员 id, 对应 member.id; 可空, 按会员过滤. */
    private Long memberId;

    /** CouponStatus 枚举 code: 1=UNUSED 未使用 2=USED 已使用 3=EXPIRED 已过期 4=REFUNDED 已退; 可空. */
    private Integer status;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
