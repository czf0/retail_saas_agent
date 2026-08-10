package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券查询请求.
 * <p>
 * 仅承载业务过滤字段;分页参数(page / pageSize)由 {@code PageParameterInterceptor} 从
 * {@code HttpServletRequest} 提取注入 {@code PageContextHolder},Agent 工具路径手动注入,
 * 本类不再承载分页参数.支持按会员 / 状态 / 模板 / 领取时间区间过滤.
 */
@Data
public class CouponQueryReq {
    /** 会员 ID */
    private Long memberId;
    /** 券状态:unused/used/expired/refunded */
    private Integer status;
    /** 模板 ID */
    private Long couponId;
    /** 领取起始时间 */
    private LocalDateTime startDate;
    /** 领取结束时间 */
    private LocalDateTime endDate;
}
