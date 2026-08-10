package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 工具专用入参: 退款单查询工具(refund:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>退款单定位: 支持状态/订单号/会员姓名/手机号多维自然语言解析, 不只依赖 refundId(铁律 20).
 */
@Data
public class RefundQueryToolReq {

    /** RefundStatus 枚举 code: 1=PENDING 待审核 2=APPROVED 审核通过 3=REJECTED 审核拒绝 4=REFUNDED 已退款 5=CANCELLED 已撤销. */
    private Integer status;

    private String orderNo;

    private String memberName;

    private String memberPhone;

    /** RefundType 枚举 code: 1=FULL 全额退款 2=PARTIAL 部分退款. */
    private Integer refundType;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 退款金额下限(含), 与 maxAmount 组合区间查询. */
    private BigDecimal minAmount;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 退款金额上限(含), 与 minAmount 组合区间查询. */
    private BigDecimal maxAmount;

    /** 退款起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 退款截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
