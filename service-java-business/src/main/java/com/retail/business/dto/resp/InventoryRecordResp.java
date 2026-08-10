package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 库存台账列表行项(库存管理列表/导出);展示某商品/仓库/门店维度的当前库存快照 + 安全库存预警(列表页 SQL 内嵌 CASE 计算 belowSafety).
 * <p>详情页点击行查完整 ProductStockResp(含锁定/在途/历史流水分页).
 */
@Data
public class InventoryRecordResp {

    private Long id;

    /** 门店外键(NULL=租户级共享仓);多门店租户列表可按 storeId 筛选. */
    private Long storeId;

    /** 商品名称冗余(Service 层回填,消除前端 "商品 #id" 数据孤岛). */
    private String productName;

    /** 仓库名(如"中心仓"/"门店1号仓";多仓库租户按仓库区分库存). */
    private String warehouse;

    /** 当前可用库存 = 总库存 - 已锁定(正数). */
    private Integer stockQty;

    /** 安全库存阈值;SKU 级优先,SPU 级兜底. */
    private Integer safetyStock;

    /** 计算字段(SQL 内嵌 CASE WHEN):true = stockQty < safetyStock → 前端红标预警. */
    private Boolean belowSafety;
}
