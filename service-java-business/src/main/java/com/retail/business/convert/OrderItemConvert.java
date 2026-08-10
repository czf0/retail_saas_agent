package com.retail.business.convert;

import com.retail.business.dto.resp.OrderItemResp;
import com.retail.business.entity.OrderItem;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 订单明细转换器.
 * <p>主转换 {@code OrderItem→OrderItemResp} 由 {@link RespConvert} 提供(toResp/toRespList).
 * 同名字段自动映射,无差异字段.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface OrderItemConvert extends RespConvert<OrderItem, OrderItemResp> {

    /** 批量列表:MapStruct 自动继承 RespConvert.toRespList,本方法仅作类型显式声明 */
    @Override
    List<OrderItemResp> toRespList(List<OrderItem> entities);
}
