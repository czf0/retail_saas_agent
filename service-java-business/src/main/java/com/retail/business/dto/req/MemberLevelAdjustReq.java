package com.retail.business.dto.req;

import lombok.Data;

/**
 * 会员等级调整请求(会员详情页等级调整/Agent 调等级工具).
 * <p>对应 Controller 路由: PUT /api/v1/members/{id:\\d+}/level; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>幂等: 以 URL {id} 为主键, 多次提交最终一致(最后一次覆盖目标等级).
 */
@Data
public class MemberLevelAdjustReq {

    /** 目标会员 id, 对应 member.id; 定位用, 可空, 与 memberName/phone 二选一. */
    private Long memberId;

    private String memberName;

    private String phone;

    /** MemberLevel 枚举 code: 1=NORMAL 普通 2=SILVER 银卡 3=GOLD 金卡 4=DIAMOND 钻石. */
    private Integer newLevel;

    private String reason;
}
