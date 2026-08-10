package com.retail.rbac.service;

import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.StoreCreateReq;
import com.retail.rbac.dto.req.StoreQueryReq;
import com.retail.rbac.dto.req.StoreUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.StoreResp;

import java.util.List;

/**
 * 门店服务(租户级).
 * <p>租户管理员仅能操作本租户门店;平台管理员可跨租户操作.
 * tenant_id 由 MetaObjectHandler 自动植入(租户管理员创建时取当前上下文).
 */
public interface SysStoreService {

    PageResp<StoreResp> listStores(StoreQueryReq req);

    /**
     * 查询全部门店(不分页,供下拉选择器使用).
     * <p>租户管理员仅可见本租户门店;平台管理员可见全部.
     * <p>包含停用门店(status=0),适用于管理列表场景.
     */
    List<StoreResp> listAllStores();

    /**
     * 查询当前用户可用的启用门店列表(业务下拉专用,无需 rbac:store:list 权限).
     * <p>解决 tenant1_admin / store1_manager 等租户用户在订单创建,库存调整,用户表单等业务场景下
     * 需要门店下拉数据,但被 /rbac/stores/all 的 @SaCheckPermission("rbac:store:list") 拒绝的问题.
     * <ul>
     *   <li>租户管理员:仅本租户的启用门店(status=1);</li>
     *   <li>平台管理员:全部启用门店(currentTenant==null 时不按租户过滤).</li>
     * </ul>
     * 强制过滤 status=1:业务下拉不应包含停用门店,避免下单到已停业门店.
     */
    List<StoreResp> listStoreOptions();

    StoreResp getStore(Long id);

    StoreResp createStore(StoreCreateReq req);

    StoreResp updateStore(Long id, StoreUpdateReq req);

    OperationResultResp deleteStore(Long id);
}
