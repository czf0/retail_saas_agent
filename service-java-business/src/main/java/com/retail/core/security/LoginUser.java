package com.retail.core.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户上下文 POJO(共享基础设施).
 * <p>登录成功后由认证模块(rbac.AuthServiceImpl)写入 Sa-Token Session,
 * 供拦截器,审计填充,业务层等统一读取,集中解耦认证信息流转.
 * <ul>
 *   <li>{@code tenantId} 为 null 表示平台管理员(跨租户);</li>
 *   <li>{@code roleKeys} 含 {@code "admin"} 时为超级管理员(由 StpInterface 返回 ["*"] 全放行).</li>
 * </ul>
 * <p>定义于 core 模块,避免 core 反向依赖 rbac;rbac 与 business 均可使用.
 */
@Data
public class LoginUser implements Serializable {

    private Long userId;

    private String username;

    /** 租户ID,null=平台管理员 */
    private Long tenantId;

    /** 所属门店ID,null=无固定门店 */
    private Long storeId;

    private String nickName;

    /** 角色 key 列表(如 ["admin"],["tenant_admin"]) */
    private List<String> roleKeys;

    /** 角色 ID 列表(sys_role.id, 供 RAG 业务过滤等场景按角色 ID 隔离文档可见性) */
    private List<Long> roleIds;
}
