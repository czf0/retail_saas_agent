package com.retail.business.convert;

import com.retail.business.dto.resp.InventoryRecordResp;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.OrderTrendResp;
import com.retail.business.dto.resp.SalesRecordResp;
import com.retail.business.entity.InventoryRecord;
import com.retail.business.entity.Member;
import com.retail.business.entity.OrderTrendRecord;
import com.retail.business.entity.SalesRecord;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 统计 实体↔DTO 转换器.
 * <p>主转换 {@code SalesRecord→SalesRecordResp} 由 {@link RespConvert} 提供;
 * 库存/订单趋势/会员为同名字段,声明命名方法.
 * <p>InventoryRecordResp.belowSafety 为计算字段(目标有,源无),由 Service 转化后手动 setter.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface StatsConvert extends RespConvert<SalesRecord, SalesRecordResp> {

    /** 库存记录:belowSafety 由 Service 手动 setter */
    InventoryRecordResp toInventoryResp(InventoryRecord entity);

    /** 批量库存:belowSafety 由 Service 逐项手动 setter */
    List<InventoryRecordResp> toInventoryRespList(List<InventoryRecord> entities);

    /** 订单趋势 */
    OrderTrendResp toOrderTrendResp(OrderTrendRecord entity);

    /** 批量订单趋势 */
    List<OrderTrendResp> toOrderTrendRespList(List<OrderTrendRecord> entities);

    /** 会员 */
    MemberResp toMemberResp(Member entity);

    /** 批量会员 */
    List<MemberResp> toMemberRespList(List<Member> entities);
}
