package com.retail.rbac.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.core.service.BaseServiceImpl;
import com.retail.rbac.constant.PlatformMenuConst;
import com.retail.rbac.convert.MenuConvert;
import com.retail.rbac.dto.req.MenuCreateReq;
import com.retail.rbac.dto.req.MenuUpdateReq;
import com.retail.rbac.dto.resp.MenuResp;
import com.retail.rbac.dto.resp.MenuTreeResp;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.entity.SysMenu;
import com.retail.rbac.entity.SysRoleMenu;
import com.retail.rbac.enums.MenuType;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysMenuMapper;
import com.retail.rbac.mapper.SysRoleMenuMapper;
import com.retail.rbac.satoken.RbacCacheManager;
import com.retail.rbac.service.SysMenuService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜单/权限服务实现(全局共享,无 tenant_id).
 * <p>所有租户共用同一份菜单定义.删除菜单时同步清理 sys_role_menu 中相关关系.
 * 菜单树(MenuTreeResp)由 Service 基于 MenuResp 列表自行组装.
 */
@Slf4j
@Service
public class SysMenuServiceImpl extends BaseServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final MenuConvert menuConvert;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /** 权限缓存管理:菜单 perms 变更 / 菜单删除时递增版本号使 Session 缓存失效 */
    private final RbacCacheManager cacheManager;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public SysMenuServiceImpl(MenuConvert menuConvert, SysRoleMenuMapper sysRoleMenuMapper,
                              RbacCacheManager cacheManager) {
        this.menuConvert = menuConvert;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    public List<MenuResp> listMenus() {
        List<SysMenu> list = loadMenusForCurrentTenant();
        return menuConvert.toRespList(list);
    }

    @Override
    public List<MenuTreeResp> menuTree() {
        List<SysMenu> list = loadMenusForCurrentTenant();
        return buildTree(list, 0L);
    }

    /**
     * 加载当前用户可见的菜单列表.
     * <p>平台管理员(effectiveTenantId 为 null)返回全部菜单;租户用户过滤掉平台级菜单
     * (见 {@link PlatformMenuConst}),使其在角色分配菜单树中不可见,不可选择.
     */
    private List<SysMenu> loadMenusForCurrentTenant() {
        List<SysMenu> list = baseMapper.selectAllMenus();
        if (LoginUserHolder.effectiveTenantId() != null) {
            return list.stream()
                    .filter(m -> !PlatformMenuConst.isPlatformMenu(m))
                    .collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public MenuResp getMenu(Long id) {
        SysMenu menu = baseMapper.selectById(id);
        if (menu == null) {
            throw new ParamException("菜单不存在: " + id);
        }
        return menuConvert.toResp(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuResp createMenu(MenuCreateReq req) {
        if (StrUtil.isBlank(req.getMenuName())) {
            throw new ParamException("菜单名称不能为空");
        }
        if (req.getMenuType() == null) {
            throw new ParamException("菜单类型不能为空");
        }
        // 同名字段由 MenuConvert 自动映射(req→entity)
        SysMenu entity = menuConvert.toEntity(req);
        entity.setMenuName(req.getMenuName().trim());
        entity.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        entity.setVisible(req.getVisible() == null ? 1 : req.getVisible());
        entity.setStatus(SysStatus.ENABLED);                                       // status 由 Service 赋默认值启用(铁律6:CreateReq 禁 status 字段)
        entity.setOrderNum(req.getOrderNum() == null ? 0 : req.getOrderNum());
        this.save(entity);
        log.info("创建菜单 id={} menuName={} menuType={} parentId={} perms={} path={}",
                entity.getId(), entity.getMenuName(), entity.getMenuType(),
                entity.getParentId(), entity.getPerms(), entity.getPath());
        return menuConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuResp updateMenu(Long id, MenuUpdateReq req) {
        SysMenu entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new ParamException("菜单不存在: " + id);
        }
        boolean changed = false;
        if (StrUtil.isNotBlank(req.getMenuName())) {
            entity.setMenuName(req.getMenuName().trim());
            changed = true;
        }
        if (req.getParentId() != null) {
            entity.setParentId(req.getParentId());
            changed = true;
        }
        if (req.getMenuType() != null) {
            entity.setMenuType(parseMenuType(req.getMenuType()));
            changed = true;
        }
        if (req.getPerms() != null) {
            entity.setPerms(req.getPerms());
            changed = true;
        }
        if (req.getPath() != null) {
            entity.setPath(req.getPath());
            changed = true;
        }
        if (req.getComponent() != null) {
            entity.setComponent(req.getComponent());
            changed = true;
        }
        if (req.getIcon() != null) {
            entity.setIcon(req.getIcon());
            changed = true;
        }
        if (req.getOrderNum() != null) {
            entity.setOrderNum(req.getOrderNum());
            changed = true;
        }
        if (req.getVisible() != null) {
            entity.setVisible(req.getVisible());
            changed = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(SysStatus.class, req.getStatus()));
            changed = true;
        }
        if (changed) {
            this.updateById(entity);
            // 菜单 perms 可能变更,使权限缓存失效
            cacheManager.bumpPermVersion();
            log.info("更新菜单 id={} changed={} menuName={} perms={} path={} component={}",
                    id, changed, req.getMenuName(), req.getPerms(),
                    req.getPath(), req.getComponent());
        }
        return menuConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationResultResp deleteMenu(Long id) {
        SysMenu menu = baseMapper.selectById(id);
        if (menu == null) {
            throw new ParamException("菜单不存在: " + id);
        }
        // 校验是否存在子菜单
        Long childCount = this.baseMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new ParamException("存在子菜单，禁止删除");
        }
        this.removeById(id);
        // 清理 sys_role_menu 中引用该菜单的关系
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        // 菜单删除后相关用户权限变化,使缓存失效
        cacheManager.bumpPermVersion();
        log.info("删除菜单 id={} menuName={} parentId={}", id, menu.getMenuName(), menu.getParentId());
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("菜单删除成功");
        return resp;
    }

    /** 递归构建菜单树 */
    private List<MenuTreeResp> buildTree(List<SysMenu> menus, Long parentId) {
        List<MenuTreeResp> tree = new ArrayList<>();
        for (SysMenu m : menus) {
            if (!Objects.equals(m.getParentId(), parentId)) {
                continue;
            }
            MenuTreeResp node = toTreeResp(m);
            List<MenuTreeResp> children = buildTree(menus, m.getId());
            node.setChildren(children.isEmpty() ? null : children);
            tree.add(node);
        }
        return tree;
    }

    private MenuTreeResp toTreeResp(SysMenu m) {
        MenuTreeResp node = new MenuTreeResp();
        node.setId(m.getId());
        node.setMenuName(m.getMenuName());
        node.setParentId(m.getParentId());
        node.setMenuType(menuTypeToCode(m.getMenuType()));
        node.setPerms(m.getPerms());
        node.setPath(m.getPath());
        node.setComponent(m.getComponent());
        node.setIcon(m.getIcon());
        node.setOrderNum(m.getOrderNum());
        node.setVisible(m.getVisible());
        node.setStatus(m.getStatus() != null ? m.getStatus().getCode() : null);
        return node;
    }

    /**
     * Integer code(1/2/3)→ MenuType 枚举.
     * <p>EnumUtil.fromCode 处理 null→null,非法 code→ParamException.
     */
    private static MenuType parseMenuType(Integer code) {
        return EnumUtil.fromCode(MenuType.class, code);
    }

    /**
     * MenuType 枚举 → Integer code(1/2/3).
     */
    private static Integer menuTypeToCode(MenuType t) {
        return t != null ? t.getCode() : null;
    }
}
