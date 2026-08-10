package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 经营报表查询工具(report:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页聚合报表).
 * <p>报表为聚合查询, 仅需时间范围 + 门店/分类/商品维度过滤.
 */
@Data
public class ReportToolReq {

    /** 统计起始日期(yyyy-MM-dd, Asia/Shanghai); 可空, 空=不限起始. */
    private String startDate;

    /** 统计截止日期(yyyy-MM-dd, Asia/Shanghai); 可空, 空=不限截止. */
    private String endDate;

    /** 目标门店 id, 对应 sys_store.id; 可空, 用于非白名单表的手动门店过滤. */
    private Long storeId;

    /** 目标分类 id, 对应 product_category.id; 可空, 按分类过滤销售/库存数据. */
    private Long categoryId;

    /** 目标商品 id, 对应 product.id; 可空, 按商品过滤销售/库存数据. */
    private Long productId;
}
