package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单支付结果响应(收银台提交支付接口 / 三方支付回调通知后的确认接口返回);包含支付状态 + 支付时间.
 * <p>注意:三方异步回调成功 ≠ 前端拿到此 success=true;前端需轮询或 WebSocket 确认 order_status 进入 PAID(2) 后才算最终完成.
 */
@Data
public class OrderPayResp {

    /** true = 支付成功(pay_amount 全到账 + 订单 PAID);false = 待继续支付/失败(二维码超时/余额不足等). */
    private Boolean success;

    /** 提示文案;success=false 时建议前端展示并提供"重试支付"按钮. */
    private String message;

    /** 订单外键(确认是哪笔订单的支付结果). */
    private Long orderId;

    /** 订单号冗余(前端订单中心直接展示用). */
    private String orderNo;

    /** 支付后订单状态:2=PAID(已支付),1=PENDING(继续待付),5=CANCELED(关单);见 OrderStatusEnum. */
    private Integer status;

    /** 支付成功时间(三方回调写入;时区 Asia/Shanghai);待付时为 NULL. */
    private LocalDateTime payTime;
}
