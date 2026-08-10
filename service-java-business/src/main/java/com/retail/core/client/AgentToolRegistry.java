package com.retail.core.client;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.retail.business.entity.AgentToolDefinition;
import com.retail.business.enums.CategoryStatus;
import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.CouponType;
import com.retail.business.enums.KbDocStatus;
import com.retail.business.enums.KbDomain;
import com.retail.business.enums.KbSourceType;
import com.retail.business.enums.MemberLevel;
import com.retail.business.enums.MovementType;
import com.retail.business.enums.OrderChannel;
import com.retail.business.enums.OrderStatus;
import com.retail.business.enums.OrderType;
import com.retail.business.enums.PayType;
import com.retail.business.enums.PointsBizType;
import com.retail.business.enums.PointsChangeType;
import com.retail.business.enums.ProductStatus;
import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.PromotionType;
import com.retail.business.enums.RefundStatus;
import com.retail.business.enums.RefundType;
import com.retail.business.enums.ReviewStatus;
import com.retail.business.enums.SkuStatus;
import com.retail.business.enums.StockBizType;
import com.retail.business.enums.TargetType;
import com.retail.business.enums.ValidType;
import com.retail.business.mapper.AgentToolDefinitionMapper;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.dto.agent.ToolMeta;
import com.retail.core.enums.EnumUtil;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具注册中心 (启动扫描 @AgentToolService → @AgentTool 方法 → 内存注册表).
 * <p>
 * 核心职责:
 * <ol>
 *   <li>启动时扫描所有 {@link AgentToolService} Bean, 遍历 {@link AgentTool} 方法,
 *       反射方法参数类型生成 JSON Schema, 构建内存 Map&lt;toolName, ToolMeta&gt;;</li>
 *   <li>与 DB {@code agent_tool_definition} 表同步: 注解声明基础元数据 (name/description/permission/
 *       destructive/outputHint), DB 管理 enabled 运行时状态 (管理界面可修改);</li>
 *   <li>提供二级定位 {@link #get(String, String)} 供 invoke 接口反射调用;</li>
 *   <li>提供 {@link #listEnabled()} 供 /registry 接口返回全量工具定义;</li>
 *   <li>提供 {@link #listAllowed()} 供 /allowed 接口返回角色可用工具 (Sa-Token 校验 + Redis 缓存);</li>
 *   <li>权限变更时 {@link #clearAllowedCache(Long, Long)} 清除 Redis 缓存.</li>
 * </ol>
 * <p>
 * toolName 格式: {@code business:operation} (如 "stock:adjust"), 二级定位调用.
 * <p>
 * 权限推导: {@code requiredPermission} 默认为 {@code business:{business}:{operation}},
 * 与现有 Controller 的 {@code @SaCheckPermission} 值对齐, 复用 SaToken RBAC 体系.
 * <p>
 * Redis 缓存设计:
 * <ul>
 *   <li>{@code tool:allowed:{tenantId}:{roleId}} → 角色可用工具列表 (权限变更时删除);</li>
 *   <li>缓存 key 按 tenantId+roleId 维度 (非 userId), 同角色用户共享缓存, 减少冗余.</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentToolRegistry {

    /** 工具注册表: key = "business:operation" (如 "stock:adjust") */
    private final Map<String, ToolMeta> tools = new ConcurrentHashMap<>();

    /** Redis 缓存 key 前缀 */
    private static final String CACHE_KEY_ALLOWED_PREFIX = "tool:allowed:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * D-1: Integer 枚举字段名 → 枚举码说明映射(key 统一小写,DTO 中该字段存的是枚举 Integer code).
     * <p>
     * 在生成 JSON Schema 时,对应字段如果是 int/Integer/long/Long,会在 description 末尾追加
     * "枚举合法值: code1=desc1|code2=desc2|...",让 LLM 在不读 tool description 的前提下
     * 也能从 schema 中直接看到合法整数码,降低取值错误.
     * <p>
     * 新增 DTO 枚举字段时在此补充对应映射即可.如果 DTO 的字段名和映射 key 不一致,
     * 可以用 FIELD_NAME_ALIAS 先做一次别名映射.
     */
    private static final Map<String, String> FIELD_ENUM_HINTS = new java.util.LinkedHashMap<>();
    static {
        // ===== 库存模块 =====
        FIELD_ENUM_HINTS.put("biztype", EnumUtil.codeDescList(StockBizType.class));
        FIELD_ENUM_HINTS.put("movementtype", EnumUtil.codeDescList(MovementType.class));

        // ===== 商品模块 =====
        FIELD_ENUM_HINTS.put("status", combine(
                EnumUtil.codeDescList(ProductStatus.class),
                EnumUtil.codeDescList(SkuStatus.class),
                EnumUtil.codeDescList(CategoryStatus.class),
                EnumUtil.codeDescList(KbDocStatus.class)));

        // ===== 评价模块 =====
        FIELD_ENUM_HINTS.put("reviewstatus", EnumUtil.codeDescList(ReviewStatus.class));

        // ===== 促销/优惠券模块 =====
        FIELD_ENUM_HINTS.put("type", combine(
                EnumUtil.codeDescList(PromotionType.class),
                EnumUtil.codeDescList(CouponType.class),
                EnumUtil.codeDescList(RefundType.class),
                EnumUtil.codeDescList(OrderType.class),
                EnumUtil.codeDescList(TargetType.class),
                EnumUtil.codeDescList(ValidType.class)));
        FIELD_ENUM_HINTS.put("promotionstatus", EnumUtil.codeDescList(PromotionStatus.class));
        FIELD_ENUM_HINTS.put("couponstatus", EnumUtil.codeDescList(CouponStatus.class));
        FIELD_ENUM_HINTS.put("promotype", EnumUtil.codeDescList(PromotionType.class));
        FIELD_ENUM_HINTS.put("coupontype", EnumUtil.codeDescList(CouponType.class));

        // ===== 订单模块 =====
        FIELD_ENUM_HINTS.put("channel", EnumUtil.codeDescList(OrderChannel.class));
        FIELD_ENUM_HINTS.put("orderstatus", EnumUtil.codeDescList(OrderStatus.class));
        FIELD_ENUM_HINTS.put("orderchannel", EnumUtil.codeDescList(OrderChannel.class));
        FIELD_ENUM_HINTS.put("ordertype", EnumUtil.codeDescList(OrderType.class));
        FIELD_ENUM_HINTS.put("paytype", EnumUtil.codeDescList(PayType.class));

        // ===== 退款模块 =====
        FIELD_ENUM_HINTS.put("refundstatus", EnumUtil.codeDescList(RefundStatus.class));
        FIELD_ENUM_HINTS.put("refundtype", EnumUtil.codeDescList(RefundType.class));

        // ===== 会员/积分模块 =====
        FIELD_ENUM_HINTS.put("level", EnumUtil.codeDescList(MemberLevel.class));
        FIELD_ENUM_HINTS.put("memberlevel", EnumUtil.codeDescList(MemberLevel.class));
        FIELD_ENUM_HINTS.put("pointschangetype", EnumUtil.codeDescList(PointsChangeType.class));
        FIELD_ENUM_HINTS.put("pointsbiztype", EnumUtil.codeDescList(PointsBizType.class));

        // ===== 知识库模块 =====
        FIELD_ENUM_HINTS.put("kbsourcetype", EnumUtil.codeDescList(KbSourceType.class));
        FIELD_ENUM_HINTS.put("kbcategory", EnumUtil.codeDescList(com.retail.business.enums.MemoryCategory.class));
        FIELD_ENUM_HINTS.put("kbdomain", EnumUtil.codeDescList(KbDomain.class));
        FIELD_ENUM_HINTS.put("kbdocstatus", EnumUtil.codeDescList(KbDocStatus.class));

        // ===== 目标类型 =====
        FIELD_ENUM_HINTS.put("targettype", EnumUtil.codeDescList(TargetType.class));
    }

    /** 去重拼接多个枚举码列表(不同枚举类 code 一致时重复出现保留多份 description 以便 LLM 区分场景) */
    private static String combine(String... lists) {
        StringBuilder sb = new StringBuilder();
        for (String s : lists) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(s);
        }
        return sb.toString();
    }

    private final ApplicationContext applicationContext;
    private final AgentToolDefinitionMapper toolDefinitionMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public AgentToolRegistry(ApplicationContext applicationContext,
                             AgentToolDefinitionMapper toolDefinitionMapper,
                             ObjectMapper objectMapper,
                             StringRedisTemplate redisTemplate) {
        this.applicationContext = applicationContext;
        this.toolDefinitionMapper = toolDefinitionMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 启动扫描: 遍历 @AgentToolService Bean → @AgentTool 方法 → 注册到内存 Map + 同步 DB.
     * <p>
     * 扫描流程:
     * <ol>
     *   <li>获取所有 @AgentToolService 注解的 Bean;</li>
     *   <li>遍历 Bean 的方法, 查找 @AgentTool 注解;</li>
     *   <li>反射方法参数类型生成 JSON Schema;</li>
     *   <li>推导 requiredPermission (默认 business:{business}:{operation});</li>
     *   <li>构建 ToolMeta, 注册到内存 Map;</li>
     *   <li>与 DB agent_tool_definition 表同步 (insert/update).</li>
     * </ol>
     */
    @PostConstruct
    public void scan() {
        Map<String, Object> serviceBeans = applicationContext.getBeansWithAnnotation(AgentToolService.class);
        log.info("AgentToolRegistry 扫描开始, 发现 {} 个 @AgentToolService Bean", serviceBeans.size());

        for (Map.Entry<String, Object> entry : serviceBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> beanClass = bean.getClass();
            AgentToolService serviceAnno = beanClass.getAnnotation(AgentToolService.class);
            if (serviceAnno == null) {
                continue;
            }
            String business = serviceAnno.business();

            for (Method method : beanClass.getDeclaredMethods()) {
                AgentTool toolAnno = method.getAnnotation(AgentTool.class);
                if (toolAnno == null) {
                    continue;
                }
                registerTool(business, bean, method, toolAnno);
            }
        }

        log.info("AgentToolRegistry 扫描完成, 共注册 {} 个工具: {}",
                tools.size(), tools.keySet());

        // 同步 DB (enabled 状态从 DB 回读)
        syncWithDb();
    }

    /**
     * 注册单个工具到内存 Map.
     * <p>
     * 构建 ToolMeta, 推导 requiredPermission, 生成 inputSchema.
     */
    private void registerTool(String business, Object serviceBean, Method method, AgentTool anno) {
        String toolName = business + ":" + anno.operation();

        // 推导 requiredPermission:
        // "<<derive>>" (默认) → 推导为 business:{business}:{operation} (对齐 @SaCheckPermission)
        // "" (显式空串) → 无权限要求 (如 StatsController 无 @SaCheckPermission)
        // 其他值 → 使用显式指定的权限标识
        String permission = anno.requiredPermission();
        if ("<<derive>>".equals(permission)) {
            permission = "business:" + business + ":" + anno.operation();
        }

        // 反射方法参数类型 (第一个参数为工具入参)
        Class<?> inputType = null;
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
            inputType = paramTypes[0];
        }

        // 生成 JSON Schema (供 Python 构建 Pydantic args_schema)
        String inputSchema = inputType != null ? generateInputSchema(inputType) : "{}";

        ToolMeta meta = new ToolMeta();
        meta.setToolName(toolName);
        meta.setBusiness(business);
        meta.setOperation(anno.operation());
        meta.setDescription(anno.description());
        meta.setRequiredPermission(permission);
        meta.setDestructive(anno.destructive());
        meta.setOutputHint(anno.outputHint());
        meta.setInputSchema(inputSchema);
        meta.setServiceBean(serviceBean);
        meta.setMethod(method);
        meta.setInputType(inputType);
        meta.setEnabled(true); // 默认启用, syncWithDb 从 DB 回读实际状态

        tools.put(toolName, meta);
        log.info("注册工具: {} (business={}, operation={}, destructive={}, permission={})",
                toolName, business, anno.operation(), anno.destructive(), permission);
    }

    /**
     * 生成方法参数类型的 JSON Schema (供 Python 构建 Pydantic args_schema).
     * <p>
     * 使用 Jackson BeanDescription 反射属性, 映射 Java 类型到 JSON Schema 类型:
     * String→string, Integer/Long→integer, Double/BigDecimal→number, Boolean→boolean,
     * LocalDateTime/LocalDate→string(date-time).
     * <p>
     * 检查 @NotNull / @NotBlank / @NotEmpty 注解标记 required 字段.
     */
    private String generateInputSchema(Class<?> paramType) {
        try {
            JavaType javaType = objectMapper.constructType(paramType);
            BeanDescription desc = objectMapper.getSerializationConfig().introspect(javaType);

            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");

            ObjectNode properties = objectMapper.createObjectNode();
            ArrayNode required = objectMapper.createArrayNode();

            desc.findProperties().forEach(prop -> {
                ObjectNode propSchema = objectMapper.createObjectNode();
                Class<?> rawType = prop.getRawPrimaryType();

                if (rawType == String.class) {
                    propSchema.put("type", "string");
                } else if (rawType == Integer.class || rawType == int.class
                        || rawType == Long.class || rawType == long.class) {
                    propSchema.put("type", "integer");
                } else if (rawType == Double.class || rawType == double.class
                        || rawType == java.math.BigDecimal.class || rawType == Float.class
                        || rawType == float.class) {
                    propSchema.put("type", "number");
                } else if (rawType == Boolean.class || rawType == boolean.class) {
                    propSchema.put("type", "boolean");
                } else if (rawType == LocalDateTime.class || rawType == LocalDate.class) {
                    propSchema.put("type", "string");
                    propSchema.put("format", "date-time");
                } else if (rawType.isEnum()) {
                    propSchema.put("type", "string");
                    ArrayNode enumValues = objectMapper.createArrayNode();
                    for (Object enumConstant : rawType.getEnumConstants()) {
                        enumValues.add(enumConstant.toString());
                    }
                    propSchema.set("enum", enumValues);
                } else if (java.util.Collection.class.isAssignableFrom(rawType)) {
                    propSchema.put("type", "array");
                    propSchema.putObject("items").put("type", "string");
                } else {
                    propSchema.put("type", "object");
                }

                // ===== D-1 字段描述 + 枚举码提示拼接 =====
                // 1) 基础描述:字段名(后续可扩展读取 @Schema/@JsonPropertyDescription 注解)
                StringBuilder descSb = new StringBuilder(prop.getName());
                // 2) Integer/Long 类型字段,如果在 FIELD_ENUM_HINTS 中登记了枚举,追加
                //    "枚举合法值: code1=desc1|code2=desc2|...",LLM 直接能看到合法整数码
                if (rawType == Integer.class || rawType == int.class
                        || rawType == Long.class || rawType == long.class) {
                    String hint = FIELD_ENUM_HINTS.get(prop.getName().toLowerCase());
                    if (hint != null && !hint.isEmpty()) {
                        descSb.append("（枚举合法值: ").append(hint).append("）");
                    }
                }
                propSchema.put("description", descSb.toString());

                properties.set(prop.getName(), propSchema);

                // 检查 @NotNull / @NotBlank / @NotEmpty 标记 required
                if (hasRequiredAnnotation(prop)) {
                    required.add(prop.getName());
                }
            });

            schema.set("properties", properties);
            if (required.size() > 0) {
                schema.set("required", required);
            }

            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("生成 JSON Schema 失败 paramType={}: {}", paramType.getSimpleName(), e.getMessage());
            return "{}";
        }
    }

    /** 检查字段是否有 @NotNull / @NotBlank / @NotEmpty 注解 (标记为 required) */
    private boolean hasRequiredAnnotation(com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition prop) {
        // 检查字段上的注解 (Jackson AnnotatedField → Java Field)
        // 使用注解类名字符串匹配, 避免硬依赖 validation-api (项目可能未引入)
        try {
            if (prop.getField() != null) {
                java.lang.reflect.Field field = prop.getField().getAnnotated();
                for (java.lang.annotation.Annotation anno : field.getAnnotations()) {
                    String name = anno.annotationType().getSimpleName();
                    if ("NotNull".equals(name) || "NotBlank".equals(name) || "NotEmpty".equals(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 反射失败时忽略, 不标记 required
        }
        return false;
    }

    /**
     * 与 DB agent_tool_definition 表同步.
     * <p>
     * 注解为唯一数据源 (声明基础元数据), DB 管理 enabled 运行时状态:
     * <ul>
     *   <li>注解中存在但 DB 缺失 → INSERT (首次注册);</li>
     *   <li>DB 中存在但注解缺失 → 标记 inactive (工具已下线, enabled=0);</li>
     *   <li>注解元数据变更 → UPDATE DB (description/permission/destructive/outputHint);</li>
     *   <li>DB 的 enabled 字段为运行时状态, 不被注解覆盖 (回读到内存 ToolMeta).</li>
     * </ul>
     */
    private void syncWithDb() {
        for (ToolMeta meta : tools.values()) {
            try {
                // 使用 @InterceptorIgnore 方法查询 (启动时无租户上下文, 工具定义为全局数据)
                AgentToolDefinition existing = toolDefinitionMapper.selectByToolNameIgnoreTenant(meta.getToolName());

                if (existing == null) {
                    // 首次注册: INSERT
                    AgentToolDefinition def = new AgentToolDefinition();
                    def.setToolName(meta.getToolName());
                    def.setDescription(meta.getDescription());
                    def.setInputSchema(meta.getInputSchema());
                    def.setRequiredPermission(meta.getRequiredPermission());
                    def.setAnnotations(buildAnnotationsJson(meta));
                    def.setToolGroup(meta.getBusiness());
                    def.setEnabled(1);
                    def.setVersion("1.0");
                    toolDefinitionMapper.insert(def);
                    log.info("工具 {} 首次注册到 DB", meta.getToolName());
                } else {
                    // 已存在: UPDATE 注解元数据, 保留 DB enabled 状态
                    existing.setDescription(meta.getDescription());
                    existing.setInputSchema(meta.getInputSchema());
                    existing.setRequiredPermission(meta.getRequiredPermission());
                    existing.setAnnotations(buildAnnotationsJson(meta));
                    existing.setToolGroup(meta.getBusiness());
                    toolDefinitionMapper.updateById(existing);

                    // 回读 DB enabled 状态到内存
                    meta.setEnabled(existing.getEnabled() != null && existing.getEnabled() == 1);
                    log.info("工具 {} 同步 DB (enabled={})", meta.getToolName(), meta.isEnabled());
                }
            } catch (Exception e) {
                log.warn("工具 {} 同步 DB 失败: {}", meta.getToolName(), e.getMessage());
            }
        }
        // ================ 孤儿工具清理:DB 中存在但内存中缺失的标记 enabled=0 ================
        // 代码中 @AgentTool 注解被删除/重命名后, 对应 DB 行不再被遍历, 需显式置为下线
        // 避免管理界面上遗留已下线工具仍显示为启用, 同时 /registry 接口虽只返回内存注册的工具, 但管理后台直接查表时会混淆
        try {
            Set<String> registered = new HashSet<>(tools.keySet());
            // Mapper 类级 @InterceptorIgnore(tenantLine = "true"), 此处查询全表不受租户拦截
            List<AgentToolDefinition> allInDb = toolDefinitionMapper.selectList(
                    new LambdaQueryWrapper<AgentToolDefinition>()
                            .eq(AgentToolDefinition::getEnabled, 1));
            for (AgentToolDefinition def : allInDb) {
                if (!registered.contains(def.getToolName())) {
                    try {
                        def.setEnabled(0);
                        def.setVersion("inactive");
                        toolDefinitionMapper.updateById(def);
                        log.info("工具 {} 在代码中已下线，DB 标记 enabled=0", def.getToolName());
                    } catch (Exception e) {
                        log.warn("标记下线工具 {} 失败: {}", def.getToolName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("孤儿工具下线同步失败: {}", e.getMessage());
        }
    }

    /** 构建 annotations JSON 字符串 (含 destructive/outputHint) */
    private String buildAnnotationsJson(ToolMeta meta) {
        try {
            ObjectNode annotations = objectMapper.createObjectNode();
            annotations.put("destructive", meta.isDestructive());
            annotations.put("readOnly", !meta.isDestructive());
            if (meta.getOutputHint() != null && !meta.getOutputHint().isEmpty()) {
                annotations.put("outputHint", meta.getOutputHint());
            }
            return objectMapper.writeValueAsString(annotations);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 二级定位: business + operation → ToolMeta.
     * <p>供 /invoke 接口反射调用.
     *
     * @param business  业务域 (如 "stock")
     * @param operation 操作标识 (如 "adjust")
     * @return ToolMeta, 未找到返回 null
     */
    public ToolMeta get(String business, String operation) {
        if (business == null || operation == null) {
            return null;
        }
        return tools.get(business + ":" + operation);
    }

    /**
     * 获取所有已注册工具 (含禁用状态, 供 /registry 接口).
     * <p>返回内存注册表全量工具, enabled 状态从 DB 回读.
     */
    public List<ToolMeta> listAll() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取所有 enabled 工具 (供 /registry 接口, enabledOnly=true).
     */
    public List<ToolMeta> listEnabled() {
        List<ToolMeta> result = new ArrayList<>();
        for (ToolMeta meta : tools.values()) {
            if (meta.isEnabled()) {
                result.add(meta);
            }
        }
        return result;
    }

    /**
     * 查询当前登录用户可用的工具列表 (供 /allowed 接口, Redis 缓存).
     * <p>
     * 缓存 key: {@code tool:allowed:{tenantId}:{roleId}} (按角色维度缓存, 同角色共享).
     * <p>
     * 权限校验:
     * <ul>
     *   <li>权限标识为空 → 直接放行 (依赖多租户隔离);</li>
     *   <li>权限标识非空 → StpUtil.hasPermission 校验;</li>
     *   <li>未登录 → 返回空列表 (保守拒绝).</li>
     * </ul>
     *
     * @return 当前用户可用的工具元数据列表
     */
    public List<ToolMeta> listAllowed() {
        LoginUser lu = LoginUserHolder.get();
        if (lu == null || lu.getUserId() == null) {
            log.warn("查询可用工具列表 userId=null（未登录，返回空列表触发 Python 降级）");
            return new ArrayList<>();
        }

        // 构建缓存 key: tenantId:roleId (按角色维度缓存, 同角色共享)
        Long tenantId = lu.getTenantId();
        String roleKey = lu.getRoleKeys() != null && !lu.getRoleKeys().isEmpty()
                ? lu.getRoleKeys().get(0) : "default";
        String cacheKey = CACHE_KEY_ALLOWED_PREFIX + tenantId + ":" + roleKey;

        // 查 Redis 缓存
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ToolMeta.class));
            }
        } catch (Exception e) {
            log.warn("读取 allowed 缓存失败 key={}: {}", cacheKey, e.getMessage());
        }

        // 缓存未命中: 遍历工具, Sa-Token 校验权限
        List<ToolMeta> allowed = new ArrayList<>();
        for (ToolMeta meta : listEnabled()) {
            String permission = meta.getRequiredPermission();
            boolean hasAccess;

            if (permission == null || permission.isEmpty()) {
                hasAccess = true;
            } else {
                try {
                    hasAccess = StpUtil.hasPermission(permission);
                } catch (Exception e) {
                    log.warn("Sa-Token 校验工具权限异常 tool={} permission={}: {}",
                            meta.getToolName(), permission, e.getMessage());
                    hasAccess = false;
                }
            }

            if (hasAccess) {
                allowed.add(meta);
            }
        }

        log.info("查询可用工具列表 userId={} tenant={} role={} 可用={}/总={}",
                lu.getUserId(), tenantId, roleKey, allowed.size(), tools.size());

        // 写 Redis 缓存
        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(allowed), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入 allowed 缓存失败 key={}: {}", cacheKey, e.getMessage());
        }

        return allowed;
    }

    /**
     * 清除角色可用工具缓存 (权限变更时调用).
     * <p>角色菜单变更 (RBAC 管理界面) 时, 删除对应 tenantId+roleId 的缓存.
     *
     * @param tenantId 租户 ID (null=平台级)
     * @param roleKey  角色 key
     */
    public void clearAllowedCache(Long tenantId, String roleKey) {
        String cacheKey = CACHE_KEY_ALLOWED_PREFIX + tenantId + ":" + roleKey;
        redisTemplate.delete(cacheKey);
        log.info("清除工具权限缓存 key={}", cacheKey);
    }
}
