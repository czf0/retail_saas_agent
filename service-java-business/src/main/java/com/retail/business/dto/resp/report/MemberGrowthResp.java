package com.retail.business.dto.resp.report;

import lombok.Data;

/**
 * 会员增长趋势报表行项(运营后台会员分析 → 增长趋势图);按日/周/月分桶统计新增与活跃会员数.
 * <p>统计口径:
 * <ul>
 *   <li>newMembers (新增会员): COUNT(member.id) WHERE DATE(member.create_time) = stat_date;首次注册/导入时间.</li>
 *   <li>activeMembers (活跃会员): COUNT(DISTINCT member_id),当日满足以下任一: ①有已支付订单 pay_time;②积分流水 points_log;③登录/浏览行为埋点.</li>
 *   <li>date 分桶: yyyy-MM-dd;周/月 = 截断为周起始日/月起始日.</li>
 * </ul>
 * <p>排除条件: member.status = 0(正常);软删除 deleted = 1 的会员不计新增与活跃.
 * <p>返回值: 每行 = 1 个时间桶;按 date ASC;缺省近 90 天.
 */
@Data
public class MemberGrowthResp {

    /** 日期(yyyy-MM-dd 格式字符串) */
    private String date;

    /** 当日新增会员数 */
    private Integer newMembers;

    /** 当日活跃会员数(当日有下单或积分变动) */
    private Integer activeMembers;
}
