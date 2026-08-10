package com.retail.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.StoreCreateReq;
import com.retail.rbac.dto.req.StoreQueryReq;
import com.retail.rbac.dto.req.StoreUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.StoreResp;
import com.retail.rbac.service.SysStoreService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店管理接口.
 * <p>路由前缀 /api/v1/rbac/stores.sys_store 表位于多租户 ignore-tables,按业务键 tenant_id 显式操作,
 * 平台管理员可创建全平台门店,租户管理员仅本租户门店.
 * <p>权限校验基于 @SaCheckPermission("rbac:store:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(如 rbac:store:list / query / add / edit / remove);
 * /options 端点仅需登录态,供业务下拉选择器使用.
 * <p>注意:/all,/options 为字面量路径,须在 /{id} 之前注册;/{id} 加 \d+ 正则守卫,避免字面量被误解析为 ID.
 */
@RestController
@RequestMapping("/api/v1/rbac/stores")
public class StoreController {

    private final SysStoreService sysStoreService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public StoreController(SysStoreService sysStoreService) {
        this.sysStoreService = sysStoreService;
    }

    /**
     * 分页查询门店列表(按租户 / 关键词 / 状态过滤).
     * <p>分页参数 page/pageSize 由 PageParameterInterceptor 从 HttpServletRequest 提取注入 ThreadLocal,
     * Controller 不再承载分页参数(分页为横切关注点).
     */
    @GetMapping("")
    @SaCheckPermission("rbac:store:list")
    public R<PageResp<StoreResp>> list(StoreQueryReq req) {
        return R.ok(sysStoreService.listStores(req));
    }

    /**
     * 全量门店列表(不分页,供下拉选择器使用).
     * <p>必须声明在 {@code /{id}} 之前,且 {@code /{id}} 加 {@code \d+} 正则约束,
     * 否则 Spring 会把 "all" 当作 {id} 解析触发 MethodArgumentTypeMismatchException.
     */
    @GetMapping("/all")
    @SaCheckPermission("rbac:store:list")
    public R<List<StoreResp>> listAll() {
        return R.ok(sysStoreService.listAllStores());
    }

    /**
     * 业务下拉专用门店列表(仅返回当前用户租户下的启用门店).
     * <p>与 {@code /all} 的差异:
     * <ul>
     *   <li>无 {@code @SaCheckPermission} 注解:仅依赖全局 SaInterceptor 登录校验,
     *       租户管理员(tenant1_admin / store1_manager)等无 rbac:store:list 权限的用户也可访问,
     *       解决订单创建 / 库存调整 / 用户表单等业务场景下门店下拉为空的问题;</li>
     *   <li>强制过滤 status=1:业务下拉不应包含停用门店,避免下单到已停业门店;</li>
     *   <li>租户隔离:租户管理员仅本租户门店;平台管理员(currentTenant==null)返回全部启用门店.</li>
     * </ul>
     * 必须声明在 {@code /{id}} 之前,避免 "options" 被 {id} 解析.
     */
    @GetMapping("/options")
    public R<List<StoreResp>> listOptions() {
        return R.ok(sysStoreService.listStoreOptions());
    }

    /** 查询门店详情(含名称 / 编码 / 地址 / 联系人 / 状态 / 所属租户). */
    @GetMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:store:query")
    public R<StoreResp> get(@PathVariable("id") Long id) {
        return R.ok(sysStoreService.getStore(id));
    }

    /** 创建门店(状态默认启用,关联当前操作人所属租户). */
    @PostMapping("")
    @SaCheckPermission("rbac:store:add")
    public R<StoreResp> create(@RequestBody StoreCreateReq req) {
        return R.ok(sysStoreService.createStore(req));
    }

    /** 修改门店(部分更新:名称 / 地址 / 联系人 / 状态 / 营业信息等). */
    @PutMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:store:edit")
    public R<StoreResp> update(@PathVariable("id") Long id, @RequestBody StoreUpdateReq req) {
        return R.ok(sysStoreService.updateStore(id, req));
    }

    /** 删除门店(逻辑删除 + 级联禁用门店下所有用户登录态). */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:store:remove")
    public R<OperationResultResp> delete(@PathVariable("id") Long id) {
        return R.ok(sysStoreService.deleteStore(id));
    }
}
