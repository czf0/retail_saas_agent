package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能对话消息 Mapper.
 * <p>
 * 多租户 / 门店隔离由 MyBatis-Plus 拦截器自动注入 tenant_id / store_id 过滤; 
 * 逻辑删除由全局配置自动追加 deleted=0 条件.
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
