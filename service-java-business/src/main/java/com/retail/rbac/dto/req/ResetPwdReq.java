package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 重置用户密码请求.
 */
@Data
public class ResetPwdReq {

    /** 新密码(明文,服务端 BCrypt 加密) */
    private String password;
}
