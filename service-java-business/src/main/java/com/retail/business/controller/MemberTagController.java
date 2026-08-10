package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.MemberTagAssignReq;
import com.retail.business.dto.req.MemberTagReq;
import com.retail.business.dto.resp.MemberTagResp;
import com.retail.business.service.MemberTagService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员标签管理接口.
 * <p>路由前缀 /api/v1/member-tags.member_tag 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:membertag:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>包含标签定义 CRUD 与会员 - 标签关系管理(分配 / 取消 / 查询),标签下会员分页参数由 PageParameterInterceptor 自动注入.
 */
@RestController
@RequestMapping("/api/v1/member-tags")
public class MemberTagController {

    private final MemberTagService memberTagService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public MemberTagController(MemberTagService memberTagService) {
        this.memberTagService = memberTagService;
    }

    /**
     * 查询标签列表,支持关键词模糊搜索.
     */
    @GetMapping("")
    @SaCheckPermission("business:membertag:query")
    public R<List<MemberTagResp>> list(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(memberTagService.listTags(keyword));
    }

    /**
     * 创建标签.
     */
    @PostMapping("")
    @SaCheckPermission("business:membertag:manage")
    public R<MemberTagResp> create(@RequestBody MemberTagReq req) {
        return R.ok(memberTagService.createTag(req));
    }

    /**
     * 修改标签(部分更新).
     */
    @PutMapping("/{tagId:\\d+}")
    @SaCheckPermission("business:membertag:manage")
    public R<MemberTagResp> update(@PathVariable Long tagId, @RequestBody MemberTagReq req) {
        return R.ok(memberTagService.updateTag(tagId, req));
    }

    /**
     * 删除标签(逻辑删除标签定义 + 物理删除会员关系).
     */
    @DeleteMapping("/{tagId:\\d+}")
    @SaCheckPermission("business:membertag:manage")
    public R<Boolean> delete(@PathVariable Long tagId) {
        return R.ok(memberTagService.deleteTag(tagId));
    }

    /**
     * 给会员批量分配标签(自动去重).
     */
    @PostMapping("/assign")
    @SaCheckPermission("business:membertag:manage")
    public R<Integer> assign(@RequestBody MemberTagAssignReq req) {
        return R.ok(memberTagService.assignTags(req));
    }

    /**
     * 取消会员的指定标签.
     */
    @DeleteMapping("/assign")
    @SaCheckPermission("business:membertag:manage")
    public R<Integer> removeAssign(@RequestBody MemberTagAssignReq req) {
        return R.ok(memberTagService.removeTags(req.getMemberId(), req.getTagIds()));
    }

    /**
     * 查询标签下的会员ID列表(分页).
     */
    @GetMapping("/members/{tagId:\\d+}")
    @SaCheckPermission("business:membertag:query")
    public R<PageResp<Long>> tagMembers(
            @PathVariable Long tagId) {
        return R.ok(memberTagService.listTagMembers(tagId));
    }
}
