package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能对话会话实体, 对应数据库 chat_session 表.
 * <p>多租户 + 门店隔离(tenant_id 由 TenantInterceptor 自动注入; store_id 由 StoreLineHandler 自动注入, chat_session 已加入 store.tables 白名单).
 * <p>业务约束: Java MySQL 为权威数据源 SSOT, 持久化会话元信息(标题/消息计数/预览/审计); Python Redis 仅作 LLM 运行态缓存(cache-aside, 未命中回调 Java /api/v1/chat/internal/sessions/{sid}/messages 回源).
 * <p>唯一约束: UNIQUE(session_id), 会话业务标识全局唯一(跨租户, 前缀 sess_ 与 Python memory key 对齐).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话唯一业务标识(UNIQUE 全局, 前缀 sess_ + UUID, 如 sess_2x8fKz9m); 与 Python Redis memory key 完全对齐(key: chat:history:{sessionId}). */
    private String sessionId;

    /** 会话标题(前端会话列表展示); 创建时默认 "新对话", 用户可手动重命名; 也可由 Agent 根据首条消息自动摘要生成(保留扩展). */
    private String title;

    /** 创建者用户 id(关联 sys_user.id); 会话列表 Service 层手动追加 user_id = 当前用户过滤, 用户只能看自己创建的会话(管理员除外). */
    private Long userId;

    /** 消息总条数(冗余计数字段, 避免每次列表 COUNT(*)); 每条 chat_message INSERT 成功后同步原子 UPDATE message_count = message_count + 1. */
    private Integer messageCount;

    /** 最后一条消息预览文本(纯文本截断 200 字符); 会话列表右侧预览展示用, 新消息到达时同步更新此字段(assistant 消息取 content). */
    private String lastMessagePreview;

    private Long tenantId;

    private Long storeId;

    /** 创建人(管理员,自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 更新人(自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 创建时间(自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间(自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        private Integer deleted = 0;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
