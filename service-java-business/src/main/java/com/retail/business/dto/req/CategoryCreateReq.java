package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品分类创建请求(运营后台商品管理 -> 分类管理 -> 新增分类).
 * <p>对应 Controller 路由: POST /api/v1/categories; status 字段 Service 层赋默认值(CategoryStatus.ENABLED=1, 铁律 6),
 * CreateReq 不承载 status.
 */
@Data
public class CategoryCreateReq {

    /** 父分类 id, 对应 product_category.id; 0=根节点(顶级分类); 树形 parentId=0 为根节点; Agent 工具支持按分类名转 ID(CategoryAgentToolService). */
    private Long parentId;

    private String name;

    /** 排序值; 同级分类按此值升序排列, 默认 0; 数值越小越靠前. */
    private Integer sortOrder = 0;

    private String description;
}
