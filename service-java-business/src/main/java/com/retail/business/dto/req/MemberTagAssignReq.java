package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 给会员批量分配标签请求.
 * <p>Service 层会自动去重:先查现有关系,过滤掉已存在的标签,仅插入新关系.
 */
@Data
public class MemberTagAssignReq {

    /** 会员ID */
    private Long memberId;

    /** 待分配的标签ID列表 */
    private List<Long> tagIds;
}
