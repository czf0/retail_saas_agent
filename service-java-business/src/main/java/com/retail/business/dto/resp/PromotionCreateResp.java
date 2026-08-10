package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 促销活动创建操作结果响应;继承通用 OperationResultResp.success+message,额外返回 promotionId 供前端跳转活动详情页.
 * <p>继承 success/message 见 {@link com.retail.business.dto.OperationResultResp}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromotionCreateResp extends OperationResultResp {
    /** 新创建活动主键(promotion_info.id;success=true 时非空). */
    private Long promotionId;
}
