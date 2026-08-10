package com.retail.core.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 知识库文件解析客户端: Java 转发上传文件到 Python /api/v1/kb/parse 解析为文本.
 * <p>
 * 设计说明 (D2 文件上传管控, 决策 2: Python 端解析):
 * - 前端上传 MultipartFile → Java Controller 接收 → 本客户端转发原始字节到 Python;
 * - Python 用 pdfplumber/python-docx 解析后返回 text, Java 拿到 text 走原有落盘+建草稿流程;
 * - 解析能力放 Python 端的原因: pdf/docx 解析库为 Python 生态, 避免在 Java 引入重型依赖.
 * <p>
 * 与 KnowledgeSyncNotifier 的区别:
 * - KnowledgeSyncNotifier 走 /api/v1/kb/sync (文档元数据同步, JSON 请求);
 * - 本客户端走 /api/v1/kb/parse (原始文件解析, multipart 请求).
 * <p>
 * 容错: Python 不可用 / 解析失败 返回 null (Controller 层降级提示用户重试, 不阻断主流程).
 */
@Slf4j
@Component
public class KbFileParseClient {

    /** Python Agent 基地址 (与 KnowledgeSyncNotifier 一致, 后续可抽配置项) */
    @Value("${agent.python-base:http://127.0.0.1:8000}")
    private String pythonBase;

    /** 知识库文件解析接口路径 */
    private static final String KB_PARSE_PATH = "/api/v1/kb/parse";

    private final RestTemplate restTemplate;

    public KbFileParseClient(RestTemplate restTemplate) {
        // 注入支持 multipart 的 RestTemplate (FormHttpMessageConverter 已在配置类注册)
        this.restTemplate = restTemplate;
    }

    /**
     * 转发文件到 Python 解析, 返回解析结果.
     * <p>
     * Python 返回 R 结构: {code: 200, msg, data: {ok, text, page_count, parse_engine, char_count, filename}}.
     *
     * @param filename 原始文件名 (用于 Python 端按扩展名分发解析)
     * @param content  文件原始字节
     * @return 解析结果 Map (含 ok/text/page_count 等); Python 不可用或解析失败返回 null
     */
    public Map<String, Object> parseFile(String filename, byte[] content) {
        try {
            // 构造 multipart/form-data 请求体 (file 字段)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // ByteArrayResource 需重写 getFilename 返回文件名, 否则 Spring 无法设置 Content-Disposition
            ByteArrayResource fileResource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    pythonBase + KB_PARSE_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null) {
                log.warn("kb_parse_empty_response filename={}", filename);
                return null;
            }
            // 成功码 200 (三端错误码统一); 补 0 兜底以防旧实例
            boolean codeOk = Integer.valueOf(200).equals(respBody.get("code"))
                    || Integer.valueOf(0).equals(respBody.get("code"));
            if (!codeOk) {
                log.warn("kb_parse_unexpected_response filename={} body={}", filename, respBody);
                return null;
            }
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null) {
                log.warn("kb_parse_no_data filename={} body={}", filename, respBody);
                return null;
            }
            // 业务级失败 (格式不支持/文件损坏): data.ok=false, 返回 data 供 Controller 提取 message
            if (!Boolean.TRUE.equals(data.get("ok"))) {
                log.warn("kb_parse_business_fail filename={} message={}", filename, data.get("message"));
            } else {
                log.info("kb_parse_ok filename={} engine={} chars={}",
                        filename, data.get("parse_engine"), data.get("char_count"));
            }
            return data;
        } catch (RestClientException e) {
            // Python 不可用: 返回 null, Controller 层降级提示用户重试
            log.warn("kb_parse_python_unavailable filename={} error={}", filename, e.getMessage());
            return null;
        }
    }
}
