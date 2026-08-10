package com.retail.business.convert;

import com.retail.business.dto.resp.StockMovementResp;
import com.retail.business.entity.StockMovement;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 库存流水 转换器.
 * <p>主转换 {@code StockMovement→StockMovementResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 同名字段自动映射,审计字段(tenantId/createBy)由全局配置忽略.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface StockMovementConvert extends RespConvert<StockMovement, StockMovementResp> {
}
