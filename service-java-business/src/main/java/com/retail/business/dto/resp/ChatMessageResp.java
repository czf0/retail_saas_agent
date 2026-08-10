package com.retail.business.dto.resp;

import lombok.Data;

/**
 * AI 智能对话单条消息列表项响应(前端消息流 MessageList 渲染 + Python 回源上下文投影);每条消息 = user 输入 or assistant 回复,按时间升序流式追加.
 * <p>注意:id 从 Long 主键转 String(前端 Vue :key + renderCache 依赖字符串 key;Python cache-aside 仅读 role+content).
 */
@Data
public class ChatMessageResp {

    /** 消息 ID(实体 Long 主键转字符串;前端 :key + renderCache Map key;session 内唯一). */
    private String id;

    /** 消息角色:user=用户提问输入 / assistant=Agent 回复;对话按 (createdAt, role) 排序渲染. */
    private String role;

    /** 消息正文(user=纯文本用户输入;assistant=Markdown 格式回复,前端统一 markdown-it 渲染). */
    private String content;

    /** 意图标签(仅 assistant 填;'pending_approval' 标记 HITL 审批请求→前端渲染"等待审批"卡片;普通回复为空串). */
    private String intent;

    /** Token 消耗数(仅 assistant 填;input+output tokens 合计;前端右下角"tokens"标签展示,计费用). */
    private Integer tokensUsed;
}
