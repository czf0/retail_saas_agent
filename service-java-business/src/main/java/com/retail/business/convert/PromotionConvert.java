package com.retail.business.convert;

import com.retail.business.dto.req.PromotionCreateReq;
import com.retail.business.dto.resp.ProductPromotionItemResp;
import com.retail.business.dto.resp.PromotionListItemResp;
import com.retail.business.dto.resp.PromotionResp;
import com.retail.business.entity.Promotion;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 促销活动 转换器.
 * <p>主转换 {@code Promotion→PromotionResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 请求转换 {@code PromotionCreateReq→Promotion} 由 {@link ReqConvert} 提供(toEntity/toEntityList);
 * 次级目标 PromotionListItemResp,ProductPromotionItemResp 均为同名字段,声明命名方法即可.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface PromotionConvert extends RespConvert<Promotion, PromotionResp>, ReqConvert<PromotionCreateReq, Promotion> {

    /** 促销列表项 */
    PromotionListItemResp toListItem(Promotion entity);

    /** 批量列表 */
    List<PromotionListItemResp> toListItemList(List<Promotion> entities);

    /** 商品参与的促销活动项 */
    ProductPromotionItemResp toProductPromotionItem(Promotion entity);

    /** 批量:商品参与的促销活动项 */
    List<ProductPromotionItemResp> toProductPromotionItemList(List<Promotion> entities);
}
