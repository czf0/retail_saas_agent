package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 会员创建请求(会员中心 -> 新增会员/Agent 建档工具).
 * <p>对应 Controller 路由: POST /api/v1/members; status 字段 Service 层赋默认值(启用, 铁律 6),
 * CreateReq 不承载 status.
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class MemberCreateReq {

    private String name;

    private String phone;

    /** MemberLevel 枚举 code: 1=NORMAL 普通 2=SILVER 银卡 3=GOLD 金卡 4=DIAMOND 钻石; 可空, 默认 1=NORMAL. */
    private Integer level;

    /** 初始积分; 可空, 默认 0; 非负整数. */
    private Integer points;

    /** 初始标签 id 列表, 对应 member_tag.id; 可空, 创建成功后由 MemberTagService 分配. */
    private List<Long> tagIds;
}
