package com.retail.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

/**
 * SSE 流式分片 DTO(三端打通:与 Python StreamChunk 同构).
 * <p>
 * Java AgentHttpClient.streamChat 完整接收 Python 全部分片类型(含 tool_call / tool_result),
 * 但在透传前端时会执行过滤:
 * <ul>
 *   <li>tool_call / tool_result 分片不透传前端(仅累加到内部 usedTools 列表供持久化审计)</li>
 *   <li>done 分片的 meta.usedTools / used_tools 剥离后再透传(仅保留 intent / tokensUsed 供前端展示)</li>
 *   <li>token / meta / done / error 分片正常透传</li>
 * </ul>
 * 工具信息仅持久化到 chat_message.tools_json 供审计,不展示给用户.
 * <p>
 * 字段与 Python schema/agent_schema.py StreamChunk 对齐.
 * 使用 {@link JsonAlias} 接受 Python snake_case 反序列化(chunk_type → chunkType),
 * 序列化到前端时使用默认驼峰命名(chunkType).
 */
@Data
public class StreamChunkDTO {

    /** 分片类型:token / meta / tool_call / tool_result / done / error */
    @JsonAlias("chunk_type")
    private String chunkType;

    /** 分片文本内容 */
    private String content;

    /** 当前会话 id(done 分片携带,供前端同步 currentSessionId) */
    @JsonAlias("session_id")
    private String sessionId;

    /** 当前分片序号 */
    private Integer index;

    /** 附加元数据(intent / tokensUsed / ragHitCount / usedTools,Python 使用 snake_case 键名) */
    private Map<String, Object> meta;

    /** 兼容字段:chunkType==done 时为 true */
    private Boolean finished;
}
