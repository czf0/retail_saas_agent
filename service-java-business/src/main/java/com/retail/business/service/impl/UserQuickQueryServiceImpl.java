package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.entity.UserQuickQuery;
import com.retail.business.mapper.UserQuickQueryMapper;
import com.retail.business.service.UserQuickQueryService;
import com.retail.core.client.KnowledgeSyncNotifier;
import com.retail.core.dto.kb.KnowledgeSyncEvent;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.tenant.TenantContext;
import com.retail.core.trace.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户快捷提问服务实现 (评审 D8: canonical_query 一鱼三吃).
 * <p>
 * 快捷提问表 (user_quick_query) 由 Java 维护, Python 检索时由调用方传 canonical_query;
 * 变更后通知 Python 失效检索缓存 (canonical_query 维度可能变化, 原缓存不再适用).
 * <p>
 * tenant_id 由 MyBatis-Plus 多租户拦截器自动注入 (user_quick_query 不在 ignore-tables).
 */
@Slf4j
@Service
public class UserQuickQueryServiceImpl extends BaseServiceImpl<UserQuickQueryMapper, UserQuickQuery>
        implements UserQuickQueryService {

    private final KnowledgeSyncNotifier syncNotifier;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 UserQuickQueryMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>syncNotifier 用于快捷提问变更后通知 Python 失效检索缓存(canonical_query 维度可能变化,原缓存不再适用).
     */
    public UserQuickQueryServiceImpl(KnowledgeSyncNotifier syncNotifier) {
        this.syncNotifier = syncNotifier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserQuickQuery save(Long userId, String shortcutText, String canonicalQuery,
                               String scenario, boolean isPublic) {
        validateSaveReq(shortcutText, canonicalQuery, isPublic, userId);
        // 公共快捷提问 userId=NULL, 个人快捷提问 userId 必填
        Long resolvedUserId = isPublic ? null : userId;

        // 查找已有 (按 tenant+shortcut_text), 存在则更新
        LambdaQueryWrapper<UserQuickQuery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQuickQuery::getShortcutText, shortcutText);
        if (resolvedUserId == null) {
            wrapper.isNull(UserQuickQuery::getUserId);
        } else {
            wrapper.eq(UserQuickQuery::getUserId, resolvedUserId);
        }
        UserQuickQuery existing = getOne(wrapper);

        UserQuickQuery entity;
        if (existing != null) {
            existing.setCanonicalQuery(canonicalQuery);
            existing.setScenario(StrUtil.isBlank(scenario) ? null : scenario);
            updateById(existing);
            entity = existing;
            log.info("更新快捷提问 id={} shortcutText={} isPublic={} userId={}",
                    entity.getId(), shortcutText, isPublic ? 1 : 0, resolvedUserId);
        } else {
            entity = new UserQuickQuery();
            entity.setUserId(resolvedUserId);
            entity.setIsPublic(isPublic ? 1 : 0);
            entity.setShortcutText(shortcutText);
            entity.setCanonicalQuery(canonicalQuery);
            entity.setScenario(StrUtil.isBlank(scenario) ? null : scenario);
            save(entity);
            log.info("创建快捷提问 id={} shortcutText={} isPublic={} userId={} scenario={}",
                    entity.getId(), shortcutText, isPublic ? 1 : 0, resolvedUserId, entity.getScenario());
        }

        // 通知 Python 失效检索缓存 (canonical_query 维度可能变化)
        notifyPythonRefresh();

        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean remove(Long quickQueryId) {
        if (quickQueryId == null) {
            throw new ParamException("快捷提问ID不能为空");
        }
        boolean removed = removeById(quickQueryId);
        if (removed) {
            notifyPythonRefresh();
            log.info("删除快捷提问 id={}", quickQueryId);
        } else {
            log.warn("删除快捷提问失败 id={} 原因=记录不存在或已删除", quickQueryId);
        }
        return removed;
    }

    @Override
    public List<UserQuickQuery> listVisible(Long tenantId, Long userId) {
        // 个人级 (user_id = ?) + 租户级公共 (is_public = 1)
        List<UserQuickQuery> result = new ArrayList<>();
        // 租户级公共快捷提问 (tenant_id 由拦截器自动注入, 仅需 is_public=1)
        LambdaQueryWrapper<UserQuickQuery> publicWrapper = new LambdaQueryWrapper<>();
        publicWrapper.eq(UserQuickQuery::getIsPublic, 1);
        result.addAll(list(publicWrapper));
        // 个人快捷提问
        if (userId != null) {
            LambdaQueryWrapper<UserQuickQuery> personalWrapper = new LambdaQueryWrapper<>();
            personalWrapper.eq(UserQuickQuery::getUserId, userId)
                    .eq(UserQuickQuery::getIsPublic, 0);
            result.addAll(list(personalWrapper));
        }
        log.debug("查询可见快捷提问 tenantId={} userId={} 命中数={}", tenantId, userId, result.size());
        return result;
    }

    // ---- 内部方法 ----

    private void validateSaveReq(String shortcutText, String canonicalQuery,
                                 boolean isPublic, Long userId) {
        if (StrUtil.isBlank(shortcutText)) {
            throw new ParamException("快捷提问文本不能为空");
        }
        if (StrUtil.isBlank(canonicalQuery)) {
            throw new ParamException("canonical_query 不能为空");
        }
        if (!isPublic && userId == null) {
            throw new ParamException("个人快捷提问必须传 userId (公共快捷提问设 isPublic=true)");
        }
    }

    /** 通知 Python 失效检索缓存 (quick_query_refresh 事件) */
    private void notifyPythonRefresh() {
        String tenant = TenantContext.getTenantId();
        if (tenant == null || tenant.isEmpty()) {
            tenant = "default";
        }
        KnowledgeSyncEvent event = KnowledgeSyncEvent.quickQueryRefresh(tenant, TraceUtil.getTraceId());
        syncNotifier.notify(event);
    }
}
