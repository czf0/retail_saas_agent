package com.retail.core.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.retail.core.context.AuditUserContext;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务 Service 通用基类.
 * <p>
 * 在 MyBatis-Plus {@link ServiceImpl} 基础上增强逻辑删除:删除时同步填充 deleteAt / deleteBy 审计字段.
 * <p>
 * 背景:MP 原生 {@code removeById(id)} 逻辑删除仅生成 {@code UPDATE SET deleted=1},
 * 不会触发 {@link com.baomidou.mybatisplus.core.handlers.MetaObjectHandler},
 * 故 deleteAt / deleteBy 需在此显式填充.
 * <p>
 * 实现采用单步 UPDATE:匹配未删除记录(deleted=0),同时设置 deleted=1 + delete_at + delete_by.
 * 不调用 {@code super.removeById(id)} 以避免依赖 MP 的 {@code TableInfo} 初始化(纯 Mockito 单元测试环境下
 * TableInfo 为 null 会导致 NullPointer),使基类在测试环境下也能正常 mock.
 * <p>
 * 使用约束:继承本类的实体表必须同时具备 deleted,delete_at,delete_by 三列(即支持逻辑删除的实体).
 * 物理删除的表(如 flow_config,统计快照表)不应继承本类,请直接继承 {@link ServiceImpl}.
 * <p>
 * 事务说明:本方法标注 {@link Transactional},但经同类 {@code this.removeById} 自调用时注解会被绕过;
 * 因此调用方(外层删除方法)应保证已开启事务,以确保操作原子性.
 */
public class BaseServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        String user = AuditUserContext.currentUser();
        // 逻辑删除:匹配未删除记录(deleted=0),单步设置 deleted=1 + 填充审计字段.
        // 注意:deleted 字段被 @TableLogic 标记,使用常规 .set("deleted", 1) 会被 MyBatis-Plus
        // 的 LogicSqlInjector 从 SET 子句剥离(视为框架托管字段,禁止业务层手动 SET),
        // 导致 deleted 列保持原值 0,delete_at/delete_by 反而被更新.改用 .setSql("deleted=1")
        // 直接拼接原始 SQL 片段,绕过 LogicSqlInjector 的字段剥离逻辑.
        // 参考文档:https://baomidou.com/guides/logic-delete/
        return this.update(new UpdateWrapper<T>()
                .eq("id", id)
                .eq("deleted", 0)
                .setSql("deleted=1")
                .set("delete_at", LocalDateTime.now())
                .set("delete_by", user));
    }
}
