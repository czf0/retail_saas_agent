package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 商品评价查询工具(review:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>商品定位: 支持 productId/productName 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class ReviewQueryToolReq {

    /** 目标商品 id, 对应 product.id. */
    private Long productId;

    private String productName;

    /** 评分; 取值 1-5(整数), 1 最差 5 最好. */
    private Integer rating;

    /** ReviewStatus 枚举 code: 1=PENDING 待审核 2=APPROVED 已通过 3=REJECTED 已拒绝. */
    private Integer status;

    private String keyword;

    /** 评价起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 评价截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
