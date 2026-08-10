package com.retail.business.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 商品分类树节点响应(自递归 children);商品列表筛选分类树 / 分类管理左侧树 / 促销活动选分类弹层 等通用返回.
 * <p>Controller: GET /api/v1/categories/tree;按 sortOrder 升序深度优先.
 */
@Data
public class CategoryTreeNodeResp {

    private Long id;

    /** 父节点ID(0=根). */
    private Long parentId;

    /** 父分类名称(Service 层从 nodeMap 填充,根分类为 null). */
    private String parentName;

    /** 分类节点展示文本. */
    private String name;

    /** 同级排序(升序). */
    private Integer sortOrder;

    /** 状态:1=启用 0=停用(停用节点前端可灰显). */
    private Integer status;

    /** 子分类节点列表(1:N 递归;叶子 = [] 空列表;最多 3 层深度,防止树爆炸). */
    private List<CategoryTreeNodeResp> children;

    /** 计算字段(SQL COUNT(product_info) 内嵌):该分类(含子孙分类)下在售商品数;前端用于展示"(120)"后缀. */
    private Integer productCount;
}
