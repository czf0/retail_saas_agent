package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 优惠券核销统计工具(coupon:redeem-stats, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页聚合报表).
 * <p>按时间范围统计各券模板的发放数,已使用数及核销率.
 */
@Data
public class CouponRedeemStatsToolReq {

    private String name;

    /** CouponType 枚举 code: 1=FULLCUT 满减 2=DISCOUNT 折扣 3=CASH 代金券; 可空. */
    private Integer type;

    /** 统计起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 统计截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;
}
