package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.MemberTagAssignReq;
import com.retail.business.dto.req.MemberTagReq;
import com.retail.business.dto.resp.MemberTagResp;
import com.retail.business.entity.MemberTag;

import java.util.List;

/**
 * 会员标签服务.
 * <p>
 * 管理标签定义(CRUD)与会员-标签多对多关系(分配/取消/查询).
 * 标签定义为逻辑删除表,关系表为物理删除.
 */
public interface MemberTagService extends IService<MemberTag> {

    /**
     * 创建标签(校验租户内名称唯一).
     */
    MemberTagResp createTag(MemberTagReq req);

    /**
     * 更新标签(部分更新,校验名称唯一).
     */
    MemberTagResp updateTag(Long tagId, MemberTagReq req);

    /**
     * 逻辑删除标签,同时物理删除该标签的所有会员关系.
     */
    boolean deleteTag(Long tagId);

    /**
     * 查询标签列表,支持关键词模糊搜索.
     */
    List<MemberTagResp> listTags(String keyword);

    /**
     * 给会员批量分配标签 (自动去重: 过滤掉已存在的关系).
     * <p>前置条件: 会员与标签必须存在, 否则抛 BizException; 单批标签数上限 50 (铁律 12), 超限抛 ParamException.
     * <p>副作用: 关系表物理写入 (member_tag_relation), 即时生效; 无跨模块调用.
     *
     * @param req 分配请求 (memberId + tagIds)
     * @return 实际新增的关系数量
     * @throws ParamException 标签数 > 50
     * @throws BizException   会员或标签不存在
     */
    int assignTags(MemberTagAssignReq req);

    /**
     * 取消会员的指定标签.
     * <p>前置条件: 会员必须存在, 否则抛 BizException.
     * <p>副作用: 关系表物理删除, 即时生效; 不影响标签定义本身.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param memberId 会员ID
     * @param tagIds   标签ID列表
     * @return 实际删除的关系数量
     * @throws BizException 会员不存在
     */
    int removeTags(Long memberId, List<Long> tagIds);

    /**
     * 查询会员的所有标签.
     */
    List<MemberTagResp> listMemberTags(Long memberId);

    /**
     * 分页查询标签下的会员ID列表.
     *
     * @param tagId 标签ID
     * @return 分页会员ID列表
     */
    PageResp<Long> listTagMembers(Long tagId);
}
