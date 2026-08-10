package com.retail.rbac.convert;

import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import com.retail.rbac.dto.req.StoreCreateReq;
import com.retail.rbac.dto.resp.StoreResp;
import com.retail.rbac.entity.SysStore;
import org.mapstruct.Mapper;

/**
 * 门店 转换器.
 * <p>toResp/toRespList 由 {@link RespConvert} 提供;请求转换 {@code StoreCreateReq→SysStore} 由 {@link ReqConvert} 提供.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface StoreConvert extends RespConvert<SysStore, StoreResp>, ReqConvert<StoreCreateReq, SysStore> {
}
