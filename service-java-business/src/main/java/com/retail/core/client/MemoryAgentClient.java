package com.retail.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.business.dto.resp.MemoryExtractResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * 长期记忆 AI 客户端: Java 调用 Python /memory/extract 与 /memory/consolidate 完成 AI 抽取/巩固.
 * <p>
 * 设计对齐《长期记忆系统整改方案》5.2:
 * <ul>
 *   <li>Java 只管存储与触发, AI 语义操作 (抽取/巩固) 由 Python 完成;</li>
 *   <li>抽取: 传增量会话消息 (from_msg_id→to_msg_id 闭区间), Python 回带置信度操作;</li>
 *   <li>巩固: 传当前用户记忆快照, Python 回带合并/去重/衰减操作;</li>
 *   <li>容错: Python 不可用/解析失败返回 null, 由 Service 层决定游标不推进 (下次重抽);</li>
 *   <li>复用项目 RestTemplate (与 AgentHttpClient / KbFileParseClient 同基础设施), 基地址取自 agent.python-base.</li>
 * </ul>
 */
@Slf4j
@Component
public class MemoryAgentClient {

    /** Python Agent 基地址 (与 KbFileParseClient 一致) */
    @Value("${agent.python-base:http://127.0.0.1:8000}")
    private String pythonBase;

    /** Python 抽取接口路径 */
    private static final String EXTRACT_PATH = "/api/v1/agent/memory/extract";
    /** Python 巩固接口路径 */
    private static final String CONSOLIDATE_PATH = "/api/v1/agent/memory/consolidate";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MemoryAgentClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 Python 抽取长期记忆.
     *
     * @param tenantId    租户ID
     * @param userId      用户ID
     * @param sessionId   会话ID
     * @param fromMsgId   增量起点消息 id (闭区间下界-1, 即上次已抽取游标)
     * @param toMsgId     增量终点消息 id
     * @param conversation 增量对话消息 (role/content, 时间正序)
     * @return 抽取操作结果; Python 不可用或解析失败返回 null (Service 据此不推进游标)
     */
    public MemoryExtractResp extract(Long tenantId, Long userId, String sessionId,
                                     Long fromMsgId, Long toMsgId,
                                     List<Map<String, String>> conversation) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("tenant_id", tenantId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("from_msg_id", fromMsgId);
        body.put("to_msg_id", toMsgId);
        body.put("conversation", conversation);
        return post(EXTRACT_PATH, body);
    }

    /**
     * 调用 Python 巩固长期记忆 (槽位溢出触发).
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param memories 当前用户现有记忆快照 (id/category/content/confidence/importance)
     * @return 巩固操作结果; Python 不可用或解析失败返回 null
     */
    public MemoryExtractResp consolidate(Long tenantId, Long userId, List<Map<String, Object>> memories) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("tenant_id", tenantId);
        body.put("user_id", userId);
        body.put("memories", memories);
        return post(CONSOLIDATE_PATH, body);
    }

    /**
     * 通用 POST: 发送 JSON 到 Python, 解析 R 结构 data 为 MemoryExtractResp.
     * <p>
     * Python 返回 R 结构: {code, msg, data: {operations: [], ok, message}}.
     * 成功判定: HTTP 200 且 body 可解析 (code==200 或 code==0 兼容旧实例).
     *
     * @return 解析后的操作结果; 失败返回 null
     */
    private MemoryExtractResp post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    pythonBase + path, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null) {
                log.warn("memory_agent_empty_response path={}", path);
                return null;
            }
            boolean codeOk = Integer.valueOf(200).equals(respBody.get("code"))
                    || Integer.valueOf(0).equals(respBody.get("code"));
            if (!codeOk) {
                log.warn("memory_agent_unexpected_response path={} body={}", path, respBody);
                return null;
            }
            Object data = respBody.get("data");
            if (data == null) {
                log.warn("memory_agent_no_data path={} body={}", path, respBody);
                return null;
            }
            // data 为 JSON 对象 (operations/ok/message), 反序列化为 MemoryExtractResp
            return objectMapper.convertValue(data, MemoryExtractResp.class);
        } catch (RestClientException e) {
            // Python 不可用: 返回 null, Service 层不推进游标 (下次重抽同一批)
            log.warn("memory_agent_python_unavailable path={} error={}", path, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("memory_agent_parse_failed path={} error={}", path, e.getMessage());
            return null;
        }
    }
}