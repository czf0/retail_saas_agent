package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户配置实体, 对应数据库 tenant_config 表.
 * <p>隔离域: 已配置在多租户拦截器 ignore-tables 中, 拦截器不自动注入 tenant_id 条件; 本表本身即租户表(一条记录 = 一个租户配置), 所有查询需手动通过 tenant_id 业务键构建条件.
 * <p>业务约束: 租户级配额 + 规则配置 SSOT; 新增租户时 SysTenantServiceImpl.insertTenant 同步创建 1 条默认配置(dailyTokenLimit=50w, pointsRate=1, allowedTools=全量 java 内建工具); 平台管理员可修改任意租户配置.
 * <p>唯一约束: UNIQUE(tenant_id), 一个租户只能有 1 条配置记录(tenant_id 本身即业务主键, 与 id 1:1 映射).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName(value = "tenant_config", autoResultMap = true)
public class TenantConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 id(业务唯一键, UNIQUE 全局, 一个租户 1 条配置); 与 sys_tenant.id 对应, 新增租户时同步创建此条配置. */
    private Long tenantId;

    /** 租户名称冗余快照(从 sys_tenant.tenantName 读取写入); 配置列表展示用, 避免 JOIN sys_tenant 大表, 租户改名时同步刷新. */
    private String tenantName;

    /** 每日 Token 消耗上限(整数, 单位: 个 token, 默认 500,000); 0 点重置计数器, 达到限额后 Agent 工具软拒绝(提示 "今日额度已用完, 请联系平台扩容"), 次日自动恢复. */
    private Integer dailyTokenLimit;

    /** 积分兑换比率(整数, 消费 1 元 = N 积分, 默认 1); 订单完成时 member.points += FLOOR(payAmount * pointsRate * 会员等级倍率); 退款时反向扣减. */
    private Integer pointsRate;

    /** 允许使用的 Agent 工具名白名单(JSON 数组字符串, 元素对应 agent_tool_definition.tool_name); RoleContextNode 取(allowedTools ∩ 用户角色权限工具)的交集作为最终 allowed 列表. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedTools;

    /** 允许使用的 Agent 子流程编码白名单(JSON 数组字符串, 如 ["refund_workflow","member_onboard"]); 未在白名单内的子流程禁止触发, Agent 用话术引导. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedSubflows;

    /** 租户启用开关(Boolean, TRUE=ENABLED 正常可用, FALSE=DISABLED 冻结); FALSE 时该租户下所有用户登录被拒绝, 用于欠费/违规场景冻结. */
    private Boolean enabled;
        private Integer deleted = 0;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
