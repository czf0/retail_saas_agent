package com.retail.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.rbac.constant.PlatformMenuConst;
import com.retail.rbac.entity.SysMenu;
import com.retail.rbac.entity.SysRole;
import com.retail.rbac.entity.SysRoleMenu;
import com.retail.rbac.enums.DataScope;
import com.retail.rbac.enums.RoleKeyConst;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysMenuMapper;
import com.retail.rbac.mapper.SysRoleMapper;
import com.retail.rbac.mapper.SysRoleMenuMapper;
import com.retail.rbac.service.TenantRbacInitializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户 RBAC 初始化器实现.
 * <p>
 * 新建租户时由 business 层 TenantConfigServiceImpl.createTenant 调用,
 * 在同一事务内创建「租户管理员」角色并分配默认菜单权限.
 * <p>
 * <b>默认菜单范围</b>:全量菜单中排除以下 5 类平台级权限前缀的菜单项——
 * {@code rbac:menu:}(菜单管理,全局共享),{@code system:tenant:}(租户管理,平台级),
 * {@code system:operlog:}(操作日志),{@code system:config:}(系统配置),
 * {@code system:dict:}(数据字典);这些均为平台级功能或前端视图未实现,
 * 租户管理员不应持有.M 目录和 C/F 菜单均保留,确保租户管理员可管理本租户的用户,角色,门店.
 * <p>
 * <b>幂等性</b>:先查 tenant_admin 角色是否存在,存在则直接返回.
 * <p>
 * sys_role / sys_menu / sys_role_menu 均在 ignore-tables 中,
 * 不受多租户拦截器自动注入影响;tenant_id 由本方法显式设置.
 */
@Slf4j
@Component
public class TenantRbacInitializerImpl implements TenantRbacInitializer {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public TenantRbacInitializerImpl(SysRoleMapper sysRoleMapper,
                                     SysRoleMenuMapper sysRoleMenuMapper,
                                     SysMenuMapper sysMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initTenantRbac(Long tenantId) {
        if (tenantId == null) {
            return;
        }

        // 1. 幂等检查:租户管理员角色已存在则跳过
        SysRole existing = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .eq(SysRole::getRoleKey, RoleKeyConst.TENANT_ADMIN));
        if (existing != null) {
            log.debug("租户 RBAC 初始化跳过 tenantId={} 原因=租户管理员角色已存在 roleId={}",
                    tenantId, existing.getId());
            return;
        }

        // 2. 创建租户管理员角色
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setRoleName("租户管理员");
        role.setRoleKey(RoleKeyConst.TENANT_ADMIN);
        role.setRoleSort(1);
        role.setDataScope(DataScope.ALL); // ALL —— 租户内全部数据
        role.setStatus(SysStatus.ENABLED);
        role.setRemark("新建租户时自动创建");
        sysRoleMapper.insert(role);

        // 3. 分配默认菜单(排除 5 类平台级权限前缀,见 EXCLUDED_PERMS_PREFIXES)
        List<SysMenu> menus = sysMenuMapper.selectAllMenus();
        int assignedCount = 0;
        for (SysMenu menu : menus) {
            // perms 为 null/空(M 目录,C 菜单的 list 权限)的节点保留分配;平台级菜单跳过(见 PlatformMenuConst)
            if (PlatformMenuConst.isPlatformMenu(menu)) {
                continue;
            }
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getId());
            rm.setMenuId(menu.getId());
            sysRoleMenuMapper.insert(rm);
            assignedCount++;
        }
        log.info("初始化租户 RBAC tenantId={} roleId={} roleName={} 分配菜单数={}/{}",
                tenantId, role.getId(), role.getRoleName(), assignedCount, menus.size());
    }
}
