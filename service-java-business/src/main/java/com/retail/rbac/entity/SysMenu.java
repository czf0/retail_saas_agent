package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.rbac.enums.MenuType;
import com.retail.rbac.enums.SysStatus;

import java.time.LocalDateTime;

/**
 * 菜单/权限实体, 对应数据库 sys_menu 表.
 * <p>隔离域: 已配置在 ignore-tables 中(全局共享, 无 tenant_id 字段), 所有租户共享同一份菜单与权限点定义.
 * <p>树形结构约束: parentId = 0 表示根节点; 其他值指向父菜单 sys_menu.id; 层级深度 max = 3(Service 创建时校验, 超限抛 ParamException).
 * <p>唯一约束: UNIQUE(parent_id, menu_name), 同层下菜单名不可重复; UNIQUE(perms), 权限标识全局唯一(NULL 不参与唯一校验, 目录通常无 perms).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String menuName;

    /** 父菜单 id, 指向 sys_menu.id; 根节点固定 = 0; 查询树时 Service 层递归组装. */
    private Long parentId;

    /** 菜单类型(MenuType 枚举本体: 1=DIR 目录, 2=MENU 菜单, 3=BUTTON 按钮); 按钮类型不渲染左侧菜单, 仅用于 @SaCheckPermission 权限点校验. */
    private MenuType menuType;

    /** 权限标识(如 rbac:user:list); 与 @SaCheckPermission 注解值严格匹配; 目录通常为 NULL(不做权限控制); UNIQUE 全局唯一. */
    private String perms;

    private String path;

    private String component;

    private String icon;

    /** 同级排序值; 数值越小越靠前(ASC 升序); 菜单管理页可拖拽调整, 保存时更新此值. */
    private Integer orderNum;

    /** 显示/隐藏标记(1=VISIBLE 显示, 0=HIDDEN 隐藏); 隐藏后菜单树不展示, 但通过路由 URL 仍可访问(用于内嵌页/跳转页). */
    private Integer visible;

    /** 启停状态(SysStatus 枚举本体: 1=ENABLED 启用, 0=DISABLED 停用); 停用后左侧菜单不展示, 按钮级权限也不生效. */
    private SysStatus status;

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
