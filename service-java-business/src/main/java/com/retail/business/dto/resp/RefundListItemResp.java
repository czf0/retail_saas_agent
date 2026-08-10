package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单列表页行项(售后管理退款列表 / 会员中心我的退款列表,返回 20/页;点击行进入详情 RefundResp).
 * <p>LEFT JOIN member.name 回填 memberName;行项 statusDesc 为 Service 层枚举映射;退款完成的 refundTime 仅 status=REFUNDED 行有值.
 */
@Data
public class RefundListItemResp {

    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    /** 会员ID(散客订单退款时为 null) */
    private Long memberId;

    /** 会员名称(LEFT JOIN member.name,散客退款时为 null) */
    private String memberName;

    private String refundType;

    private BigDecimal refundAmount;

    /** 退款数量(部分退款时指定,全退时为 null) */
    private Integer refundQty;

    /** 退款原因 */
    private String reason;

    private Integer status;

    /** 状态描述(由 Service 层调用 RefundStatus.description 填充) */
    private String statusDesc;

    private LocalDateTime applyTime;

    /** 实际退款时间(status 变为 refunded 时填充) */
    private LocalDateTime refundTime;

    private LocalDateTime createdAt;
}
