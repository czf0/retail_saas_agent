package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 工具专用入参: 商品改价/改成本工具(product:price-adjust, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: ProductService.adjustPrice(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 * <p>商品定位: 支持 productId/name/spuCode 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class ProductPriceAdjustToolReq {

    /** 目标商品 id, 对应 product.id; 优先使用, 三选一定位. */
    private Long productId;

    private String name;

    private String spuCode;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); 新售价, 至少传 newPrice/newCost 之一. */
    private BigDecimal newPrice;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); 新成本价(内部核算用), 至少传 newPrice/newCost 之一. */
    private BigDecimal newCost;

    private String reason;
}
