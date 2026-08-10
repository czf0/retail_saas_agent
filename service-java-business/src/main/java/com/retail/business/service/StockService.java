package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.StockAdjustReq;
import com.retail.business.dto.req.StockCountReq;
import com.retail.business.dto.req.StockMovementQueryReq;
import com.retail.business.dto.req.StockMovementToolReq;
import com.retail.business.dto.req.StockQueryReq;
import com.retail.business.dto.req.StockSafetySetReq;
import com.retail.business.dto.req.StockTransferReq;
import com.retail.business.dto.resp.ProductStockResp;
import com.retail.business.dto.resp.StockAdjustResp;
import com.retail.business.dto.resp.StockCountResp;
import com.retail.business.dto.resp.StockMovementResp;
import com.retail.business.dto.resp.StockTransferResp;
import com.retail.business.entity.ProductStock;
import com.retail.business.enums.StockBizType;
import com.retail.core.dto.PageResp;

/**
 * 商品库存账户与流水服务.
 * <p>
 * product_stock / stock_movement 均为多租户 + 门店隔离表,tenant_id / store_id 由拦截器自动注入;
 * 库存账户为实时账户(与 inventory_record 日聚合快照并存,互不冲突).
 * <p>
 * {@link #inbound} / {@link #outbound} 为 public 方法,供订单模块(OrderServiceImpl)跨模块调用,
 * 完成支付出库 / 退款入库联动(同事务保证一致性).
 */
public interface StockService extends IService<ProductStock> {

    /**
     * 获取或创建库存账户(幂等).
     * <p>
     * 按 productId + skuId 查询当前门店(拦截器自动隔离)的库存账户,不存在则创建.
     * 幂等性由唯一键 uk_tenant_store_sku 保证;并发创建冲突时捕获异常并重试查询.
     *
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @param storeId   门店ID(拦截器自动隔离,传入仅用于业务上下文传递)
     * @return 库存账户实体
     */
    ProductStock getOrCreateStock(Long productId, Long skuId, Long storeId);

    /**
     * 查询库存账户(按商品 + SKU 自然键).
     *
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @param storeId   门店ID(拦截器自动隔离)
     * @return 库存账户响应;不存在返回 null
     */
    ProductStockResp getStock(Long productId, Long skuId, Long storeId);

    /**
     * 按账户ID查询库存详情.
     *
     * @param stockId 库存账户ID
     * @return 库存账户响应;不存在返回 null
     */
    ProductStockResp getStockById(Long stockId);

    /**
     * 分页查询库存账户列表.
     *
     * @param req 查询条件(含分页)
     * @return 分页响应
     */
    PageResp<ProductStockResp> listStocks(StockQueryReq req);

    /**
     * 分页查询库存流水列表.
     *
     * @param req 查询条件(含分页)
     * @return 分页响应
     */
    PageResp<StockMovementResp> listMovements(StockMovementQueryReq req);

    /**
     * 分页查询库存流水列表(Agent 工具专用,支持 Integer code 与业务语义字段).
     * <p>
     * 与 {@link #listMovements} 的区别:
     * <ul>
     *   <li>movementType / bizType 用 Integer code(对齐 Java 端枚举规范),内部经
     *       {@code EnumUtil.fromCode} 转枚举校验非法值,LLM 由工具描述记住数字码;</li>
     *   <li>productName / storeName 支持业务语义定位(反查 product_info / sys_store 得 ID 过滤).</li>
     * </ul>
     *
     * @param req 工具查询条件(含分页)
     * @return 分页响应
     */
    PageResp<StockMovementResp> listStockMovements(StockMovementToolReq req);

    /**
     * 手动调整库存(核心方法,事务内:取账户→更新 available_qty→写 stock_movement 流水).
     *
     * @param req 调整请求
     * @return 调整结果(含账户ID,调整后库存,流水ID)
     */
    StockAdjustResp adjust(StockAdjustReq req);

    /**
     * 商品盘点(事务内:取账户→计算盘差→写 check_gain/check_loss 流水).
     * <p>
     * 盘差 = actualQty - bookQty(bookQty 缺省取当前可用库存):
     * <ul>
     *   <li>盘差 &gt; 0:写 {@code MovementType.CHECK_GAIN}(盘盈);</li>
     *   <li>盘差 &lt; 0:写 {@code MovementType.CHECK_LOSS}(盘亏);</li>
     *   <li>盘差 = 0:账实相符,不写流水.</li>
     * </ul>
     *
     * @param req 盘点请求
     * @return 盘点结果(账面/实盘/盘差/结果类型/调整后库存)
     */
    StockCountResp count(StockCountReq req);

    /**
     * 设置安全库存阈值(当前用户门店维度).
     *
     * @param req 请求(商品定位 + 新安全库存)
     * @return 更新后的库存账户(含 belowSafety 标记)
     */
    ProductStockResp setSafetyStock(StockSafetySetReq req);

    /**
     * 门店间调拨(事务内:源门店出库 + 目标门店入库,两条流水同单据号关联成对).
     * <p>
     * 跨门店操作绕过租户/门店拦截器,显式指定 tenant_id / store_id(见
     * {@code ProductStockMapper#selectByStoreSku} / {@code insertIgnoreTenant} / {@code updateAvailableIgnoreTenant}).
     *
     * @param req 调拨请求(商品 + 源/目标门店名 + 数量)
     * @return 调拨结果(源/目标门店调拨后库存 + 调拨单号)
     */
    StockTransferResp transfer(StockTransferReq req);

    /**
     * 入库(供其他模块调用,如采购 / 退款回滚).
     * <p>
     * 事务内:获取或创建账户 → available_qty 增加 → 写 movement_type=inbound 流水.
     *
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @param storeId   门店ID(拦截器自动隔离)
     * @param qty       入库数量(正数)
     * @param bizType   业务类型枚举,取值见 {@link com.retail.business.enums.StockBizType}
     * @param bizNo     关联单据号
     * @param remark    备注
     */
    void inbound(Long productId, Long skuId, Long storeId, Integer qty, StockBizType bizType, String bizNo, String remark);

    /**
     * 出库(供订单模块调用,订单支付成功时扣减库存).
     * <p>
     * 事务内:获取或创建账户 → 校验可用库存充足 → available_qty 减少 → 写 movement_type=outbound 流水.
     *
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @param storeId   门店ID(拦截器自动隔离)
     * @param qty       出库数量(正数)
     * @param bizType   业务类型枚举,取值见 {@link com.retail.business.enums.StockBizType}
     * @param bizNo     关联单据号
     * @param remark    备注
     */
    void outbound(Long productId, Long skuId, Long storeId, Integer qty, StockBizType bizType, String bizNo, String remark);
}
