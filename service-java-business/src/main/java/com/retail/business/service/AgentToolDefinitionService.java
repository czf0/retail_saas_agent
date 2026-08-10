package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.resp.AgentToolDefinitionResp;
import com.retail.business.entity.AgentToolDefinition;

import java.util.List;

/**
 * Agent 工具定义注册表服务 (SSOT, 对齐 MCP tools/list).
 * <p>
 * 提供 registry 查询接口供 Python 拉取全量工具 schema (M6 一致性校验);
 * 管理端 CRUD (save/delete) 供后续工具管理界面使用.
 * <p>
 * 继承 {@link IService} 复用 MyBatis-Plus 通用 CRUD;
 * 逻辑删除由 {@link com.retail.core.service.BaseServiceImpl} 保障 deleteAt/deleteBy 填充.
 */
public interface AgentToolDefinitionService extends IService<AgentToolDefinition> {

    /**
     * 查询工具注册表 (对齐 MCP tools/list).
     * <p>
     * 返回全局工具 (tenant_id IS NULL) + 当前租户工具, 可按 enabled 过滤.
     * Python 启动时拉此接口校验本地声明一致性 (M6).
     *
     * @param enabledOnly true=仅返回启用的工具, false=返回全部 (含禁用)
     * @return 工具定义列表 (剥离审计字段)
     */
    List<AgentToolDefinitionResp> listRegistry(boolean enabledOnly);

    /**
     * 按工具名查询单个工具定义 (对齐 MCP tools/call 前的 schema 查询).
     * <p>
     * 匹配全局工具 + 当前租户工具, 未找到返回 null.
     *
     * @param toolName 工具名 (与 Python BaseTool.name 对齐)
     * @return 工具定义 (剥离审计字段), 未找到返回 null
     */
    AgentToolDefinitionResp getByName(String toolName);
}
