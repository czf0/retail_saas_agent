package com.retail.rbac.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * RBAC 菜单类型枚举(若依风格); sys_menu.menu_type 列.
 * <p>code = 1 目录 M 2 菜单 C 3 按钮 F; perms=按钮权限编码(business:{module}:{action}), 与 Sa-Token @SaCheckPermission 对齐. 目录 / 菜单对应前端路由; 按钮仅存权限编码不生成路由:
 * <ul>
 *   <li>DIR(1 目录 M): 菜单树父节点; 图标 + 排序; 对应前端路由分组(Layout 包裹); 不对应具体页面组件.</li>
 *   <li>MENU(2 菜单 C): 对应一个前端页面; 定义 path + component; 权限 perms 编码 = 列表查询权限; 渲染侧边栏菜单项.</li>
 *   <li>BUTTON(3 按钮 F): 页面内部操作按钮; 仅存 perms 权限编码; 前端根据权限渲染按钮显隐; 不进入路由表.</li>
 * </ul>
 */
public enum MenuType implements BaseEnum {

    /** 目录(菜单树父节点); 配置图标和排序号; 对应前端 Layout 包裹的路由组; 不绑定具体页面组件. */
    DIR(1, "目录"),
    /** 菜单(前端页面); 定义路由 path 和 component 路径; perms 编码 = 列表查询权限; 渲染侧边栏菜单项. */
    MENU(2, "菜单"),
    /** 按钮(页面内操作); 仅存 perms 权限编码; 前端 v-hasPermi 控制按钮显隐; 不参与前端路由生成. */
    BUTTON(3, "按钮");

    @EnumValue
    private final Integer code;
    private final String desc;

    MenuType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
