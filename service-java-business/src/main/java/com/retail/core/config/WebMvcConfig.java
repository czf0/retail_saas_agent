package com.retail.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring MVC 全局格式化与转换配置.
 * <p>
 * <b>B-14 修复</b>:前端报表日期选择器传 {@code yyyy-MM-dd} 字符串(如 "2026-07-19"),
 * 而 {@code ReportTimeRangeReq.startDate/endDate} 为 {@link LocalDateTime} 类型.
 * <p>
 * 原仅靠 {@code @DateTimeFormat(pattern = "yyyy-MM-dd")} 注解无效——
 * 因为 {@code DateTimeFormatter.ofPattern("yyyy-MM-dd")} 只能解析为 {@link LocalDate},
 * 无法直接填充 {@code LocalDateTime} 的时间分量,触发
 * {@code MethodArgumentNotValidException}.
 * <p>
 * 此处注册全局 {@link Converter},对短日期字符串(长度 ≤ 10)自动补 {@code 00:00:00};
 * 对长日期时间字符串(含 T 或空格)按 ISO-8601 解析.不影响 @RequestBody 走 Jackson 的路径.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 日期格式(前端日期选择器输出) */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日期时间格式(含时间分量的完整格式) */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // String → LocalDateTime:支持 "yyyy-MM-dd"(补 00:00:00)与 "yyyy-MM-dd HH:mm:ss" 两种格式
        registry.addConverter(new StringToLocalDateTimeConverter());
    }

    /**
     * 字符串到 LocalDateTime 的全局转换器.
     * <ul>
     *   <li>空字符串 → null</li>
     *   <li>长度 ≤ 10(纯日期)→ 解析为 {@link LocalDate#atStartOfDay}</li>
     *   <li>含 T 或空格(日期时间)→ 按完整格式解析</li>
     * </ul>
     */
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        @Nullable
        public LocalDateTime convert(@Nullable String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            String text = source.trim();
            try {
                // 纯日期格式(如 "2026-07-19")→ 补 00:00:00
                if (text.length() <= 10) {
                    return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
                }
                // 含时间分量(如 "2026-07-19 00:00:00" 或 "2026-07-19T00:00:00")
                String normalized = text.replace('T', ' ');
                return LocalDateTime.parse(normalized, DATETIME_FORMATTER);
            } catch (Exception e) {
                // 转换失败时返回 null,让后续 @Valid 校验或业务逻辑处理空值
                return null;
            }
        }
    }
}
