package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表页行项(管理后台/会员中心订单列表,返回 20/页;前端点击行进入详情查询完整 OrderResp).
 * <p>统计辅助字段如 itemCount/SKU 数为列表页 SQL 聚合计算,详情查询 OrderResp 不重复返回 itemCount 字段以节省带宽.
 */
@Data
public class OrderListItemResp {

    private Long id;

    /** 订单业务号(YYYYMMDD + 流水;列表搜索关键词字段). */
    private String orderNo;

    /** 下单会员外键;散客单 = NULL. */
    private Long memberId;

    /** 下单会员姓名/昵称快照冗余. */
    private String memberName;

    /** 订单类型枚举:1=普通 2=赠品 3=积分兑换 4=预售;见 OrderTypeEnum. */
    private Integer orderType;

    /** 订单状态码:1=PENDING 2=PAID 3=SHIPPED 4=COMPLETED 5=CANCELED 6=PARTIAL_REFUND;见 OrderStatusEnum. */
    private Integer status;

    /** 状态中文描述(Service 层枚举映射;前端直接展示). */
    private String statusDesc;

    /** 实付金额(单位: 元,精度: 分;pay_amount 订单主表). */
    private BigDecimal payAmount;

    /** 支付方式枚举:1=微信 2=支付宝 3=余额 4=现金;见 PayTypeEnum. */
    private Integer payType;

    /** 下单渠道:1=H5 2=小程序 3=APP 4=PC后台 5=POS;见 OrderChannelEnum. */
    private Integer channel;

    /** 门店外键(NULL=租户级跨门店通用订单);列表筛选 store_id 字段. */
    private Long storeId;

    /** 门店名称冗余(Service 层批量回填;消除前端 "门店 #id" 数据孤岛). */
    private String storeName;

    /** 下单时间(order_info.order_time;列表默认按此字段 DESC 倒序分页). */
    private LocalDateTime orderTime;

    /** 计算字段(SQL COUNT(order_item.id) 内嵌):该订单商品 SKU 种类数;用于列表快速预览"买了几样". */
    private Integer itemCount;
}
