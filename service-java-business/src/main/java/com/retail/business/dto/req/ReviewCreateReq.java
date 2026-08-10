package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 商品评价创建请求(会员订单完成后晒单/评价).
 * <p>对应 Controller 路由: POST /api/v1/reviews; status 字段 Service 层赋默认值(ReviewStatus.PENDING=1, 铁律 6),
 * CreateReq 不承载 status.
 */
@Data
public class ReviewCreateReq {
    /** 目标商品 id, 对应 product.id; Service 层校验该会员是否有已完成订单含此商品. */
    private Long productId;
    /** 评分; 取值 1-5(整数), 1 最差 5 最好; Service 层校验范围. */
    private Integer rating;
    private String content;
    /** 评价图片 URL 列表; 可空, 最多 9 张(Service 层校验). */
    private List<String> images;
}
