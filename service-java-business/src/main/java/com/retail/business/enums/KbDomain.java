package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 知识库文档业务域枚举(严格对齐 Python paradigm_router._SCENARIO_PROFILE 域字段).
 * <p>RAG 检索时应用域过滤, 避免跨域噪声(如库存问题误召回促销文档):
 * <ul>
 *   <li>ORDER(1 订单域): 订单创建/支付/发货/状态查询, 订单修改/取消 SOP.</li>
 *   <li>INVENTORY(2 库存域): 库存查询/调整/调拨, 出入库流程, 盘点 SOP.</li>
 *   <li>SALES(3 销售域): 销售诊断案例, 销售话术, 加购/交叉销售推荐规则.</li>
 *   <li>PROMO(4 促销域): 满减 / 优惠券 / 限时秒杀政策, 叠加规则, 促销日历.</li>
 *   <li>MEMBER(5 会员域): 会员等级, 积分获取/兑换规则, 会员专属权益.</li>
 *   <li>SOP(6 SOP 域): 口径定义, 退换货政策, 盘点工作流, 开店/关店 checklist.</li>
 *   <li>CATEGORY_TREE(7 品类树): KnowledgeDocGenerator 自动生成的静态结构化数据; 全品类层级.</li>
 *   <li>PRODUCT_CATALOG(8 商品目录): KnowledgeDocGenerator 自动生成的静态结构化数据; SKU 列表含规格/价格.</li>
 *   <li>STORE_LIST(9 门店清单): KnowledgeDocGenerator 自动生成的静态结构化数据; 门店目录含地址/营业时间.</li>
 * </ul>
 */
public enum KbDomain implements BaseEnum {

    /** 订单业务域; 订单生命周期, 支付, 发货, 状态查询; 用户问题提及订单/发货/支付状态时 RAG 过滤命中. */
    ORDER(1, "订单域"),
    /** 库存业务域; 库存查询, 调整, 调拨, 出入库, 盘点; 库存/库存量相关问题时 RAG 过滤命中. */
    INVENTORY(2, "库存域"),
    /** 销售业务域; 诊断案例, 销售会话话术, 加购策略; 销售绩效 / 辅导类问题时 RAG 过滤命中. */
    SALES(3, "销售域"),
    /** 促销业务域; 优惠券, 满减, 限时秒杀政策及叠加规则; 促销 / 折扣类问题时 RAG 过滤命中. */
    PROMO(4, "促销域"),
    /** 会员业务域; 会员等级, 积分规则, 专属权益; 会员 / 忠诚度计划类问题时 RAG 过滤命中. */
    MEMBER(5, "会员域"),
    /** SOP / 标准作业流程域; 退换货, 盘点, 开店/关店流程; How-to / 工作流类问题时 RAG 过滤命中. */
    SOP(6, "SOP域"),
    /** 品类树静态结构化数据; KnowledgeDocGenerator 从 product_category 表自动生成; 不用于自然语言搜索. */
    CATEGORY_TREE(7, "品类树"),
    /** 商品目录静态结构化数据; KnowledgeDocGenerator 从 product/sku 表自动生成; 不用于自然语言搜索. */
    PRODUCT_CATALOG(8, "商品目录"),
    /** 门店清单静态结构化数据; KnowledgeDocGenerator 从 sys_store 表自动生成; 不用于自然语言搜索. */
    STORE_LIST(9, "门店清单");

    @EnumValue
    private final Integer code;
    private final String desc;

    KbDomain(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
