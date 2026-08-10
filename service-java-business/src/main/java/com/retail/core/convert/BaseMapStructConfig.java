package com.retail.core.convert;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct 全局配置:业务 Converter 通过 {@code @Mapper(config = BaseMapStructConfig.class)} 引用.
 * <ul>
 *   <li>{@code componentModel = "spring"}:生成的 MapperImpl 注册为 Spring Bean,可构造注入;</li>
 *   <li>{@code uses = EnumConverter.class}:所有业务 Mapper 自动引入枚举通用转换器,
 *       零 @Mapping 完成枚举↔Integer 双向映射(Entity→Resp 自动 toCode;Req→Entity 自动 toEnum 并校验非法 code);</li>
 *   <li>{@code unmappedSourcePolicy = IGNORE}:实体审计字段(deleted/tenantId/createBy/updateBy/deleteAt/deleteBy)
 *       仅存在于源端,resp 无对应字段,全局忽略,无需每个 Converter 重复声明;</li>
 *   <li>{@code unmappedTargetPolicy = IGNORE}:目标端「有此字段,源无对应」静默跳过
 *       (如 belowSafety 等计算字段),由 Service 在转化后手动 setter,不再产生告警,无需 {@code @Mapping(ignore)}.</li>
 * </ul>
 */
@MapperConfig(
        componentModel = "spring",
        uses = EnumConverter.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BaseMapStructConfig {
}
