package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请请求(订单详情页申请退款/Agent 退款工具).
 * <p>对应 Controller 路由: POST /api/v1/refunds; status 字段 Service 层赋默认值(RefundStatus.PENDING=1, 铁律 6),
 * CreateReq 不承载 status.
 * <p>仅 PAID/SHIPPED/COMPLETED 状态订单可申请退款; refundAmount 不能超过订单可退金额(pay_amount - 已退金额).
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class RefundCreateReq {

    /** 原订单 id, 对应 order.id; Service 层校验订单状态是否可退. */
    private Long orderId;

    /** RefundType 枚举 code: 1=FULL 全额退款 2=PARTIAL 部分退款. */
    private Integer refundType;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); 全额退时为订单可退金额; 部分退时由调用方指定, 不可超过可退余额. */
    private BigDecimal refundAmount;

    /** 退款商品数量; refundType=PARTIAL 时指定, FULL 时为空; 正整数. */
    private Integer refundQty;

    private String reason;
}
