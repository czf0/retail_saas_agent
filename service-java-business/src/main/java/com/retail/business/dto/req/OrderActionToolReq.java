package com.retail.business.dto.req;

import lombok.Data;

/**
 * 订单状态变更 Agent 工具入参 (发货/完成/取消).
 * <p>
 * 支持按订单ID或订单号定位, 对应 OrderService 的 shipOrder/completeOrder/cancelOrder.
 */
@Data
public class OrderActionToolReq {

    /** 订单 ID(可选,优先使用;否则用 orderNo 反查) */
    private Long orderId;

    /** 订单号(业务员无需知道订单ID) */
    private String orderNo;
}
