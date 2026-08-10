package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单创建操作结果响应;包含创建是否成功 + 生成的 orderId/orderNo + 待付金额(用于前端跳转收银台).
 * <p>幂等:同 outTradeNo(外部商户订单号)重复提交返回首次成功的 orderId,不会重复创建 order_info 行.
 */
@Data
public class OrderCreateResp {

    /** true = 库存已扣 + order_info 行已插入(进入待付状态);false = 创建失败(库存不足/风控拦截等,具体原因见 message). */
    private Boolean success;

    /** 前端 toast 提示(成功=跳转收银台;失败=校验失败文案). */
    private String message;

    /** 新订单主键(order_info.id;成功时非空). */
    private Long orderId;

    /** 新订单业务号(YYYYMMDD + 流水;成功时非空;幂等键=orderNo). */
    private String orderNo;

    /** 待付金额(单位: 元,精度: 分;收银台三方支付入参金额直接使用此值;与订单 payAmount 一致). */
    private BigDecimal payAmount;
}
