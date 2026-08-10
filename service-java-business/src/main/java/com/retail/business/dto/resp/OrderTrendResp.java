package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单趋势报表行项(运营报表 → 订单趋势查询接口返回);按日/周/月 + 门店分桶聚合订单与退款核心指标.
 * <p>Controller: GET /api/v1/reports/order-trend;默认按 stat_date ASC 升序近 30 天.
 */
@Data
public class OrderTrendResp {

    private Long id;

    /** 门店ID(NULL=租户级汇总,跨门店合计行);仅多门店租户返回多条. */
    private Long storeId;

    /** 统计日期分桶(yyyy-MM-dd 00:00:00);周/月粒度时截断为起始日 00:00. */
    private LocalDateTime statDate;

    /** 订单数(COUNT(DISTINCT order_id) WHERE pay_time 在分桶区间;不含 CANCELED/PENDING). */
    private Integer orderCount;

    /** 订单金额(SUM(pay_amount),同订单集;单位: 元,精度: 分). */
    private BigDecimal orderAmount;

    /** 退款笔数(COUNT(refund_id) WHERE refund_status = REFUNDED AND refund_time 在分桶区间). */
    private Integer refundCount;
}
