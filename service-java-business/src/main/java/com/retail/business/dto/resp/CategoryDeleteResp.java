package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类删除操作结果响应(软删除级联子节点);继承通用 OperationResultResp.success+message,返回级联删除节点数 + 受影响商品数(前端二次确认提示).
 * <p>注意:非空分类(有 product_count > 0)需用户二次确认(affectedProducts > 0 时前端弹窗"影响 N 个商品,是否继续？").
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryDeleteResp extends OperationResultResp {

    /** 级联软删除的分类节点数(含自身 + 所有子孙;=0 表示根节点不存在/已删). */
    private Integer deletedCount;

    /** 受影响商品数(原归属到该分类及其子分类的 product_info 更新到 category_id = NULL 或"未分类"的数量). */
    private Integer affectedProducts;
}
