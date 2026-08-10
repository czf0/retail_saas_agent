package com.retail.business.dto.req;

import lombok.Data;

/**
 * 智能对话会话重命名请求.
 */
@Data
public class ChatSessionRenameReq {

    /** 新标题 */
    private String title;
}
