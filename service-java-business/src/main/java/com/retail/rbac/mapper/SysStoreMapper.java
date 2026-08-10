package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysStore;

/**
 * 门店 Mapper. 
 * <p>sys_store 在 ignore-tables 中(与 sys_user/sys_role 一致, 便于平台管理员跨租户操作), 
 * tenant_id 由 {@link com.retail.core.config.AuditMetaObjectHandler} 自动植入, 查询在 Service 层手动按 tenant_id 过滤: 
 * <ul>
 *   <li>租户管理员: 仅查本租户门店; </li>
 *   <li>平台管理员: 查全部门店. </li>
 * </ul>
 */
public interface SysStoreMapper extends BaseMapper<SysStore> {
}
