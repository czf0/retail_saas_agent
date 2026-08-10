package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.ProductCategory;

/**
 * 商品分类 Mapper, 对应 product_category 表.
 * <p>多租户 + 门店隔离; tenant_id 与 store_id 由拦截器注入.
 * <p>基础 CRUD 由 BaseMapper 提供; 树形结构组装在 Service 层完成.
 */
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
}