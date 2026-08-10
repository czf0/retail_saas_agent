package com.retail.core.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.retail.core.tenant.TenantContext;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户上下文读写工具(基于 Sa-Token Session,共享基础设施).
 * <p>集中管理 {@link LoginUser} 的存取,供认证,拦截器,审计填充,业务层统一调用,
 * 避免各处直接操作 Session 字符串 key 造成耦合.
 * <p>非请求线程(定时任务,初始化等)或未登录场景下,{@link #get()} 返回 null,
 * 便捷方法 {@link #currentTenantId()} / {@link #currentStoreId()} 返回 null.
 */
public final class LoginUserHolder {

    /** Session 中存储 LoginUser 的 key */
    private static final String LOGIN_USER_KEY = "loginUser";

    private LoginUserHolder() {
    }

    /** 写入登录用户到当前账号 Session */
    public static void set(LoginUser user) {
        StpUtil.getSession().set(LOGIN_USER_KEY, user);
    }

    /** 读取当前登录用户;未登录或非请求上下文返回 null */
    public static LoginUser get() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            SaSession session = StpUtil.getSession();
            Object obj = session.get(LOGIN_USER_KEY);
            return obj instanceof LoginUser ? (LoginUser) obj : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前登录用户名;未登录返回 null */
    public static String currentUsername() {
        LoginUser u = get();
        return u != null ? u.getUsername() : null;
    }

    /** 当前登录用户ID;未登录返回 null */
    public static Long currentUserId() {
        LoginUser u = get();
        return u != null ? u.getUserId() : null;
    }

    /** 当前租户ID;平台管理员或未登录返回 null */
    public static Long currentTenantId() {
        LoginUser u = get();
        return u != null ? u.getTenantId() : null;
    }

    /** 当前门店ID;无固定门店或未登录返回 null */
    public static Long currentStoreId() {
        LoginUser u = get();
        return u != null ? u.getStoreId() : null;
    }

    /** 是否平台管理员(tenantId 为 null,可跨租户) */
    public static boolean isPlatformAdmin() {
        LoginUser u = get();
        return u != null && u.getTenantId() == null;
    }

    /**
     * 有效租户ID:优先取登录用户的租户(租户用户),平台管理员回退取 {@link TenantContext}
     * (由 GlobalReqInterceptor 从 X-Tenant-Id 头注入的当前选中租户),均无则返回 null
     * (平台管理员未选租户,查看全部).
     * <p><b>用途</b>:Service 层手动租户过滤(sys_user / sys_role / sys_store 等 ignore-tables 表),
     * 使 admin 切换租户后仅见该租户数据;未选租户时返回 null 不过滤,admin 跨租户可见全部.
     * <p><b>注意区分</b>:{@link #currentTenantId()} 取 session 值(admin 为 null),用于
     * {@code AuditMetaObjectHandler} 插入填充——admin 插入时 tenantId 保持 null,由业务代码显式赋值,
     * 不应回退读 TenantContext,否则会把平台级记录误植为租户级.
     */
    public static Long effectiveTenantId() {
        Long session = currentTenantId();
        if (session != null) {
            return session;
        }
        // 平台管理员回退:读 TenantContext(X-Tenant-Id 头注入的当前选中租户)
        String ctx = TenantContext.getTenantId();
        if (ctx != null && !ctx.isEmpty()) {
            try {
                return Long.valueOf(ctx);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 当前用户角色 key 列表;未登录返回空列表 */
    public static List<String> currentRoleKeys() {
        LoginUser u = get();
        return u != null && u.getRoleKeys() != null ? u.getRoleKeys() : Collections.emptyList();
    }
}
