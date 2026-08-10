package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import com.retail.business.enums.ReviewStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价实体, 对应数据库 product_review 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(评价为租户全局展示, 门店下单的评价也汇总到商品 SPU).
 * <p>业务约束: 本表只有 created_at, 无 updated_at / updateBy(评价创建后不可修改, 传统电商模式, 避免改评刷单); images 为 JSON 数组字段, 依赖 JacksonTypeHandler + autoResultMap=true; 运营可通过 replyContent 回复评价.
 * <p>唯一约束: UNIQUE(tenant_id, order_id, product_id, member_id), 同一订单同一商品同一会员只能评价一次(避免刷评).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName(value = "product_review", autoResultMap = true)
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 商品 SPU id, 指向 product_info.id; 评价聚合按此维度汇总 avgRating / totalCount, 商品详情页展示评价列表. */
    private Long productId;

    /** 商品评分(1-5 星整数); 1=非常不满, 3=一般, 5=非常满意; 商品详情页 avgRating = AVG(rating) WHERE status=APPROVED. */
    private Integer rating;

    /** 评价正文(纯文本, 长度限制 10-500 字); 敏感词过滤: 创建时通过 SensitiveWordFilter 过滤, 命中则自动置 status=PENDING 人工审核. */
    private String content;

    /** 评价图片 URL 列表(JSON 数组, 最多 9 张, 与电商惯例一致); 依赖 JacksonTypeHandler 序列化; 图片压缩上传由 OSS 服务端处理, 此处存 CDN URL. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 评价审核状态(ReviewStatus 枚举本体: 1=PENDING 待审, 2=APPROVED 通过, 3=REJECTED 拒绝); 无图 + 评分 >= 4 的评价默认自动 APPROVED, 否则 PENDING 人工审核. */
    private ReviewStatus status;

    /** 运营/商家回复内容(纯文本, 长度限制 500 字); 回复后前端评价详情页展示 "商家回复:" 板块; 只能回复一次, 不可修改. */
    private String replyContent;

    /** 运营回复时间(Asia/Shanghai 时区); replyContent 写入时同步填充此值; NULL=尚未回复. */
    private LocalDateTime replyAt;
        private Integer deleted = 0;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
