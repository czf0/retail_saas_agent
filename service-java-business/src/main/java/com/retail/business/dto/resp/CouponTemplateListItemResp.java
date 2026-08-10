package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券模板列表页行项(运营后台券模板管理列表,返回 20/页;点击行进入详情 CouponTemplateResp).
 * <p>列表辅助字段 issuedCount/totalCount 为实时统计聚合(SQL 内嵌 COUNT + SUM),详情页不重复返回,节省分页带宽.
 */
@Data
public class CouponTemplateListItemResp {
    private Long id;
    private String name;
    /** 类型:fullcut/discount/cash */
    private Integer type;
    private BigDecimal faceValue;
    private BigDecimal threshold;
    private Integer issuedCount;
    private Integer totalCount;
    /** 状态:active/inactive */
    private Integer status;
}
