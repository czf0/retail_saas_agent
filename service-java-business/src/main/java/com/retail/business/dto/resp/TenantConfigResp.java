package com.retail.business.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 租户 Agent 能力配额配置详情响应;聚合当前租户 AI Agent 每日 token 上限 + 允许使用的工具清单 + 允许调用子流清单 + 启停开关.
 * <p>Controller: GET /api/v1/system/tenant-config/{tenantId:\\d+};平台管理员可跨租户编辑,租户管理员仅可读.
 */
@Data
public class TenantConfigResp {

    private Long id;

    /** 租户外键(sys_tenant.id);平台级配置 tenantId = NULL. */
    private Long tenantId;

    /** 租户名冗余(前端展示). */
    private String tenantName;

    /** 每日调用 Token 上限(千 tokens;0 = 不限制;超上限 Agent 返回 429 配额耗尽). */
    private Integer dailyTokenLimit;

    /** 允许该租户调用的 Agent 工具 ID 白名单;空 = 继承平台默认工具集. */
    private List<String> allowedTools;

    /** 允许该租户调用的工作流子流 ID 白名单;空 = 全部禁止. */
    private List<String> allowedSubflows;

    /** true = 该租户启用 AI Agent 能力;false = 全功能开关(前端入口隐藏 + 后端接口 403). */
    private Boolean enabled;
}
