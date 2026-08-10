package com.retail.core.trace;

import cn.hutool.core.util.IdUtil;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import org.slf4j.MDC;

/**
 * 链路追踪工具(基于 SLF4J MDC).
 * <p>
 * 在 {@code GlobalReqInterceptor.preHandle} 中调用,为每个 HTTP 请求生成全局唯一 traceId,
 * 并填充租户/用户上下文到 MDC,使日志 pattern 中的 {@code %X{traceId} %X{tenantId} %X{userId}}
 * 能输出完整链路信息,便于按链路/租户/用户检索日志.
 * <p>
 * 请求结束时由 {@code GlobalReqInterceptor.afterCompletion} 调用 {@link #clear()} 清理 MDC,
 * 防止线程池复用导致上下文串号.
 */
public class TraceUtil {

    /** MDC key:链路追踪 ID(Snowflake 全局唯一) */
    public static final String TRACE_ID_KEY = "traceId";

    /** MDC key:租户 ID(平台管理员为 null,不写入 MDC) */
    public static final String TENANT_ID_KEY = "tenantId";

    /** MDC key:登录用户 ID(未登录不写入 MDC) */
    public static final String USER_ID_KEY = "userId";

    /** MDC key:登录用户名(未登录不写入 MDC) */
    public static final String USERNAME_KEY = "username";

    private TraceUtil() {
    }

    /** 生成 Snowflake traceId 并写入 MDC */
    public static void genTraceId() {
        String traceId = IdUtil.getSnowflakeNextIdStr();
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 从 {@link LoginUserHolder} 读取登录用户上下文,填充 tenantId / userId / username 到 MDC.
     * <p>
     * 已登录才填充:未登录请求(如 /api/v1/auth/login){@link LoginUserHolder#get()} 返回 null,
     * MDC 中三项保持空,日志 pattern 用 {@code %X{key:-}} 兜底显示 "-",不报错.
     * <p>
     * 平台管理员 tenantId 为 null(跨租户),不写入 MDC.tenantId,仅写 userId / username.
     */
    public static void fillUserContext() {
        LoginUser lu = LoginUserHolder.get();
        if (lu == null) {
            return;
        }
        if (lu.getTenantId() != null) {
            MDC.put(TENANT_ID_KEY, String.valueOf(lu.getTenantId()));
        }
        if (lu.getUserId() != null) {
            MDC.put(USER_ID_KEY, String.valueOf(lu.getUserId()));
        }
        if (lu.getUsername() != null && !lu.getUsername().isEmpty()) {
            MDC.put(USERNAME_KEY, lu.getUsername());
        }
    }

    /** 获取当前 MDC 中的 traceId */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /** 清除 MDC 全部上下文(traceId / tenantId / userId / username),防止线程池复用串号 */
    public static void clear() {
        MDC.clear();
    }
}
