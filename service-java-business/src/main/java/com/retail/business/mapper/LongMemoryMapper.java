package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.LongMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长期记忆 Mapper.
 * <p>
 * 多租户隔离由 MyBatis-Plus 拦截器自动注入 tenant_id 过滤;
 * 逻辑删除由全局配置自动追加 deleted=0 条件.
 * 用户作用域 (user_id) 由 Service 层手动过滤 (拦截器只管租户).
 */
@Mapper
public interface LongMemoryMapper extends BaseMapper<LongMemory> {
}