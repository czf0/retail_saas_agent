package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单详情页展示响应;聚合退款申请主信息 + 关联订单/会员快照 + 金额/数量 + 状态流转 + 审核节点.
 * <p>Controller: GET /api/v1/refunds/{id:\\d+};{id} 正则守卫;退款单一旦状态 = REFUNDED(已完成) 不可变,结果缓存 30min.
 */
@Data
public class RefundResp {

    private Long id;

    /** 退款单号(业务唯一键;RF + YYYYMMDD + 流水;幂等判断用). */
    private String refundNo;

    /** 原订单外键(order_info.id);一个订单可多笔部分退款. */
    private Long orderId;

    /** 原订单号冗余(Service 层回填,避免前端 N+1 查订单号). */
    private String orderNo;

    /** 申请退款的会员(member.id);与原订单 member_id 一致性校验通过才受理. */
    private Long memberId;

    /** 退款类型:FULL(全额) / PARTIAL(部分);字符串枚举,见 RefundTypeEnum. */
    private String refundType;

    /** 申请/实退金额(单位: 元,精度: 分;全额退款 = 原订单 payAmount - 已退部分). */
    private BigDecimal refundAmount;

    /** 退款商品件数(部分退款时 < 原订单总 qty;全额退款 = SUM 全部 qty). */
    private Integer refundQty;

    /** 用户退款原因(前端下拉 + 自定义输入,如"质量问题"/"7天无理由";售后分析用). */
    private String reason;

    /** 退款状态码:1=PENDING_AUDIT(待审核) 2=AUDIT_PASSED(审核通过) 3=REJECTED(审核拒绝) 4=REFUNDING(退款中) 5=REFUNDED(已完成);见 RefundStatusEnum. */
    private Integer status;

    /** 状态中文描述(Service 层枚举映射;前端直接展示). */
    private String statusDesc;

    /** 用户提交申请时间(退款流程起点;SLA 审核时效从此时算起). */
    private LocalDateTime applyTime;

    /** 退款实际到账时间(三方退款回调成功写入;财务记账时间;NULL 表示未到账). */
    private LocalDateTime refundTime;

    private LocalDateTime createdAt;
}
