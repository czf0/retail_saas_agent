package com.retail.rbac.enums;

/**
 * RBAC 内置角色键常量类; sys_role.role_key 唯一标识.
 * <p>{@link #SUPER_ADMIN} 为超管标识: 用户持有该角色时, StpInterface.getPermissionList 直接返回 ["*"], 绕过所有权限校验. 角色键字符串常量与 sys_role.role_key 列匹配; 租户注册时自动创建 {@link #TENANT_ADMIN} 键的租户管理员:
 * <ul>
 *   <li>SUPER_ADMIN = "admin": 平台级超管; 绕过全部 @SaCheckPermission; 可看 ALL 租户的数据范围; 平台启动时创建; 不可删除.</li>
 *   <li>TENANT_ADMIN = "tenant_admin": 单租户默认管理员; 租户注册时自动创建; 默认 DataScope.OWN_STORE; 租户范围内拥有租户级全权限.</li>
 * </ul>
 * 
 * @see com.retail.business.enums.RoleEnum
 */
public final class RoleKeyConst {

    /** 平台级超管角色键; 用户持有此角色时, StpInterface 返回 ["*"] 跳过所有权限校验; 数据范围=DataScope.ALL; 平台初始化时创建, 禁止物理删除. */
    public static final String SUPER_ADMIN = "admin";

    /** 租户默认管理员角色键; 每个租户注册时自动创建; 默认 DataScope.OWN_STORE; 在当前租户 scope 内拥有租户级完整权限. */
    public static final String TENANT_ADMIN = "tenant_admin";

    private RoleKeyConst() {
    }
}
