package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 退款审核操作结果响应(通过/拒绝);返回是否成功 + 退款单当前最新状态(前端据此刷新列表行状态标签).
 * <p>幂等:对同一 refundId 重复审核(再次点通过)不会重复写 refund_status 审计日志,直接返回上次通过结果.
 */
@Data
public class RefundAuditResp {

    /** true = 审核写入成功(通过 or 拒绝都算);false = 当前状态已非 PENDING_AUDIT,不可重复审核(幂等提示). */
    private Boolean success;

    /** 审核结果说明;失败时包含拒绝原因或"已审核不可重复操作". */
    private String message;

    /** 退款单主键(refund_info.id;成功非空). */
    private Long refundId;

    /** 审核后退款单最新状态码:2=AUDIT_PASSED(通过) 3=REJECTED(拒绝);见 RefundStatusEnum. */
    private Integer status;
}
