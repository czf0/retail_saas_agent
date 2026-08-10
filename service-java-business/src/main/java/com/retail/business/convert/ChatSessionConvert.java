package com.retail.business.convert;

import com.retail.business.dto.resp.ChatSessionResp;
import com.retail.business.entity.ChatSession;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 智能对话会话转换器.
 * <p>主转换 {@code ChatSession→ChatSessionResp} 由 {@link RespConvert} 提供(toResp/toRespList),
 * 同名字段自动映射,无差异字段需声明.
 * <p>Resp 为前端侧边栏投影:仅保留 sessionId / title / lastMessagePreview / updatedAt;
 * 剥离的 userId / storeId / createdAt / messageCount / 审计字段均为「源有,目标无」,
 * 由全局 {@code unmappedSourcePolicy=IGNORE} 静默跳过.
 * <p>updatedAt 保持 LocalDateTime(Jackson 输出 ISO 字符串,与项目其余 Resp 一致).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ChatSessionConvert extends RespConvert<ChatSession, ChatSessionResp> {
}
