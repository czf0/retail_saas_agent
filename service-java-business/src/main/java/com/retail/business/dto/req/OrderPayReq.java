package com.retail.business.dto.req;

import lombok.Data;

/**
 * 订单支付请求(收银台支付/Agent 代客支付).
 * <p>对应 Controller 路由: POST /api/v1/orders/{id:\\d+}/pay; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>仅 PENDING 状态订单可支付; 支付成功后状态变为 PAID, 并触发库存锁定(RESERVATION)出库联动.
 */
@Data
public class OrderPayReq {

    /** PayType 枚举 code: 1=WECHAT 微信 2=ALIPAY 支付宝 3=BALANCE 余额 4=CASH 现金. */
    private Integer payType;
}
