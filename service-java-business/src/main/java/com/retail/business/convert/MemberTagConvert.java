package com.retail.business.convert;

import com.retail.business.dto.req.MemberTagReq;
import com.retail.business.dto.resp.MemberTagResp;
import com.retail.business.entity.MemberTag;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

/**
 * 会员标签 转换器.
 * <p>toResp/toRespList 由 {@link RespConvert} 提供(同名字段自动映射);
 * 请求转换 {@code MemberTagReq→MemberTag} 由 {@link ReqConvert} 提供(toEntity/toEntityList).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface MemberTagConvert extends RespConvert<MemberTag, MemberTagResp>, ReqConvert<MemberTagReq, MemberTag> {
}
