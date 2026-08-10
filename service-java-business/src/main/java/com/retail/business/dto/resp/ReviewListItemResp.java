package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价列表页行项(PDP 评价区/后台审核列表/会员中心我的评价,20/页;点击行进入详情 ReviewResp).
 * <p>Service 层 LEFT JOIN product_info.name 回填 productName;避免前端 N+1 查商品名.
 */
@Data
public class ReviewListItemResp {
    private Long id;
    private Long productId;
    /** 商品名称(Service 层批量查询 product_info 填充,消除前端数据孤岛) */
    private String productName;
    private Integer rating;
    private String content;
    private Integer status;
    private String replyContent;
    private LocalDateTime createdAt;
}
