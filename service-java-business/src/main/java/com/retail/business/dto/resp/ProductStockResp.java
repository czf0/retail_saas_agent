package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品库存账户响应.
 * <p>
 * belowSafety 为计算字段(available_qty &lt; safety_stock),实体无此字段,
 * 由 Service 转化后手动 setter(参考 {@link InventoryRecordResp} 的 belowSafety 设计).
 */
@Data
public class ProductStockResp {

    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 门店ID(NULL=租户中心仓) */
    private Long storeId;

    /** 商品ID */
    private Long productId;

    /** 商品名称(由 Service 批量回填,避免 N+1 查询) */
    private String productName;

    /** SKU ID(无规格商品为 null) */
    private Long skuId;

    /** SKU 编码(由 Service 批量回填,无规格商品为 null) */
    private String skuCode;

    /** 可用库存 */
    private Integer availableQty;

    /** 锁定库存(待付款订单占用) */
    private Integer lockedQty;

    /** 在途库存(采购在途) */
    private Integer inTransitQty;

    /** 安全库存阈值 */
    private Integer safetyStock;

    /** 是否低于安全库存(计算字段,由 Service 手动 setter) */
    private Boolean belowSafety;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 门店名称(由 Service 批量回填 sys_store.store_name,消除前端显示 "门店 #id" 数据孤岛) */
    private String storeName;
}
