package com.retail.business.agent;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.CategoryCreateReq;
import com.retail.business.dto.req.CategoryDetailToolReq;
import com.retail.business.dto.req.CategoryUpdateToolReq;
import com.retail.business.dto.resp.CategoryCreateResp;
import com.retail.business.dto.resp.CategoryResp;
import com.retail.business.dto.resp.CategoryTreeNodeResp;
import com.retail.business.entity.ProductCategory;
import com.retail.business.mapper.ProductCategoryMapper;
import com.retail.business.service.ProductCategoryService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.exception.ParamException;

import java.util.List;

/**
 * 商品分类 Agent 工具服务 (business="category").
 * <p>
 * 聚合分类域的工具方法, 复用 {@link ProductCategoryService} 现有业务逻辑:
 * <ul>
 *   <li>{@code category:tree}   — 查询分类树 (只读, 含子分类层级);</li>
 *   <li>{@code category:detail} — 查询分类详情 (只读);</li>
 *   <li>{@code category:create} — 创建分类 (破坏性, HITL 审批);</li>
 *   <li>{@code category:update} — 更新分类 (破坏性, HITL 审批).</li>
 * </ul>
 * <p>
 * 权限说明: ProductCategoryController 无 @SaCheckPermission, 依赖多租户隔离即可,
 * 因此 requiredPermission 显式设为空串 "" (不自动推导, 无权限要求).
 */
@AgentToolService(business = "category")
public class CategoryAgentToolService {

    private final ProductCategoryService productCategoryService;
    private final ProductCategoryMapper productCategoryMapper;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public CategoryAgentToolService(ProductCategoryService productCategoryService, ProductCategoryMapper productCategoryMapper) {
        this.productCategoryService = productCategoryService;
        this.productCategoryMapper = productCategoryMapper;
    }

    /**
     * 解析分类ID:优先使用传入的 categoryId;否则按分类名称反查.
     * <p>
     * 业务人员通常只掌握分类名称,不掌握内部分类ID,故提供按名称定位的入口.
     * 反查要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param categoryId 分类ID(可空)
     * @param name       分类名称(可空)
     * @return 解析后的分类ID
     */
    private Long resolveCategoryId(Long categoryId, String name) {
        if (categoryId != null) {
            return categoryId;
        }
        if (StrUtil.isBlank(name)) {
            throw new ParamException("请提供分类ID或分类名称");
        }
        ProductCategory category = productCategoryMapper.selectOne(
                new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getName, name));
        if (category == null) {
            throw new ParamException("未找到匹配的分类，请提供更精确的分类名称");
        }
        return category.getId();
    }

    /**
     * 查询分类树 (只读, 含子分类层级).
     * <p>
     * 复用 {@link ProductCategoryService#listCategoryTree}, 对齐 ProductCategoryController.tree (无 @SaCheckPermission).
     * 返回完整分类树结构 (含父子关系), 默认包含停用分类.
     *
     * @return 分类树节点列表
     */
    @AgentTool(
        operation = "tree",
        description = "查询商品分类树。返回所有分类的树形结构，包含父子层级关系。用于回答'有哪些商品分类''分类结构'等问题。",
        requiredPermission = "",
        outputHint = "返回分类树，包含分类ID、名称、父分类、排序、状态、子分类列表。展示为树形结构文本。"
    )
    public List<CategoryTreeNodeResp> tree() {
        return productCategoryService.listCategoryTree(false);
    }

    /**
     * 查询分类详情 (只读).
     * <p>
     * 复用 {@link ProductCategoryService#getCategory}, 对齐 ProductCategoryController.get (无 @SaCheckPermission).
     *
     * @param req 查询条件 (categoryId / name)
     * @return 分类详情
     */
    @AgentTool(
        operation = "detail",
        description = "查询商品分类详情。支持按分类ID或分类名称定位，返回分类的完整信息，包括名称、父分类、排序、状态、描述。用于回答'分类XX的详细信息'。",
        requiredPermission = "",
        outputHint = "返回分类详情，包含分类ID、名称、父分类ID、排序、状态、描述。展示为结构化文本。"
    )
    public CategoryResp detail(CategoryDetailToolReq req) {
        Long categoryId = resolveCategoryId(req.getCategoryId(), req.getName());
        return productCategoryService.getCategory(categoryId);
    }

    /**
     * 创建分类 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductCategoryService#createCategory}, 对齐 ProductCategoryController.create (无 @SaCheckPermission).
     *
     * @param req 创建请求 (name / parentId / sortOrder / status / description)
     * @return 创建结果 (含分类 ID)
     */
    @AgentTool(
        operation = "create",
        description = "创建商品分类。需要分类名称，可指定父分类(创建子分类)、排序、状态、描述。状态为整数code：1启用/0停用，默认1启用，必须传数字。此操作会新增分类数据，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "",
        outputHint = "返回创建结果，包含分类ID、名称、父分类ID。展示为文本，提示用户分类已创建成功。"
    )
    public CategoryCreateResp create(CategoryCreateReq req) {
        return productCategoryService.createCategory(req);
    }

    /**
     * 更新分类 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductCategoryService#updateCategory}, 对齐 ProductCategoryController.update (无 @SaCheckPermission).
     * 支持部分更新 (name / sortOrder / status / description).
     *
     * @param req 更新请求 (categoryId + 可更新字段)
     * @return 更新结果 (true=成功)
     */
    @AgentTool(
        operation = "update",
        description = "更新商品分类信息。支持修改名称、排序、状态、描述。需要分类ID定位。此操作会修改分类数据，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "",
        outputHint = "返回更新结果，true表示成功。展示为文本，提示用户分类已更新成功。"
    )
    public boolean update(CategoryUpdateToolReq req) {
        productCategoryService.updateCategory(req.getCategoryId(), req);
        return true;
    }
}
