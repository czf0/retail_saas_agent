package com.retail.business.convert;

import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.dto.resp.RefundResp;
import com.retail.business.entity.OrderRefund;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 退款单转换器.
 * <p>主转换 {@code OrderRefund→RefundResp} 由 {@link RespConvert} 提供(toResp/toRespList).
 * <p>差异字段:statusDesc 由 Service 调用 {@code RefundStatus.description()} 后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface RefundConvert extends RespConvert<OrderRefund, RefundResp> {

    /** 退款列表项:statusDesc 由 Service 手动 setter */
    RefundListItemResp toListItem(OrderRefund entity);

    /** 批量列表:MapStruct 自动循环调用 {@link #toListItem} */
    List<RefundListItemResp> toListItemList(List<OrderRefund> entities);
}
