package com.retail.rbac.constant;

import cn.hutool.core.util.StrUtil;
import com.retail.rbac.entity.SysMenu;

/**
 * 平台级菜单定义常量(唯一权威来源).
 * <p>
 * 这些权限前缀对应的菜单均为<b>平台级功能</b>,仅平台管理员可操作,
 * 租户管理员不可见,不可分配给角色:
 * <ul>
 *   <li>{@code rbac:menu:} —— 菜单管理,全局共享,仅平台管理员可操作</li>
 *   <li>{@code system:tenant:} —— 租户管理,平台级({@code @SaCheckRole("admin")})</li>
 *   <li>{@code system:operlog:} —— 操作日志,平台级 + 前端视图未实现</li>
 *   <li>{@code system:config:} —— 系统配置,平台级 + 前端视图未实现</li>
 *   <li>{@code system:dict:} —— 数据字典,平台级 + 前端视图未实现</li>
 * </ul>
 * 后续如需对租户开放某项平台级功能,仅需从此数组移除对应前缀即可.
 * <p>
 * 供以下场景统一使用,避免重复定义导致漂移:
 * <ul>
 *   <li>{@code TenantRbacInitializerImpl}:新建租户时初始化租户管理员默认菜单(排除平台级)</li>
 *   <li>{@code SysMenuServiceImpl}:租户用户加载菜单树时过滤平台级菜单</li>
 *   <li>{@code SysRoleServiceImpl}:租户用户分配角色菜单时校验禁止平台级菜单</li>
 * </ul>
 */
public final class PlatformMenuConst {

    /** 工具类禁止实例化 */
    private PlatformMenuConst() {
    }

    /** 平台级权限前缀(租户管理员不可持有/不可分配) */
    public static final String[] EXCLUDED_PERMS_PREFIXES = {
            "rbac:menu:",
            "system:tenant:",
            "system:operlog:",
            "system:config:",
            "system:dict:"
    };

    /**
     * 判断菜单是否属于平台级菜单.
     * <p>根据 perms 命中任一平台级前缀判定;perms 为空(M 目录,C 菜单的 list 权限为 null)时返回 false,
     * 即目录始终保留,避免整体过滤掉租户可见的目录骨架.
     *
     * @param menu 菜单实体;null 返回 false
     * @return true=平台级菜单(租户不可见/不可分配)
     */
    public static boolean isPlatformMenu(SysMenu menu) {
        if (menu == null) {
            return false;
        }
        String perms = menu.getPerms();
        return StrUtil.isNotBlank(perms) && StrUtil.startWithAny(perms, EXCLUDED_PERMS_PREFIXES);
    }
}
