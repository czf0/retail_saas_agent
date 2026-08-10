package com.retail.business.convert;

import com.retail.business.dto.req.TenantConfigCreateReq;
import com.retail.business.dto.resp.TenantConfigResp;
import com.retail.business.entity.TenantConfig;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 租户配置 转换器.toResp/toRespList 由 {@link RespConvert} 提供;
 * 请求转换 {@code TenantConfigCreateReq→TenantConfig} 由 {@link ReqConvert} 提供(toEntity/toEntityList).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface TenantConvert extends RespConvert<TenantConfig, TenantConfigResp>, ReqConvert<TenantConfigCreateReq, TenantConfig> {
}
