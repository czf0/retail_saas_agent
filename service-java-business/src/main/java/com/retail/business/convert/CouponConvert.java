package com.retail.business.convert;

import com.retail.business.dto.req.CouponTemplateCreateReq;
import com.retail.business.dto.resp.CouponTemplateListItemResp;
import com.retail.business.dto.resp.CouponTemplateResp;
import com.retail.business.entity.CouponTemplate;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 优惠券模板 转换器.
 * <p>主转换 {@code CouponTemplate→CouponTemplateResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code CouponTemplateCreateReq→CouponTemplate} 由 {@link ReqConvert} 提供(toEntity/toEntityList);
 * 次级目标 CouponTemplateListItemResp 为同名字段,声明命名方法即可.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface CouponConvert extends RespConvert<CouponTemplate, CouponTemplateResp>, ReqConvert<CouponTemplateCreateReq, CouponTemplate> {

    /** 优惠券模板列表项 */
    CouponTemplateListItemResp toListItem(CouponTemplate entity);

    /** 批量列表 */
    List<CouponTemplateListItemResp> toListItemList(List<CouponTemplate> entities);
}
