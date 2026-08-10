package com.retail.business.dto.req;

import lombok.Data;

/**
 * 智能对话会话创建请求.
 */
@Data
public class ChatSessionCreateReq {

    /** 会话标题(可选,默认"新对话") */
    private String title;
}
