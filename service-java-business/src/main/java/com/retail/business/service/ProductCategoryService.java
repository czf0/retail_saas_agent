package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.CategoryCreateReq;
import com.retail.business.dto.req.CategoryUpdateReq;
import com.retail.business.dto.resp.CategoryCreateResp;
import com.retail.business.dto.resp.CategoryDeleteResp;
import com.retail.business.dto.resp.CategoryResp;
import com.retail.business.dto.resp.CategoryTreeNodeResp;
import com.retail.business.entity.ProductCategory;

import java.util.List;

/**
 * 商品分类服务 (product_category 表).
 * <p>提供分类树状结构维护 (增删改查) 与树形查询能力, 分类采用 parentId 父子关联;
 * 删除分类时校验其下无子分类与关联商品, 否则抛 BizException 拒绝删除.
 */
public interface ProductCategoryService extends IService<ProductCategory> {

    CategoryCreateResp createCategory(CategoryCreateReq req);

    List<CategoryTreeNodeResp> listCategoryTree(boolean activeOnly);

    CategoryResp getCategory(Long id);

    void updateCategory(Long id, CategoryUpdateReq req);

    CategoryDeleteResp deleteCategory(Long id);
}