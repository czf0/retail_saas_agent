package com.retail.core.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.retail.core.context.AuditUserContext;
import com.retail.core.security.LoginUserHolder;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器.
 * <p>
 * insert 填充:createdAt,createBy,updatedAt,updateBy,tenantId,storeId;
 * update 填充:updatedAt,updateBy.
 * <p>
 * 采用 strictInsertFill / strictUpdateFill,仅当字段当前为 null 时填充,
 * 不会覆盖业务代码已显式赋值的字段.
 * <p>
 * <b>多租户/门店自动植入</b>:insert 时从 {@link LoginUserHolder} 取当前登录用户的
 * tenantId / storeId 填充.对 ignore-tables 实体(sys_user/sys_role/sys_store 等,拦截器不注入)
 * 由本处理器植入;对拦截器表(product_info 等)与拦截器同值填充不冲突.
 * 平台管理员(tenantId=null)跳过填充,保持 null.
 * <p>
 * 注意:逻辑删除(removeById)不会触发 updateFill,因此 deleteAt / deleteBy
 * 不在此填充,由 {@link com.retail.core.service.BaseServiceImpl#removeById} 显式填充.
 * 实体不存在某字段时(如快照表无 updatedAt,关系表无 tenantId),strict*Fill 会自动跳过,不报错.
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String user = AuditUserContext.currentUser();
        LocalDateTime now = LocalDateTime.now();
        // 创建时间/创建人
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        // 首次写入时更新时间/更新人等同于创建
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        if (StrUtil.isNotBlank(user)) {
            this.strictInsertFill(metaObject, "createBy", String.class, user);
            this.strictInsertFill(metaObject, "updateBy", String.class, user);
        }
        // 多租户/门店自动植入:从登录上下文取值,非空时填充(平台管理员 → null,跳过)
        Long tenantId = LoginUserHolder.currentTenantId();
        if (tenantId != null) {
            this.strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        }
        Long storeId = LoginUserHolder.currentStoreId();
        if (storeId != null) {
            this.strictInsertFill(metaObject, "storeId", Long.class, storeId);
        }
        // DEBUG 级:insert 自动填充元信息(高频调用,仅 dev 联调可见)
        if (log.isDebugEnabled()) {
            log.debug("insertFill entity={} createBy={} tenantId={} storeId={}",
                    metaObject.getOriginalObject().getClass().getSimpleName(), user, tenantId, storeId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String user = AuditUserContext.currentUser();
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        if (StrUtil.isNotBlank(user)) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, user);
        }
        // DEBUG 级:update 自动填充元信息(高频调用,仅 dev 联调可见)
        if (log.isDebugEnabled()) {
            log.debug("updateFill entity={} updateBy={}",
                    metaObject.getOriginalObject().getClass().getSimpleName(), user);
        }
    }
}
