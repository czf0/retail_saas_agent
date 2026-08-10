package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品评价分页查询请求(运营后台评价管理列表页筛选).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 * <p>供 HTTP 接口与 review:query 工具共同复用.
 */
@Data
public class ReviewQueryReq {

    /** 目标商品 id, 对应 product.id. */
    private Long productId;

    /** 评分; 取值 1-5(整数), 1 最差 5 最好. */
    private Integer rating;

    /** ReviewStatus 枚举 code: 1=PENDING 待审核 2=APPROVED 已通过 3=REJECTED 已拒绝. */
    private Integer status;

    private String productName;

    private String keyword;

    /** 评价起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 评价截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;
}
