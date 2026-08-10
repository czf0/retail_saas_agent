package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 快捷提问批量保存请求 (懒持久化初始化用).
 * <p>用户首次修改快捷提问时, 前端将 DEFAULT_QUICK_QUERIES 常量批量保存到数据库,
 * 初始化个人快捷提问集, 此后 DB 成为权威数据源.
 */
@Data
public class QuickQueryBatchReq {

    /** 批量保存的快捷提问列表 */
    private List<QuickQuerySaveReq> items;
}
