package com.retail.business.convert;

import com.retail.business.dto.req.KnowledgeDocCreateReq;
import com.retail.business.dto.resp.KnowledgeDocListItemResp;
import com.retail.business.dto.resp.KnowledgeDocResp;
import com.retail.business.entity.KnowledgeDoc;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 知识文档转换器 (MapStruct 同名字段自动映射).
 * <p>主转换 KnowledgeDoc→KnowledgeDocResp 由 RespConvert 提供 (toResp/toRespList);
 * 请求转换 KnowledgeDocCreateReq→KnowledgeDoc 由 ReqConvert 提供 (toEntity/toEntityList);
 * 列表项 KnowledgeDocListItemResp 同名字段自动映射 (不含 content 正文).
 */
@Mapper(config = BaseMapStructConfig.class)
public interface KnowledgeDocConvert extends RespConvert<KnowledgeDoc, KnowledgeDocResp>,
        ReqConvert<KnowledgeDocCreateReq, KnowledgeDoc> {

    /** 知识文档列表项 (精简, 不含 content) */
    KnowledgeDocListItemResp toListItem(KnowledgeDoc entity);

    /** 批量列表 */
    List<KnowledgeDocListItemResp> toListItemList(List<KnowledgeDoc> entities);
}
