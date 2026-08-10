package com.retail.business.dto.req;

import lombok.Data;

/**
 * 会员资料更新请求(会员详情页编辑/Agent 改会员资料工具).
 * <p>对应 Controller 路由: PUT /api/v1/members/{id:\\d+}; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>幂等: 以 URL {id} 为主键, 多次提交最终一致(最后一次覆盖); null 字段不更新.
 */
@Data
public class MemberUpdateReq {

    /** 目标会员 id, 对应 member.id; 定位用, 可空, 与 memberName/phone 二选一. */
    private Long memberId;

    private String memberName;

    private String phone;

    private String newName;

    private String newPhone;
}
