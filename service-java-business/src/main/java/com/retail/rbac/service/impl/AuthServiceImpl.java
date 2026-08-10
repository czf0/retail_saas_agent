package com.retail.rbac.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.retail.rbac.convert.AuthConvert;
import com.retail.rbac.dto.req.LoginReq;
import com.retail.rbac.dto.resp.LoginResp;
import com.retail.rbac.dto.resp.RouterResp;
import com.retail.rbac.dto.resp.UserInfo;
import com.retail.rbac.dto.resp.UserPermResp;
import com.retail.rbac.entity.SysMenu;
import com.retail.rbac.entity.SysUser;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysMenuMapper;
import com.retail.rbac.entity.SysRole;
import com.retail.rbac.mapper.SysRoleMapper;
import com.retail.rbac.mapper.SysUserMapper;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import com.retail.rbac.service.AuthService;
import com.retail.rbac.service.TenantNameResolver;
import com.retail.core.exception.AuthException;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 认证服务实现，基于 Sa-Token。
 * <p>
 * 登录流程：校验用户名密码(BCrypt) → StpUtil.login(userId) → 写入 LoginUser 到 Session
 * → 更新 last_login_at → 返回 token + 用户信息。
 * <p>
 * 用户信息统一通过 {@link LoginUserHolder} 读写 Session，替代散落的字符串 key，集中解耦。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final AuthConvert authConvert;
    /** 租户名解析（由 business 层实现，避免 rbac 反向依赖；微服务化时可替换为远程调用） */

    /** 构造注入：单构造器由 Spring 自动注入，依赖不可变且便于单元测试 */
    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           SysRoleMapper sysRoleMapper,
                           SysMenuMapper sysMenuMapper,
                           AuthConvert authConvert) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.authConvert = authConvert;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(LoginReq req) {
        if (req == null || StrUtil.isBlank(req.getUsername()) || StrUtil.isBlank(req.getPassword())) {
            log.warn("登录失败(参数为空) username={}", req == null ? null : req.getUsername());
            throw new ParamException("用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectByUsername(req.getUsername().trim());
        if (user == null || !BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败(用户名或密码错误) username={}", req.getUsername());
            throw new AuthException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != SysStatus.ENABLED) {
            log.warn("登录失败(账号已被禁用) username={} userId={}", req.getUsername(), user.getId());
            throw new AuthException("账号已被禁用");
        }

        // Sa-Token 登录，签发 token
        StpUtil.login(user.getId());

        // 查询角色 key 列表，写入统一登录上下文
        List<String> roleKeys = sysRoleMapper.selectRoleKeysByUserId(user.getId());
        // 查询角色 ID 列表 (sys_role.id), 供 RAG 业务过滤按角色 ID 隔离文档可见性 (D1.5)
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<Long> roleIds = roles != null
                ? roles.stream().map(SysRole::getId).collect(Collectors.toList())
                : Collections.emptyList();
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setStoreId(user.getStoreId());
        loginUser.setNickName(user.getNickName());
        loginUser.setRoleKeys(roleKeys != null ? roleKeys : Collections.emptyList());
        loginUser.setRoleIds(roleIds);
        LoginUserHolder.set(loginUser);

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        log.info("登录成功 userId={} username={} tenantId={} storeId={}",
                user.getId(), user.getUsername(), user.getTenantId(), user.getStoreId());

        LoginResp resp = new LoginResp();
        resp.setToken(StpUtil.getTokenValue());
        resp.setUserInfo(authConvert.toResp(user));
        return resp;
    }

    @Override
    public UserInfo currentUser() {
        if (!StpUtil.isLogin()) {
            throw new AuthException("未登录");
        }
        LoginUser lu = LoginUserHolder.get();
        if (lu == null) {
            throw new AuthException("登录态已失效");
        }
        SysUser user = sysUserMapper.selectById(lu.getUserId());
        if (user == null) {
            throw new AuthException("用户不存在");
        }
        return authConvert.toResp(user);
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            // 登出前捕获 userId/username（logout 后 Session 失效，无法再读取）
            Long userId = StpUtil.getLoginIdAsLong();
            LoginUser lu = LoginUserHolder.get();
            String username = lu != null ? lu.getUsername() : null;
            StpUtil.logout();
            log.info("登出 userId={} username={}", userId, username);
        }
    }

    @Override
    public UserPermResp getUserPermInfo() {
        if (!StpUtil.isLogin()) {
            throw new AuthException("未登录");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }
        // UserInfo info = buildUserInfo(user, LoginUserHolder.currentRoleKeys());
        UserPermResp resp = new UserPermResp();
        resp.setUser(authConvert.toResp(user));
        // 委托 StpInterface（StpInterfaceImpl）查询角色与权限
        resp.setRoles(StpUtil.getRoleList());
        resp.setPermissions(StpUtil.getPermissionList());
        return resp;
    }

    @Override
    public List<RouterResp> getRouters() {
        if (!StpUtil.isLogin()) {
            throw new AuthException("未登录");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        if (menus == null || menus.isEmpty()) {
            return Collections.emptyList();
        }
        // 由根节点（parent_id=0）递归构建路由树
        return buildRouterTree(menus, 0L);
    }

    /**
     * 构建登录用户信息。
     * 同名字段由 AuthConvert 自动映射；role 取首个角色 key（超管为 "admin"，兼容旧前端）；
     * tenantName 通过 TenantNameResolver 反查（平台管理员为 null）。
     */
    // private UserInfo buildUserInfo(SysUser user, List<String> roleKeys) {
    //     UserInfo info = authConvert.toResp(user);
    //     info.setRole(roleKeys != null && !roleKeys.isEmpty() ? roleKeys.get(0) : null);
    //     if (user.getTenantId() != null && tenantNameResolver != null) {
    //         info.setTenantName(tenantNameResolver.resolve(user.getTenantId()));
    //     }
    //     return info;
    // }

    /**
     * 递归构建前端路由树。
     * <p>顶层（parentId=0）component 置为 Layout，path 加 "/" 前缀；子菜单 path/component 取实体值；
     * hidden = visible==0；name 取 path 首字母大写。
     * <p>注意：种子数据 sys_menu.path 可能已含前导 "/"（如 "/system"），
     * 直接拼 "/"+"/system" 会产生双斜杠 "//system" 导致前端 menuMetaMap 查不到；
     * 故顶层先 strip 前导 "/"，再统一加 "/" 前缀。
     */
    private List<RouterResp> buildRouterTree(List<SysMenu> menus, Long parentId) {
        List<RouterResp> tree = new ArrayList<>();
        for (SysMenu m : menus) {
            if (!Objects.equals(m.getParentId(), parentId)) {
                continue;
            }
            RouterResp router = new RouterResp();
            boolean isTop = parentId == 0L;
            String rawPath = m.getPath() != null ? m.getPath() : "";
            router.setName(capitalize(rawPath));
            // 菜单标题取 sys_menu.menu_name（中文），作为前端展示的权威数据源（SSOT）；
            // 前端优先使用此字段，避免新增菜单因前端映射表遗漏而显示英文 name
            router.setTitle(m.getMenuName());
            if (isTop) {
                // 顶层：去掉前导 "/" 后统一加 "/"，避免 "//system" 双斜杠
                String stripped = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
                router.setPath("/" + stripped);
            } else {
                router.setPath(rawPath);
            }
            router.setComponent(isTop ? "Layout" : m.getComponent());
            router.setHidden(m.getVisible() != null && m.getVisible() == 0);
            List<RouterResp> children = buildRouterTree(menus, m.getId());
            if (!children.isEmpty()) {
                router.setChildren(children);
                router.setRedirect(isTop ? "noRedirect" : null);
            }
            tree.add(router);
        }
        return tree;
    }

    private String capitalize(String s) {
        if (StrUtil.isBlank(s)) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + (s.length() > 1 ? s.substring(1) : "");
    }
}
