package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券模板查询请求(业务层).
 * <p>
 * 仅承载业务过滤字段;分页参数(page / pageSize)由 {@code PageParameterInterceptor} 从
 * {@code HttpServletRequest} 提取注入 {@code PageContextHolder},Agent 工具路径手动注入.
 * 供 HTTP 接口与 {@code coupon:query} 工具共同复用.
 */
@Data
public class CouponTemplateQueryReq {

    /** 模板状态 (active/inactive) */
    private Integer status;

    /** 优惠券类型 (DISCOUNT/FULL_REDUCTION) */
    private Integer type;

    /** 模板名称模糊查询 */
    private String keyword;

    /** 最低面额(含) */
    private BigDecimal minFaceValue;

    /** 最高面额(含) */
    private BigDecimal maxFaceValue;

    /** 最低使用门槛(含) */
    private BigDecimal minThreshold;

    /** 最高使用门槛(含) */
    private BigDecimal maxThreshold;

    /** 有效期开始时间(yyyy-MM-dd,fixed 类型有效起止过滤) */
    private String validStart;

    /** 有效期结束时间(yyyy-MM-dd,fixed 类型有效起止过滤) */
    private String validEnd;
}
