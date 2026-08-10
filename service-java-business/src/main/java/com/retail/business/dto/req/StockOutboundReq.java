package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品出库请求(领用/报废/调拨出库/Agent 出库工具).
 * <p>对应 Service 方法: StockService.outbound(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class StockOutboundReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 有规格商品定位. */
    private Long skuId;

    private String skuCode;

    /** 出库数量; 正整数, 不能为 0 或负数; Service 层校验不超过可用库存, 超卖抛 ParamException. */
    private Integer qty;

    /** StockBizType 枚举 code: 1=ORDER 订单 2=PURCHASE 采购 3=ADJUST 调整 4=REFUND 退款 5=MANUAL 手工; 默认 5=MANUAL. */
    private Integer bizType;

    /** 关联业务单据号; 可空. */
    private String bizNo;

    private String remark;
}
