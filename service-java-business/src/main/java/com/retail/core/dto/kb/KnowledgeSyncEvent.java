package com.retail.core.dto.kb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 知识库同步事件 DTO (Java → Python, 与 Python api/kb_sync_router.py KbSyncRequest 对齐).
 * <p>
 * 字段命名通过 @JsonProperty 指定为 snake_case, 与 Python 侧 Pydantic 模型对齐,
 * 经 Jackson 序列化后 Python 直接解析 (Java 字段保持 camelCase 编码规范).
 * <p>
 * 事件类型 (event_type):
 * - doc_upsert: 文档新增/更新, payload 含 docs 列表;
 * - doc_delete: 文档删除, payload 含 doc_ids 列表;
 * - doc_expire: 文档失效, 复用 doc_delete 处理;
 * - synonym_refresh: 同义词变更, payload 可选 domain;
 * - quick_query_refresh: 快捷提问变更;
 * - full_rebuild: 全量重建, payload 含全量 docs.
 */
@Data
public class KnowledgeSyncEvent {

    /** 事件类型: doc_upsert/doc_delete/doc_expire/synonym_refresh/quick_query_refresh/full_rebuild */
    @JsonProperty("event_type")
    private String eventType;

    /** 租户 ID (字符串, 与 Python 侧 tenant_id 对齐) */
    @JsonProperty("tenant_id")
    private String tenantId;

    /** 链路追踪 ID (透传, 供端到端关联) */
    @JsonProperty("trace_id")
    private String traceId;

    /** 事件载荷 (结构随 event_type 变化) */
    private Map<String, Object> payload;

    /**
     * 构造文档同步 payload 中的单文档项 (与 Python KbSyncDocItem 对齐).
     * <p>嵌套在 payload.docs 列表中, 字段同样通过 @JsonProperty 序列化为 snake_case.
     */
    @Data
    public static class DocItem {
        /** 文档 ID */
        @JsonProperty("doc_id")
        private String docId;
        /** 文档标题 */
        private String title;
        /** 文档内容 (从 file_path 读取的全量原文) */
        private String content;
        /** 业务域 */
        private String domain;
        /** 可见角色ID (空字符串=全员可见) */
        @JsonProperty("role_id")
        private String roleId;
        /** 门店范围 (空则全局) */
        @JsonProperty("store_id")
        private String storeId;
        /** 失效时间 YYYY-MM-DD */
        @JsonProperty("valid_until")
        private String validUntil;
        /** 版本号 */
        private Integer version;
        /** 额外元数据 */
        private Map<String, Object> metadata;
    }

    /** 便捷工厂: 构造 doc_upsert 事件 */
    public static KnowledgeSyncEvent docUpsert(String tenantId, String traceId, List<DocItem> docs) {
        KnowledgeSyncEvent event = new KnowledgeSyncEvent();
        event.setEventType("doc_upsert");
        event.setTenantId(tenantId);
        event.setTraceId(traceId);
        event.setPayload(Map.of("docs", docs));
        return event;
    }

    /** 便捷工厂: 构造 doc_delete 事件 */
    public static KnowledgeSyncEvent docDelete(String tenantId, String traceId, List<String> docIds) {
        KnowledgeSyncEvent event = new KnowledgeSyncEvent();
        event.setEventType("doc_delete");
        event.setTenantId(tenantId);
        event.setTraceId(traceId);
        event.setPayload(Map.of("doc_ids", docIds));
        return event;
    }

    /** 便捷工厂: 构造 synonym_refresh 事件 */
    public static KnowledgeSyncEvent synonymRefresh(String tenantId, String traceId) {
        KnowledgeSyncEvent event = new KnowledgeSyncEvent();
        event.setEventType("synonym_refresh");
        event.setTenantId(tenantId);
        event.setTraceId(traceId);
        event.setPayload(Map.of());
        return event;
    }

    /** 便捷工厂: 构造 quick_query_refresh 事件 */
    public static KnowledgeSyncEvent quickQueryRefresh(String tenantId, String traceId) {
        KnowledgeSyncEvent event = new KnowledgeSyncEvent();
        event.setEventType("quick_query_refresh");
        event.setTenantId(tenantId);
        event.setTraceId(traceId);
        event.setPayload(Map.of());
        return event;
    }

    /** 便捷工厂: 构造 full_rebuild 事件 */
    public static KnowledgeSyncEvent fullRebuild(String tenantId, String traceId, List<DocItem> docs) {
        KnowledgeSyncEvent event = new KnowledgeSyncEvent();
        event.setEventType("full_rebuild");
        event.setTenantId(tenantId);
        event.setTraceId(traceId);
        event.setPayload(Map.of("docs", docs));
        return event;
    }
}
