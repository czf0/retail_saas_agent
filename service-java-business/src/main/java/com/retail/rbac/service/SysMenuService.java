package com.retail.rbac.service;

import com.retail.rbac.dto.req.MenuCreateReq;
import com.retail.rbac.dto.req.MenuUpdateReq;
import com.retail.rbac.dto.resp.MenuResp;
import com.retail.rbac.dto.resp.MenuTreeResp;
import com.retail.rbac.dto.resp.OperationResultResp;

import java.util.List;

/**
 * 菜单/权限服务(全局共享).
 */
public interface SysMenuService {

    /** 全部菜单扁平列表 */
    List<MenuResp> listMenus();

    /** 菜单树(含子节点) */
    List<MenuTreeResp> menuTree();

    MenuResp getMenu(Long id);

    MenuResp createMenu(MenuCreateReq req);

    MenuResp updateMenu(Long id, MenuUpdateReq req);

    OperationResultResp deleteMenu(Long id);
}
