package com.retail.core.exception;

import com.retail.core.enums.ErrCodeEnum;

/**
 * 参数校验异常.
 * <p>触发时机: 参数缺失/非法/越界等业务参数错误时 throw (如 {@code EnumUtil.fromCode} 非法枚举 code).
 * <p>解决的问题: 将参数错误归一到 {@link BizException} 异常体系, 由
 * {@code GlobalExceptionHandler} 统一转 R 响应, 避免散抛 RuntimeException.
 * <p>使用约束: msg 面向用户编写, 不得包含内部字段名/堆栈 (铁律 15); 默认错误码由
 * {@code ErrCodeEnum.PARAM_INVALID} 提供.
 */
public class ParamException extends BizException {
    public ParamException() {
        super(ErrCodeEnum.PARAM_INVALID);
    }
    public ParamException(String customMsg) {
        super(ErrCodeEnum.PARAM_INVALID, customMsg);
    }
}
