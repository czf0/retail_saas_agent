package com.retail.core.enums;

/**
 * 枚举统一接口:所有业务/权限枚举({@code ErrCodeEnum} 等错误码体系除外)均实现本接口.
 * <ul>
 *   <li>{@link #getCode()}:DB 存储值,统一为 Integer;枚举字段标 {@code @EnumValue}(MyBatis-Plus 据此 enum↔DB 自动转换)
 *       + {@code @JsonValue}(Jackson 序列化兜底,防实体直接暴露时序列化为 name);</li>
 *   <li>{@link #getDesc()}:中文描述,供字典端点与前端展示,替代原 {@code description(String)} 静态方法.</li>
 * </ul>
 * <p>设计目标:消除 {@code BaseEnum<T>} 泛型复杂度,所有枚举 code 强制 Integer;
 * 通用转换器 {@code EnumConverter} 据本接口自动完成枚举↔Integer 双向映射 + 校验.
 */
public interface BaseEnum {

    /** DB 存储值(统一 Integer),枚举字段标 @EnumValue */
    Integer getCode();

    /** 中文描述,供字典端点与前端展示 */
    String getDesc();
}
