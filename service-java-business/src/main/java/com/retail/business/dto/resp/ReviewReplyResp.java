package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量/单条商家客服回复评价操作结果响应;replyContent 写入 review_info.reply_content,replyAt 写入当前时间.
 * <p>幂等:同 reviewId 重复回复覆盖旧内容(但 replyAt 取首次回复时间不覆盖;用于展示"商家回复于 x 天前").
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewReplyResp extends OperationResultResp {
    /** 本次成功写入回复内容的评价条数(内容非空才计入;空内容被过滤不计). */
    private Long replied;
}
