package com.retail.business.convert;

import com.retail.business.dto.resp.PointsLogResp;
import com.retail.business.entity.PointsLog;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 会员积分流水 转换器.
 * <p>主转换 {@code PointsLog→PointsLogResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 同名字段自动映射,审计字段(tenantId/storeId)由全局配置忽略.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface PointsConvert extends RespConvert<PointsLog, PointsLogResp> {
}
