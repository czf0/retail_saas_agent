package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.QuickQueryBatchReq;
import com.retail.business.dto.req.QuickQuerySaveReq;
import com.retail.business.entity.UserQuickQuery;
import com.retail.business.service.UserQuickQueryService;
import com.retail.core.exception.ParamException;
import com.retail.core.result.R;
import com.retail.core.security.LoginUserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户快捷提问管理接口.
 * <p>路由前缀 /api/v1/chat/quick-queries.user_quick_query 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验:个人快捷提问基于 @SaCheckPermission("business:chat:*");
 * 租户级公共快捷提问需 @SaCheckPermission("kb:manage"),仅知识库管理员维护.
 * <p>懒持久化模式:默认快捷提问写死在前端 DEFAULT_QUICK_QUERIES 常量,
 * 用户首次修改时调 /batch 接口初始化个人快捷提问集,此后每次加载查数据库.
 */
@RestController
@RequestMapping("/api/v1/chat/quick-queries")
public class UserQuickQueryController {

    private final UserQuickQueryService userQuickQueryService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public UserQuickQueryController(UserQuickQueryService userQuickQueryService) {
        this.userQuickQueryService = userQuickQueryService;
    }

    /**
     * 查询当前用户可见的快捷提问 (个人级 + 租户级公共).
     * <p>前端首次加载调此接口: 返回空则展示前端 DEFAULT_QUICK_QUERIES 常量;
     * 返回非空则用 DB 数据 (用户已初始化过).
     */
    @GetMapping
    @SaCheckPermission("business:chat:query")
    public R<List<UserQuickQuery>> listVisible() {
        Long userId = LoginUserHolder.currentUserId();
        Long tenantId = LoginUserHolder.currentTenantId();
        if (userId == null) {
            return R.ok(new ArrayList<>());
        }
        return R.ok(userQuickQueryService.listVisible(tenantId, userId));
    }

    /**
     * 保存个人快捷提问 (isPublic=false).
     * <p>同一 shortcut_text 在个人范围内唯一, 存在则更新.
     */
    @PostMapping
    @SaCheckPermission("business:chat:manage")
    public R<UserQuickQuery> savePersonal(@RequestBody QuickQuerySaveReq req) {
        Long userId = LoginUserHolder.currentUserId();
        if (userId == null) {
            throw new ParamException("未登录, 无法保存快捷提问");
        }
        return R.ok(userQuickQueryService.save(
                userId, req.getShortcutText(), req.getCanonicalQuery(), req.getScenario(), false));
    }

    /**
     * 批量保存个人快捷提问 (懒持久化初始化用).
     * <p>用户首次修改快捷提问时, 前端将 DEFAULT_QUICK_QUERIES 批量入库,
     * 初始化个人快捷提问集; 此后 DB 成为权威数据源.
     */
    @PostMapping("/batch")
    @SaCheckPermission("business:chat:manage")
    public R<Boolean> batchSave(@RequestBody QuickQueryBatchReq req) {
        Long userId = LoginUserHolder.currentUserId();
        if (userId == null) {
            throw new ParamException("未登录, 无法保存快捷提问");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return R.ok(true);
        }
        for (QuickQuerySaveReq item : req.getItems()) {
            userQuickQueryService.save(
                    userId, item.getShortcutText(), item.getCanonicalQuery(), item.getScenario(), false);
        }
        return R.ok(true);
    }

    /**
     * 保存租户级公共快捷提问 (isPublic=true, 需 kb:manage 权限).
     * <p>公共快捷提问全租户用户可见, 仅管理员维护.
     */
    @PostMapping("/public")
    @SaCheckPermission("kb:manage")
    public R<UserQuickQuery> savePublic(@RequestBody QuickQuerySaveReq req) {
        return R.ok(userQuickQueryService.save(
                null, req.getShortcutText(), req.getCanonicalQuery(), req.getScenario(), true));
    }

    /**
     * 删除快捷提问.
     * <p>个人快捷提问只能删自己的 (通过 Service 层校验 ownership);
     * 公共快捷提问需 kb:manage 权限 (由 Service 层校验).
     */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("business:chat:manage")
    public R<Boolean> remove(@PathVariable Long id) {
        return R.ok(userQuickQueryService.remove(id));
    }
}
