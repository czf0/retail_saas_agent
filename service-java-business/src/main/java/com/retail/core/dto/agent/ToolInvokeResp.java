package com.retail.core.dto.agent;

import com.retail.core.enums.ErrCodeEnum;
import lombok.Data;

/**
 * Agent 工具统一调用响应 DTO (Java /invoke 接口 → Python).
 * <p>
 * Java 端只返回原始业务对象 (如 StockAdjustResp), 不做格式化;
 * Python 端 LLM 根据 outputHint (注入 system prompt) 自行组织输出格式.
 * <p>
 * 幂等命中时返回缓存的 {@code data}, 不重复执行工具.
 * <p>
 * 失败时 {@code success=false}, {@code error} 包含面向用户的错误信息,
 * {@code errorCode} 为 Integer 错误码 (与 {@link ErrCodeEnum} 对齐, 三端统一).
 */
@Data
public class ToolInvokeResp {

    /** 是否执行成功 */
    private boolean success;

    /** 原始业务对象 (成功时, LLM 据 outputHint 组织输出) */
    private Object data;

    /** 面向用户的错误信息 (失败时) */
    private String error;

    /** 错误码 (失败时, 与 ErrCodeEnum 对齐: 40001=TOOL_NOT_FOUND / 40002=TOOL_DISABLED / ...) */
    private Integer errorCode;

    /** 是否命中幂等缓存 (true=未实际执行工具, 返回缓存结果) */
    private boolean idempotentHit;

    /** 工具执行耗时 (ms, 不含幂等命中场景) */
    private Long elapsedMs;

    public static ToolInvokeResp ok(Object data, Long elapsedMs) {
        ToolInvokeResp resp = new ToolInvokeResp();
        resp.setSuccess(true);
        resp.setData(data);
        resp.setElapsedMs(elapsedMs);
        return resp;
    }

    public static ToolInvokeResp idempotent(Object cachedData) {
        ToolInvokeResp resp = new ToolInvokeResp();
        resp.setSuccess(true);
        resp.setData(cachedData);
        resp.setIdempotentHit(true);
        return resp;
    }

    /**
     * 构造失败响应 — 传入错误码枚举, error 取枚举预设的面向用户提示.
     */
    public static ToolInvokeResp fail(ErrCodeEnum err) {
        ToolInvokeResp resp = new ToolInvokeResp();
        resp.setSuccess(false);
        resp.setErrorCode(err.getCode());
        resp.setError(err.getMsg());
        return resp;
    }

    /**
     * 构造失败响应 — 传入 code 和 msg.
     * <p>用于 BizException 透传 (其 msg 已由 Service 层面向用户编写).
     */
    public static ToolInvokeResp fail(Integer code, String msg) {
        ToolInvokeResp resp = new ToolInvokeResp();
        resp.setSuccess(false);
        resp.setErrorCode(code);
        resp.setError(msg);
        return resp;
    }
}
