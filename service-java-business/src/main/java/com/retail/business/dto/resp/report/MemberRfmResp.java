package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员 RFM 模型报表行项(运营后台会员分析 → RFM 分群饼图/列表);按 RFM 三维度将会员分桶,每行 = 1 个客群.
 * <p>统计口径:
 * <ul>
 *   <li>R (Recency): 最近一次下单距今天数(基于 pay_time 支付时间,非下单 create_time);取消订单不计.</li>
 *   <li>F (Frequency): 近 30 天已完成订单数(order_status = COMPLETED,含部分退款订单不扣).</li>
 *   <li>M (Monetary): 近 30 天累计实付金额(pay_amount,SUM 不含运费,退款审核通过反向扣减).</li>
 *   <li>RFM 分桶: 每维度取中位数为高低阈值,2x2x2 = 8 个客群(重要价值/重要发展/重要保持/重要挽留/一般价值/一般发展/一般保持/一般挽留).</li>
 * </ul>
 * <p>排除条件: status = CANCELED(5) 订单;tenant_id = 当前上下文租户;会员表 status = 0(正常).
 * <p>返回值: 每行 = 1 个 RFM 客群(按 segment 分组),排序默认按 memberCount 降序;percentage 求和 = 100%.
 */
@Data
public class MemberRfmResp {

    /** 客群分类名称(如"重要价值客户","一般发展客户"等) */
    private String segment;

    /** 该客群会员数量 */
    private Integer memberCount;

    /** 占总会员数百分比(0-100) */
    private BigDecimal percentage;

    /** 该客群平均消费金额 */
    private BigDecimal avgSpent;
}
