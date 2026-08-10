package com.retail.business.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.AgentToolDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent 工具定义注册表 Mapper.
 * <p>
 * 工具定义为全局数据 (tenant_id=NULL), 所有操作关闭租户拦截器,
 * 避免 AgentToolRegistry 启动同步 DB 时因无租户上下文导致 INSERT/UPDATE 失败.
 * <p>
 * 逻辑删除由全局配置自动追加 deleted=0 条件.
 * <p>
 * {@link #selectByToolNameIgnoreTenant} 保留方法级 @InterceptorIgnore 用于显式标注
 * (类级已全局关闭, 方法级注解为冗余保险, 不影响行为).
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AgentToolDefinitionMapper extends BaseMapper<AgentToolDefinition> {

    /**
     * 按工具名查询 (忽略租户拦截, 供 AgentToolRegistry 启动同步用).
     * <p>工具定义为全局数据 (tenant_id=NULL), 启动时无租户上下文, 需关闭拦截器.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM agent_tool_definition WHERE tool_name = #{toolName} AND deleted = 0 LIMIT 1")
    AgentToolDefinition selectByToolNameIgnoreTenant(@Param("toolName") String toolName);
}
