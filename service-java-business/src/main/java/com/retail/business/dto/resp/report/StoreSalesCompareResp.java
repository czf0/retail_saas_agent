package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店销售对比报表行项(运营后台经营看板 → 多门店横向对比);按门店维度聚合销售指标,每行 = 1 个门店.
 * <p>统计口径:
 * <ul>
 *   <li>salesAmount: SUM(order_info.pay_amount) WHERE order_info.store_id = 门店.id;基于 pay_time;order_status 为已支付及以上(排除 PENDING/CANCELED).</li>
 *   <li>orderCount: COUNT(DISTINCT order_info.id),同订单集.</li>
 *   <li>avgOrderValue (客单价): salesAmount / orderCount;报表层除法,精度 2 位小数.</li>
 * </ul>
 * <p>排除条件: 已停用门店(store.status = DISABLED)仍展示历史数据但前端可配置过滤;tenant_id 过滤.
 * <p>返回值: 每行 = 1 个门店;默认按 salesAmount DESC 降序;store_id = NULL 行汇总租户级跨门店订单(可配置).
 */
@Data
public class StoreSalesCompareResp {

    /** 门店ID */
    private Long storeId;

    /** 门店名称 */
    private String storeName;

    /** 销售金额合计 */
    private BigDecimal salesAmount;

    /** 订单数 */
    private Integer orderCount;

    /** 客单价 = 销售金额 / 订单数 */
    private BigDecimal avgOrderValue;
}
