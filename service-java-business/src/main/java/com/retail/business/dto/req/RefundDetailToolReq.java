package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 退款单详情查询工具(refund:detail, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(单条详情查询).
 * <p>退款单定位: 支持 refundId/refundNo/orderNo 多维自然语言解析, 不只依赖 refundId(铁律 20).
 */
@Data
public class RefundDetailToolReq {

    /** 退款单 id, 对应 refund.id; 可选, 优先使用; 否则用 refundNo/orderNo 反查. */
    private Long refundId;

    private String refundNo;

    private String orderNo;
}
