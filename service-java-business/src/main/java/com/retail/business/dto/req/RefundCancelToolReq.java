package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 撤销退款单工具(refund:cancel, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: RefundService.cancel(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class RefundCancelToolReq {

    /** 退款单 id, 对应 refund.id; 可空, 优先按 refundNo 定位时兜底. */
    private Long refundId;

    private String refundNo;
}
