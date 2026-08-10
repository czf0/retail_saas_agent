package com.retail.core.exception;

import com.retail.core.enums.ErrCodeEnum;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 业务异常基类.
 * BizException
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public abstract class BizException extends RuntimeException {
    private final Integer code;
    private final String msg;

    // 子类专用:传入固定枚举
    protected BizException(ErrCodeEnum err) {
        super(err.getMsg());
        this.code = err.getCode();
        this.msg = err.getMsg();
    }

    // 子类专用:固定错误码 + 自定义提示文案
    protected BizException(ErrCodeEnum err, String customMsg) {
        super(customMsg);
        this.code = err.getCode();
        this.msg = customMsg;
    }

}
