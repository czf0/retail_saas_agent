package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关系实体, 对应数据库 sys_role_menu 表.
 * <p>隔离域: 已配置在 ignore-tables 中(无 tenant_id / store_id 字段, 通过 role_id 间接触发租户隔离, 分配菜单时 Service 层先校验角色归属).
 * <p>业务约束: 物理删除表(无 deleted / 审计字段); 分配菜单时先按 role_id 物理 DELETE 旧关系, 再批量 INSERT 新关系(保证原子性, 避免部分更新).
 * <p>唯一约束: UNIQUE(role_id, menu_id), 同一角色下不可重复绑定同一菜单.
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 id, 指向 sys_role.id; 分配前 Service 层校验: 平台内置角色(tenant_id=NULL)仅平台管理员可修改, 租户角色仅本租户管理员可修改. */
    private Long roleId;

    /** 菜单 id, 指向 sys_menu.id; 若绑定的是目录/菜单, 默认级联绑定其下所有按钮权限(SysRoleMenuServiceImpl.saveRoleMenus 递归展开). */
    private Long menuId;
}
