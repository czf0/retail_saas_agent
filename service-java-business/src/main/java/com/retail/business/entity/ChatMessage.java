package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能对话消息实体, 对应数据库 chat_message 表.
 * <p>多租户 + 门店隔离(tenant_id 由 TenantInterceptor 自动注入; store_id 由 StoreLineHandler 自动注入, chat_message 已加入 store.tables 白名单).
 * <p>业务约束: Java MySQL 为权威数据源 SSOT, 记录会话内每条消息(user / assistant); assistant 消息携带 intent / toolsJson / tokensUsed 元数据, 其中 toolsJson 仅审计存储不展示前端(Java AgentHttpClient.streamChat 过滤工具分片).
 * <p>唯一约束: UNIQUE(session_id, id), 每条消息归属一个会话, 按 id 自增排序(即时间顺序, 与 created_at 等价).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话业务标识(关联 chat_session.session_id); 消息列表按 session_id 查询 + ORDER BY id ASC(自增主键顺序即发送顺序, 避免 created_at 相同排序不稳定). */
    private String sessionId;

    /** 消息角色(枚举字符串: user 用户提问 / assistant AI 回答); system prompt 不入库(属于 LLM 运行态配置), 仅存 user + assistant 交互轮次. */
    private String role;

    /** 消息正文; user 提问为纯文本(可能含 @商品 / #订单号 标记); assistant 回答为 Markdown(前端渲染富文本). */
    private String content;

    /** 意图分类标签(assistant 回答附带, Python meta.intent 透传); 如 order_query / inventory_check / promotion_analysis; 用于后续对话聚类报表 + 快捷提问推荐训练. */
    private String intent;

    /** 工具调用详情 JSON(assistant 回答附带, Python meta.used_tools 原样透传); 审计存储仅后台管理员可见, 前端用户消息列表不展示; 格式: [{name,args,result}]. */
    private String toolsJson;

    /** Token 消耗总量(assistant 回答, prompt_tokens + completion_tokens 之和, Python meta.tokens_used 透传); 用于租户 dailyTokenLimit 限额抵扣 + 成本报表. */
    private Integer tokensUsed;

    private Long tenantId;

    private Long storeId;

    /** 创建人(自动填充) */
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
