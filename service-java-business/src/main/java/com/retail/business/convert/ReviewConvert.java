package com.retail.business.convert;

import com.retail.business.dto.req.ReviewCreateReq;
import com.retail.business.dto.resp.ReviewListItemResp;
import com.retail.business.dto.resp.ReviewResp;
import com.retail.business.entity.ProductReview;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 商品评价 转换器.
 * <p>主转换 {@code ProductReview→ReviewResp} 由 {@link RespConvert} 提供;
 * 请求转换 {@code ReviewCreateReq→ProductReview} 由 {@link ReqConvert} 提供(toEntity/toEntityList);
 * 次级目标 ReviewListItemResp 同名字段,声明命名方法.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface ReviewConvert extends RespConvert<ProductReview, ReviewResp>, ReqConvert<ReviewCreateReq, ProductReview> {

    /** 评价列表项 */
    ReviewListItemResp toListItem(ProductReview entity);

    /** 批量列表 */
    List<ReviewListItemResp> toListItemList(List<ProductReview> entities);
}
