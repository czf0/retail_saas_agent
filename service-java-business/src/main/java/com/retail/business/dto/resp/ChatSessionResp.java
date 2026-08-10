package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 智能对话会话列表行项响应(前端侧边栏"历史会话" 20/页;按 updatedAt 倒序);展示会话 ID/标题/最后消息预览/最近活跃时间.
 * <p>点击行 → 前端切换 sessionId 并拉取完整消息历史 ChatMessageResp 列表;新建会话时前端自动生成 sessionId 并 POST.
 */
@Data
public class ChatSessionResp {

    /** 会话唯一标识(前缀 sess_,前端列表 key / 切换 / 选中态) */
    private String sessionId;

    /** 会话标题(显示 + 行内重命名) */
    private String title;

    /** 最后一条消息预览(侧边栏预览行) */
    private String lastMessagePreview;

    /** 最近活跃时间(侧边栏时间标签,Jackson 输出 ISO 字符串) */
    private LocalDateTime updatedAt;
}
