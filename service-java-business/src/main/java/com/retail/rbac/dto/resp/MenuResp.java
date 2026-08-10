package com.retail.rbac.dto.resp;

import lombok.Data;

/**
 * 菜单详情页展示响应;聚合菜单节点基础信息 + 目录/菜单/按钮类型 + 路由路径/组件路径/icon + 权限标识(菜单管理模块新增/编辑回显).
 * <p>Controller: GET /api/v1/system/menus/{id:\\d+};{id} 正则守卫;菜单树为 parentId=0 自递归结构.
 */
@Data
public class MenuResp {

    private Long id;

    /** 菜单/按钮名称(中文展示;侧边栏/面包屑文本;RouterResp.title 来源). */
    private String menuName;

    /** 父菜单外键(自关联 sys_menu.id;根节点 parentId=0;用于构建前端树). */
    private Long parentId;

    /** 菜单类型:1=CATALOG(目录/Layout) 2=MENU(菜单页面) 3=BUTTON(按钮/API权限);见 MenuType. */
    private Integer menuType;

    /** 权限标识(菜单类型=BUTTON 时必填;对应 @SaCheckPermission 注解值;如 "system:user:add"). */
    private String perms;

    /** 路由路径(menuType=MENU/CATALOG 时必填;如 "/system/user";CATALOG 路由一般 redirect 到第一个子菜单). */
    private String path;

    /** 前端组件路径(MENU 页面文件路径;如 "system/user/index";CATALOG 固定为 "Layout";BUTTON 为空). */
    private String component;

    /** 菜单图标(Element Plus/SvgIcon name;侧边栏展示;CATALOG/MENU 展示,BUTTON 为空). */
    private String icon;

    /** 同级排序号(升序;越小越靠前;CATALOG 下子菜单的展示顺序). */
    private Integer orderNum;

    /** 是否前端可见:1=SHOW(正常显示) 0=HIDE(隐藏,如编辑详情页不在侧边栏出现但路由可跳);注意:隐藏 ≠ 无权限. */
    private Integer visible;

    /** 菜单状态:1=ENABLED(启用) 0=DISABLED(停用;菜单不返回给前端 getRouters,等同于无此菜单). */
    private Integer status;
}
