package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 SPU 创建请求(运营后台商品管理 -> 新增 SPU).
 * <p>对应 Controller 路由: POST /api/v1/products; status 字段 Service 层赋默认值(ProductStatus.OFF_SHELF=0, 铁律 6),
 * CreateReq 不承载 status.
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class ProductCreateReq {

    private String name;

    /** 目标分类 id, 对应 product_category.id; 树形 parentId=0 为根节点; Agent 工具支持按分类名转 ID(CategoryAgentToolService). */
    private Long categoryId;

    private String category;

    private String spuCode;

    private String brand;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); 商品 SPU 标准售价. */
    private BigDecimal price;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); 商品成本价(内部核算用, 不对外展示). */
    private BigDecimal cost;

    private String description;

    private String imageUrl;

    /** 初始库存数量; 默认 0; 非负整数. */
    private Integer stockQty = 0;

    /** 安全库存预警阈值; 当可用库存 <= 此值时触发临期预警, 默认 0=不预警. */
    private Integer safetyStock = 0;
}
