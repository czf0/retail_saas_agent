package com.retail.business.convert;

import com.retail.business.dto.req.ProductCreateReq;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductResp;
import com.retail.business.entity.ProductInfo;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 商品 转换器.
 * <p>主转换 {@code ProductInfo→ProductResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code ProductCreateReq→ProductInfo} 由 {@link ReqConvert} 提供(toEntity/toEntityList);
 * 次级目标 ProductListItemResp 声明命名方法;belowSafety 为计算字段,由 Service 转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ProductConvert extends RespConvert<ProductInfo, ProductResp>, ReqConvert<ProductCreateReq, ProductInfo> {

    /** 商品列表项:belowSafety 为计算字段,由 Service 调用后手动 setter */
    ProductListItemResp toListItem(ProductInfo entity);

    /** 批量列表:MapStruct 自动循环调用 {@link #toListItem} */
    List<ProductListItemResp> toListItemList(List<ProductInfo> entities);
}
