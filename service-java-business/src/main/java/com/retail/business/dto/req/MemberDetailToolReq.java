package com.retail.business.dto.req;

import lombok.Data;

/**
 * 会员详情查询 Agent 工具入参.
 * <p>
 * 支持按会员ID或手机号定位,业务人员可用手机号查询,无需知道会员ID.
 */
@Data
public class MemberDetailToolReq {

    /** 会员ID(可空,与手机号二选一) */
    private Long memberId;

    /** 手机号(可空,与会员ID二选一,精确匹配) */
    private String phone;
}
