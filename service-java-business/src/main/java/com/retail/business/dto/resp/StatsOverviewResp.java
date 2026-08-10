package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 后台首页 Dashboard 概览卡片响应(Agent 经营问答首屏摘要复用);当前租户维度的核心对象数 4 项摘要(商品/活动/评价/会员).
 * <p>注意:各 count 为总数(非今日新增);"今日新增"走 SalesSummaryResp/SalesTrendResp 趋势接口.
 */
@Data
public class StatsOverviewResp {

    /** 在售/停用商品总数量(含上下架;仅 deleted = 0). */
    private Long productCount;

    /** 进行中+未开始+已结束活动总数(仅 deleted = 0). */
    private Long promotionCount;

    /** 商品评价总数(含待审核/已通过/已拒绝;软删除不计). */
    private Long reviewCount;

    /** 正常会员总数(status=0 正常;冻结/黑名单不计). */
    private Long memberCount;
}
