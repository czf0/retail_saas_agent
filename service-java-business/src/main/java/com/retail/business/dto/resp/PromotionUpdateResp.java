package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 促销活动修改操作结果响应;继承通用 OperationResultResp.success+message,额外返回受影响行数(乐观锁版本校验).
 * <p>继承 success/message 见 {@link com.retail.business.dto.OperationResultResp}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromotionUpdateResp extends OperationResultResp {
    /** 受影响行数(=1 成功;=0 版本号冲突或已删除 → 前端提示"数据已过时,刷新重试"). */
    private Long updated;
}
