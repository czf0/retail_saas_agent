package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员创建商品评价操作结果响应;评价创建后默认状态 = PENDING(待审核),需后台运营通过后前端才展示.
 * <p>幂等:同 orderId + productId + memberId 三元组重复提交返回首次 reviewId,不重复写行.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewCreateResp extends OperationResultResp {
    /** 生成的评价 ID(review_info.id);状态 = PENDING,需审核通过才展示. */
    private Long reviewId;
}
