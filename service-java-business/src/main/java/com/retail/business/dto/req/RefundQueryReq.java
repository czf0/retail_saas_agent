package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款单分页查询请求(运营后台订单管理 -> 退款单列表筛选).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 * <p>供 HTTP 接口与 refund:query 工具共同复用.
 */
@Data
public class RefundQueryReq {

    /** RefundStatus 枚举 code: 1=PENDING 待审核 2=APPROVED 审核通过 3=REJECTED 审核拒绝 4=REFUNDED 已退款 5=CANCELLED 已撤销. */
    private Integer status;

    private String orderNo;

    /** 退款申请起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 退款申请截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    private String memberName;

    private String memberPhone;

    /** RefundType 枚举 code: 1=FULL 全额退款 2=PARTIAL 部分退款. */
    private Integer refundType;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 退款金额下限(含), 与 maxAmount 组合区间查询. */
    private BigDecimal minAmount;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 退款金额上限(含), 与 minAmount 组合区间查询. */
    private BigDecimal maxAmount;
}
