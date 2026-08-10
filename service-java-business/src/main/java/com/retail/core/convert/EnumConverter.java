package com.retail.core.convert;

import com.retail.core.enums.EnumUtil;
import com.retail.core.enums.BaseEnum;
import org.mapstruct.Mapper;
import org.mapstruct.TargetType;
import org.mapstruct.factory.Mappers;

/**
 * 枚举通用转换器:业务 Mapper 通过 {@code @Mapper(config = BaseMapStructConfig.class)} 间接引入
 * ({@link BaseMapStructConfig} 已 {@code uses = EnumConverter.class}),MapStruct 编译期据源/目标类型自动选用,
 * 零 @Mapping 完成枚举↔Integer 双向映射.
 * <ul>
 *   <li>{@link #toCode}:Entity → Resp 方向,{@link BaseEnum} → Integer;</li>
 *   <li>{@link #toEnum}:Req → Entity 方向,Integer → 枚举,内部调 {@link EnumUtil#fromCode} 校验,非法 code 抛 ParamException.</li>
 * </ul>
 * <p>禁用项:业务 Mapper 内不得再定义逐枚举 {@code default map} 方法(会令 MapStruct 优先选用具体方法,绕过通用校验);
 * create 场景状态由业务默认时,仍用 {@code @Mapping(target="status", ignore=true)}.
 */
@Mapper(componentModel = "spring")
public interface EnumConverter {

    // EnumConverter INSTANCE = Mappers.getMapper(EnumConverter.class);


    /** Entity → Resp:BaseEnum → Integer(MapStruct 据目标 Integer 自动选用,零 @Mapping) */
    default Integer toCode(BaseEnum e) {
        return e == null ? null : e.getCode();
    }

    /**
     * Req → Entity:Integer → 枚举(@TargetType 自动绑定目标枚举类型;内部调 {@link EnumUtil#fromCode} 校验,非法 code 抛 ParamException).
     *
     * @param code      请求侧 Integer code
     * @param enumClass 目标枚举类型(由 MapStruct @TargetType 自动注入)
     * @param <E>       枚举类型,需实现 {@link BaseEnum}
     * @return 对应枚举实例;code 为 null 返回 null
     */
    default <E extends Enum<E> & BaseEnum> E toEnum(Integer code, @TargetType Class<E> enumClass) {
        return EnumUtil.fromCode(enumClass, code);
    }
}
