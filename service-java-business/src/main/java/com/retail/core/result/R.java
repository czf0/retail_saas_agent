package com.retail.core.result;

import com.retail.core.enums.ErrCodeEnum;
import lombok.Data;

/**
 * 统一返回体 — 三端 (Java / Python / 前端) 字段完全对齐.
 * <p>
 * 成功约定: {@code code = 200} (HTTP 标准成功码,与 {@link ErrCodeEnum#SUCCESS} 对齐).
 * <p>
 * 失败约定: {@code code} 为 5 位业务错误码 (如 10001/20002/40005),{@code msg} 为面向用户的友好提示.
 * 技术详情 (异常类名,堆栈,内部字段名) 仅写入后端日志,不通过 msg 泄漏.
 */
@Data
public class R<T> {
    /** 业务码: 200=成功, 非200=失败 (5 位错误码) */
    private Integer code;
    /** 面向用户的提示信息 */
    private String msg;
    /** 业务数据 (成功时) */
    private T data;
    /** 链路追踪 ID (便于按 traceId 检索日志) */
    private String traceId;

    public R(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public R(T data) {
        this.data = data;
    }

    public R() {
    }

    /**
     * 构造成功返回体.
     * <p>code = 200 (与 HTTP 标准成功码对齐,废弃旧 null 约定).
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.data = data;
        return r;
    }

    /**
     * 构造失败返回体 — 传入错误码枚举,msg 取枚举预设的面向用户提示.
     */
    public static <T> R<T> fail(ErrCodeEnum err) {
        return new R<>(err.getCode(), err.getMsg());
    }

    /**
     * 构造失败返回体 — 传入自定义 code 和 msg.
     * <p>用于 Service 层 BizException 透传 (其 msg 已面向用户编写).
     */
    public static <T> R<T> fail(Integer code, String msg) {
        return new R<>(code, msg);
    }
}
