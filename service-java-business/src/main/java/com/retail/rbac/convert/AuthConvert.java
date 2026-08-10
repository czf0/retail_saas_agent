package com.retail.rbac.convert;

import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import com.retail.rbac.dto.resp.UserInfo;
import com.retail.rbac.entity.SysUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户实体 → 登录信息 转换器.
 * <p>同名字段(username/tenantId)自动映射;id→userId,nickName→displayName 显式映射;
 * role/tenantName 不在实体上,由 AuthService 调用转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface AuthConvert extends RespConvert<SysUser, UserInfo> {

    @Mapping(source = "id", target = "userId")
    @Mapping(source = "nickName", target = "displayName")
    @Override
    UserInfo toResp(SysUser source);
}
