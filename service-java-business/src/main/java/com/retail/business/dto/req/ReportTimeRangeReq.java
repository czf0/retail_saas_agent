package com.retail.business.dto.req;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 报表通用查询参数基类.
 * <p>
 * 所有报表 Service 方法接收此参数,支持按时间范围 + 门店 + 分类 + 商品维度过滤.
 * <ul>
 *   <li>startDate / endDate:时间范围过滤,可空(空=不限)</li>
 *   <li>storeId:门店过滤,可空.门店白名单表(order_info/order_item 等)由拦截器自动按当前用户 storeId 隔离;
 *       此字段非空时用于对非白名单表(member/coupon_template 等)手动附加门店条件</li>
 *   <li>categoryId / productId:商品维度过滤,可空</li>
 * </ul>
 * <p>
 * <b>B-14 修复</b>:前端日期选择器传 {@code yyyy-MM-dd} 字符串(如 "2026-07-19"),
 * 而 {@code LocalDateTime} 默认只接受 ISO-8601 完整日期时间格式(如 "2026-07-19T00:00:00"),
 * 导致 {@code MethodArgumentNotValidException}.加 {@link DateTimeFormat} 注解显式指定格式,
 * Spring 会把日期字符串解析为当天 00:00:00 的 LocalDateTime.
 */
@Data
public class ReportTimeRangeReq {

    /** 查询起始时间(可空,空=不限起始时间;前端传 yyyy-MM-dd,解析为当天 00:00:00) */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime startDate;

    /** 查询结束时间(可空,空=不限结束时间;前端传 yyyy-MM-dd,解析为当天 00:00:00) */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime endDate;

    /** 门店ID(可空,用于非白名单表的手动门店过滤) */
    private Long storeId;

    /** 商品分类ID(可空,按分类过滤销售/库存数据) */
    private Long categoryId;

    /** 商品ID(可空,按商品过滤销售/库存数据) */
    private Long productId;
}
