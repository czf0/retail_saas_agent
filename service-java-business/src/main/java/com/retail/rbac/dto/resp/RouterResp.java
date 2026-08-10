package com.retail.rbac.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 前端路由响应(getRouters 接口返回:根据用户菜单生成路由树).
 */
@Data
public class RouterResp {

    /** 路由 name(取 menu.path 首字母大写,作为 vue-router 稳定标识符,不参与展示) */
    private String name;

    /**
     * 菜单展示标题(取 sys_menu.menu_name,即中文菜单名).
     * <p>作为菜单名称的权威数据源(SSOT):前端优先使用此字段渲染侧边栏文本,
     * 仅在此字段为空时才回退到前端 menuMetaMap 兜底,避免新增菜单遗漏映射而显示英文.
     */
    private String title;

    /** 路由路径 */
    private String path;

    /** 组件路径(目录为 Layout,菜单为 component,按钮无) */
    private String component;

    /** 是否隐藏 */
    private Boolean hidden;

    /** 重定向(目录且有子菜单时) */
    private String redirect;

    /** 子路由 */
    private List<RouterResp> children;
}
