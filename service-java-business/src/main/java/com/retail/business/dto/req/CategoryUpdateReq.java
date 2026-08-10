package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品分类更新请求, 运营后台商品管理 -> 分类管理 -> 编辑分类, 部分更新名称/排序/状态/描述.
 * <p>对应 Controller 路由: PUT /api/v1/products/categories/{categoryId:\d+}; {categoryId} 由 PathVariable 正则守卫(铁律 26).
 * <p>status 仅编辑场景可更新, 由 Service 层经 EnumUtil.fromCode 转换为 CategoryStatus(铁律 10).
 */
@Data
public class CategoryUpdateReq {

    private String name;

    private Integer sortOrder;

    private Integer status;

    private String description;
}