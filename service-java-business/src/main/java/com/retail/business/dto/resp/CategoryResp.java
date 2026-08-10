package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 商品分类详情页响应;聚合单节点分类基础信息 + 父分类名(前端编辑回显);分类树走 CategoryTreeNodeResp.
 * <p>Controller: GET /api/v1/categories/{id:\\d+};{id} 正则守卫.
 */
@Data
public class CategoryResp {

    private Long id;

    /** 父分类外键(自关联 product_category.id;根节点 parentId=0). */
    private Long parentId;

    /** 父分类名称(Service 层查询填充,根分类为 null);前端面包屑展示. */
    private String parentName;

    private String name;

    /** 同级排序号(升序 ASC;越小越靠前;前端可拖动排序更新此值). */
    private Integer sortOrder;

    /** 分类状态:1=ENABLED(启用,前台可展示可下单) 0=DISABLED(停用,前台隐藏). */
    private Integer status;

    private String description;
}
