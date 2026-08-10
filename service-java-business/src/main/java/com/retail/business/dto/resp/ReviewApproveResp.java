package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量审核通过评价操作结果响应(destructive=false 非破坏性但属内容审核,需日志留痕);包含本次成功通过的评价条数.
 * <p>状态机流转:PENDING(1) → APPROVED(2);已 APPROVED 的重复调用为幂等(不计入 approved);已 REJECTED 需先"反拒绝"再通过.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewApproveResp extends OperationResultResp {
    /** 本次实际通过审核的评价条数(幂等去重后计数;即从 PENDING → APPROVED 状态变迁的条数). */
    private Long approved;
}
