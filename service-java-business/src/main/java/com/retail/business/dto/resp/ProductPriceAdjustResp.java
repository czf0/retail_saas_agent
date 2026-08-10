package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品改价操作结果响应(SPU 默认价 + SKU 价 + 成本调整);返回变更前后价格 + 差价(HITL 人工复核 / Agent 回述确认).
 * <p>幂等:同一 adjustBatchNo 重复提交返回首次结果,不会重复写 price_adjust_log;改价完成后自动触发 Search ES 价格索引重建.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPriceAdjustResp extends OperationResultResp {

    /** 商品ID */
    private Long productId;

    /** 商品名 */
    private String productName;

    /** 调整前售价(NULL=此前无售价) */
    private BigDecimal oldPrice;

    /** 调整后售价 */
    private BigDecimal newPrice;

    /** 售价差价(新 - 旧) */
    private BigDecimal priceDiff;

    /** 调整前成本 */
    private BigDecimal oldCost;

    /** 调整后成本 */
    private BigDecimal newCost;

    /** 成本差价(新 - 旧) */
    private BigDecimal costDiff;
}
