package com.retail.business.dto.req;

import lombok.Data;

/**
 * 退款审核请求(运营后台订单管理 -> 退款单审核).
 * <p>对应 Controller 路由: PUT /api/v1/refunds/{id:\\d+}/audit; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>仅 PENDING 状态退款单可审核; 审核通过后触发退款联动(退券/退积分/库存回滚/订单 refund_amount 累加).
 */
@Data
public class RefundAuditReq {

    /** RefundStatus 枚举 code(审核结果): 2=APPROVED 审核通过 3=REJECTED 审核拒绝. */
    private Integer result;

    private String remark;
}
