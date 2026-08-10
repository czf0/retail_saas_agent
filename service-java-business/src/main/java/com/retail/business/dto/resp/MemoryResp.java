package com.retail.business.dto.resp;

import com.retail.business.enums.MemoryCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI Agent 长期记忆详情/列表项响应(会员画像标签 → Agent 注入上下文 + 前端"会员画像"页展示);每条记忆 = 从 N 条聊天对话中抽取的结构化偏好/约束/习惯/目标.
 * <p>统计口径: 按 member_id + category(memory_category 枚举) 查询; 置信度 confidence >= 0.7 才对外展示; Python 侧抽取完成写表后 Java 侧读.
 * <p>排除条件: deleted = 1 记忆; confidence < 0.5 丢弃不写表; tenant_id = 当前上下文租户.
 */
@Data
public class MemoryResp {

    private Long id;

    /** 记忆类型(细粒度):preference=偏好/constraint=约束/habit=习惯/goal=目标/event=事件;注入 Agent system prompt 前缀区分. */
    private String memoryType;

    /** 记忆大类枚举(粗粒度):见 MemoryCategoryEnum;1=商品偏好 2=价格敏感度 ... 前端按 category 分 Tab 展示. */
    private MemoryCategory category;

    /** 记忆正文(用户主观偏好/约束自然语言描述;Python LLM 抽取结果,最多 200 字符). */
    private String content;

    /** 置信度(抽取可信度;范围 0.00-1.00;>=0.7 展示给前端,<0.5 丢弃不写表). */
    private BigDecimal confidence;

    /** 重要性评分(1-5;Python 侧 importance scorer 打分;5=长期稳定特征如过敏,1=临时偏好). */
    private Integer importance;

    /** 累计访问次数(Agent 上下文注入命中 +1;Top-K 重排时 accessCount*importance + recency 加权分). */
    private Integer accessCount;

    /** 最近访问时间(recency 衰减函数用;久未访问记忆权重指数衰减,避免老记忆死占上下文). */
    private LocalDateTime lastAccessedAt;
}
