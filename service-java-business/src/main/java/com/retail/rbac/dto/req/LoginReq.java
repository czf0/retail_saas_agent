package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 登录请求 DTO.
 */
@Data
public class LoginReq {

    private String username;

    private String password;
}
