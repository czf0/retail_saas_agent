package com.retail.rbac.convert;

import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import com.retail.rbac.dto.req.UserCreateReq;
import com.retail.rbac.dto.resp.UserResp;
import com.retail.rbac.entity.SysUser;
import org.mapstruct.Mapper;

/**
 * 用户 转换器.
 * <p>toResp/toRespList 由 {@link RespConvert} 提供(同名字段自动映射);
 * 请求转换 {@code UserCreateReq→SysUser} 由 {@link ReqConvert} 提供.
 * 差异字段:passwordHash(BCrypt 加密),roleIds(写关系表)由 Service 转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface UserConvert extends RespConvert<SysUser, UserResp>, ReqConvert<UserCreateReq, SysUser> {
}
