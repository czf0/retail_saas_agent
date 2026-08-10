package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.rbac.enums.DataScope;
import com.retail.rbac.enums.SysStatus;

import java.time.LocalDateTime;

/**
 * 角色实体, 对应数据库 sys_role 表.
 * <p>多租户隔离: 已配置在 ignore-tables 中(tenant_id 可空, NULL=平台内置角色, 如 role_key='admin' 超管), 拦截器不自动注入 tenant_id 条件, Service 层手动按 tenant_id 过滤(租户用户仅见本租户角色 + 平台内置角色).
 * <p>唯一约束: UNIQUE(tenant_id, role_key), 同一租户下 role_key 不可重复; UNIQUE(tenant_id, role_name), 同一租户下角色名不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID,NULL=平台内置角色 */
    private Long tenantId;

    private String roleName;

    /** 权限标识(如 admin/tenant_admin); role_key='admin' 触发超管全权限放行(AuthInterceptor 短路校验, 不查 sys_role_menu). */
    private String roleKey;

    /** 同级排序值; 数值越小越靠前(ASC 升序); 角色管理页可拖拽调整, 保存时更新此值. */
    private Integer roleSort;

    /** 数据权限范围(DataScope 枚举本体: 1=ALL 全部, 2=CUSTOM 自定义, 5=SELF 仅本人); 仅本人时 SQL 层自动追加 create_by = 当前用户条件. */
    private DataScope dataScope;

    /** 启停状态(SysStatus 枚举:1启用/0停用) */
    private SysStatus status;

    private String remark;

    @TableLogic
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
