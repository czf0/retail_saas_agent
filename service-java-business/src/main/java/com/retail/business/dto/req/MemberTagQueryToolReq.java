package com.retail.business.dto.req;

import lombok.Data;

/**
 * 会员标签查询 Agent 工具入参.
 * <p>
 * 支持按关键词模糊搜索标签列表, 或查询指定会员的标签.
 */
@Data
public class MemberTagQueryToolReq {

    /** 标签关键词模糊查询 (查询标签列表时使用) */
    private String keyword;

    /** 会员 ID (查询会员标签时使用) */
    private Long memberId;
}
