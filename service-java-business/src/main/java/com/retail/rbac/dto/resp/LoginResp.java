package com.retail.rbac.dto.resp;

import lombok.Data;

/**
 * 登录操作结果响应;认证成功后返回 access token + token 类型前缀 + 当前登录用户 UserInfo 快照.
 * <p>Controller: POST /api/v1/auth/login;错误(密码错/账号锁/验证码错)不返回此 DTO,抛业务异常(全局异常翻译).
 */
@Data
public class LoginResp {

    /** Sa-Token access token(255 位随机串;请求头需带 'Authorization: Bearer {token}',铁律 7).过期默认 24h,可配置. */
    private String token;

    /** Token 类型前缀(常量 'Bearer';OAuth2 RFC6750 约定;拼接 Authorization 用). */
    private String tokenType = "Bearer";

    /** 当前登录用户信息快照(内嵌对象;前端侧边栏/头像区展示用;权限标识单独接口查). */
    private UserInfo userInfo;
}
