package com.retail.business.dto.req;

import lombok.Data;

/**
 * 优惠券模板详情查询 Agent 工具入参.
 * <p>
 * 支持按优惠券模板ID或券名定位, 查询模板完整信息.
 */
@Data
public class CouponDetailToolReq {

    /** 优惠券模板 ID(可选,优先使用;否则用 name 反查) */
    private Long couponId;

    /** 券名(业务员无需知道优惠券模板ID) */
    private String name;
}
