package com.retail.business.convert;

import com.retail.business.dto.resp.ChatMessageResp;
import com.retail.business.entity.ChatMessage;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 智能对话消息转换器.
 * <p>主转换 {@code ChatMessage→ChatMessageResp} 由 {@link RespConvert} 提供(toResp/toRespList).
 * <p>差异字段(与实体的有意义差异):
 * <ul>
 *   <li>id:实体 Long 主键 → Resp String,供前端 :key 与 renderCache Map key 使用;</li>
 *   <li>剥离 sessionId / createdAt(前端不渲染,Python 不用)与 toolsJson(仅审计不展示前端),
 *       由全局 {@code unmappedSourcePolicy=IGNORE} 静默跳过;</li>
 *   <li>审计字段(tenantId / storeId / createBy / updateBy / deleted 等)由全局配置忽略.</li>
 * </ul>
 * <p>仅 id 一处需类型适配,内联 {@code @Mapping(expression=...)} 处理,无需共享类型适配基类.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ChatMessageConvert extends RespConvert<ChatMessage, ChatMessageResp> {

    /**
     * 覆盖基类 toResp:仅 id 需 Long→String 适配(null 安全),其余同名字段自动映射.
     * <p>批量 {@link #toRespList} 由 MapStruct 生成循环,内部调用本被覆盖的 toResp,保留 id 适配.
     */
    @Override
    @Mapping(target = "id",
            expression = "java(source.getId() != null ? String.valueOf(source.getId()) : null)")
    ChatMessageResp toResp(ChatMessage source);
}
