package com.retail.core.enums;

import lombok.Getter;

/**
 * 统一错误码枚举 — 三端 (Java / Python / 前端) 共用同一套 Integer 码值.
 * <p>
 * 编码规则: 成功 = 200 (HTTP 标准); 错误码 5 位分段 (XXYYY):
 * <ul>
 *   <li>10xxx — 系统级 (内部异常,繁忙,参数校验)</li>
 *   <li>20xxx — 认证鉴权 (未登录,无权限,角色不足)</li>
 *   <li>30xxx — 租户/门店 (上下文缺失,隔离校验)</li>
 *   <li>40xxx — 工具调用 (不存在,禁用,权限,参数,执行,超时,熔断,HITL)</li>
 *   <li>50xxx — LLM 调用 (失败,超时,限流,上下文超长)</li>
 *   <li>60xxx — RAG 检索</li>
 *   <li>70xxx — Agent 编排 (流式异常,恢复异常,Skill 异常,拦截)</li>
 * </ul>
 * <p>
 * 设计原则:
 * <ol>
 *   <li>msg 字段面向终端用户,不含技术细节 (异常类名,内部字段名,权限码等);</li>
 *   <li>技术详情仅写入后端日志 (含 traceId),不通过 R.msg 泄漏;</li>
 *   <li>三端对齐: Python ErrorCode / 前端 errorCodeMap 与本枚举一一对应.</li>
 * </ol>
 */
@Getter
public enum ErrCodeEnum {
    SUCCESS(200, "操作成功"),

    // ---- 系统级 (1xxxx) ----
    SYSTEM_INTERNAL_ERROR(10001, "服务暂时不可用，请稍后重试"),
    SYSTEM_BUSY(10002, "系统繁忙，请稍后重试"),
    PARAM_INVALID(10003, "提交的信息有误，请检查后重试"),
    PARAM_TYPE_MISMATCH(10004, "提交的信息格式有误，请检查后重试"),

    // ---- 认证鉴权 (2xxxx) ----
    AUTH_NOT_LOGIN(20001, "登录已过期，请重新登录"),
    AUTH_PERMISSION_DENIED(20002, "您没有执行此操作的权限"),
    AUTH_ROLE_DENIED(20003, "您的账号角色无法执行此操作"),

    // ---- 租户/门店 (3xxxx) ----
    TENANT_MISSING(30001, "租户信息缺失，请联系管理员"),
    TENANT_FORBIDDEN(30002, "无权访问该租户的数据"),
    TENANT_STORE_MISSING(30003, "门店信息缺失，请联系管理员"),

    // ---- 工具调用 (4xxxx) ----
    TOOL_NOT_FOUND(40001, "该功能暂不可用"),
    TOOL_DISABLED(40002, "该功能已下线"),
    TOOL_PERMISSION_DENIED(40003, "您没有使用此功能的权限"),
    TOOL_PARAM_INVALID(40004, "功能参数有误，请检查输入"),
    TOOL_EXEC_ERROR(40005, "操作执行失败，请稍后重试"),
    TOOL_TIMEOUT(40006, "操作处理超时，请稍后重试"),
    TOOL_CIRCUIT_OPEN(40007, "该功能暂时不可用，请稍后重试"),
    TOOL_HITL_REJECTED(40008, "操作已取消"),
    TOOL_HITL_NO_CONTEXT(40009, "该操作需要通过对话发起"),
    TOOL_REMOTE_TIMEOUT(40010, "服务处理超时，请稍后重试"),
    TOOL_REMOTE_FAILED(40011, "服务暂时不可用，请稍后重试"),
    TOOL_GATEWAY_ERROR(40012, "服务返回异常，请稍后重试"),

    // ---- LLM 调用 (5xxxx) ----
    LLM_CALL_FAILED(50001, "AI 服务暂时不可用，请稍后重试"),
    LLM_TIMEOUT(50002, "AI 响应超时，请稍后重试"),
    LLM_RATE_LIMIT(50003, "当前提问人数较多，请稍后重试"),
    LLM_CONTEXT_TOO_LONG(50004, "对话内容过长，请开启新对话"),

    // ---- RAG 检索 (6xxxx) ----
    RAG_RETRIEVE_FAILED(60001, "知识检索失败，请稍后重试"),

    // ---- Agent 编排 (7xxxx) ----
    AGENT_STREAM_ERROR(70001, "生成回答时遇到问题，请重试"),
    AGENT_RESUME_ERROR(70002, "恢复执行时遇到问题，请重新发起对话"),
    AGENT_SKILL_ERROR(70003, "处理您的问题时遇到异常，请重试"),
    AGENT_BLOCKED(70004, "您的请求被拦截，请调整后重试"),

    // ---- Agent 远程调用 (旧码保留兼容, 后续迁移到 40xxx) ----
    AGENT_SERVICE_DOWN(6001, "AI 推理服务不可达"),
    AGENT_REQ_FAIL(6002, "请求 AI 服务失败");

    private final Integer code;
    private final String msg;

    ErrCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
