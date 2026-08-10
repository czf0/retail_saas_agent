package com.retail.rbac.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.service.BaseServiceImpl;
import com.retail.rbac.constant.PlatformMenuConst;
import com.retail.rbac.convert.RoleConvert;
import com.retail.rbac.dto.req.RoleCreateReq;
import com.retail.rbac.dto.req.RoleQueryReq;
import com.retail.rbac.dto.req.RoleUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.RoleResp;
import com.retail.rbac.entity.SysMenu;
import com.retail.rbac.entity.SysRole;
import com.retail.rbac.entity.SysRoleMenu;
import com.retail.rbac.enums.DataScope;
import com.retail.rbac.enums.RoleKeyConst;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysMenuMapper;
import com.retail.rbac.mapper.SysRoleMapper;
import com.retail.rbac.mapper.SysRoleMenuMapper;
import com.retail.rbac.satoken.RbacCacheManager;
import com.retail.core.security.LoginUserHolder;
import com.retail.rbac.service.SysRoleService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.AuthException;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现(租户级).
 * <p>sys_role 在 ignore-tables 中,查询在 Service 层手动过滤:
 * 租户管理员可见本租户角色 + 平台内置角色(tenant_id IS NULL);平台管理员可见全部.
 * 菜单分配写 sys_role_menu(先删后插).
 */
@Slf4j
@Service
public class SysRoleServiceImpl extends BaseServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final RoleConvert roleConvert;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /** 菜单 Mapper:分配菜单时校验租户用户不可分配平台级菜单 */
    private final SysMenuMapper sysMenuMapper;
    /** 权限缓存管理:角色菜单分配变更 / 角色增删时递增版本号使 Session 缓存失效 */
    private final RbacCacheManager cacheManager;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public SysRoleServiceImpl(RoleConvert roleConvert, SysRoleMenuMapper sysRoleMenuMapper,
                              SysMenuMapper sysMenuMapper, RbacCacheManager cacheManager) {
        this.roleConvert = roleConvert;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    public PageResp<RoleResp> listRoles(RoleQueryReq req) {
        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        LambdaQueryWrapper<SysRole> wrapper = buildRoleQueryWrapper(req == null ? null : req.getRoleName());
        Page<SysRole> page = PageContextHolder.get();
        IPage<SysRole> result = baseMapper.selectPage(page, wrapper);
        List<RoleResp> items = roleConvert.toRespList(result.getRecords());
        items.forEach(this::fillMenuIds);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<RoleResp> listAllRoles() {
        // 不分页全量查询,供下拉选择器使用;复用 listRoles 的过滤逻辑
        LambdaQueryWrapper<SysRole> wrapper = buildRoleQueryWrapper(null);
        wrapper.orderByAsc(SysRole::getRoleSort).orderByDesc(SysRole::getId);
        List<SysRole> list = this.list(wrapper);
        List<RoleResp> items = roleConvert.toRespList(list);
        items.forEach(this::fillMenuIds);
        return items;
    }

    /**
     * 构建角色查询条件(租户隔离 + 名称模糊).
     * <p>租户管理员:本租户角色 + 平台内置角色(tenant_id IS NULL);
     * 平台管理员(currentTenant==null):可见全部角色.
     */
    private LambdaQueryWrapper<SysRole> buildRoleQueryWrapper(String roleName) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null) {
            wrapper.and(w -> w.eq(SysRole::getTenantId, currentTenant).or().isNull(SysRole::getTenantId));
        }
        if (StrUtil.isNotBlank(roleName)) {
            wrapper.like(SysRole::getRoleName, roleName.trim());
        }
        return wrapper;
    }

    @Override
    public RoleResp getRole(Long id) {
        SysRole role = loadAndCheck(id);
        RoleResp resp = roleConvert.toResp(role);
        fillMenuIds(resp);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp createRole(RoleCreateReq req) {
        if (StrUtil.isBlank(req.getRoleName())) {
            throw new ParamException("角色名称不能为空");
        }
        if (StrUtil.isBlank(req.getRoleKey())) {
            throw new ParamException("角色标识不能为空");
        }
        // 同名字段由 RoleConvert 自动映射(req→entity)
        SysRole entity = roleConvert.toEntity(req);
        entity.setRoleName(req.getRoleName().trim());
        entity.setRoleKey(req.getRoleKey().trim());
        entity.setStatus(SysStatus.ENABLED);                                       // status 由 Service 赋默认值启用(铁律6:CreateReq 禁 status 字段)
        entity.setDataScope(req.getDataScope() == null ? DataScope.ALL : EnumUtil.fromCode(DataScope.class, req.getDataScope()));
        entity.setRoleSort(req.getRoleSort() == null ? 0 : req.getRoleSort());
        // tenant_id 由 MetaObjectHandler 自动植入(租户管理员创建时取当前租户;平台管理员为 null=平台角色)
        this.save(entity);

        // 分配菜单(若指定)
        if (req.getMenuIds() != null && !req.getMenuIds().isEmpty()) {
            replaceMenus(entity.getId(), req.getMenuIds());
            cacheManager.bumpPermVersion();
        }
        log.info("创建角色 id={} roleKey={} roleName={} dataScope={} status={} tenantId={} menuIds={}",
                entity.getId(), entity.getRoleKey(), entity.getRoleName(),
                entity.getDataScope(), entity.getStatus(), entity.getTenantId(), req.getMenuIds());

        RoleResp resp = roleConvert.toResp(entity);
        resp.setMenuIds(req.getMenuIds());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp updateRole(Long id, RoleUpdateReq req) {
        SysRole entity = loadAndCheck(id);
        // 平台内置角色(tenant_id NULL)禁止非平台管理员修改
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (entity.getTenantId() == null && currentTenant != null) {
            throw new AuthException("无权修改平台内置角色");
        }
        boolean changed = false;
        if (StrUtil.isNotBlank(req.getRoleName())) {
            entity.setRoleName(req.getRoleName().trim());
            changed = true;
        }
        if (req.getRoleSort() != null) {
            entity.setRoleSort(req.getRoleSort());
            changed = true;
        }
        if (req.getDataScope() != null) {
            entity.setDataScope(EnumUtil.fromCode(DataScope.class, req.getDataScope()));
            changed = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(SysStatus.class, req.getStatus()));
            changed = true;
        }
        if (req.getRemark() != null) {
            entity.setRemark(req.getRemark());
            changed = true;
        }
        if (changed) {
            this.updateById(entity);
        }
        if (req.getMenuIds() != null) {
            replaceMenus(id, req.getMenuIds());
            cacheManager.bumpPermVersion();
        }
        log.info("更新角色 id={} changed={} roleName={} status={} dataScope={} menuIds={}",
                id, changed, req.getRoleName(), req.getStatus(),
                req.getDataScope(), req.getMenuIds());

        RoleResp resp = roleConvert.toResp(entity);
        fillMenuIds(resp);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationResultResp deleteRole(Long id) {
        SysRole role = loadAndCheck(id);
        // B-30 修复:原检查 `if (role.getTenantId() == null) throw` 过于宽泛,
        // 阻止 admin 删除自己创建的平台级角色(admin 创建角色时 MetaObjectHandler 注入 tenant_id=null).
        // 改为只阻止删除超级管理员角色(role_key='admin'),其他平台角色允许 admin 删除.
        // 租户管理员删除平台角色的场景由下方 currentTenant 校验拦截(currentTenant != null 时
        // 与 role.tenant_id=null 不相等 → 抛「无权删除其他租户角色」).
        // 统一:用 RoleKeyConst.SUPER_ADMIN 常量替代硬编码 "admin",避免多处散落的魔术字符串
        if (RoleKeyConst.SUPER_ADMIN.equals(role.getRoleKey())) {
            throw new AuthException("超级管理员角色禁止删除");
        }
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null && !currentTenant.equals(role.getTenantId())) {
            throw new AuthException("无权删除其他租户角色");
        }
        this.removeById(role.getId());
        // 清理角色-菜单关系
        sysRoleMenuMapper.deleteByRoleId(id);
        cacheManager.bumpRoleVersion();
        log.info("删除角色 id={} roleKey={} roleName={} tenantId={}",
                id, role.getRoleKey(), role.getRoleName(), role.getTenantId());
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("角色删除成功");
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long id, List<Long> menuIds) {
        SysRole role = loadAndCheck(id);
        // 平台内置角色(tenant_id NULL)禁止租户管理员分配菜单(与 updateRole 的守卫一致)
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (role.getTenantId() == null && currentTenant != null) {
            throw new AuthException("无权为平台内置角色分配菜单");
        }
        replaceMenus(id, menuIds);
        cacheManager.bumpPermVersion();
        log.info("分配角色菜单 roleId={} menuCount={}", id, menuIds != null ? menuIds.size() : 0);
    }

    @Override
    public List<Long> getRoleMenuIds(Long id) {
        loadAndCheck(id);
        return sysRoleMenuMapper.selectMenuIdsByRoleId(id);
    }

    /** 加载角色并校验租户归属 */
    private SysRole loadAndCheck(Long id) {
        SysRole role = baseMapper.selectById(id);
        if (role == null) {
            throw new ParamException("角色不存在: " + id);
        }
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null && role.getTenantId() != null && !currentTenant.equals(role.getTenantId())) {
            throw new AuthException("无权操作其他租户角色");
        }
        return role;
    }

    /** 全量覆盖角色菜单:先删旧关系,再批量插入 */
    private void replaceMenus(Long roleId, List<Long> menuIds) {
        // 租户用户禁止分配平台级菜单(纵深防御:前端可能绕过菜单树直接提交平台级菜单 ID)
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null && menuIds != null) {
            for (Long menuId : menuIds) {
                if (menuId == null) {
                    continue;
                }
                SysMenu menu = sysMenuMapper.selectById(menuId);
                if (menu != null && PlatformMenuConst.isPlatformMenu(menu)) {
                    throw new AuthException("无权分配平台级菜单: " + menu.getMenuName());
                }
            }
        }
        sysRoleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                if (menuId == null) {
                    continue;
                }
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
    }

    /** 回填 RoleResp.menuIds(查关系表) */
    private void fillMenuIds(RoleResp resp) {
        resp.setMenuIds(sysRoleMenuMapper.selectMenuIdsByRoleId(resp.getId()));
    }
}
