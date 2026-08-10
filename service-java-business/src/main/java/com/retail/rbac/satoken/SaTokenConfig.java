package com.retail.rbac.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.retail.core.interceptor.GlobalReqInterceptor;
import com.retail.core.interceptor.PageParameterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置.
 * <p>注册两层拦截器(顺序敏感):
 * <ol>
 *   <li>{@link GlobalReqInterceptor}(最高优先级):生成 traceId + 填充租户上下文,先于登录校验保证链路追踪完整;</li>
 *   <li>{@link SaInterceptor}:路由级登录校验,除白名单(登录/actuator/error)外均需登录.</li>
 * </ol>
 * <p>接口级权限(@SaCheckPermission)由 Sa-Token 注解 AOP 在方法层校验,无需在此声明.
 * <p>B-32:{@link GlobalReqInterceptor} 改为 {@code @Component}(依赖 TenantConfigMapper 校验租户),
 * 此处通过构造注入获取 Spring 管理的 Bean,而非 new 实例,确保依赖链完整.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaTokenConfig implements WebMvcConfigurer {

    private final GlobalReqInterceptor globalReqInterceptor;
    /** 分页参数拦截器:从 HttpServletRequest 提取 page/pageSize 注入 ThreadLocal */
    private final PageParameterInterceptor pageParameterInterceptor;

    /** 构造注入:GlobalReqInterceptor / PageParameterInterceptor 均为 @Component,由 Spring 注入解析依赖 */
    public SaTokenConfig(GlobalReqInterceptor globalReqInterceptor,
                         PageParameterInterceptor pageParameterInterceptor) {
        this.globalReqInterceptor = globalReqInterceptor;
        this.pageParameterInterceptor = pageParameterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 全局请求拦截器:traceId 生成 + 租户上下文填充(先执行,登录与否均放行)
        registry.addInterceptor(globalReqInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE);

        // 2. Sa-Token 登录校验:白名单外强制登录
        registry.addInterceptor(new SaInterceptor(handle ->
                SaRouter.match("/**")
                        .notMatch(
                                "/api/v1/auth/login",
                                "/actuator/**",
                                "/error",
                                "/favicon.ico"
                        )
                        .check(r -> StpUtil.checkLogin())
        )).addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 1);

        // 3. 分页参数拦截器:从 request 提取 page/pageSize 注入 ThreadLocal
        //    置登录校验之后(仅对通过校验的请求生效,被拦截的请求不浪费提取开销)
        registry.addInterceptor(pageParameterInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 2);
    }
}
