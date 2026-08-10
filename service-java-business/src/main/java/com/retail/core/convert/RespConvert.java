package com.retail.core.convert;

import java.util.List;


/**
 * 实体 → 响应 DTO 转换泛化基类.
 * <p>业务 Converter {@code extends RespConvert<实体, Resp>} 绑定具体类型参数后,
 * MapStruct 编译期自动生成 {@link #toResp} / {@link #toRespList} 实现(同名字段自动映射,审计字段由全局配置忽略).
 * 差异字段(目标有,源无或需计算)由 Service 调用转化后手动 setter.
 *
 * @param <S> 源实体类型
 * @param <T> 目标响应 DTO 类型
 */
public interface RespConvert<S, T> {


    /** 单个:源实体 → 响应 DTO(同名字段自动拷贝) */
    T toResp(S source);

    /** 批量:MapStruct 自动生成循环调用 {@link #toResp} 的实现 */
    List<T> toRespList(List<S> sources);
}
