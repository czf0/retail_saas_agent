package com.retail.rbac.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点响应(自递归 children);菜单管理模块左侧树 / 角色分配菜单权限树 / 数据权限部门树 等通用树结构返回体基础版.
 * <p>Controller: GET /api/v1/system/menus/tree;按 orderNum 升序 + 深度优先递归构建.
 */
@Data
public class MenuTreeResp {

    private Long id;

    /** 菜单名称(同 MenuResp.menuName;树节点展示文本). */
    private String menuName;

    /** 父节点ID(0=根). */
    private Long parentId;

    /** 菜单类型:1=目录 2=菜单 3=按钮(见 MenuType);树过滤时可选仅展示目录+菜单. */
    private Integer menuType;

    /** 权限标识(按钮级;权限分配复选框用). */
    private String perms;

    /** 路由路径(用于前端预览跳转). */
    private String path;

    /** 组件路径. */
    private String component;

    /** 图标(仅目录/菜单级展示). */
    private String icon;

    /** 同级排序(升序). */
    private Integer orderNum;

    /** 显示状态:1=显示 0=隐藏(隐藏节点仍在树中用于权限分配). */
    private Integer visible;

    /** 菜单状态:1=启用 0=停用(停用节点仍展示为灰). */
    private Integer status;

    /** 子节点集合(1:N 递归;叶子节点=[]空列表;后端递归构建,最多支持 5 层深度防止树爆炸). */
    private List<MenuTreeResp> children;
}
