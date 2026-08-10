package com.retail.rbac.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.service.BaseServiceImpl;
import com.retail.rbac.convert.StoreConvert;
import com.retail.rbac.dto.req.StoreCreateReq;
import com.retail.rbac.dto.req.StoreQueryReq;
import com.retail.rbac.dto.req.StoreUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.StoreResp;
import com.retail.rbac.entity.SysStore;
import com.retail.rbac.mapper.SysStoreMapper;
import com.retail.core.security.LoginUserHolder;
import com.retail.rbac.service.SysStoreService;
import com.retail.rbac.enums.SysStatus;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.AuthException;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 门店服务实现(租户级).
 * <p>sys_store 在 ignore-tables 中,tenant_id 由 MetaObjectHandler 自动植入(租户管理员创建时取当前租户);
 * 查询在 Service 层手动按 tenant_id 过滤:租户管理员仅查本租户门店,平台管理员查全部.
 */
@Slf4j
@Service
public class SysStoreServiceImpl extends BaseServiceImpl<SysStoreMapper, SysStore> implements SysStoreService {

    private final StoreConvert storeConvert;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public SysStoreServiceImpl(StoreConvert storeConvert) {
        this.storeConvert = storeConvert;
    }

    @Override
    public PageResp<StoreResp> listStores(StoreQueryReq req) {
        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        LambdaQueryWrapper<SysStore> wrapper = buildStoreQueryWrapper(req == null ? null : req.getStoreName(), false);
        Page<SysStore> page = PageContextHolder.get();
        IPage<SysStore> result = baseMapper.selectPage(page, wrapper);
        List<StoreResp> items = storeConvert.toRespList(result.getRecords());
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<StoreResp> listAllStores() {
        // 不分页全量查询,供下拉选择器使用;复用 listStores 的过滤逻辑
        // 包含停用门店(管理列表场景需要看到全部),业务下拉请使用 listStoreOptions()
        LambdaQueryWrapper<SysStore> wrapper = buildStoreQueryWrapper(null, false);
        wrapper.orderByDesc(SysStore::getId);
        List<SysStore> list = this.list(wrapper);
        return storeConvert.toRespList(list);
    }

    @Override
    public List<StoreResp> listStoreOptions() {
        // 业务下拉专用:强制过滤 status=1(启用门店),避免下单到已停业门店
        // 无需 rbac:store:list 权限,仅依赖 SaInterceptor 全局登录校验
        LambdaQueryWrapper<SysStore> wrapper = buildStoreQueryWrapper(null, true);
        wrapper.orderByDesc(SysStore::getId);
        List<SysStore> list = this.list(wrapper);
        return storeConvert.toRespList(list);
    }

    /**
     * 构建门店查询条件(租户隔离 + 名称模糊 + 可选状态过滤).
     * <p>租户管理员:仅本租户门店;平台管理员(currentTenant==null):可见全部.
     * <p>activeOnly=true 时强制过滤 status=1(业务下拉场景),避免停用门店污染业务选择器.
     *
     * @param storeName 门店名称模糊匹配(可空)
     * @param activeOnly 是否仅返回启用门店(status=1)
     */
    private LambdaQueryWrapper<SysStore> buildStoreQueryWrapper(String storeName, boolean activeOnly) {
        LambdaQueryWrapper<SysStore> wrapper = new LambdaQueryWrapper<>();
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null) {
            wrapper.eq(SysStore::getTenantId, currentTenant);
        }
        if (activeOnly) {
            // 业务下拉强制过滤启用门店:停用门店不应出现在订单创建/库存调整等业务场景
            wrapper.eq(SysStore::getStatus, 1);
        }
        if (StrUtil.isNotBlank(storeName)) {
            wrapper.like(SysStore::getStoreName, storeName.trim());
        }
        return wrapper;
    }

    @Override
    public StoreResp getStore(Long id) {
        SysStore store = loadAndCheck(id);
        return storeConvert.toResp(store);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreResp createStore(StoreCreateReq req) {
        if (StrUtil.isBlank(req.getStoreName())) {
            throw new ParamException("门店名称不能为空");
        }
        // 同名字段由 StoreConvert 自动映射(req→entity)
        SysStore entity = storeConvert.toEntity(req);
        entity.setStoreName(req.getStoreName().trim());
        entity.setStatus(SysStatus.ENABLED);                                       // status 由 Service 赋默认值启用(铁律6:CreateReq 禁 status 字段)
        // tenant_id 由 MetaObjectHandler 自动植入(平台管理员跨租户创建时取 req.tenantId 显式值)
        this.save(entity);
        log.info("创建门店 id={} storeName={} storeCode={} status={} tenantId={}",
                entity.getId(), entity.getStoreName(), entity.getStoreCode(),
                entity.getStatus(), entity.getTenantId());
        return storeConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreResp updateStore(Long id, StoreUpdateReq req) {
        SysStore entity = loadAndCheck(id);
        boolean changed = false;
        if (StrUtil.isNotBlank(req.getStoreName())) {
            entity.setStoreName(req.getStoreName().trim());
            changed = true;
        }
        if (req.getStoreCode() != null) {
            entity.setStoreCode(req.getStoreCode());
            changed = true;
        }
        // 业务扩展字段部分更新(null 不更新,允许传空串显式清空)
        if (req.getAddress() != null) {
            entity.setAddress(req.getAddress());
            changed = true;
        }
        if (req.getPhone() != null) {
            entity.setPhone(req.getPhone());
            changed = true;
        }
        if (req.getBusinessHours() != null) {
            entity.setBusinessHours(req.getBusinessHours());
            changed = true;
        }
        if (req.getManagerName() != null) {
            entity.setManagerName(req.getManagerName());
            changed = true;
        }
        if (req.getManagerId() != null) {
            entity.setManagerId(req.getManagerId());
            changed = true;
        }
        if (req.getLongitude() != null) {
            entity.setLongitude(req.getLongitude());
            changed = true;
        }
        if (req.getLatitude() != null) {
            entity.setLatitude(req.getLatitude());
            changed = true;
        }
        if (req.getRemark() != null) {
            entity.setRemark(req.getRemark());
            changed = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(SysStatus.class, req.getStatus()));
            changed = true;
        }
        if (changed) {
            this.updateById(entity);
            log.info("更新门店 id={} changed={} storeName={} status={} managerId={}",
                    id, changed, req.getStoreName(), req.getStatus(), req.getManagerId());
        }
        return storeConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationResultResp deleteStore(Long id) {
        SysStore store = loadAndCheck(id);
        this.removeById(store.getId());
        log.info("删除门店 id={} storeName={} tenantId={}", id, store.getStoreName(), store.getTenantId());
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("门店删除成功");
        return resp;
    }

    /** 加载门店并校验租户归属(租户管理员仅能操作本租户门店) */
    private SysStore loadAndCheck(Long id) {
        SysStore store = baseMapper.selectById(id);
        if (store == null) {
            throw new ParamException("门店不存在: " + id);
        }
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null && !currentTenant.equals(store.getTenantId())) {
            throw new AuthException("无权操作其他租户门店");
        }
        return store;
    }
}
