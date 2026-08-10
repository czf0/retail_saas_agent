package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量软删除评价操作结果响应(destructive=true,HITL 需二次确认);deleted = 1 软删除;前台 + 后台列表默认过滤删除行.
 * <p>注意:物理删除仅平台级 DBA 执行;日常运营用软删(保留主键不释放,避免外部引用悬空).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewDeleteResp extends OperationResultResp {
    /** 本次成功软删除的评价条数(已软删除的再次调用幂等不计入;单批上限 50 条). */
    private Long deleted;
}
