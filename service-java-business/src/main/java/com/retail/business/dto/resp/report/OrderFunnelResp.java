package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单转化漏斗报表行项(经营看板 → 订单漏斗图);统计指定时间范围内订单在各状态阶段的留存数与转化率.
 * <p>统计口径:
 * <ul>
 *   <li>阶段定义 (按 order_status 映射): pending=待付(1), paid=已付(2), shipped=已发货(3), completed=已完成(4);退款状态(5/6)不计入漏斗正向阶段.</li>
 *   <li>count: 该阶段的订单数量 = COUNT(order_id) WHERE status = 当前阶段 AND create_time 在查询范围.</li>
 *   <li>percentage: count / pending阶段count * 100;相对待付阶段总订单.</li>
 * </ul>
 * <p>排除条件: 统计基于 create_time 下单时间(非 pay_time);仅漏斗内 4 个正向状态.
 * <p>返回值: 每行 = 1 个漏斗阶段;按阶段顺序 pending→paid→shipped→completed;4 行固定.
 */
@Data
public class OrderFunnelResp {

    /** 漏斗阶段名称(pending/paid/shipped/completed) */
    private String stage;

    /** 该阶段订单数 */
    private Integer count;

    /** 占待付订单总数的百分比(0-100) */
    private BigDecimal percentage;
}
