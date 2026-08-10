package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDate;

/**
 * 知识文档创建请求(知识库管理 -> 新增文档/Agent 知识入库).
 * <p>对应 Controller 路由: POST /api/v1/knowledge/docs; status 字段 Service 层赋默认值(KbDocStatus.DRAFT=1, 铁律 6),
 * CreateReq 不承载 status.
 * <p>status 默认 DRAFT(草稿), 需调发布接口才同步到 Python 向量库; 校验在 Service 层手动执行.
 */
@Data
public class KnowledgeDocCreateReq {

    private String title;

    /** KbDomain 枚举 code: 1=ORDER 订单域 2=INVENTORY 库存域 3=SALES 销售域 4=PROMO 促销域 5=MEMBER 会员域 6=SOP SOP域 7=CATEGORY_TREE 品类树 8=PRODUCT_CATALOG 商品目录 9=STORE_LIST 门店清单. */
    private Integer domain;

    /** 可见角色 id, 对应 sys_role.id; NULL=全员可见; 非空=仅该角色可见. */
    private Long roleId;

    /** 门店范围 id, 对应 sys_store.id; NULL=全局可见. */
    private Long storeId;

    /** 生效日期(含当日, Asia/Shanghai); NULL=立即生效. */
    private LocalDate validFrom;

    /** 失效日期(含当日, Asia/Shanghai); NULL=长期有效; 必须晚于 validFrom. */
    private LocalDate validUntil;

    /** KbSourceType 枚举 code: 1=MANUAL 手动录入 2=UPLOAD 文件上传 3=GENERATED 系统生成; 默认 1=MANUAL. */
    private Integer sourceType;

    private String content;
}
