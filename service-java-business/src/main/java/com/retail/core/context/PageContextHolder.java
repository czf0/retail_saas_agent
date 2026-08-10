package com.retail.core.context;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页上下文持有者(ThreadLocal,仿 {@link AuditUserContext} / TenantContext 模式).
 * <p>
 * 分页作为横切关注点:HTTP 路径由 {@code PageParameterInterceptor} 从 {@code HttpServletRequest}
 * 提取 page / pageSize 参数注入此处;Agent 工具路径(反射调用不经 HTTP 拦截器)由工具方法
 * 手动 {@link #set} 注入 + finally {@link #clear} 清理.
 * <p>
 * 业务 Req / Controller / Service 签名完全不承载分页参数,统一经本类传递给 MyBatis-Plus {@code selectPage}.
 */
public final class PageContextHolder {

    /** 默认页码(参数缺失 / 越界时回退) */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数(参数缺失 / 越界时回退) */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 单页最大条数上限(与 PaginationInnerInterceptor.maxLimit 双重保险,防恶意大分页拖垮 DB) */
    public static final int MAX_PAGE_SIZE = 500;

    private static final ThreadLocal<Page<?>> HOLDER = new ThreadLocal<>();

    private PageContextHolder() {
    }

    public static void set(Page<?> page) {
        HOLDER.set(page);
    }

    /**
     * 取当前分页对象,<b>永不为 null</b>.
     * <p>
     * ThreadLocal 为空时(非 HTTP 线程 / 单元测试 / Agent 工具路径遗漏注入)兜底返回
     * 默认 {@code Page(DEFAULT_PAGE, DEFAULT_PAGE_SIZE)},避免 Service 层 {@code page.getCurrent()} NPE.
     * <p>
     * 兜底=安全分页(第 1 页 20 条),即便注入遗漏也不会全表扫描拖垮 DB.
     *
     * @param <T> 实体泛型
     * @return 当前请求的分页对象(非 null)
     */
    @SuppressWarnings("unchecked")
    public static <T> Page<T> get() {
        Page<?> page = HOLDER.get();
        return page != null ? (Page<T>) page : build(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 构造边界安全的分页对象(page<1→1,pageSize 越界→默认 / 截断到上限).
     * <p>
     * 拦截器与 Agent 工具路径共用,集中处理边界,避免各处重复校验.
     *
     * @param page     请求页码(可空 / 非法)
     * @param pageSize 每页条数(可空 / 非法)
     * @param <T>      实体泛型
     * @return 边界安全的 MyBatis-Plus 分页对象
     */
    public static <T> Page<T> build(Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(p, ps);
    }
}
