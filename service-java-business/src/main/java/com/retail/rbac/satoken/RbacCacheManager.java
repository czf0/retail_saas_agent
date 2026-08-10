package com.retail.rbac.satoken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RBAC 权限缓存版本管理器.
 * <p>
 * 采用「版本号失效」策略:权限/角色数据变更时递增对应版本号,
 * {@link StpInterfaceImpl} 读取时比对 Session 中缓存的版本号,不一致则重新查库.
 * <p>
 * 相比遍历所有在线用户 Session 逐个清除,版本号方式无需遍历,O(1) 失效,
 * 适合角色菜单批量变更等全局性场景.
 * <p>
 * <b>微服务化注意</b>:当前版本号基于 JVM 内存(单实例),多实例部署后需替换为
 * Redis 自增 key(如 {@code INCR rbac:perm_ver}),保证跨实例失效一致性.
 */
@Slf4j
@Component
public class RbacCacheManager {

    /** 权限列表缓存版本(菜单 perms / 角色菜单关系变更时递增) */
    private final AtomicLong permVersion = new AtomicLong(0);

    /** 角色列表缓存版本(用户角色分配 / 角色增删时递增) */
    private final AtomicLong roleVersion = new AtomicLong(0);

    /** Sa-Token Session 中存储权限缓存的 key */
    public static final String PERM_CACHE_KEY = "rbac:permList";
    /** Sa-Token Session 中存储权限缓存版本的 key */
    public static final String PERM_VER_KEY = "rbac:permVer";
    /** Sa-Token Session 中存储角色缓存的 key */
    public static final String ROLE_CACHE_KEY = "rbac:roleList";
    /** Sa-Token Session 中存储角色缓存版本的 key */
    public static final String ROLE_VER_KEY = "rbac:roleVer";

    /**
     * 获取当前权限缓存版本号.
     *
     * @return 版本号
     */
    public long getPermVersion() {
        return permVersion.get();
    }

    /**
     * 获取当前角色缓存版本号.
     *
     * @return 版本号
     */
    public long getRoleVersion() {
        return roleVersion.get();
    }

    /**
     * 递增权限缓存版本号(菜单 perms 变更,角色菜单分配变更时调用).
     * 使所有用户的权限列表缓存失效,下次请求重新查库.
     */
    public void bumpPermVersion() {
        long newVer = permVersion.incrementAndGet();
        log.info("RBAC 权限缓存版本递增 newPermVer={}（菜单/角色菜单变更，触发全员权限重查）", newVer);
    }

    /**
     * 递增角色缓存版本号(用户角色分配变更,角色增删时调用).
     * 角色变更同时影响权限列表,故一并递增权限版本号.
     */
    public void bumpRoleVersion() {
        long newRoleVer = roleVersion.incrementAndGet();
        long newPermVer = permVersion.incrementAndGet();
        log.info("RBAC 角色缓存版本递增 newRoleVer={} newPermVer={}（角色变更联动权限失效）",
                newRoleVer, newPermVer);
    }
}
