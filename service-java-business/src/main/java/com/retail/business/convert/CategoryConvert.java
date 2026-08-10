package com.retail.business.convert;

import com.retail.business.dto.req.CategoryCreateReq;
import com.retail.business.dto.resp.CategoryResp;
import com.retail.business.entity.ProductCategory;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 商品分类 转换器.toResp/toRespList 由 {@link RespConvert} 提供;
 * 请求转换 {@code CategoryCreateReq→ProductCategory} 由 {@link ReqConvert} 提供(toEntity/toEntityList).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface CategoryConvert extends RespConvert<ProductCategory, CategoryResp>, ReqConvert<CategoryCreateReq, ProductCategory> {
}
