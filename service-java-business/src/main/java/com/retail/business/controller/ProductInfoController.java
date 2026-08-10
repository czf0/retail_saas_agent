package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ProductCreateReq;
import com.retail.business.dto.req.ProductListReq;
import com.retail.business.dto.req.ProductOffShelfReq;
import com.retail.business.dto.req.ProductOffShelfToolReq;
import com.retail.business.dto.req.ProductOnShelfToolReq;
import com.retail.business.dto.req.ProductPriceAdjustReq;
import com.retail.business.dto.req.ProductPriceAdjustToolReq;
import com.retail.business.dto.req.ProductUpdateReq;
import com.retail.business.dto.resp.ProductBatchActionResp;
import com.retail.business.dto.resp.ProductCreateResp;
import com.retail.business.dto.resp.ProductDeleteResp;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductPriceAdjustResp;
import com.retail.business.dto.resp.ProductResp;
import com.retail.business.dto.resp.ProductUpdateResp;
import com.retail.business.service.ProductInfoService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 商品管理接口.
 * <p>路由前缀 /api/v1/products.product_info 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离;
 * 数据权限(角色 data_scope=SELF)通过 DataScopeHelper 在 Service 层附加 create_by 过滤,仅本人创建的商品可见.
 * <p>权限校验基于 @SaCheckPermission("business:product:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>注意:/off-shelf,/on-shelf,/price-adjust 为字面量路径,须在 /{productId} 之前注册以保证优先匹配.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductInfoController {

    private final ProductInfoService productInfoService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public ProductInfoController(ProductInfoService productInfoService) {
        this.productInfoService = productInfoService;
    }

    /**
     * 创建商品(状态默认上架 ON_SHELF,铁律 6:CreateReq 禁 status 字段).
     * <p>category 路径字符串根据 categoryId 从 product_category 反查父级链拼接.
     */
    @PostMapping("")
    @SaCheckPermission("business:product:add")
    public R<ProductCreateResp> create(@RequestBody ProductCreateReq req) {
        return R.ok(productInfoService.createProduct(req));
    }

    /**
     * 分页查询商品列表(多条件过滤:关键词 / 分类 / 品牌 / 状态 / 价格区间 / 低库存 / 有货 / 清仓 / 创建时间).
     */
    @GetMapping("")
    @SaCheckPermission("business:product:query")
    public R<PageResp<ProductListItemResp>> list(ProductListReq req) {
        return R.ok(productInfoService.listProducts(req));
    }

    /**
     * 查询商品详情(含 SKU,分类,品牌,描述等完整信息).
     */
    @GetMapping("/{productId:\\d+}")
    @SaCheckPermission("business:product:query")
    public R<ProductResp> get(@PathVariable("productId") Long productId) {
        return R.ok(productInfoService.getProduct(productId));
    }

    /**
     * 修改商品(部分更新:名称 / 价格 / 成本 / 描述 / 图片 / 库存 / 安全库存 / 分类 / SPU编码 / 品牌).
     */
    @PutMapping("/{productId:\\d+}")
    @SaCheckPermission("business:product:edit")
    public R<ProductUpdateResp> update(@PathVariable("productId") Long productId,
                                       @RequestBody ProductUpdateReq req) {
        return R.ok(productInfoService.updateProduct(productId, req));
    }

    /**
     * 删除商品(逻辑删除,由 BaseServiceImpl 填充 delete_at / delete_by 审计字段).
     */
    @DeleteMapping("/{productId:\\d+}")
    @SaCheckPermission("business:product:remove")
    public R<ProductDeleteResp> delete(@PathVariable("productId") Long productId) {
        return R.ok(productInfoService.deleteProduct(productId));
    }

    /** 单商品下架:复用 batchOffShelf 包装单条 productIds 列表 */
    @PostMapping("/{productId:\\d+}/off-shelf")
    @SaCheckPermission("business:product:offShelf")
    public R<ProductBatchActionResp> offShelf(@PathVariable("productId") Long productId,
                                              @RequestBody(required = false) ProductOffShelfReq body) {
        ProductOffShelfToolReq req = new ProductOffShelfToolReq();
        req.setProductIds(Collections.singletonList(productId));
        if (body != null) req.setReason(body.getReason());
        return R.ok(productInfoService.batchOffShelf(req));
    }

    /** 单商品上架:复用 batchOnShelf 包装单条 productIds 列表 */
    @PostMapping("/{productId:\\d+}/on-shelf")
    @SaCheckPermission("business:product:onShelf")
    public R<ProductBatchActionResp> onShelf(@PathVariable("productId") Long productId) {
        ProductOnShelfToolReq req = new ProductOnShelfToolReq();
        req.setProductIds(Collections.singletonList(productId));
        return R.ok(productInfoService.batchOnShelf(req));
    }

    /** 单商品调价:复用 priceAdjust,定位 productId */
    @PostMapping("/{productId:\\d+}/price-adjust")
    @SaCheckPermission("business:product:priceAdjust")
    public R<ProductPriceAdjustResp> priceAdjust(@PathVariable("productId") Long productId,
                                                  @RequestBody ProductPriceAdjustReq body) {
        ProductPriceAdjustToolReq req = new ProductPriceAdjustToolReq();
        req.setProductId(productId);
        req.setNewPrice(body.getNewPrice());
        req.setNewCost(body.getNewCost());
        req.setReason(body.getReason());
        return R.ok(productInfoService.priceAdjust(req));
    }
}
