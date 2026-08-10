package com.retail.core.enums;

import com.retail.core.exception.ParamException;

/**
 * 枚举通用转换工具:被通用转换器 {@code EnumConverter} 与 Service 共用,保证零静默吞错.
 * <p>转换铁律:
 * <ul>
 *   <li>Req → Entity(code → 枚举):调 {@link #fromCode},非法 code 抛 {@link ParamException};</li>
 *   <li>Entity → Resp(枚举 → code):原则上交给 {@code EnumConverter.toCode} 自动,本方法仅作兜底.</li>
 * </ul>
 */
public final class EnumUtil {

    private EnumUtil() {
    }

    /**
     * code → 枚举,非法 code 抛 ParamException(绝不静默吞错).
     * <p>Req → Entity 方向:被 {@code EnumConverter.toEnum} 与 Service 显式校验共用.
     *
     * @param enumClass 目标枚举类型
     * @param code      DB 存储值(Integer)
     * @param <E>       枚举类型,需实现 {@link BaseEnum}
     * @return 对应枚举实例;code 为 null 返回 null
     * @throws ParamException code 非法(不在枚举定义内)
     */
    public static <E extends Enum<E> & BaseEnum> E fromCode(Class<E> enumClass, Integer code) {
        if (code == null) {
            return null;
        }
        for (E e : enumClass.getEnumConstants()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        throw new ParamException("非法" + enumClass.getSimpleName() + "值: " + code);
    }

    /**
     * 校验 code 是否在枚举定义内,非法 code 抛 ParamException(绝不静默吞错).
     *
     * @param enumClass 目标枚举类型
     * @param code      DB 存储值(Integer)
     * @param allowNull 是否允许 null 值
     * @param message   校验失败提示信息
     * @throws ParamException code 非法(不在枚举定义内)
     */
    public static <E extends Enum<E> & BaseEnum> void checkCode(Class<E> enumClass, Integer code, boolean allowNull, String message) {
        if (code == null && !allowNull) {
            throw new ParamException(message);
        }
        for (E e : enumClass.getEnumConstants()) {
            if (e.getCode().equals(code)) {
                return;
            }
        }
        throw new ParamException(message);
    }


    /**
     * 枚举 → code,供不便用 MapStruct 的场景兜底;原则上 Entity → Resp 交 {@code EnumConverter.toCode} 自动.
     *
     * @param e 枚举实例
     * @return code;e 为 null 返回 null
     */
    public static Integer toCode(BaseEnum e) {
        return e == null ? null : e.getCode();
    }

    /**
     * 生成枚举所有 code:desc 列表的紧凑字符串(用于 JSON Schema description 的枚举值提示).
     * <p>格式 {@code code1=desc1|code2=desc2|...},便于 LLM 在读取 schema description 时直接看到合法整数码.
     *
     * @param enumClass 目标枚举类
     * @param <E>       枚举类型
     * @return code:desc 列表;类无枚举常量时返回空串
     */
    public static <E extends Enum<E> & BaseEnum> String codeDescList(Class<E> enumClass) {
        StringBuilder sb = new StringBuilder();
        for (E e : enumClass.getEnumConstants()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getCode()).append('=').append(e.getDesc());
        }
        return sb.toString();
    }
}
