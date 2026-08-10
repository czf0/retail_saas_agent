package com.retail.core.context;

import cn.dev33.satoken.stp.StpUtil;
import com.retail.core.security.LoginUserHolder;

/**
 * 审计用户上下文.
 * <p>
 * 用于 createBy / updateBy / deleteBy 自动填充,统一从 {@link LoginUserHolder} 获取当前操作人.
 * 非请求线程(定时任务,初始化等)或未登录场景回退为 "system",避免空值.
 */
public final class AuditUserContext {

    /** 未登录/异常时的兜底操作人标识 */
    public static final String SYSTEM_USER = "system";

    private AuditUserContext() {
    }

    /**
     * 获取当前操作人.
     * 优先取 LoginUserHolder 中的 username;缺失时回退 Sa-Token 登录 id;均不可用时返回 "system".
     *
     * @return 当前操作人标识
     */
    public static String currentUser() {
        try {
            String username = LoginUserHolder.currentUsername();
            if (username != null && !username.isEmpty()) {
                return username;
            }
            // username 缺失时回退到 Sa-Token 登录 id
            if (StpUtil.isLogin()) {
                Object loginId = StpUtil.getLoginId();
                if (loginId != null) {
                    return loginId.toString();
                }
            }
        } catch (Exception ignored) {
            // 非请求上下文或 Sa-Token 异常,回退兜底
        }
        return SYSTEM_USER;
    }
}
