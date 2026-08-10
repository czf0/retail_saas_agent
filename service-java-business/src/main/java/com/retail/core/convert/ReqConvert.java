package com.retail.core.convert;

import java.util.List;

/**
 * 请求 → 实体 转换泛化基类(用于 create/update 场景).
 * <p>业务 Converter {@code extends ReqConvert<Req, 实体>} 绑定类型参数后,
 * MapStruct 编译期自动生成 {@link #toEntity} / {@link #toEntityList} 实现(同名字段自动映射).
 * <p>差异字段(默认值,trim,系统时间,计算字段等)由 Service 调用转化后手动 setter.
 * 部分更新场景 MapStruct 默认对 source=null 的字段不映射(符合 null 跳过语义),
 * 但空串("")不会被跳过,需按业务单独处理.
 *
 * @param <S> 源请求类型(Req)
 * @param <T> 目标实体类型(Entity)
 */
public interface ReqConvert<S, T> {

    /** 单个:请求 → 实体(同名字段自动拷贝,source 为 null 的字段不映射) */
    T toEntity(S source);

    /** 批量:MapStruct 自动生成循环调用 {@link #toEntity} 的实现 */
    List<T> toEntityList(List<S> sources);
}
