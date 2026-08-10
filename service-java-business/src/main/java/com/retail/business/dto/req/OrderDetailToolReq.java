package com.retail.business.dto.req;

import lombok.Data;

/**
 * 订单详情查询 Agent 工具入参.
 * <p>
 * 支持按订单ID或订单号定位, 查询订单完整信息 (含明细列表,状态描述).
 */
@Data
public class OrderDetailToolReq {

    /** 订单 ID(可选,优先使用;否则用 orderNo 反查) */
    private Long orderId;

    /** 订单号(业务员无需知道订单ID) */
    private String orderNo;
}
