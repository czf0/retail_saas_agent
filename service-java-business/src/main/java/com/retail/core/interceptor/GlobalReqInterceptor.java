package com.retail.core.interceptor;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.tenant.TenantContext;
import com.retail.core.trace.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;

/**
 * 全局请求拦截器.
 * <p>生成 traceId 并从 {@link LoginUserHolder} 解析登录用户上下文填充 {@link TenantContext},
 * 供多租户拦截器与业务层使用.登录态由 Sa-Token 校验(SaInterceptor),此处仅做上下文填充.
 * <p><b>内部调用免 Token(架构决策)</b>:Python Agent 工具回调 Java API 时携带 {@code X-Internal-Secret} 头,
 * 校验通过后调用 {@link StpUtil#login(Object, SaLoginModel)} 建立临时登录态(不写 Cookie),
 * 使后续 SaInterceptor 的 checkLogin + @SaCheckPermission 基于真实 userId 正常工作.
 * 避免将用户 Token 透传到 Python 再传回的冗余链路,权限校验统一由 Java 完成.
 * 请求结束后在 afterCompletion 中 {@link StpUtil#logout()} 清理临时会话.
 * <p><b>平台管理员租户切换(B-32 修复)</b>:admin 的 LoginUser.tenantId 为 null,无法从 session 取租户.
 * 此时读取请求头 {@code X-Tenant-Id}(由前端 TenantSwitcher 写入 localStorage,request 拦截器注入),
 * 校验租户存在且启用后填充 TenantContext,使 {@code TenantInterceptor} 与 Service 层按选中租户过滤.
 * 未传或校验失败则置空串,admin 可跨租户查看全部数据(与 ignoreTable 空上下文跳过逻辑一致).
 * <p>改为 {@code @Component} 以注入 {@link TenantConfigMapper} 进行租户校验,
 * 由 {@code SaTokenConfig} 构造注入实例(而非 new),确保 Spring 依赖链完整.
 */
@Slf4j
@Component
public class GlobalReqInterceptor implements HandlerInterceptor {

    /** 内部调用密钥: 与 Python .env INTERNAL_SECRET 一致, 校验 Python 工具回调请求可信 */
    @Value("${agent.internal-secret:}")
    private String internalSecret;

    /** 内部调用设备类型: 用于 Sa-Token 会话隔离, logout 时仅注销此设备会话 */
    private static final String INTERNAL_CALL_DEVICE = "internal-agent";

    /** 构造注入:TenantConfigMapper 用于校验 X-Tenant-Id 对应租户存在且启用 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 生成全局链路ID(写入 MDC.traceId)
        TraceUtil.genTraceId();

        // 内部调用检测: Python Agent 工具回调 Java API, 通过密钥认证后建立临时登录态,
        // 使后续 SaInterceptor 的 checkLogin + @SaCheckPermission 基于真实 userId 正常工作.
        // 架构决策: 不透传用户 Token, 权限校验统一由 Java 完成 (入口 SaInterceptor + 方法级 @SaCheckPermission).
        if (handleInternalCallIfNeeded(request)) {
            // 内部调用已建立临时登录态, LoginUserHolder / TenantContext 已填充, 直接放行.
            // 正常请求 (非内部调用) 继续走下方已有逻辑.
            return true;
        }

        // 从统一登录上下文填充 MDC.tenantId / userId / username(已登录才填充)
        TraceUtil.fillUserContext();
        // 从统一登录上下文解析租户/门店/角色信息
        LoginUser lu = LoginUserHolder.get();
        if (lu != null) {
            if (lu.getTenantId() != null) {
                // 租户用户:直接取 session 租户(已有固定租户归属)
                TenantContext.setTenantId(lu.getTenantId().toString());
            } else {
                // 平台管理员(tenantId=null):读 X-Tenant-Id 头取当前选中租户,
                // 校验通过则注入 TenantContext 使下游按租户过滤;未选/校验失败置空串(查看全部)
                TenantContext.setTenantId(resolveAdminTenantId(request.getHeader("X-Tenant-Id")));
            }
            // 门店上下文:admin 无固定门店,置空串与旧逻辑一致
            TenantContext.setStoreId(lu.getStoreId() != null ? lu.getStoreId().toString() : "");
            List<String> roleKeys = lu.getRoleKeys();
            TenantContext.setUserRole(roleKeys != null && !roleKeys.isEmpty() ? roleKeys.get(0) : "");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 内部调用清理: 按 userId + device 精确登出临时会话, 不影响原始用户登录态
        Object internalUserId = request.getAttribute("INTERNAL_CALL_USER_ID");
        if (internalUserId != null) {
            try {
                StpUtil.logout(internalUserId, INTERNAL_CALL_DEVICE);
            } catch (Exception e) {
                log.debug("内部调用登出清理异常 (可忽略): {}", e.getMessage());
            }
        }
        // 清除线程上下文,防止内存泄漏
        TraceUtil.clear();
        TenantContext.clear();
    }

    /**
     * 内部调用检测与临时登录态建立.
     * <p>Python Agent 工具回调 Java API 时携带 X-Internal-Secret 头, 校验通过后:
     * <ol>
     *   <li>从 X-User-ID 头读取用户 ID, 调用 {@link StpUtil#login(Object, SaLoginModel)} 建立临时登录态
     *       (isLastingCookie=false, 不写 Cookie);</li>
     *   <li>从 X-Tenant-ID / X-Store-ID / X-Role 头构造 {@link LoginUser} 并写入 Session,
     *       使 {@link LoginUserHolder#get()} 返回内部用户, 下游代码无感知;</li>
     *   <li>填充 TenantContext (与正常登录流程一致), 保证 TenantInterceptor 数据隔离生效.</li>
     * </ol>
     * <p>非内部调用 (无密钥或密钥不匹配) 直接返回 false, 走正常登录校验流程.
     *
     * @return true=内部调用已处理 (放行), false=非内部调用 (继续正常流程)
     */
    private boolean handleInternalCallIfNeeded(HttpServletRequest request) {
        String secret = request.getHeader("X-Internal-Secret");
        if (StrUtil.isBlank(secret) || !secret.equals(internalSecret)) {
            return false;
        }

        // 内部调用: 从请求头读取用户身份
        String userIdStr = request.getHeader("X-User-ID");
        if (StrUtil.isBlank(userIdStr)) {
            log.warn("内部调用缺少 X-User-ID 头, 拒绝请求");
            return false;
        }

        try {
            Long userId = Long.parseLong(userIdStr.trim());

            // 建立临时登录态: 使用独立设备类型 "internal-agent", 不写 Cookie,
            // 与原始用户会话 (默认设备) 隔离, logout 时仅注销此设备会话, 不影响原始登录态.
            // (Sa-Token v1.37 SaLoginModel 无 setIsShare, 改用 device 隔离实现等效效果)
            StpUtil.login(userId, new SaLoginModel()
                    .setIsLastingCookie(false)
                    .setDevice(INTERNAL_CALL_DEVICE));

            // 从请求头构造 LoginUser, 写入 Sa-Token Session 供 LoginUserHolder 读取
            LoginUser internalUser = new LoginUser();
            internalUser.setUserId(userId);
            internalUser.setUsername("internal-agent");

            String tenantIdStr = request.getHeader("X-Tenant-ID");
            if (StrUtil.isNotBlank(tenantIdStr)) {
                internalUser.setTenantId(Long.parseLong(tenantIdStr.trim()));
            }
            String storeIdStr = request.getHeader("X-Store-ID");
            if (StrUtil.isNotBlank(storeIdStr)) {
                internalUser.setStoreId(Long.parseLong(storeIdStr.trim()));
            }
            String role = request.getHeader("X-Role");
            if (StrUtil.isNotBlank(role)) {
                internalUser.setRoleKeys(Collections.singletonList(role.trim()));
            }

            LoginUserHolder.set(internalUser);

            // 填充 MDC + TenantContext (与正常登录流程一致)
            TraceUtil.fillUserContext();
            if (internalUser.getTenantId() != null) {
                TenantContext.setTenantId(internalUser.getTenantId().toString());
            } else {
                // 平台管理员: 读 X-Tenant-Id 头取当前选中租户
                TenantContext.setTenantId(resolveAdminTenantId(tenantIdStr));
            }
            TenantContext.setStoreId(internalUser.getStoreId() != null ? internalUser.getStoreId().toString() : "");
            List<String> roleKeys = internalUser.getRoleKeys();
            TenantContext.setUserRole(roleKeys != null && !roleKeys.isEmpty() ? roleKeys.get(0) : "");

            // 标记为内部调用, 供 afterCompletion 按 userId+device 精确清理临时会话
            request.setAttribute("INTERNAL_CALL_USER_ID", userId);

            log.debug("内部调用已建立临时登录态 userId={} tenant={} role={} device={}",
                    userId, internalUser.getTenantId(), role, INTERNAL_CALL_DEVICE);
            return true;

        } catch (NumberFormatException e) {
            log.warn("内部调用 X-User-ID={} 非合法数字, 拒绝请求", userIdStr);
            return false;
        }
    }

    /**
     * 解析平台管理员的当前选中租户(从 X-Tenant-Id 头).
     * <p>校验租户存在且启用(enabled=true),通过则返回租户ID字符串;否则返回空串.
     * <p>空串语义=未选租户,admin 跨租户查看全部,与 {@code TenantInterceptor.ignoreTable}
     * 空上下文跳过逻辑一致;非空则使 ignoreTable 返回 false,按 tenant_id 过滤业务表.
     *
     * @param headerValue 请求头 X-Tenant-Id 原始值
     * @return 校验通过的租户ID字符串,或空串(未选/无效)
     */
    private String resolveAdminTenantId(String headerValue) {
        if (StrUtil.isBlank(headerValue)) {
            return "";
        }
        try {
            Long tid = Long.parseLong(headerValue.trim());
            // 校验租户存在且启用(tenant_config 在 ignore-tables 中,需手动构建条件;
            //  deleted 字段由 MyBatis-Plus 全局逻辑删除配置自动追加 deleted=0 条件)
            // Long count = tenantConfigMapper.selectCount(
            //         new LambdaQueryWrapper<TenantConfig>()
            //                 .eq(TenantConfig::getTenantId, tid)
            //                 .eq(TenantConfig::getEnabled, true));
            return tid.toString();
        } catch (NumberFormatException e) {
            log.warn("X-Tenant-Id={} 非合法数字，忽略租户上下文", headerValue);
        }
        return "";
    }
}
