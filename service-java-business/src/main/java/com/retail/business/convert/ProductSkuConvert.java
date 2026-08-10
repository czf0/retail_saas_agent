package com.retail.business.convert;

import com.retail.business.dto.req.ProductSkuCreateReq;
import com.retail.business.dto.resp.ProductSkuListItemResp;
import com.retail.business.dto.resp.ProductSkuResp;
import com.retail.business.entity.ProductSku;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 商品 SKU 转换器.
 * <p>主转换 {@code ProductSku→ProductSkuResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code ProductSkuCreateReq→ProductSku} 由 {@link ReqConvert} 提供(toEntity/toEntityList);
 * 列表项 ProductSkuListItemResp 为同名字段,声明命名方法即可.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ProductSkuConvert extends RespConvert<ProductSku, ProductSkuResp>, ReqConvert<ProductSkuCreateReq, ProductSku> {

    /** SKU 列表项 */
    ProductSkuListItemResp toListItem(ProductSku entity);

    /** 批量列表 */
    List<ProductSkuListItemResp> toListItemList(List<ProductSku> entities);
}
