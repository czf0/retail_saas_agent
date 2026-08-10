package com.retail.business.agent;

import com.retail.business.dto.req.MemberTagAssignReq;
import com.retail.business.dto.req.MemberTagQueryToolReq;
import com.retail.business.dto.req.MemberTagRemoveToolReq;
import com.retail.business.dto.resp.MemberTagResp;
import com.retail.business.service.MemberTagService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;

import java.util.List;

/**
 * 会员标签 Agent 工具服务 (business="membertag").
 * <p>
 * 聚合会员标签域的工具方法, 复用 {@link MemberTagService} 现有业务逻辑:
 * <ul>
 *   <li>{@code membertag:query}        — 查询标签列表 (只读, 关键词搜索);</li>
 *   <li>{@code membertag:member_tags}  — 查询会员已分配的标签 (只读);</li>
 *   <li>{@code membertag:assign}       — 给会员批量分配标签 (破坏性, HITL 审批);</li>
 *   <li>{@code membertag:remove}       — 取消会员标签 (破坏性, HITL 审批).</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:
 * <ul>
 *   <li>query/member_tags → business:membertag:query (对齐 MemberTagController.list/tagMembers @SaCheckPermission);</li>
 *   <li>assign/remove → business:membertag:manage (对齐 MemberTagController.assign/removeAssign @SaCheckPermission).</li>
 * </ul>
 */
@AgentToolService(business = "membertag")
public class MemberTagAgentToolService {

    private final MemberTagService memberTagService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public MemberTagAgentToolService(MemberTagService memberTagService) {
        this.memberTagService = memberTagService;
    }

    /**
     * 查询标签列表 (只读, 支持关键词模糊搜索).
     * <p>
     * 复用 {@link MemberTagService#listTags}, 对齐 MemberTagController.list 的 @SaCheckPermission("business:membertag:query").
     *
     * @param req 查询条件 (keyword)
     * @return 标签列表
     */
    @AgentTool(
        operation = "query",
        description = "查询会员标签列表。支持按名称关键词模糊搜索。返回所有标签定义。用于回答'有哪些标签''查找标签'等问题。",
        requiredPermission = "business:membertag:query",
        outputHint = "返回标签列表，包含标签ID、名称、描述、状态。展示为 markdown 表格。"
    )
    public List<MemberTagResp> query(MemberTagQueryToolReq req) {
        return memberTagService.listTags(req.getKeyword());
    }

    /**
     * 查询会员已分配的标签 (只读).
     * <p>
     * 复用 {@link MemberTagService#listMemberTags}, 对齐 MemberTagController (查询会员标签).
     *
     * @param req 查询条件 (memberId)
     * @return 会员标签列表
     */
    @AgentTool(
        operation = "member_tags",
        description = "查询指定会员的所有标签。返回该会员当前已分配的标签列表。用于回答'会员XX有哪些标签''会员标签情况'等问题。",
        requiredPermission = "business:membertag:query",
        outputHint = "返回会员标签列表，包含标签ID、名称、描述。展示为 markdown 表格或标签云。"
    )
    public List<MemberTagResp> memberTags(MemberTagQueryToolReq req) {
        return memberTagService.listMemberTags(req.getMemberId());
    }

    /**
     * 给会员批量分配标签 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link MemberTagService#assignTags}, 对齐 MemberTagController.assign 的 @SaCheckPermission("business:membertag:manage").
     * 自动去重: 过滤掉已存在的关系, 仅插入新关系.
     *
     * @param req 分配请求 (memberId + tagIds)
     * @return 实际新增的关系数量
     */
    @AgentTool(
        operation = "assign",
        description = "给会员批量分配标签。需要会员ID和标签ID列表。自动去重，已存在的标签关系不会重复分配。此操作会修改会员标签关系，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:membertag:manage",
        outputHint = "返回新增标签关系数量。展示为文本，提示用户已为会员分配标签。"
    )
    public int assign(MemberTagAssignReq req) {
        return memberTagService.assignTags(req);
    }

    /**
     * 取消会员的指定标签 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link MemberTagService#removeTags}, 对齐 MemberTagController.removeAssign 的 @SaCheckPermission("business:membertag:manage").
     *
     * @param req 取消请求 (memberId + tagIds)
     * @return 实际删除的关系数量
     */
    @AgentTool(
        operation = "remove",
        description = "取消会员的指定标签。需要会员ID和待取消的标签ID列表。此操作会删除会员标签关系，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:membertag:manage",
        outputHint = "返回删除标签关系数量。展示为文本，提示用户已取消会员标签。"
    )
    public int remove(MemberTagRemoveToolReq req) {
        return memberTagService.removeTags(req.getMemberId(), req.getTagIds());
    }
}
