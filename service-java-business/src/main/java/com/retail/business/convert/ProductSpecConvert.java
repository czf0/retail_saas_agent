package com.retail.business.convert;

import com.retail.business.dto.req.ProductSpecReq;
import com.retail.business.dto.resp.ProductSpecResp;
import com.retail.business.entity.ProductSpec;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 商品规格定义转换器.
 * <p>主转换 {@code ProductSpec→ProductSpecResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code ProductSpecReq→ProductSpec} 由 {@link ReqConvert} 提供(toEntity/toEntityList).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ProductSpecConvert extends RespConvert<ProductSpec, ProductSpecResp>, ReqConvert<ProductSpecReq, ProductSpec> {
}
