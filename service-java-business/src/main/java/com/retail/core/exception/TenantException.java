package com.retail.core.exception;

import com.retail.core.enums.ErrCodeEnum;

/**
 * 租户隔离异常.
 * <p>触发时机: 租户上下文缺失/非法时 throw (如 {@code TenantInterceptor} 校验租户头失败).
 * <p>解决的问题: 在多租户场景下标记租户数据隔离相关错误, 由
 * {@code GlobalExceptionHandler} 统一转 R 响应, 防止租户信息泄漏.
 * <p>使用约束: msg 面向用户提示, 不得泄漏租户内部信息 (铁律 15);
 * 默认错误码由 {@code ErrCodeEnum.TENANT_MISSING} 提供.
 */
public class TenantException extends BizException {
    // 使用类内置默认提示
    public TenantException() {
        super(ErrCodeEnum.TENANT_MISSING);
    }
    // 自定义提示文案,错误码固定不变
    public TenantException(String customMsg) {
        super(ErrCodeEnum.TENANT_MISSING, customMsg);
    }
}
