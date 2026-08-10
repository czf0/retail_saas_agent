package com.retail.business.dto.req;

import lombok.Data;

/**
 * 订单更新请求(订单详情页修改/Agent 改订单工具, 部分更新).
 * <p>对应 Controller 路由: PUT /api/v1/orders/{id:\\d+}; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>仅允许修改备注,收货人信息等非关键字段; 状态变更由专用接口(pay/cancel/ship/complete)处理; null 字段不更新.
 */
@Data
public class OrderUpdateReq {

    /** 目标订单 id, 对应 order.id; 与 orderNo 二选一, Agent 定位订单用. */
    private Long orderId;

    private String orderNo;

    private String remark;

    /** PayType 枚举 code: 1=WECHAT 微信 2=ALIPAY 支付宝 3=BALANCE 余额 4=CASH 现金; 仅 PENDING 状态时可改, 已废弃后端忽略. */
    private Integer payType;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;
}
