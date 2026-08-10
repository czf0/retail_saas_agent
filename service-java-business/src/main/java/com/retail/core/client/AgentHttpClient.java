package com.retail.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.core.dto.agent.AgentChatDTO;
import com.retail.core.dto.agent.AgentResumeDTO;
import com.retail.core.dto.agent.StreamChunkDTO;
import com.retail.core.dto.agent.StreamChatHandler;
import com.retail.core.exception.AgentRemoteException;
import com.retail.core.result.R;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.tenant.TenantContext;
import com.retail.core.trace.TraceUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Python Agent HTTP 客户端(三端打通核心:Java → Python SSE 真实转发).
 * <p>
 * 提供两种调用方式:
 * <ul>
 *   <li>{@link #chat}: 一次性对话(强类型 DTO 入参出参)</li>
 *   <li>{@link #streamChat}: SSE 流式对话(真实订阅 Python SSE,逐片过滤后透传前端)</li>
 * </ul>
 * 身份上下文通过请求头透传(X-Tenant-ID / X-Store-ID / X-User-ID / X-Role / X-Trace-ID),
 * 与 Python ContextMiddleware.load_from_headers 对齐.
 * <p>
 * streamChat 过滤策略(工具信息不展示前端):
 * <ul>
 *   <li>tool_call / tool_result 分片不透传前端,仅累加到 usedTools 列表供持久化审计</li>
 *   <li>done 分片的 meta.used_tools / usedTools 剥离后再透传(仅保留 intent / tokensUsed)</li>
 *   <li>token / meta / done / error 分片正常透传</li>
 * </ul>
 */
@Component
public class AgentHttpClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;
    // 本地硬编码 Python Agent 地址,后续可替换 Nacos
    private static final String AGENT_BASE = "http://127.0.0.1:8000/api/v1/agent";

    public AgentHttpClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                           @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 普通一次性对话,强类型 DTO 入参出参.
     * <p>通过请求头透传身份上下文,供 Python ContextMiddleware 加载到线程上下文.
     */
    public R<Object> chat(AgentChatDTO dto) {
        fillContext(dto);
        try {
            HttpHeaders headers = buildHeaders(dto);
            HttpEntity<AgentChatDTO> entity = new HttpEntity<>(dto, headers);
            return restTemplate.exchange(AGENT_BASE + "/chat", HttpMethod.POST, entity, R.class).getBody();
        } catch (RestClientException e) {
            throw new AgentRemoteException();
        }
    }

    /**
     * SSE 流式对话(向后兼容,无持久化回调).
     * <p>已废弃,请使用 {@link #streamChat(AgentChatDTO, StreamChatHandler)} 由 ChatSessionService 编排持久化.
     */
    @Deprecated
    public SseEmitter streamChat(AgentChatDTO dto) {
        return streamChat(dto, null);
    }

    /**
     * SSE 流式对话:真实订阅 Python SSE,逐片过滤后透传前端,done 时回调持久化.
     * <p>
     * 流程:
     * <ol>
     *   <li>构造 Python ChatRequest 请求体(snake_case: query / session_id / tenant_id / store_id)</li>
     *   <li>POST 到 Python /api/v1/agent/stream/chat,订阅 SSE 流</li>
     *   <li>逐行读取 SSE data 行,反序列化为 StreamChunkDTO</li>
     *   <li>过滤 tool_call / tool_result(不透传前端,累加 usedTools)</li>
     *   <li>done 分片剥离 meta.used_tools 后透传前端,回调 handler.onDone 持久化</li>
     *   <li>token / meta / error 分片正常透传</li>
     * </ol>
     * SseEmitter 超时 120 秒(LLM 长回答场景).
     */
    public SseEmitter streamChat(AgentChatDTO dto, StreamChatHandler handler) {
        fillContext(dto);
        SseEmitter emitter = new SseEmitter(120000L);
        asyncExecutor.execute(() -> {
            try {
                // 构造 Python ChatRequest 请求体(snake_case)
                // 身份/链路元数据 (tenant_id/store_id) 统一走 header (buildHeaders 已设置
                // X-Tenant-ID/X-Store-ID), 不再放入 body, 避免双源不一致与前端篡改.
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("query", dto.getQuery());
                if (dto.getSessionId() != null) {
                    requestBody.put("session_id", dto.getSessionId());
                }
                byte[] bodyBytes = objectMapper.writeValueAsBytes(requestBody);

                // 构造请求头
                HttpHeaders headers = buildHeaders(dto);
                headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));

                // 执行请求并读取 SSE 流
                restTemplate.execute(
                        AGENT_BASE + "/stream/chat",
                        HttpMethod.POST,
                        request -> {
                            request.getHeaders().putAll(headers);
                            StreamUtils.copy(bodyBytes, request.getBody());
                        },
                        response -> {
                            readSseStream(response.getBody(), emitter, handler);
                            return null;
                        }
                );
            } catch (Exception e) {
                sendErrorChunk(emitter, "Agent 服务调用失败: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * HITL 审批恢复 SSE 流式对话: 用户审批后调 Python /stream/resume 续接被中断的 graph.
     * <p>
     * 流程与 {@link #streamChat} 一致, 区别:
     * <ul>
     *   <li>POST 到 Python /api/v1/agent/stream/resume (非 /stream/chat)</li>
     *   <li>请求体为 session_id + approved + reason (非 query + session_id)</li>
     *   <li>复用 readSseStream 分片处理 (含 pending_approval 透传 + done 持久化)</li>
     * </ul>
     * done 时回调 handler.onDone 持久化 assistant 消息 (与 streamChat 一致).
     *
     * @param dto     恢复请求 (含 sessionId / approved / reason, 继承 AgentChatDTO 供身份透传)
     * @param handler done 回调 (持久化 assistant 消息)
     * @return SseEmitter SSE 流式响应
     */
    public SseEmitter resumeStream(AgentResumeDTO dto, StreamChatHandler handler) {
        fillContext(dto);
        SseEmitter emitter = new SseEmitter(120000L);
        asyncExecutor.execute(() -> {
            try {
                // 构造 Python ResumeRequest 请求体 (snake_case: session_id / approved / reason)
                Map<String, Object> requestBody = new HashMap<>();
                if (dto.getSessionId() != null) {
                    requestBody.put("session_id", dto.getSessionId());
                }
                requestBody.put("approved", Boolean.TRUE.equals(dto.getApproved()));
                if (dto.getReason() != null) {
                    requestBody.put("reason", dto.getReason());
                }
                byte[] bodyBytes = objectMapper.writeValueAsBytes(requestBody);

                // 构造请求头 (复用 buildHeaders, 身份上下文透传与 streamChat 一致)
                HttpHeaders headers = buildHeaders(dto);
                headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));

                // 执行请求并读取 SSE 流 (复用 readSseStream 分片处理逻辑)
                restTemplate.execute(
                        AGENT_BASE + "/stream/resume",
                        HttpMethod.POST,
                        request -> {
                            request.getHeaders().putAll(headers);
                            StreamUtils.copy(bodyBytes, request.getBody());
                        },
                        response -> {
                            readSseStream(response.getBody(), emitter, handler);
                            return null;
                        }
                );
            } catch (Exception e) {
                sendErrorChunk(emitter, "Agent 恢复调用失败: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 读取 Python SSE 流,过滤工具分片,透传其余分片到前端,done 时回调持久化.
     * <p>
     * 分片处理策略:
     * <ul>
     *   <li>tool_call / tool_result: 不透传前端, 仅累加 usedTools 供审计持久化</li>
     *   <li>token / meta: 累加正文 + 透传前端</li>
     *   <li>done: 剥离 meta.used_tools 后透传前端, 回调 handler.onDone 持久化</li>
     *   <li>error: 透传前端</li>
     *   <li>pending_approval (HITL): 透传前端, 不触发持久化 (流程未完成), 不累加正文</li>
     * </ul>
     */
    private void readSseStream(InputStream is, SseEmitter emitter, StreamChatHandler handler) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder contentAccumulator = new StringBuilder();
        List<Object> usedTools = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }

            StreamChunkDTO chunk = objectMapper.readValue(data, StreamChunkDTO.class);
            String chunkType = chunk.getChunkType();

            // 工具分片:不透传前端,仅累加 usedTools 供审计持久化
            if ("tool_call".equals(chunkType) || "tool_result".equals(chunkType)) {
                if (chunk.getMeta() != null) {
                    usedTools.add(chunk.getMeta());
                }
                continue;
            }

            // pending_approval (HITL): 透传前端展示审批卡片, 持久化为 assistant 消息供刷新恢复
            // (graph 被 interrupt() 暂停, 状态在 checkpointer, 等待 resume 请求)
            if ("pending_approval".equals(chunkType)) {
                // 持久化: intent='pending_approval', content=工具信息 JSON (供前端刷新后恢复审批卡片)
                handler.onPendingApproval(
                        chunk.getSessionId() != null ? chunk.getSessionId() : "",
                        chunk.getContent() != null ? chunk.getContent() : "{}"
                );
                emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.APPLICATION_JSON));
                continue;
            }

            // token / meta 分片:累加正文 + 透传前端
            if (("token".equals(chunkType) || "meta".equals(chunkType)) && chunk.getContent() != null) {
                contentAccumulator.append(chunk.getContent());
            }

            if ("done".equals(chunkType)) {
                handleDoneChunk(chunk, emitter, handler, contentAccumulator, usedTools);
            } else if ("error".equals(chunkType)) {
                emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.APPLICATION_JSON));
            } else {
                // token / meta / 其他:正常透传前端
                emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.APPLICATION_JSON));
            }
        }
        emitter.complete();
    }

    /**
     * 处理 done 分片:剥离 meta.used_tools → 透传前端;提取元数据 → 回调持久化.
     */
    @SuppressWarnings("unchecked")
    private void handleDoneChunk(StreamChunkDTO chunk, SseEmitter emitter, StreamChatHandler handler,
                                  StringBuilder contentAccumulator, List<Object> usedTools) throws IOException {
        chunk.setFinished(true);
        String intent = null;
        Integer tokensUsed = null;

        if (chunk.getMeta() != null) {
            Map<String, Object> meta = chunk.getMeta();
            intent = getStrMeta(meta, "intent");
            tokensUsed = getIntMeta(meta, "tokens_used", "tokensUsed");

            // 从 done.meta 合并 used_tools(Python 可能在 done 中汇总而非逐片发送)
            Object toolsFromMeta = meta.get("used_tools");
            if (toolsFromMeta == null) {
                toolsFromMeta = meta.get("usedTools");
            }
            if (toolsFromMeta instanceof List) {
                usedTools = (List<Object>) toolsFromMeta;
            }

            // 剥离工具信息后构造干净 meta(仅保留 intent / tokensUsed 供前端展示)
            Map<String, Object> cleanMeta = new HashMap<>(meta);
            cleanMeta.remove("used_tools");
            cleanMeta.remove("usedTools");
            chunk.setMeta(cleanMeta);
        }

        // done 分片的 content 为权威完整答案,为空则用累加的 token 分片
        String fullContent = chunk.getContent() != null ? chunk.getContent() : contentAccumulator.toString();

        // 透传 done 分片到前端
        emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.APPLICATION_JSON));

        // 回调持久化 assistant 消息
        if (handler != null) {
            String toolsJson = usedTools.isEmpty() ? null : objectMapper.writeValueAsString(usedTools);
            handler.onDone(chunk.getSessionId(), fullContent, intent, toolsJson, tokensUsed);
        }
    }

    /** 从 meta Map 中取字符串值(尝试多个可能的键名) */
    private String getStrMeta(Map<String, Object> meta, String... keys) {
        for (String key : keys) {
            Object val = meta.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }

    /** 从 meta Map 中取整数值(尝试多个可能的键名) */
    private Integer getIntMeta(Map<String, Object> meta, String... keys) {
        for (String key : keys) {
            Object val = meta.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        }
        return null;
    }

    /** 向前端发送 error 分片(Python 连接失败等异常场景) */
    private void sendErrorChunk(SseEmitter emitter, String message) {
        try {
            StreamChunkDTO errorChunk = new StreamChunkDTO();
            errorChunk.setChunkType("error");
            errorChunk.setContent(message);
            emitter.send(SseEmitter.event().name("message").data(errorChunk, MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            // emitter 已关闭,忽略
        }
    }

    /**
     * 自动填充租户,链路,用户身份上下文.
     * <p>从 LoginUserHolder 取当前登录用户,填充 userId/role;
     * 从 TenantContext 取租户/门店;从 TraceUtil 取链路 ID.
     */
    private void fillContext(AgentChatDTO dto) {
        LoginUser lu = LoginUserHolder.get();
        if (lu != null) {
            if (dto.getUserId() == null) {
                dto.setUserId(lu.getUserId());
            }
            if (dto.getRole() == null && lu.getRoleKeys() != null && !lu.getRoleKeys().isEmpty()) {
                dto.setRole(lu.getRoleKeys().get(0));
            }
            // 角色 ID (sys_role.id): 供 Python RAG 业务过滤按角色 ID 隔离文档可见性 (D1.5)
            if (dto.getRoleId() == null && lu.getRoleIds() != null && !lu.getRoleIds().isEmpty()) {
                dto.setRoleId(String.valueOf(lu.getRoleIds().get(0)));
            }
        }
        if (dto.getTenantId() == null) {
            dto.setTenantId(TenantContext.getTenantId());
        }
        if (dto.getStoreId() == null) {
            dto.setStoreId(TenantContext.getStoreId());
        }
        dto.setTraceId(TraceUtil.getTraceId());
    }

    /**
     * 构造透传给 Python Agent 的请求头.
     * <p>身份上下文通过请求头透传(而非请求体),与 Python ContextMiddleware 的
     * load_from_headers 对齐,确保双端协议一致.
     */
    private HttpHeaders buildHeaders(AgentChatDTO dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (dto.getTraceId() != null) {
            headers.set("X-Trace-ID", dto.getTraceId());
        }
        if (dto.getTenantId() != null) {
            headers.set("X-Tenant-ID", dto.getTenantId());
        }
        if (dto.getStoreId() != null) {
            headers.set("X-Store-ID", dto.getStoreId());
        }
        if (dto.getUserId() != null) {
            headers.set("X-User-ID", String.valueOf(dto.getUserId()));
        }
        if (dto.getRole() != null) {
            headers.set("X-Role", dto.getRole());
        }
        if (dto.getRoleId() != null) {
            headers.set("X-Role-Id", dto.getRoleId());
        }
        return headers;
    }
}
