package com.retail.rbac.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.retail.rbac.convert.UserConvert;
import com.retail.rbac.dto.req.AssignRoleReq;
import com.retail.rbac.dto.req.ResetPwdReq;
import com.retail.rbac.dto.req.UserCreateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.UserResp;
import com.retail.rbac.entity.SysUser;
import com.retail.rbac.entity.SysUserRole;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysUserMapper;
import com.retail.rbac.mapper.SysUserRoleMapper;
import com.retail.rbac.satoken.RbacCacheManager;
import com.retail.core.exception.AuthException;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统用户服务单元测试。
 * <p>
 * 覆盖用户创建（密码加密 / 角色分配 / 重名校验）、删除（关系清理 / 缓存失效）、
 * 分配角色、重置密码、跨租户操作拦截等核心流程。
 * <p>
 * 通过 {@link ReflectionTestUtils} 将 mock 的 {@link SysUserMapper} 注入到
 * 父类 {@code ServiceImpl} 的 {@code baseMapper} 字段（该字段由 Spring @Autowired 字段注入，
 * 构造函数未声明），并使用 {@link MockedStatic} 隔离 {@link LoginUserHolder} 静态方法。
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private SysUserMapper sysUserMapper;
    private SysUserRoleMapper sysUserRoleMapper;
    private UserConvert userConvert;
    private RbacCacheManager cacheManager;
    private SysUserServiceImpl sysUserService;

    private MockedStatic<LoginUserHolder> loginUserHolderMock;

    /** 测试用明文密码与对应 BCrypt 哈希 */
    private static final String RAW_PASSWORD = "User@123";

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        sysUserRoleMapper = mock(SysUserRoleMapper.class);
        userConvert = mock(UserConvert.class);
        cacheManager = mock(RbacCacheManager.class);
        sysUserService = new SysUserServiceImpl(userConvert, sysUserRoleMapper, cacheManager);
        // 注入父类 ServiceImpl 的 baseMapper 字段（构造函数未声明，由 Spring 字段注入）
        ReflectionTestUtils.setField(sysUserService, "baseMapper", sysUserMapper);
        // 隔离 LoginUserHolder 静态方法（currentTenantId 用于租户归属校验）
        loginUserHolderMock = Mockito.mockStatic(LoginUserHolder.class);
    }

    @AfterEach
    void tearDown() {
        loginUserHolderMock.close();
    }

    /** 构造一个已存在的租户用户（用于 loadAndCheck 返回） */
    private SysUser buildExistingUser() {
        SysUser u = new SysUser();
        u.setId(100L);
        u.setUsername("existuser");
        u.setTenantId(1001L);
        u.setStoreId(2001L);
        u.setStatus(SysStatus.ENABLED);
        u.setPasswordHash("$2a$10$somehash");
        return u;
    }

    /** 创建用户成功：密码被 BCrypt 加密、实体被保存、roleIds 回填到响应 */
    @Test
    void createUser_success_hashesPasswordAndSavesEntity() {
        when(sysUserMapper.selectByUsername(any())).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(10L);
            return 1;
        });
        when(userConvert.toEntity(any())).thenReturn(new SysUser());
        when(userConvert.toResp(any())).thenAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            UserResp r = new UserResp();
            r.setId(u.getId());
            r.setUsername(u.getUsername());
            return r;
        });

        UserCreateReq req = new UserCreateReq();
        req.setUsername("newuser");
        req.setPassword(RAW_PASSWORD);
        req.setNickName("新用户");

        UserResp resp = sysUserService.createUser(req);

        assertNotNull(resp);
        assertEquals(10L, resp.getId());
        // 验证密码已被 BCrypt 加密（不等于明文）
        assertNotNull(resp);
        // 通过 ArgumentCaptor 思路验证：捕获 insert 调用的实体，校验密码哈希
        org.mockito.ArgumentCaptor<SysUser> captor = org.mockito.ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertNotEquals(RAW_PASSWORD, saved.getPasswordHash());
        assertTrue(BCrypt.checkpw(RAW_PASSWORD, saved.getPasswordHash()), "存储的哈希应能校验通过明文密码");
        assertEquals("newuser", saved.getUsername());
        assertEquals("新用户", saved.getNickName());
    }

    /** 创建用户时分配角色：先删旧关系再批量插入，并递增角色缓存版本 */
    @Test
    void createUser_withRoles_assignsRolesAndBumpsVersion() {
        when(sysUserMapper.selectByUsername(any())).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(20L);
            return 1;
        });
        when(userConvert.toEntity(any())).thenReturn(new SysUser());
        when(userConvert.toResp(any())).thenReturn(new UserResp());

        UserCreateReq req = new UserCreateReq();
        req.setUsername("roleuser");
        req.setPassword(RAW_PASSWORD);
        req.setRoleIds(List.of(1L, 2L, 3L));

        UserResp resp = sysUserService.createUser(req);

        assertNotNull(resp);
        assertEquals(List.of(1L, 2L, 3L), resp.getRoleIds());
        // 先删旧关系
        verify(sysUserRoleMapper).deleteByUserId(20L);
        // 批量插入 3 条关系
        verify(sysUserRoleMapper, times(3)).insert(any(SysUserRole.class));
        // 缓存版本递增
        verify(cacheManager).bumpRoleVersion();
    }

    /** 用户名已存在：抛 ParamException，不插入数据 */
    @Test
    void createUser_duplicateUsername_throwsParamException() {
        SysUser existing = new SysUser();
        existing.setUsername("dupuser");
        when(sysUserMapper.selectByUsername("dupuser")).thenReturn(existing);

        UserCreateReq req = new UserCreateReq();
        req.setUsername("dupuser");
        req.setPassword(RAW_PASSWORD);

        ParamException ex = assertThrows(ParamException.class, () -> sysUserService.createUser(req));
        assertEquals("用户名已存在: dupuser", ex.getMsg());
        verify(sysUserMapper, never()).insert(any());
        verify(cacheManager, never()).bumpRoleVersion();
    }

    /** 用户名为空：抛 ParamException，不查库 */
    @Test
    void createUser_blankUsername_throwsParamException() {
        UserCreateReq req = new UserCreateReq();
        req.setUsername("");
        req.setPassword(RAW_PASSWORD);

        ParamException ex = assertThrows(ParamException.class, () -> sysUserService.createUser(req));
        assertEquals("用户名不能为空", ex.getMsg());
        verify(sysUserMapper, never()).selectByUsername(any());
    }

    /** 密码为空：抛 ParamException */
    @Test
    void createUser_blankPassword_throwsParamException() {
        UserCreateReq req = new UserCreateReq();
        req.setUsername("nopass");
        req.setPassword("");

        ParamException ex = assertThrows(ParamException.class, () -> sysUserService.createUser(req));
        assertEquals("密码不能为空", ex.getMsg());
    }

    /** 删除用户：清理用户-角色关系并递增缓存版本 */
    @Test
    void deleteUser_success_cleansUpRolesAndBumpsVersion() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        // 平台管理员（currentTenantId=null）可跨租户删除
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(null);
        // BaseServiceImpl.removeById 内部调用 baseMapper.update(entity, wrapper)
        when(sysUserMapper.update(any(), any())).thenReturn(1);

        OperationResultResp resp = sysUserService.deleteUser(100L);

        assertTrue(resp.getSuccess());
        assertEquals("用户删除成功", resp.getMessage());
        verify(sysUserRoleMapper).deleteByUserId(100L);
        verify(cacheManager).bumpRoleVersion();
    }

    /** 跨租户删除用户：抛 AuthException */
    @Test
    void deleteUser_crossTenant_throwsAuthException() {
        SysUser user = buildExistingUser();
        user.setTenantId(9999L); // 其他租户的用户
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        // 当前登录用户属于租户 1001
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);

        AuthException ex = assertThrows(AuthException.class, () -> sysUserService.deleteUser(100L));
        assertEquals("无权操作其他租户用户", ex.getMsg());
        verify(sysUserRoleMapper, never()).deleteByUserId(anyLong());
        verify(cacheManager, never()).bumpRoleVersion();
    }

    /** 分配角色：先删后插并递增缓存版本 */
    @Test
    void assignRoles_replacesAndBumpsVersion() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);

        AssignRoleReq req = new AssignRoleReq();
        req.setRoleIds(List.of(5L, 6L));

        sysUserService.assignRoles(100L, req);

        verify(sysUserRoleMapper).deleteByUserId(100L);
        verify(sysUserRoleMapper, times(2)).insert(any(SysUserRole.class));
        verify(cacheManager).bumpRoleVersion();
    }

    /** 分配角色：roleIds 为 null 抛 ParamException */
    @Test
    void assignRoles_nullRoleIds_throwsParamException() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);

        AssignRoleReq req = new AssignRoleReq();
        req.setRoleIds(null);

        ParamException ex = assertThrows(ParamException.class, () -> sysUserService.assignRoles(100L, req));
        assertEquals("角色ID列表不能为空", ex.getMsg());
        verify(sysUserRoleMapper, never()).deleteByUserId(anyLong());
    }

    /** 重置密码：使用 BCrypt 加密新密码并更新 */
    @Test
    void resetPassword_hashesAndUpdates() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);
        when(sysUserMapper.updateById(any())).thenReturn(1);

        ResetPwdReq req = new ResetPwdReq();
        req.setPassword("NewPass@456");

        sysUserService.resetPassword(100L, req);

        // 验证密码哈希已被更新（通过捕获 updateById 入参）
        org.mockito.ArgumentCaptor<SysUser> captor = org.mockito.ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        SysUser updated = captor.getValue();
        assertTrue(BCrypt.checkpw("NewPass@456", updated.getPasswordHash()),
                "更新后的哈希应能校验通过新明文密码");
    }

    /** 重置密码：新密码为空抛 ParamException */
    @Test
    void resetPassword_blankPassword_throwsParamException() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);

        ResetPwdReq req = new ResetPwdReq();
        req.setPassword("");

        ParamException ex = assertThrows(ParamException.class, () -> sysUserService.resetPassword(100L, req));
        assertEquals("新密码不能为空", ex.getMsg());
        verify(sysUserMapper, never()).updateById(any());
    }

    /** 查询单个用户：成功返回 UserResp 并回填 roleIds */
    @Test
    void getUser_success_returnsRespWithRoleIds() {
        SysUser user = buildExistingUser();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        loginUserHolderMock.when(LoginUserHolder::currentTenantId).thenReturn(1001L);
        when(userConvert.toResp(user)).thenAnswer(inv -> {
            UserResp r = new UserResp();
            r.setId(100L);
            r.setUsername("existuser");
            return r;
        });
        when(sysUserRoleMapper.selectRoleIdsByUserId(100L)).thenReturn(List.of(7L, 8L));

        UserResp resp = sysUserService.getUser(100L);

        assertNotNull(resp);
        assertEquals(100L, resp.getId());
        assertEquals(List.of(7L, 8L), resp.getRoleIds());
    }
}
