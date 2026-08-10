package com.retail.business.convert;

import com.retail.business.dto.resp.AgentToolDefinitionResp;
import com.retail.business.entity.AgentToolDefinition;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * Agent 工具定义转换器.
 * <p>主转换 {@code AgentToolDefinition→AgentToolDefinitionResp} 由 {@link RespConvert} 提供
 * (toResp/toRespList),同名字段自动映射,无差异字段需声明.
 * <p>Resp 为 Python /registry 投影:仅保留 toolName / description / inputSchema /
 * requiredPermission / destructive / outputHint / toolGroup / enabled(对齐 MCP tools/list 消费).
 * <p>剥离的 outputSchema / annotations / version / createAt / updateAt / 审计字段均为「源有,目标无」,
 * 由全局 {@code unmappedSourcePolicy=IGNORE} 静默跳过.
 * <p>destructive / outputHint 为目标端独有槽位(源无对应),当前留空,
 * 与原手动转化行为一致;后续可按需从 annotations JSON 派生以驱动 HITL 中断与输出约束.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface AgentToolDefinitionConvert extends RespConvert<AgentToolDefinition, AgentToolDefinitionResp> {
}
