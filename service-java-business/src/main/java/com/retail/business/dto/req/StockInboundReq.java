package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品入库请求(采购到货/退货回滚/补货到货/Agent 入库工具).
 * <p>对应 Service 方法: StockService.inbound(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class StockInboundReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 有规格商品定位. */
    private Long skuId;

    private String skuCode;

    /** 入库数量; 正整数, 不能为 0 或负数. */
    private Integer qty;

    /** StockBizType 枚举 code: 1=ORDER 订单 2=PURCHASE 采购 3=ADJUST 调整 4=REFUND 退款 5=MANUAL 手工; 默认 2=PURCHASE. */
    private Integer bizType;

    /** 关联业务单据号; 采购单号/退货单号等, 可空. */
    private String bizNo;

    private String remark;
}
