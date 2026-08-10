package com.retail.core.dto.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HITL 审批恢复请求 DTO (前端 → Java → Python /stream/resume).
 * <p>
 * 用户在前端审批弹窗选择批准/拒绝后, 前端调用 Java /api/v1/agent/stream/resume,
 * Java 透传给 Python /api/v1/agent/stream/resume, 恢复被 interrupt() 暂停的 graph.
 * <p>
 * 继承 {@link AgentChatDTO} 复用身份上下文透传 (fillContext / buildHeaders),
 * sessionId 从请求体传入 (与被中断的 graph thread_id 对齐).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentResumeDTO extends AgentChatDTO {

    /** 用户审批结果: true=批准执行, false=拒绝执行 */
    private Boolean approved;

    /** 拒绝原因 (approved=false 时由前端传入, 喂回 LLM 供其调整方案) */
    private String reason;
}
