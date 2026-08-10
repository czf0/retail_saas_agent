package com.retail.core.exception;

import com.retail.core.enums.ErrCodeEnum;

/**
 * 认证/权限异常.
 * <p>触发时机: 未登录/登录态失效/无访问权限等认证场景 throw.
 * <p>解决的问题: 与 Sa-Token 异常互补, 提供业务侧主动抛出的认证异常入口,
 * 由 {@code GlobalExceptionHandler} 统一转 R 响应.
 * <p>使用约束: msg 面向用户提示, 不得泄漏权限码/内部标识 (铁律 15);
 * 默认错误码由 {@code ErrCodeEnum.AUTH_NOT_LOGIN} 提供.
 */
public class AuthException extends BizException {
    public AuthException() {
        super(ErrCodeEnum.AUTH_NOT_LOGIN);
    }
    public AuthException(String customMsg) {
        super(ErrCodeEnum.AUTH_NOT_LOGIN, customMsg);
    }
}
