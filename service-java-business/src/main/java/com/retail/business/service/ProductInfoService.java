package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ProductCreateReq;
import com.retail.business.dto.req.ProductListReq;
import com.retail.business.dto.req.ProductOffShelfToolReq;
import com.retail.business.dto.req.ProductOnShelfToolReq;
import com.retail.business.dto.req.ProductPriceAdjustToolReq;
import com.retail.business.dto.req.ProductUpdateReq;
import com.retail.business.dto.resp.ProductBatchActionResp;
import com.retail.business.dto.resp.ProductCreateResp;
import com.retail.business.dto.resp.ProductDeleteResp;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductPriceAdjustResp;
import com.retail.business.dto.resp.ProductResp;
import com.retail.business.dto.resp.ProductUpdateResp;
import com.retail.business.entity.ProductInfo;

/**
 * 商品基础信息服务 (product_info 表) + 商品维度 Agent 工具动作入口.
 * <p>除常规 CRUD 外, 承接商品上/下架,改价,按业务字段定位等动作型能力, 供 HTTP 接口与
 * {@code product:*} Agent 工具复用; 批量动作单批上限 50 (铁律 12), 超出抛 ParamException.
 * <p>上/下架与改价属破坏性操作, Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
 */
public interface ProductInfoService extends IService<ProductInfo> {

    ProductCreateResp createProduct(ProductCreateReq req);

    PageResp<ProductListItemResp> listProducts(ProductListReq req);

    ProductResp getProduct(Long id);

    ProductUpdateResp updateProduct(Long id, ProductUpdateReq req);

    ProductDeleteResp deleteProduct(Long id);

    /**
     * 批量下架: 按业务字段 (name/spuCode/brand/category 等) 圈选商品, 将状态置为下架.
     * <p>前置条件: 至少提供一种定位条件, 且商品存在, 否则抛 ParamException.
     * <p>副作用: 下架后该商品不可再被新订单选择; 单批上限 50 (铁律 12), 超限抛 ParamException.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param req 圈选条件 + 待处理商品列表
     * @return 批量动作结果 (成功/失败计数, 含每个商品的定位回显)
     * @throws ParamException 定位条件缺失或商品不存在 / 批量超 50
     */
    ProductBatchActionResp batchOffShelf(ProductOffShelfToolReq req);

    /**
     * 批量上架: 按显式商品名/ID 列表恢复为可售状态.
     * <p>前置条件: 商品必须存在且当前为下架状态, 否则跳过并计数.
     * <p>副作用: 上架后商品恢复可售, 参与后续下单与促销圈选; 单批上限 50 (铁律 12).
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param req 商品名/ID 列表
     * @return 批量动作结果 (成功/失败计数)
     * @throws ParamException 批量超 50
     */
    ProductBatchActionResp batchOnShelf(ProductOnShelfToolReq req);

    /**
     * 改价/改成本: 至少传一个非空新值, 事务内更新商品价格字段.
     * <p>前置条件: 商品必须存在, 新价格必须为正数, 否则抛 ParamException.
     * <p>副作用: 改价影响后续订单金额计算与促销门槛判定, 历史订单不受影响.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param req 商品定位 + 新价格/新成本
     * @return 调整后的价格信息
     * @throws ParamException 商品不存在或新价格非法
     */
    ProductPriceAdjustResp priceAdjust(ProductPriceAdjustToolReq req);

    /**
     * 按业务字段定位商品 ID: productId / name / spuCode 三选一, 须唯一命中.
     * <p>前置条件: 至少提供其一; 命中多行或零行时抛 ParamException.
     * <p>用途: 供 Agent 工具层做「业务语义字段 → 内部 ID」解析 (铁律 20), 不暴露内部主键给 LLM.
     *
     * @param productId 商品 ID (可空)
     * @param name      商品名 (可空)
     * @param spuCode   商品编码 (可空)
     * @return 唯一命中的商品 ID
     * @throws ParamException 定位条件缺失 / 无命中 / 多行命中
     */
    Long resolveProductId(Long productId, String name, String spuCode);
}
