package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.entity.UserQuickQuery;

import java.util.List;

/**
 * 用户快捷提问服务 (评审 D8: canonical_query 一鱼三吃).
 * <p>
 * 用户保存常用问法绑定 canonical_query, 提升检索缓存命中率;
 * 管理员可设租户级公共快捷提问 (is_public=1), 全租户共享 canonical_query;
 * 变更后通知 Python 失效检索缓存 (canonical_query 维度可能变化).
 */
public interface UserQuickQueryService extends IService<UserQuickQuery> {

    /**
     * 保存快捷提问: 个人级 (userId 非空) 或租户级公共 (is_public=1, userId=NULL).
     * <p>前置条件: 同一 shortcut_text 在同范围内唯一, 存在则更新; 无显式幂等键.
     * <p>副作用: 变更后通知 Python 失效检索缓存 (canonical_query 维度可能变化), 异步执行.
     * <p>跨模块: 仅回调 Python 检索缓存失效接口, 不写业务表以外数据.
     */
    UserQuickQuery save(Long userId, String shortcutText, String canonicalQuery, String scenario, boolean isPublic);

    /**
     * 删除快捷提问.
     * <p>前置条件: 快捷提问必须存在且属于当前用户/租户, 否则抛 BizException.
     * <p>副作用: 删除后该快捷提问不可再用; 通知 Python 失效检索缓存.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    Boolean remove(Long quickQueryId);

    /**
     * 查询用户可见的快捷提问 (个人级 + 租户级公共).
     * 命中快捷提问时, 直接用 canonical_query 做检索缓存 key + 范式路由.
     */
    List<UserQuickQuery> listVisible(Long tenantId, Long userId);
}
