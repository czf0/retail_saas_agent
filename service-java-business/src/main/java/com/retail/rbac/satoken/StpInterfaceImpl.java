package com.retail.rbac.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.retail.rbac.enums.RoleKeyConst;
import com.retail.rbac.mapper.SysMenuMapper;
import com.retail.rbac.mapper.SysRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色查询实现(带 Session 缓存).
 * <p>
 * <b>缓存策略</b>:查询结果缓存到用户 Sa-Token Session,附带 {@link RbacCacheManager} 的版本号.
 * 读取时比对版本号,不一致则重新查库并更新缓存,避免使用过期数据.
 * <p>
 * {@code getPermissionList}:查 sys_user_role→sys_role_menu→sys_menu.perms;
 * 若用户角色 key 含 {@link RoleKeyConst#SUPER_ADMIN},直接返回 {@code ["*"]} 绕过所有权限校验.
 * <p>
 * <b>失效时机</b>(由 Service 层调用 {@link RbacCacheManager} 递增版本号):
 * <ul>
 *   <li>用户角色分配变更 → bumpRoleVersion(角色+权限双重失效)</li>
 *   <li>角色菜单分配变更 / 菜单 perms 变更 → bumpPermVersion(权限失效)</li>
 *   <li>角色增删 → bumpRoleVersion</li>
 * </ul>
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final RbacCacheManager cacheManager;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public StpInterfaceImpl(SysRoleMapper sysRoleMapper,
                            SysMenuMapper sysMenuMapper,
                            RbacCacheManager cacheManager) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }

        // 尝试从 Session 缓存读取(版本号匹配则直接返回)
        SaSession session = getSessionIfExists(userId);
        if (session != null) {
            Long cachedVer = (Long) session.get(RbacCacheManager.PERM_VER_KEY);
            if (cachedVer != null && cachedVer == cacheManager.getPermVersion()) {
                Object cached = session.get(RbacCacheManager.PERM_CACHE_KEY);
                if (cached instanceof List) {
                    log.debug("权限列表命中Session缓存 userId={} permVer={} permCount={}",
                            userId, cachedVer, ((List<?>) cached).size());
                    return (List<String>) cached;
                }
            }
        }

        // 缓存未命中或版本过期,查库
        List<String> roleKeys = sysRoleMapper.selectRoleKeysByUserId(userId);
        // 超级管理员:全权限放行
        List<String> perms;
        if (roleKeys != null && roleKeys.stream().anyMatch(RoleKeyConst.SUPER_ADMIN::equals)) {
            perms = Collections.singletonList("*");
            log.debug("权限列表查库 userId={} 命中=超级管理员 全权限放行", userId);
        } else {
            List<String> dbPerms = sysMenuMapper.selectPermsByUserId(userId);
            perms = dbPerms != null
                    ? dbPerms.stream().filter(StrUtil::isNotBlank).distinct().toList()
                    : Collections.emptyList();
            log.debug("权限列表查库 userId={} permCount={}", userId, perms.size());
        }

        // 写入 Session 缓存(仅在 Session 存在时写入,避免为未登录用户创建 Session)
        if (session != null) {
            session.set(RbacCacheManager.PERM_CACHE_KEY, perms);
            session.set(RbacCacheManager.PERM_VER_KEY, cacheManager.getPermVersion());
        }

        return perms;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }

        // 尝试从 Session 缓存读取
        SaSession session = getSessionIfExists(userId);
        if (session != null) {
            Long cachedVer = (Long) session.get(RbacCacheManager.ROLE_VER_KEY);
            if (cachedVer != null && cachedVer == cacheManager.getRoleVersion()) {
                Object cached = session.get(RbacCacheManager.ROLE_CACHE_KEY);
                if (cached instanceof List) {
                    log.debug("角色列表命中Session缓存 userId={} roleVer={} roleCount={}",
                            userId, cachedVer, ((List<?>) cached).size());
                    return (List<String>) cached;
                }
            }
        }

        // 缓存未命中,查库
        List<String> roleKeys = sysRoleMapper.selectRoleKeysByUserId(userId);
        List<String> roles = roleKeys != null ? roleKeys : Collections.emptyList();
        log.debug("角色列表查库 userId={} roleCount={}", userId, roles.size());

        // 写入 Session 缓存
        if (session != null) {
            session.set(RbacCacheManager.ROLE_CACHE_KEY, roles);
            session.set(RbacCacheManager.ROLE_VER_KEY, cacheManager.getRoleVersion());
        }

        return roles;
    }

    /**
     * 获取用户 Session(仅在已存在时返回,不为未登录用户创建新 Session).
     *
     * @param userId 用户ID
     * @return 已存在的 Session,不存在返回 null
     */
    private SaSession getSessionIfExists(Long userId) {
        try {
            return StpUtil.getSessionByLoginId(userId, false);
        } catch (Exception e) {
            // Session 不存在或已过期,返回 null 触发无缓存查询
            return null;
        }
    }

    private Long parseUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
