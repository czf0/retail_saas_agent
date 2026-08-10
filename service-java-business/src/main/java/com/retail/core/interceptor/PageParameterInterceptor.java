package com.retail.core.interceptor;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.context.PageContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 分页参数拦截器(若依模式:从 {@link HttpServletRequest} 直接提取分页参数).
 * <p>
 * preHandle 从 query 参数提取 {@code page} / {@code pageSize},构造 MyBatis-Plus {@link Page}
 * 存入 {@link PageContextHolder},供 Service 层 {@code selectPage} 使用.
 * <p>
 * 业务 Req 与 Controller 不再承载分页参数,分页彻底解耦为横切关注点.
 * <p>
 * afterCompletion 清理 ThreadLocal 防内存泄漏(与 {@link GlobalReqInterceptor} 同模式).
 */
@Slf4j
@Component
public class PageParameterInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 query 参数提取分页参数
        Integer page = parseInt(request.getParameter("page"));
        Integer pageSize = parseInt(request.getParameter("pageSize"));
        // 注入点显式声明默认值:缺失 / 非法时回退 PageContextHolder 默认常量(第 1 页 / 每页 20 条)
        // build() 仍保留同源兜底 + pageSize 上限截断(MAX_PAGE_SIZE),覆盖 Agent 工具与 get() 回退等非 HTTP 路径
        if (page == null || page < 1) {
            page = PageContextHolder.DEFAULT_PAGE;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = PageContextHolder.DEFAULT_PAGE_SIZE;
        }
        Page<?> p = PageContextHolder.build(page, pageSize);
        PageContextHolder.set(p);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal,防止线程池复用导致内存泄漏与上下文串用
        PageContextHolder.clear();
    }

    /**
     * 安全解析分页参数字符串为 Integer.
     * <p>空串 / 非数字返回 null(交由 {@link PageContextHolder#build} 兜底默认值),不抛异常.
     *
     * @param s 原始 query 参数值
     * @return 解析后的整数,或 null
     */
    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
