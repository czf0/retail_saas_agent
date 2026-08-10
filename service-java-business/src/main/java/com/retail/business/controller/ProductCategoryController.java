package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.OperationResultResp;
import com.retail.business.dto.req.CategoryCreateReq;
import com.retail.business.dto.req.CategoryUpdateReq;
import com.retail.business.dto.resp.CategoryCreateResp;
import com.retail.business.dto.resp.CategoryDeleteResp;
import com.retail.business.dto.resp.CategoryResp;
import com.retail.business.dto.resp.CategoryTreeNodeResp;
import com.retail.business.service.ProductCategoryService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类管理接口.
 * <p>路由前缀 /api/v1/products/categories.product_category 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * 权限校验基于 @SaCheckPermission("business:category:*") 注解(AOP),
 * 与 sys_menu 商品分类按钮 perms 字段、前端 PermissionButton/v-permission 保持一致.
 * <p>分类支持两级树:一级(parent_id=null)+ 二级(parent_id 指向一级),删除一级分类时子分类级联迁移到父级(null).
 */
@RestController
@RequestMapping("/api/v1/products/categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    /**
     * 创建商品分类(支持一级 / 二级).
     * <p>二级分类需指定 parent_id 关联一级分类,状态默认为启用.
     */
    @PostMapping("")
    @SaCheckPermission("business:category:add")
    public R<CategoryCreateResp> create(@RequestBody CategoryCreateReq req) {
        return R.ok(productCategoryService.createCategory(req));
    }

    /**
     * 查询商品分类树(前端商品选择器展示).
     * <p>activeOnly=true 仅返回启用状态分类(供业务下拉使用),false 返回全部(含停用,供后台维护).
     */
    @GetMapping("")
    @SaCheckPermission("business:category:query")
    public R<List<CategoryTreeNodeResp>> tree(
            @RequestParam(value = "activeOnly", required = false, defaultValue = "false") Boolean activeOnly) {
        return R.ok(productCategoryService.listCategoryTree(Boolean.TRUE.equals(activeOnly)));
    }

    /**
     * 查询分类详情(含父级链信息).
     */
    @GetMapping("/{categoryId:\\d+}")
    @SaCheckPermission("business:category:query")
    public R<CategoryResp> get(@PathVariable("categoryId") Long categoryId) {
        return R.ok(productCategoryService.getCategory(categoryId));
    }

    /**
     * 修改商品分类(部分更新:名称 / 排序 / 状态 / 父级).
     */
    @PutMapping("/{categoryId:\\d+}")
    @SaCheckPermission("business:category:edit")
    public R<OperationResultResp> update(@PathVariable("categoryId") Long categoryId,
                                          @RequestBody CategoryUpdateReq req) {
        productCategoryService.updateCategory(categoryId, req);
        OperationResultResp result = new OperationResultResp();
        result.setSuccess(true);
        result.setMessage("分类更新成功");
        return R.ok(result);
    }

    /**
     * 删除商品分类(逻辑删除,一级分类含子分类时子分类级联迁移到根级 parent_id=null).
     */
    @DeleteMapping("/{categoryId:\\d+}")
    @SaCheckPermission("business:category:remove")
    public R<CategoryDeleteResp> delete(@PathVariable("categoryId") Long categoryId) {
        return R.ok(productCategoryService.deleteCategory(categoryId));
    }
}
