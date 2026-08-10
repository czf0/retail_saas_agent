package com.retail.rbac.service;

import com.retail.rbac.dto.req.LoginReq;
import com.retail.rbac.dto.resp.LoginResp;
import com.retail.rbac.dto.resp.RouterResp;
import com.retail.rbac.dto.resp.UserInfo;
import com.retail.rbac.dto.resp.UserPermResp;

import java.util.List;

/**
 * 认证服务,基于 Sa-Token.
 * <p>提供登录,当前用户,登出,权限信息(getInfo),前端路由(getRouters).
 */
public interface AuthService {

    /**
     * 账号密码登录 (基于 Sa-Token).
     * <p>前置条件: 账号必须存在且密码匹配, 否则抛 AuthException; 登录失败不区分"账号不存在/密码错误" (防撞库).
     * <p>副作用: 登录成功后签发 Sa-Token 会话, 注入当前租户/门店上下文; 失败时记录登录日志.
     *
     * @param req 登录请求 (账号 + 密码)
     * @return 登录响应 (含 token 与用户/角色信息)
     * @throws AuthException 账号不存在 / 密码错误 / 账号被禁用
     */
    LoginResp login(LoginReq req);

    /**
     * 查询当前登录用户信息.
     * <p>前置条件: 请求需携带有效 Sa-Token, 否则抛 AuthException.
     * <p>副作用: 无; 仅读取当前会话上下文.
     */
    UserInfo currentUser();

    /**
     * 登出: 注销当前 Sa-Token 会话.
     * <p>前置条件: 需处于已登录状态.
     * <p>副作用: 会话失效后后续请求需重新登录; 清理当前租户/门店线程上下文.
     */
    void logout();

    /** 用户权限信息:用户 + 角色 key 列表 + 权限标识列表(超管返回 ["*"]) */
    UserPermResp getUserPermInfo();

    /** 前端路由树:根据用户菜单生成 */
    List<RouterResp> getRouters();
}
