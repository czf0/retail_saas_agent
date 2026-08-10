package com.retail.business.convert;

import com.retail.business.dto.req.OrderCreateReq;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.dto.resp.OrderResp;
import com.retail.business.entity.OrderInfo;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 订单转换器.
 * <p>主转换 {@code OrderInfo→OrderResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code OrderCreateReq→OrderInfo} 由 {@link ReqConvert} 提供(toEntity/toEntityList).
 * <p>差异字段处理:
 * <ul>
 *   <li>statusDesc:状态中文描述,由 Service 调用 {@code OrderStatus.description()} 后手动 setter</li>
 *   <li>items:订单明细列表,由 Service 查 order_item 表后调用 {@link OrderItemConvert#toRespList} 并手动 setter</li>
 *   <li>itemCount:列表项的明细数,由 Service 查 order_item 表 count 后手动 setter</li>
 * </ul>
 */
@Mapper(config = BaseMapStructConfig.class)
public interface OrderConvert extends RespConvert<OrderInfo, OrderResp>, ReqConvert<OrderCreateReq, OrderInfo> {

    /** 订单列表项:statusDesc 由 Service 手动 setter */
    OrderListItemResp toListItem(OrderInfo entity);

    /** 批量列表:MapStruct 自动循环调用 {@link #toListItem} */
    List<OrderListItemResp> toListItemList(List<OrderInfo> entities);
}
