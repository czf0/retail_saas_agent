package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.convert.AgentToolDefinitionConvert;
import com.retail.business.dto.resp.AgentToolDefinitionResp;
import com.retail.business.entity.AgentToolDefinition;
import com.retail.business.mapper.AgentToolDefinitionMapper;
import com.retail.business.service.AgentToolDefinitionService;
import com.retail.core.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 工具定义注册表服务实现 (SSOT, 对齐 MCP tools/list).
 * <p>
 * 继承 {@link BaseServiceImpl} 确保逻辑删除时 deleteAt / deleteBy 被填充.
 * <p>
 * 多租户隔离: MyBatis-Plus 拦截器自动注入 tenant_id 过滤当前租户工具;
 * 全局工具 (tenant_id IS NULL) 需在查询时用 OR tenant_id IS NULL 补充,
 * 否则拦截器注入 tenant_id 后全局工具查不到.
 * <p>
 * 注意: 拦截器对 admin (TenantContext 为空) 会跳过过滤, 此时能查到全部租户工具;
 * 对租户用户, 拦截器注入 tenant_id, 需 OR tenant_id IS NULL 补充全局工具.
 */
@Service
public class AgentToolDefinitionServiceImpl
        extends BaseServiceImpl<AgentToolDefinitionMapper, AgentToolDefinition>
        implements AgentToolDefinitionService {

    /** 实体→Resp 转换器(MapStruct 生成,替代原手写 toResp) */
    private final AgentToolDefinitionConvert agentToolDefinitionConvert;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>agentToolDefinitionConvert 为 MapStruct 生成的 Spring Bean,
     * 替代原手写 toResp(仅映射 Python /registry 实际消费的 8 个字段).
     */
    public AgentToolDefinitionServiceImpl(AgentToolDefinitionConvert agentToolDefinitionConvert) {
        this.agentToolDefinitionConvert = agentToolDefinitionConvert;
    }

    /**
     * 查询工具注册表 (对齐 MCP tools/list).
     * <p>
     * 返回全局工具 (tenant_id IS NULL) + 当前租户工具, 可按 enabled 过滤.
     * 拦截器自动追加 tenant_id 条件, 此处用 OR tenant_id IS NULL 补充全局工具.
     */
    @Override
    public List<AgentToolDefinitionResp> listRegistry(boolean enabledOnly) {
        LambdaQueryWrapper<AgentToolDefinition> wrapper = new LambdaQueryWrapper<>();
        if (enabledOnly) {
            wrapper.eq(AgentToolDefinition::getEnabled, 1);
        }
        // 全局工具 (tenant_id IS NULL) 不被拦截器过滤: OR 条件补充
        // 注: 拦截器已追加 tenant_id=<当前租户>, 此 OR 使全局工具也被查出
        wrapper.or().isNull(AgentToolDefinition::getTenantId);
        wrapper.orderByAsc(AgentToolDefinition::getToolGroup, AgentToolDefinition::getToolName);
        return agentToolDefinitionConvert.toRespList(list(wrapper));
    }

    /**
     * 按工具名查询单个工具定义.
     * <p>
     * 匹配全局工具 + 当前租户工具, 未找到返回 null.
     */
    @Override
    public AgentToolDefinitionResp getByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<AgentToolDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentToolDefinition::getToolName, toolName);
        wrapper.or().isNull(AgentToolDefinition::getTenantId);
        wrapper.last("LIMIT 1");
        AgentToolDefinition entity = getOne(wrapper);
        return entity != null ? agentToolDefinitionConvert.toResp(entity) : null;
    }
}
