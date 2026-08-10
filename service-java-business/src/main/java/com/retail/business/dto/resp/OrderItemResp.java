package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单项明细响应(1:N 嵌入 OrderResp.items / RefundResp.itemList);订单项为下单当时商品快照,商品后续变更不回写历史.
 * <p>快照字段:productName / skuSpec / unitPrice / costPrice 均为下单时刻值;可独立于商品中心变更展示订单历史.
 */
@Data
public class OrderItemResp {

    private Long id;

    /** 商品 SPU 外键(order_item.product_id;快照冗余 product_name/sku). */
    private Long productId;

    /** 下单时刻商品名快照(冗余;order_item.product_name 不可变). */
    private String productName;

    /** 下单时刻分类名快照(冗余;用于分类报表聚合). */
    private String category;

    /** 商品 SKU 外键;无规格 SPU 的单 SKU 仍填 sku_id. */
    private Long skuId;

    /** SKU 编码快照(冗余;用于 ERP 对接核对). */
    private String skuCode;

    /** SKU 规格文本快照(冗余;用户退款申请时展示"买的是什么规格"). */
    private String skuSpec;

    /** 下单时单价快照(单位: 元,精度: 分;非商品中心现价,不可变历史). */
    private BigDecimal unitPrice;

    /** 购买件数(正整数,>=1;部分退款时此 qty 不扣减,由 refundQty 反映已退). */
    private Integer qty;

    /** 小计金额 = unitPrice × qty(下单时计算,快照;非当前重算).单位: 元. */
    private BigDecimal subtotal;

    /** 下单时成本价快照(后台毛利计算基础;前端仅运营/财务有权限可见).单位: 元. */
    private BigDecimal costPrice;

    /** 计算字段(聚合 refund_item):该 item 已退款件数;refundQty = qty 表示全额退完.列表页展示"可退件数 = qty - refundQty". */
    private Integer refundQty;
}
