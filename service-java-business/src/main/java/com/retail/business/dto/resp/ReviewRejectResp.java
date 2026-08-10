package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量审核拒绝评价操作结果响应(destructive=true,HITL 需运营二次确认);拒绝原因必填(写入 reject_reason + 日志审计).
 * <p>状态机流转:PENDING(1) → REJECTED(3);已 REJECTED 重复调用幂等;会员端"我的评价"中被拒不可见.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewRejectResp extends OperationResultResp {
    /** 本次实际拒绝审核的评价条数(幂等去重后计数;即从 PENDING → REJECTED 状态变迁的条数). */
    private Long rejected;
}
