package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.OperationResultResp;
import com.retail.business.dto.req.ProductSkuCreateReq;
import com.retail.business.dto.req.ProductSkuUpdateReq;
import com.retail.business.dto.resp.ProductSkuListItemResp;
import com.retail.business.dto.resp.ProductSkuResp;
import com.retail.business.service.ProductSkuService;
import com.retail.core.dto.PageResp;
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

/**
 * 商品 SKU 管理接口.
 * <p>路由前缀 /api/v1/products/{productId}/skus,按商品维度管理其 SKU 列表.product_sku 表为多租户表,
 * tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:sku:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>路径 productId 以正则 {@code \d+} 守卫,避免被误解析为占位符路径.
 */
@RestController
@RequestMapping("/api/v1/products/{productId:\\d+}/skus")
public class ProductSkuController {

    private final ProductSkuService productSkuService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public ProductSkuController(ProductSkuService productSkuService) {
        this.productSkuService = productSkuService;
    }

    /**
     * 创建 SKU(多规格商品如颜色 / 尺码 / 规格组合).
     * <p>以路径变量 productId 为 SKU 归属商品 ID,覆盖请求体中的同名字段.
     * 状态默认 ON_SHELF(铁律 6:CreateReq 禁 status 字段).
     */
    @PostMapping
    @SaCheckPermission("business:sku:add")
    public R<ProductSkuResp> create(@PathVariable Long productId,
                                    @RequestBody ProductSkuCreateReq req) {
        // 以路径 productId 为准,确保 SKU 归属正确
        req.setProductId(productId);
        return R.ok(productSkuService.createSku(req));
    }

    /**
     * 分页查询某商品的 SKU 列表(按状态 / 关键词过滤).
     */
    @GetMapping
    @SaCheckPermission("business:sku:query")
    public R<PageResp<ProductSkuListItemResp>> list(@PathVariable Long productId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword) {
        return R.ok(productSkuService.listSkus(productId, status, keyword));
    }

    /**
     * 查询 SKU 详情(含 SKU 编码 / 名称 / 价格 / 成本 / 状态 / 规格图片).
     */
    @GetMapping("/{skuId:\\d+}")
    @SaCheckPermission("business:sku:query")
    public R<ProductSkuResp> detail(@PathVariable Long skuId) {
        return R.ok(productSkuService.getSku(skuId));
    }

    /**
     * 修改 SKU(部分更新:编码 / 名称 / 价格 / 成本 / 状态 / 规格图片).
     */
    @PutMapping("/{skuId:\\d+}")
    @SaCheckPermission("business:sku:edit")
    public R<ProductSkuResp> update(@PathVariable Long skuId,
                                    @RequestBody ProductSkuUpdateReq req) {
        return R.ok(productSkuService.updateSku(skuId, req));
    }

    /**
     * 删除 SKU(逻辑删除,由 BaseServiceImpl 填充 delete_at / delete_by 审计字段).
     */
    @DeleteMapping("/{skuId:\\d+}")
    @SaCheckPermission("business:sku:remove")
    public R<OperationResultResp> delete(@PathVariable Long skuId) {
        return R.ok(productSkuService.deleteSku(skuId));
    }
}
