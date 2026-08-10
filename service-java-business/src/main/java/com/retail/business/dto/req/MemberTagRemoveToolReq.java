package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 取消会员标签 Agent 工具入参.
 * <p>
 * 需会员 ID 和标签 ID 列表, 批量取消指定会员的标签.
 */
@Data
public class MemberTagRemoveToolReq {

    /** 会员 ID */
    private Long memberId;

    /** 待取消的标签 ID 列表 */
    private List<Long> tagIds;
}
