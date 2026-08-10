package com.retail.rbac.service;

import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.AssignRoleReq;
import com.retail.rbac.dto.req.ResetPwdReq;
import com.retail.rbac.dto.req.UserCreateReq;
import com.retail.rbac.dto.req.UserQueryReq;
import com.retail.rbac.dto.req.UserUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.UserResp;

/**
 * 系统用户服务.
 * <p>租户管理员仅能操作本租户用户;平台管理员可跨租户操作.
 * tenant_id / store_id 由 MetaObjectHandler 自动植入(租户管理员创建时取当前上下文).
 */
public interface SysUserService {

    PageResp<UserResp> listUsers(UserQueryReq req);

    UserResp getUser(Long id);

    UserResp createUser(UserCreateReq req);

    UserResp updateUser(Long id, UserUpdateReq req);

    OperationResultResp deleteUser(Long id);

    /**
     * 分配角色 (全量覆盖).
     * <p>前置条件: 用户必须存在, 且不能分配超管角色给他人 (仅平台超管可持有), 否则抛 BizException.
     * <p>副作用: 事务内删除旧角色关联后重建, 即时影响该用户权限判定; 变更后需重新登录生效.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    void assignRoles(Long id, AssignRoleReq req);

    /**
     * 重置密码.
     * <p>前置条件: 用户必须存在, 否则抛 BizException; 新密码须满足复杂度要求, 否则抛 ParamException.
     * <p>副作用: 立即生效, 该用户旧会话失效需重新登录.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    void resetPassword(Long id, ResetPwdReq req);
}
