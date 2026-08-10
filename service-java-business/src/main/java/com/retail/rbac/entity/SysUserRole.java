package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关系实体, 对应数据库 sys_user_role 表.
 * <p>隔离域: 已配置在 ignore-tables 中(无 tenant_id / store_id 字段, 通过 user_id / role_id 间接触发租户隔离, 分配角色时 Service 层先校验用户 + 角色归属).
 * <p>业务约束: 物理删除表(无 deleted / 审计字段); 分配角色时先按 user_id 物理 DELETE 旧关系, 再批量 INSERT 新关系(保证原子性, 避免部分更新).
 * <p>唯一约束: UNIQUE(user_id, role_id), 同一用户下不可重复绑定同一角色.
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id, 指向 sys_user.id; 分配前 Service 层校验: 平台管理员仅能给平台用户绑平台内置角色, 租户管理员仅能给本租户用户绑本租户角色. */
    private Long userId;

    /** 角色 id, 指向 sys_role.id; 若用户同时拥有多个角色, 权限取并集, 数据权限取最大范围(优先 ALL > CUSTOM > SELF). */
    private Long roleId;
}
