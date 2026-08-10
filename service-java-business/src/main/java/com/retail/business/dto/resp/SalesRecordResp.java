package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售明细流水列表行项(销售记录导出/对账);按日 + 商品 + 门店维度聚合的销售日结快照(按 record_date 分桶归档行).
 * <p>Controller: GET /api/v1/reports/sales-records;可按 productId/storeId 范围查询;支持导出 Excel.
 */
@Data
public class SalesRecordResp {

    private Long id;

    /** 门店外键(NULL=租户级跨门店合计行). */
    private Long storeId;

    /** 商品名称快照冗余(用于导出 Excel/搜索,不 join 商品中心). */
    private String productName;

    /** 商品分类名快照(如"服装/上衣";分类报表聚合用). */
    private String category;

    /** 当日该商品销售额 = SUM(subtotal_amount);单位: 元,精度: 分. */
    private BigDecimal salesAmount;

    /** 当日该商品销售件数 = SUM(qty);正整数. */
    private Integer salesQty;

    /** 当日含该商品的订单数 = COUNT(DISTINCT order_id);用于计算连带率(salesQty / orderCount). */
    private Integer orderCount;

    /** 分桶日期(yyyy-MM-dd 00:00:00);周/月报表前端再按天聚合. */
    private LocalDateTime recordDate;
}
