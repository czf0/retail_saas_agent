package com.retail.gateway;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.business.dto.resp.AgentToolDefinitionResp;
import com.retail.core.client.AgentToolRegistry;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.agent.ToolInvokeReq;
import com.retail.core.dto.agent.ToolInvokeResp;
import com.retail.core.dto.agent.ToolMeta;
import com.retail.core.dto.agent.ToolPermissionDTO;
import com.retail.core.enums.ErrCodeEnum;
import com.retail.core.exception.BizException;
import com.retail.core.result.R;
import com.retail.core.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工具发现与统一调用接口(对齐 MCP tools/list + tools/call).
 * <p>路由前缀 /api/v1/agent/tools,与 AgentGatewayController 同属 Agent 网关模块.
 * agent_tool_definition 表位于多租户 ignore-tables,按 business + operation 业务键显式操作.
 * <p>权限校验:/registry 为 SaIgnore 公开路由(Python 启动拉取);/allowed 需登录态(Sa-Token);
 * /invoke 由 Python 携带 X-Internal-Secret 与 X-User-ID 头,GlobalReqInterceptor 建立临时登录态后做 RBAC 二次校验.
 * <p>三个端点:GET /registry(全量工具定义,对齐 MCP tools/list),
 * GET /allowed(当前用户可用工具,Redis 缓存 tenantId:roleId),
 * POST /invoke(统一调用,二级定位 business+operation → 反射调用 @AgentTool 方法).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/tools")
public class AgentToolDiscoveryController {

    /** 幂等缓存 key 前缀 + TTL */
    private static final String IDEMPOTENCY_KEY_PREFIX = "tool:invoke:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(1);

    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /** 构造注入: AgentToolRegistry 替代原 ToolPermissionService + AgentToolDefinitionService */
    public AgentToolDiscoveryController(AgentToolRegistry toolRegistry,
                                        ObjectMapper objectMapper,
                                        StringRedisTemplate redisTemplate) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 工具发现接口 ====================

    /**
     * 查询当前登录用户可用的工具列表.
     * <p>Python 侧调用此接口拉取角色可用工具白名单, 缓存到本地 (Redis/内存, TTL 5-10 min),
     * 供 tool_registry.execute 前置做 L1 软拒绝.
     * <p>权限校验由 Sa-Token 拦截器保障: 未登录访问会被 Sa-Token 拦截, 不进入此方法.
     * <p>Redis 缓存按 tenantId:roleId 维度 (同角色共享), 权限变更时清除.
     *
     * @return 当前用户可用的工具列表 (含工具名与权限标识)
     */
    @GetMapping("/allowed")
    public R<List<ToolPermissionDTO>> allowedTools() {
        List<ToolMeta> allowed = toolRegistry.listAllowed();
        List<ToolPermissionDTO> result = allowed.stream()
                .map(this::toPermissionDTO)
                .collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 查询工具注册表 (全量 schema, 对齐 MCP tools/list).
     * <p>Python 启动/RoleContextNode 拉取全量工具定义, 校验本地声明一致性 (M6):
     * name/permission 与 Python BaseTool 对齐, enabled=0 的工具 Python 侧禁用.
     * <p>返回 enabled=1 的工具, 含 JSON Schema / destructive / outputHint.
     *
     * @return 工具定义列表 (含 inputSchema / destructive / outputHint / requiredPermission)
     */
    @SaIgnore
    @GetMapping("/registry")
    public R<List<AgentToolDefinitionResp>> listRegistry() {
        List<ToolMeta> enabled = toolRegistry.listEnabled();
        List<AgentToolDefinitionResp> result = enabled.stream()
                .map(this::toDefinitionResp)
                .collect(Collectors.toList());
        return R.ok(result);
    }

    // ==================== 统一工具调用接口 ====================

    /**
     * 统一工具调用 (二级定位 business+operation → 反射调用 @AgentTool 方法).
     * <p>
     * Python 端 DynamicJavaToolLoader 构建的 LangChain 工具统一调此接口,
     * 传入 business + operation + args, Java 端反射调用对应的 @AgentTool 方法.
     * <p>
     * 认证: Python 携带 X-Internal-Secret + X-User-ID 头, GlobalReqInterceptor 建立临时登录态.
     * 幂等: Python 传 X-Idempotency-Key 头 (tool_call_id), Java 端 Redis 缓存兜底.
     * 链路追踪: Python 传 X-Trace-Id 头, Java 端写入 MDC, 审计日志贯穿.
     *
     * @param traceId        链路追踪 ID (Python 生成, 贯穿两端日志)
     * @param idempotencyKey 幂等键 (tool_call_id, 相同 key 返回缓存结果)
     * @param req            调用请求 (business + operation + args)
     * @return 工具执行结果 (原始业务对象, LLM 据 outputHint 组织输出)
     */
    @PostMapping("/invoke")
    public R<ToolInvokeResp> invoke(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ToolInvokeReq req) {

        // 设置 MDC (traceId 贯穿日志)
        if (traceId != null && !traceId.isBlank()) {
            MDC.put("traceId", traceId);
        }
        String toolName = req.getBusiness() + ":" + req.getOperation();
        MDC.put("toolName", toolName);

        try {
            // 1. 查 Redis 幂等缓存
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
                try {
                    String cached = redisTemplate.opsForValue().get(cacheKey);
                    if (cached != null) {
                        ToolInvokeResp resp = objectMapper.readValue(cached, ToolInvokeResp.class);
                        resp.setIdempotentHit(true);
                        log.info("工具调用命中幂等缓存 tool={} idempotencyKey={}", toolName, idempotencyKey);
                        return R.ok(resp);
                    }
                } catch (Exception e) {
                    log.warn("读取幂等缓存失败 key={}: {}", cacheKey, e.getMessage());
                }
            }

            // 2. 二级定位: 查找 ToolMeta
            ToolMeta meta = toolRegistry.get(req.getBusiness(), req.getOperation());
            if (meta == null) {
                log.warn("工具不存在 tool={}", toolName);
                return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_NOT_FOUND));
            }

            // 3. enabled 校验 (DB 运行时状态)
            if (!meta.isEnabled()) {
                log.warn("工具已禁用 tool={}", toolName);
                return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_DISABLED));
            }

            // 4. 权限二次校验 (复用 SaToken RBAC) — 日志记权限码, 对外返回通用提示
            String permission = meta.getRequiredPermission();
            if (permission != null && !permission.isEmpty()) {
                try {
                    if (!StpUtil.hasPermission(permission)) {
                        log.warn("权限不足 tool={} permission={} userId={}",
                                toolName, permission, LoginUserHolder.currentUserId());
                        return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_PERMISSION_DENIED));
                    }
                } catch (Exception e) {
                    log.warn("权限校验异常 tool={}: {}", toolName, e.getMessage());
                    return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_PERMISSION_DENIED));
                }
            }

            // 5. 参数反序列化 (JSON Map → 方法参数类型) — 日志记详情, 对外返回通用提示
            Object[] methodArgs;
            try {
                if (meta.getInputType() != null && req.getArgs() != null) {
                    Object converted = objectMapper.convertValue(req.getArgs(), meta.getInputType());
                    methodArgs = new Object[]{converted};
                } else {
                    methodArgs = new Object[0];
                }
            } catch (Exception e) {
                log.warn("参数反序列化失败 tool={} inputType={}: {}",
                        toolName, meta.getInputType(), e.getMessage());
                return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_PARAM_INVALID));
            }

            // 6. 反射调用 @AgentTool 方法 — BizException 透传 (msg 已面向用户), 其他异常返回通用提示
            long start = System.currentTimeMillis();
            Object result;
            try {
                result = meta.getMethod().invoke(meta.getServiceBean(), methodArgs);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                log.error("工具执行异常 tool={}: {}", toolName, cause.getMessage(), cause);
                // BizException (含 ParamException 等) 的 msg 已面向用户编写, 透传 code+msg
                if (cause instanceof BizException biz) {
                    return R.ok(ToolInvokeResp.fail(biz.getCode(), biz.getMsg()));
                }
                return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_EXEC_ERROR));
            } catch (Exception e) {
                log.error("反射调用异常 tool={}: {}", toolName, e.getMessage(), e);
                return R.ok(ToolInvokeResp.fail(ErrCodeEnum.TOOL_EXEC_ERROR));
            }
            long elapsed = System.currentTimeMillis() - start;

            // 7. 审计日志 (traceId 已在 MDC 中)
            log.info("工具调用完成 tool={} userId={} tenant={} args={} 耗时={}ms",
                    toolName, LoginUserHolder.currentUserId(), LoginUserHolder.currentTenantId(),
                    req.getArgs(), elapsed);

            // 8. 构建响应
            ToolInvokeResp resp = ToolInvokeResp.ok(result, elapsed);

            // 9. 写 Redis 幂等缓存 (TTL=1h)
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
                try {
                    redisTemplate.opsForValue().set(cacheKey,
                            objectMapper.writeValueAsString(resp), IDEMPOTENCY_TTL);
                } catch (Exception e) {
                    log.warn("写入幂等缓存失败 key={}: {}", cacheKey, e.getMessage());
                }
            }

            return R.ok(resp);
        } finally {
            // ThreadLocal 统一清理:MDC trace/tool(HTTP 线程池复用场景)与分页上下文
            // (工具反射调用里的 listPage 等会读 PageContextHolder.get(),不清理则线程复用时
            // 上一个请求的 page/pageSize 串到下一个,出现"明明没传分页但结果被分页"的脏读)
            MDC.remove("traceId");
            MDC.remove("toolName");
            PageContextHolder.clear();
        }
    }

    // ==================== DTO 转换 ====================

    /** ToolMeta → AgentToolDefinitionResp (供 /registry 接口) */
    private AgentToolDefinitionResp toDefinitionResp(ToolMeta meta) {
        AgentToolDefinitionResp resp = new AgentToolDefinitionResp();
        resp.setToolName(meta.getToolName());
        resp.setDescription(meta.getDescription());
        resp.setInputSchema(meta.getInputSchema());
        resp.setRequiredPermission(meta.getRequiredPermission());
        resp.setDestructive(meta.isDestructive());
        resp.setOutputHint(meta.getOutputHint());
        resp.setToolGroup(meta.getBusiness());
        resp.setEnabled(meta.isEnabled() ? 1 : 0);
        return resp;
    }

    /** ToolMeta → ToolPermissionDTO (供 /allowed 接口) */
    private ToolPermissionDTO toPermissionDTO(ToolMeta meta) {
        ToolPermissionDTO dto = new ToolPermissionDTO();
        dto.setToolName(meta.getToolName());
        dto.setPermission(meta.getRequiredPermission() != null ? meta.getRequiredPermission() : "");
        dto.setSensitiveFields(new ArrayList<>()); // P2 字段级脱敏, 当前为空
        return dto;
    }
}
