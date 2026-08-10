package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能对话会话 Mapper.
 * <p>
 * 多租户 / 门店隔离由 MyBatis-Plus 拦截器自动注入 tenant_id / store_id 过滤; 
 * 逻辑删除由全局配置自动追加 deleted=0 条件; 
 * 用户隔离由 Service 层手动追加 user_id 过滤(参照 SysUser/SysRole/SysStore 模式).
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
