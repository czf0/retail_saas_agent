package com.retail.rbac.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.service.BaseServiceImpl;
import com.retail.rbac.convert.UserConvert;
import com.retail.rbac.dto.req.AssignRoleReq;
import com.retail.rbac.dto.req.ResetPwdReq;
import com.retail.rbac.dto.req.UserCreateReq;
import com.retail.rbac.dto.req.UserQueryReq;
import com.retail.rbac.dto.req.UserUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.UserResp;
import com.retail.rbac.entity.SysUser;
import com.retail.rbac.entity.SysUserRole;
import com.retail.rbac.enums.SysStatus;
import com.retail.rbac.mapper.SysUserMapper;
import com.retail.rbac.mapper.SysUserRoleMapper;
import com.retail.rbac.satoken.RbacCacheManager;
import com.retail.core.security.LoginUserHolder;
import com.retail.rbac.service.SysUserService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.AuthException;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户服务实现.
 * <p>sys_user 在 ignore-tables 中,查询在 Service 层手动按 tenant_id 过滤:
 * <ul>
 *   <li>租户管理员:仅操作本租户用户(tenant_id = 当前租户);</li>
 *   <li>平台管理员:可跨租户操作(tenantId=null,按 req.tenantId 筛选).</li>
 * </ul>
 * tenant_id / store_id 由 MetaObjectHandler 自动植入;passwordHash 由 BCrypt 加密;roleIds 写 sys_user_role.
 */
@Slf4j
@Service
public class SysUserServiceImpl extends BaseServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final UserConvert userConvert;
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 权限缓存管理:角色分配变更时递增版本号使 Session 缓存失效 */
    private final RbacCacheManager cacheManager;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public SysUserServiceImpl(UserConvert userConvert, SysUserRoleMapper sysUserRoleMapper,
                               RbacCacheManager cacheManager) {
        this.userConvert = userConvert;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    public PageResp<UserResp> listUsers(UserQueryReq req) {
        if (req == null) {
            req = new UserQueryReq();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 租户隔离:租户管理员强制本租户;平台管理员按 req.tenantId 筛选(可选)
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null) {
            wrapper.eq(SysUser::getTenantId, currentTenant);
        } else if (req.getTenantId() != null) {
            wrapper.eq(SysUser::getTenantId, req.getTenantId());
        }
        if (req.getStoreId() != null) {
            wrapper.eq(SysUser::getStoreId, req.getStoreId());
        }
        if (StrUtil.isNotBlank(req.getUsername())) {
            wrapper.like(SysUser::getUsername, req.getUsername().trim());
        }
        if (StrUtil.isNotBlank(req.getPhone())) {
            wrapper.like(SysUser::getPhone, req.getPhone().trim());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(SysUser::getId);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<SysUser> page = PageContextHolder.get();
        IPage<SysUser> result = baseMapper.selectPage(page, wrapper);

        List<UserResp> items = userConvert.toRespList(result.getRecords());
        // 回填 roleIds(差异字段,需查关系表)
        items.forEach(this::fillRoleIds);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public UserResp getUser(Long id) {
        SysUser user = loadAndCheck(id);
        UserResp resp = userConvert.toResp(user);
        fillRoleIds(resp);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp createUser(UserCreateReq req) {
        if (StrUtil.isBlank(req.getUsername())) {
            throw new ParamException("用户名不能为空");
        }
        if (StrUtil.isBlank(req.getPassword())) {
            throw new ParamException("密码不能为空");
        }
        // 校验用户名唯一
        SysUser exist = baseMapper.selectByUsername(req.getUsername().trim());
        if (exist != null) {
            throw new ParamException("用户名已存在: " + req.getUsername());
        }
        // 同名字段由 UserConvert 自动映射(req→entity)
        SysUser entity = userConvert.toEntity(req);
        entity.setUsername(req.getUsername().trim());                                    // trim 差异
        entity.setPasswordHash(BCrypt.hashpw(req.getPassword()));                         // 加密差异
        entity.setNickName(StrUtil.isBlank(req.getNickName()) ? req.getUsername() : req.getNickName().trim());
        entity.setStatus(SysStatus.ENABLED);                                              // status 由 Service 赋默认值启用(铁律6:CreateReq 禁 status 字段)
        entity.setGender(req.getGender() == null ? 0 : req.getGender());                 // 默认值差异
        // tenant_id / store_id 由 MetaObjectHandler 自动植入(平台管理员跨租户创建时取 req 显式值)
        this.save(entity);

        // 分配角色(若指定)
        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            replaceRoles(entity.getId(), req.getRoleIds());
            cacheManager.bumpRoleVersion();
        }
        log.info("创建用户 id={} username={} nickName={} tenantId={} storeId={} status={} roleIds={}",
                entity.getId(), entity.getUsername(), entity.getNickName(),
                entity.getTenantId(), entity.getStoreId(), entity.getStatus(), req.getRoleIds());

        UserResp resp = userConvert.toResp(entity);
        resp.setRoleIds(req.getRoleIds());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp updateUser(Long id, UserUpdateReq req) {
        SysUser entity = loadAndCheck(id);
        boolean changed = false;
        if (StrUtil.isNotBlank(req.getNickName())) {
            entity.setNickName(req.getNickName().trim());
            changed = true;
        }
        if (req.getEmail() != null) {
            entity.setEmail(req.getEmail());
            changed = true;
        }
        if (req.getPhone() != null) {
            entity.setPhone(req.getPhone());
            changed = true;
        }
        if (req.getGender() != null) {
            entity.setGender(req.getGender());
            changed = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(SysStatus.class, req.getStatus()));
            changed = true;
        }
        if (req.getRemark() != null) {
            entity.setRemark(req.getRemark());
            changed = true;
        }
        if (req.getStoreId() != null) {
            entity.setStoreId(req.getStoreId());
            changed = true;
        }
        if (changed) {
            this.updateById(entity);
        }
        // 重新分配角色(非 null 才更新)
        if (req.getRoleIds() != null) {
            replaceRoles(id, req.getRoleIds());
            cacheManager.bumpRoleVersion();
        }
        log.info("更新用户 id={} changed={} nickName={} status={} storeId={} roleIds={}",
                id, changed, req.getNickName(), req.getStatus(), req.getStoreId(), req.getRoleIds());

        UserResp resp = userConvert.toResp(entity);
        fillRoleIds(resp);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationResultResp deleteUser(Long id) {
        SysUser user = loadAndCheck(id);
        this.removeById(user.getId());
        // 清理用户-角色关系
        sysUserRoleMapper.deleteByUserId(id);
        cacheManager.bumpRoleVersion();
        log.info("删除用户 id={} username={} tenantId={}", id, user.getUsername(), user.getTenantId());
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("用户删除成功");
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, AssignRoleReq req) {
        loadAndCheck(id);
        if (req == null || req.getRoleIds() == null) {
            throw new ParamException("角色ID列表不能为空");
        }
        replaceRoles(id, req.getRoleIds());
        cacheManager.bumpRoleVersion();
        log.info("分配用户角色 userId={} roleIds={}", id, req.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPwdReq req) {
        SysUser user = loadAndCheck(id);
        if (req == null || StrUtil.isBlank(req.getPassword())) {
            throw new ParamException("新密码不能为空");
        }
        user.setPasswordHash(BCrypt.hashpw(req.getPassword()));
        this.updateById(user);
        // 密码重置为敏感操作,记录 WARN 级日志(不记录密码明文)
        log.warn("重置用户密码 id={} username={}", id, user.getUsername());
    }

    /**
     * 加载用户并校验租户归属(租户管理员仅能操作本租户用户).
     */
    private SysUser loadAndCheck(Long id) {
        SysUser user = baseMapper.selectById(id);
        if (user == null) {
            throw new ParamException("用户不存在: " + id);
        }
        Long currentTenant = LoginUserHolder.effectiveTenantId();
        if (currentTenant != null && !currentTenant.equals(user.getTenantId())) {
            throw new AuthException("无权操作其他租户用户");
        }
        return user;
    }

    /** 全量覆盖用户角色:先删旧关系,再批量插入 */
    private void replaceRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    /** 回填 UserResp.roleIds(查关系表) */
    private void fillRoleIds(UserResp resp) {
        resp.setRoleIds(sysUserRoleMapper.selectRoleIdsByUserId(resp.getId()));
    }
}
