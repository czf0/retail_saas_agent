package com.retail.business.convert;

import com.retail.business.dto.resp.ProductStockResp;
import com.retail.business.entity.ProductStock;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 商品库存账户 转换器.
 * <p>主转换 {@code ProductStock→ProductStockResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 同名字段自动映射,审计字段(deleted/tenantId/createBy/updateBy/deleteAt/deleteBy)由全局配置忽略.
 * <p>belowSafety 为计算字段(目标有,源无),由 Service 转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface StockConvert extends RespConvert<ProductStock, ProductStockResp> {
}
