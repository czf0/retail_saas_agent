package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级分布报表行项(运营后台会员分析 → 等级分布饼图);按 MemberLevel 统计各等级当前有效会员数.
 * <p>统计口径:
 * <ul>
 *   <li>level 枚举映射: 1=NORMAL(普通会员), 2=SILVER(银卡), 3=GOLD(金卡), 4=DIAMOND(钻石);具体见 {@link com.retail.business.enums.MemberLevelEnum}.</li>
 *   <li>memberCount: COUNT(member.id) WHERE member.level = 等级值 AND member.status = 0(正常) AND deleted = 0;实时快照(非历史分桶).</li>
 *   <li>percentage: memberCount / SUM(所有等级.memberCount) * 100;求和 = 100%.</li>
 * </ul>
 * <p>排除条件: member.status != 0(冻结/黑名单)不计;已软删除会员不计;tenant_id 过滤.
 * <p>返回值: 每行 = 1 个会员等级;固定 4 行(按等级值升序 1→2→3→4).
 */
@Data
public class MemberLevelDistResp {

    /** 会员等级(normal普通/silver银卡/gold金卡/diamond钻石) */
    private Integer level;

    /** 该等级会员数量 */
    private Integer memberCount;

    /** 占总会员数百分比(0-100) */
    private BigDecimal percentage;
}
