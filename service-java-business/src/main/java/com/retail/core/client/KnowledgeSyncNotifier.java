package com.retail.core.client;

import com.retail.core.dto.kb.KnowledgeSyncEvent;
import com.retail.core.trace.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 知识库同步通知器: Java 文档变更后调 Python /api/v1/kb/sync 增量更新索引.
 * <p>
 * 设计说明 (知识文档管理模块设计 §4.2 + 评审 C5/D5):
 * - Java 为 SSOT, 文档发布/失效/删除/同义词变更时, 由本通知器调 Python kb_sync 入口;
 * - Python 端处理为幂等 (ingest 按 doc_id 去重, delete 已删无副作用);
 * - 通知失败不阻断 Java 主事务 (catch + warn), 由 Python 侧定时全量校对兜底;
 * - 复用项目已有 RestTemplate (与 AgentHttpClient 同基础设施);
 * - Python 地址与 AgentHttpClient 保持一致 (http://127.0.0.1:8000), 后续可抽配置项.
 * <p>
 * 与 AgentHttpClient 的区别:
 * - AgentHttpClient 走 /api/v1/agent/* (对话流), 透传身份头;
 * - 本通知器走 /api/v1/kb/sync (知识库同步), 服务间内部调用, 不需身份头.
 */
@Slf4j
@Component
public class KnowledgeSyncNotifier {

    /** Python Agent 基地址 (与 AgentHttpClient 一致, 后续可抽配置项) */
    private static final String PYTHON_BASE = "http://127.0.0.1:8000";

    /** 知识库同步接口路径 */
    private static final String KB_SYNC_PATH = "/api/v1/kb/sync";

    private final RestTemplate restTemplate;

    public KnowledgeSyncNotifier(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送同步事件到 Python.
     * <p>失败不抛异常 (catch + warn), 避免阻断 Java 主事务; Python 侧定时全量校对兜底.
     *
     * <p>D1.2 升级为请求-响应: 返回 Python 响应的 data Map (含 ok/message/affected/chunks),
     * 供 Service 层提取 chunks 落库 kb_doc_chunk. 通知失败或业务失败时返回 null.
     *
     * @param event 同步事件 (含 event_type/tenant_id/payload)
     * @return Python 响应 data Map (含 chunks 字段), 失败返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> notify(KnowledgeSyncEvent event) {
        if (event == null || event.getEventType() == null || event.getTenantId() == null) {
            log.warn("kb_sync_event_invalid, 跳过通知: event={}", event);
            return null;
        }
        // 填充 traceId (若未传则取当前链路)
        if (event.getTraceId() == null || event.getTraceId().isEmpty()) {
            event.setTraceId(TraceUtil.getTraceId());
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<KnowledgeSyncEvent> entity = new HttpEntity<>(event, headers);

            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    PYTHON_BASE + KB_SYNC_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            // Python 返回 R 结构: {code, msg, data: {ok, message, affected, chunks}}
            // 三端错误码统一后成功码为 200 (原 0 已废弃); 补 data.ok 兜底以防旧实例回传 0
            Map<String, Object> body = resp.getBody();
            boolean codeOk = body != null
                    && (Integer.valueOf(200).equals(body.get("code"))
                        || Integer.valueOf(0).equals(body.get("code")));
            if (codeOk) {
                Map<String, Object> data = body != null ? (Map<String, Object>) body.get("data") : null;
                boolean ok = data != null && Boolean.TRUE.equals(data.get("ok"));
                if (ok) {
                    log.info("kb_sync_ok event={} tenant={} affected={} chunks={}",
                            event.getEventType(), event.getTenantId(),
                            data != null ? data.get("affected") : "?",
                            data != null && data.get("chunks") != null
                                    ? ((List<?>) data.get("chunks")).size() : 0);
                    return data;
                } else {
                    log.warn("kb_sync_business_fail event={} tenant={} data={}",
                            event.getEventType(), event.getTenantId(), data);
                }
            } else {
                log.warn("kb_sync_unexpected_response event={} tenant={} body={}",
                        event.getEventType(), event.getTenantId(), body);
            }
        } catch (RestClientException e) {
            // Python 不可用不阻断 Java 主事务, 定时全量校对兜底
            log.warn("kb_sync_python_unavailable event={} tenant={} error={}",
                    event.getEventType(), event.getTenantId(), e.getMessage());
        }
        return null;
    }
}
