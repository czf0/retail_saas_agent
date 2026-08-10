package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价详情页展示响应;聚合评分 + 文本内容 + 晒图列表 + 状态 + 商家回复(后台审核/会员中心"我的评价"详情共用).
 * <p>Controller: GET /api/v1/reviews/{id:\\d+};{id} 正则守卫;审核拒绝评价会员本人不可见详情(返回 404).
 */
@Data
public class ReviewResp {
    private Long id;
    /** 被评价商品外键(product_info.id);前台 PDP 评价区按此聚合. */
    private Long productId;
    /** 星级评分:1=很差 ~ 5=很满意(整数 1-5;前端 5 颗星渲染). */
    private Integer rating;
    /** 评价文本内容(用户输入,最长 1000 字符;后台审核敏感词替换). */
    private String content;
    /** 晒图图片 URL 列表(0~9 张;前端 swiper 轮播展示). */
    private List<String> images;
    /** 评价状态:1=PENDING(待审核) 2=APPROVED(已通过) 3=REJECTED(已拒绝);见 ReviewStatusEnum. */
    private Integer status;
    /** 商家客服回复内容(后台运营回复;NULL = 未回复). */
    private String replyContent;
    /** 商家回复时间(未回复 = NULL;回复后再次更新不覆盖首次). */
    private LocalDateTime replyAt;
    /** 评价提交时间(会员端输入瞬间;列表按此倒序). */
    private LocalDateTime createdAt;
}
