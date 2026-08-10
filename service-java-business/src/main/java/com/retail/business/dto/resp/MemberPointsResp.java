package com.retail.business.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 会员积分账户总览响应(会员中心"我的积分"页 / 后台会员详情积分卡片);聚合当前余额 + 累计 earn/exchange + 近 30 天流水列表.
 * <p>Controller: GET /api/v1/members/{memberId:\\d+}/points;积分过期清零积分流水 changeType=ADJUST 扣减(带 remark 说明原因).
 */
@Data
public class MemberPointsResp {

    /** 会员ID */
    private Long memberId;

    /** 当前积分余额 */
    private Integer currentPoints;

    /** 累计获取积分(earn + gift 类型之和) */
    private Integer totalEarned;

    /** 累计兑换积分(exchange 类型绝对值之和) */
    private Integer totalExchanged;

    /** 近30天变动流水(按时间倒序) */
    private List<PointsLogResp> recentLogs;
}
