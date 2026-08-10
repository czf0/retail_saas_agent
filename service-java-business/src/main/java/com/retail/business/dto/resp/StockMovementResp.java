package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水响应.
 * <p>
 * 流水为不可变历史记录,仅含创建时间 / 创建人,无更新与删除审计字段.
 */
@Data
public class StockMovementResp {

    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 门店ID(NULL=租户级) */
    private Long storeId;

    /** 商品ID */
    private Long productId;

    /** SKU ID(无规格商品为 null) */
    private Long skuId;

    /** 库存账户ID */
    private Long stockId;

    /** 变动类型:inbound/outbound/adjust/reservation/release/check_gain/check_loss */
    private Integer movementType;

    /** 变动数量(正数增加,负数减少) */
    private Integer changeQty;

    /** 变动前可用库存 */
    private Integer beforeQty;

    /** 变动后可用库存 */
    private Integer afterQty;

    /** 业务类型:order/purchase/adjust/refund/manual */
    private Integer bizType;

    /** 关联业务单据号(orderNo / purchaseNo / adjustNo;前端"关联单据"列点击跳转对应详情页). */
    private String bizNo;

    private String remark;

    private LocalDateTime createdAt;

    private String createBy;

    /** 商品名称(由 Service 批量回填 product_info.name,消除前端 "商品 #id" 数据孤岛) */
    private String productName;

    /** SKU编码(由 Service 批量回填 product_sku.sku_code,无规格商品为 null) */
    private String skuCode;

    /** SKU名称(由 Service 批量回填 product_sku.sku_name,如"红色-XL") */
    private String skuName;

    /** 门店名称(由 Service 批量回填 sys_store.store_name,消除前端 "门店 #id" 数据孤岛) */
    private String storeName;
}
