package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.StockAdjustReq;
import com.retail.business.dto.req.StockMovementQueryReq;
import com.retail.business.dto.req.StockQueryReq;
import com.retail.business.dto.resp.ProductStockResp;
import com.retail.business.dto.resp.StockAdjustResp;
import com.retail.business.dto.resp.StockMovementResp;
import com.retail.business.service.StockService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品库存账户与流水接口.
 * <p>
 * 路由前缀 /api/v1/stocks.product_stock / stock_movement 均为多租户 + 门店隔离表,
 * tenant_id / store_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>
 * 注意:/movements,/adjust 为字面量路径,须在 /{stockId} 之前注册以保证优先匹配.
 */
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** 库存账户列表(分页,支持按商品/SKU/低库存筛选) */
    @GetMapping("")
    @SaCheckPermission("business:stock:query")
    public R<PageResp<ProductStockResp>> list(StockQueryReq req) {
        return R.ok(stockService.listStocks(req));
    }

    /** 库存流水列表(分页,支持按商品/类型/单据号/时间范围筛选) */
    @GetMapping("/movements")
    @SaCheckPermission("business:stock:movement")
    public R<PageResp<StockMovementResp>> movements(StockMovementQueryReq req) {
        return R.ok(stockService.listMovements(req));
    }

    /** 手动调整库存(事务内更新账户 + 写流水) */
    @PostMapping("/adjust")
    @SaCheckPermission("business:stock:adjust")
    public R<StockAdjustResp> adjust(@RequestBody StockAdjustReq req) {
        return R.ok(stockService.adjust(req));
    }

    /** 库存账户详情(按账户ID) */
    @GetMapping("/{stockId:\\d+}")
    @SaCheckPermission("business:stock:query")
    public R<ProductStockResp> detail(@PathVariable Long stockId) {
        return R.ok(stockService.getStockById(stockId));
    }
}
