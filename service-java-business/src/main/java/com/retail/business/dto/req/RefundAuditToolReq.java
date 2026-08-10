package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 退款审核工具(refund:audit, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: RefundService.audit(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class RefundAuditToolReq {

    /** 退款单 id, 对应 refund.id; 可选, 优先使用; 否则用 refundNo/orderNo 反查. */
    private Long refundId;

    private String refundNo;

    private String orderNo;

    /** RefundStatus 枚举 code(审核结果): 2=APPROVED 审核通过 3=REJECTED 审核拒绝. */
    private Integer result;

    private String remark;
}
