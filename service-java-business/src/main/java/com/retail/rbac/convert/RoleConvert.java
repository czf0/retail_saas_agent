package com.retail.rbac.convert;

import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import com.retail.rbac.dto.req.RoleCreateReq;
import com.retail.rbac.dto.resp.RoleResp;
import com.retail.rbac.entity.SysRole;
import org.mapstruct.Mapper;

/**
 * 角色 转换器.
 * <p>toResp/toRespList 由 {@link RespConvert} 提供;请求转换 {@code RoleCreateReq→SysRole} 由 {@link ReqConvert} 提供.
 * 差异字段 menuIds(写 sys_role_menu)由 Service 转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface RoleConvert extends RespConvert<SysRole, RoleResp>, ReqConvert<RoleCreateReq, SysRole> {
}
