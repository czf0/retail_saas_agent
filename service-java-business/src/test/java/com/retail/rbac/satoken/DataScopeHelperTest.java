package com.retail.rbac.satoken;

import com.retail.core.context.AuditUserContext;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import com.retail.rbac.enums.RoleKeyConst;
import com.retail.rbac.mapper.SysRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据权限辅助工具单元测试。
 * <p>
 * 覆盖 {@link DataScopeHelper#needSelfScope()} 的核心分支：
 * 未登录 / 超级管理员 / ALL 角色 / SELF 角色 / 无角色 五种场景，
 * 以及 {@link DataScopeHelper#currentOperator()} 委托 {@link AuditUserContext} 的行为。
 * <p>
 * 使用 {@link MockedStatic} 隔离 {@link LoginUserHolder}、{@link AuditUserContext} 静态方法。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeHelperTest {

    private SysRoleMapper sysRoleMapper;
    private DataScopeHelper dataScopeHelper;

    private MockedStatic<LoginUserHolder> loginUserHolderMock;
    private MockedStatic<AuditUserContext> auditUserContextMock;

    @BeforeEach
    void setUp() {
        sysRoleMapper = Mockito.mock(SysRoleMapper.class);
        dataScopeHelper = new DataScopeHelper(sysRoleMapper);
        loginUserHolderMock = Mockito.mockStatic(LoginUserHolder.class);
        auditUserContextMock = Mockito.mockStatic(AuditUserContext.class);
    }

    @AfterEach
    void tearDown() {
        loginUserHolderMock.close();
        auditUserContextMock.close();
    }

    /** 未登录：不附加过滤（无法构造过滤条件） */
    @Test
    void needSelfScope_notLoggedIn_returnsFalse() {
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(null);

        assertFalse(dataScopeHelper.needSelfScope());
        verify(sysRoleMapper, never()).selectMinDataScopeByUserId(Mockito.any());
    }

    /** 超级管理员（roleKeys 含 "admin"）：全数据放行，不附加过滤，也不查库 */
    @Test
    void needSelfScope_superAdmin_returnsFalse() {
        LoginUser lu = new LoginUser();
        lu.setUserId(1L);
        lu.setRoleKeys(List.of(RoleKeyConst.SUPER_ADMIN));
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(lu);

        assertFalse(dataScopeHelper.needSelfScope());
        // 超管短路返回，不查库
        verify(sysRoleMapper, never()).selectMinDataScopeByUserId(Mockito.any());
    }

    /** ALL 角色（data_scope=1）：不附加过滤 */
    @Test
    void needSelfScope_allRole_returnsFalse() {
        LoginUser lu = new LoginUser();
        lu.setUserId(2L);
        lu.setRoleKeys(List.of("tenant_admin"));
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(lu);
        when(sysRoleMapper.selectMinDataScopeByUserId(2L)).thenReturn(1);

        assertFalse(dataScopeHelper.needSelfScope());
    }

    /** SELF 角色（data_scope=5）：附加 create_by 过滤 */
    @Test
    void needSelfScope_selfRole_returnsTrue() {
        LoginUser lu = new LoginUser();
        lu.setUserId(3L);
        lu.setRoleKeys(Collections.emptyList());
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(lu);
        when(sysRoleMapper.selectMinDataScopeByUserId(3L)).thenReturn(5);

        assertTrue(dataScopeHelper.needSelfScope());
    }

    /** 最广范围优先：同时拥有 ALL(1) 与 SELF(5) 时，取 MIN=1，按 ALL 放行 */
    @Test
    void needSelfScope_mixedScopes_takesMinAndReturnsFalse() {
        LoginUser lu = new LoginUser();
        lu.setUserId(4L);
        lu.setRoleKeys(Collections.emptyList());
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(lu);
        when(sysRoleMapper.selectMinDataScopeByUserId(4L)).thenReturn(1);

        assertFalse(dataScopeHelper.needSelfScope());
    }

    /** 无有效角色（minScope=null）：保守不附加过滤，由权限注解兜底拦截 */
    @Test
    void needSelfScope_noRoles_returnsFalse() {
        LoginUser lu = new LoginUser();
        lu.setUserId(5L);
        lu.setRoleKeys(Collections.emptyList());
        loginUserHolderMock.when(LoginUserHolder::get).thenReturn(lu);
        when(sysRoleMapper.selectMinDataScopeByUserId(5L)).thenReturn(null);

        assertFalse(dataScopeHelper.needSelfScope());
    }

    /** currentOperator 委托 AuditUserContext.currentUser()，保证过滤值与 createBy 填充值一致 */
    @Test
    void currentOperator_delegatesToAuditUserContext() {
        auditUserContextMock.when(AuditUserContext::currentUser).thenReturn("operator_alice");

        assertEquals("operator_alice", dataScopeHelper.currentOperator());
    }
}
