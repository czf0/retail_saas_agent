package com.retail.core.dto.agent;

import lombok.Data;

import java.util.Map;

/**
 * Agent 工具统一调用请求 DTO (Python → Java /invoke 接口).
 * <p>
 * 二级定位: {@code business} + {@code operation} 组合为 toolName (如 "stock" + "adjust" → "stock:adjust"),
 * AgentToolRegistry 据此查找 ToolMeta, 反射调用对应的 @AgentTool 方法.
 * <p>
 * {@code args} 为工具参数 Map, Java 端通过 ObjectMapper 反序列化为方法参数类型 (如 StockAdjustReq).
 * <p>
 * 认证: Python 调用时携带 X-Internal-Secret + X-User-ID 头, GlobalReqInterceptor 建立临时登录态;
 * 幂等: Python 传 X-Idempotency-Key 头 (tool_call_id), Java 端 Redis 缓存兜底.
 */
@Data
public class ToolInvokeReq {

    /** 业务域 (如 "stock", "order", "stats") */
    private String business;

    /** 操作标识 (如 "adjust", "query", "check") */
    private String operation;

    /** 工具参数 (Map 形式, Java 端反序列化为方法参数类型) */
    private Map<String, Object> args;
}
