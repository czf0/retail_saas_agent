package com.retail.business.dto.req;

import lombok.Data;

/**
 * 优惠券模板查询 Agent 工具入参.
 * <p>
 * 支持按状态,类型,名称关键词,面额区间,使用门槛区间,有效期范围过滤, 分页返回.
 */
@Data
public class CouponQueryToolReq {

    /** 模板状态 (active/inactive) */
    private Integer status;

    /** 优惠券类型 (DISCOUNT/FULL_REDUCTION) */
    private Integer type;

    /** 模板名称模糊查询 */
    private String keyword;

    /** 最低面额(含) */
    private java.math.BigDecimal minFaceValue;

    /** 最高面额(含) */
    private java.math.BigDecimal maxFaceValue;

    /** 最低使用门槛(含) */
    private java.math.BigDecimal minThreshold;

    /** 最高使用门槛(含) */
    private java.math.BigDecimal maxThreshold;

    /** 有效期开始时间 (yyyy-MM-dd) */
    private String validStart;

    /** 有效期结束时间 (yyyy-MM-dd) */
    private String validEnd;

    /** 页码 (默认 1) */
    private Integer page = 1;

    /** 每页条数 (默认 20) */
    private Integer pageSize = 20;
}
