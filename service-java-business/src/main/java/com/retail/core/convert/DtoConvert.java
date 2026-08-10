package com.retail.core.convert;

import java.util.List;

/**
 * 实体 → 非响应 DTO 转换泛化基类(如内部/Agent DTO).
 * <p>用法同 {@link RespConvert}:业务 Converter {@code extends DtoConvert<实体, DTO>} 即可获得
 * {@link #toDto} / {@link #toDtoList} 的编译期生成实现.
 * 当前业务转换均为 entity→Resp,本基类为非 Resp 场景预留.
 *
 * @param <S> 源实体类型
 * @param <T> 目标 DTO 类型
 */
public interface DtoConvert<S, T> {

    /** 单个:源实体 → DTO(同名字段自动拷贝) */
    T toDto(S source);

    /** 批量:MapStruct 自动生成循环调用 {@link #toDto} 的实现 */
    List<T> toDtoList(List<S> sources);
}
