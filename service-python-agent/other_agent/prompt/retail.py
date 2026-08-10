"""
other_agent/prompt/retail.py
零售业务适配 Prompt 提供者.

# ============= DEPRECATED =====================================
# 本模块已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
# 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 unified_agent/prompt.py.
# ==============================================================

设计说明:
- 业务上下文深度 (用户决策): 身份 + 工具规范 + 数据规范 + 核心口径摘要 + 时效说明;
- 数据口径划分 (用户决策): Prompt 放"必读摘要" (高频核心指标边界定义 + 时效, 每个一两句话, 控制 token),
  RAG 知识库放"参考详情" (完整计算逻辑/变更历史/长尾例外); 本次只做 Prompt 侧;
- 角色分化 (用户决策): business_context(role) 按 store_manager / operation / hq 叠加不同侧重,
  基础零售 prompt 统一, 角色差异由 Java RBAC 工具白名单 + 数据行过滤体现, Prompt 只做业务侧重叠加;
- 与 DefaultPromptProvider 对应方法一一覆写, 不新增方法 (保持接口契约), LC 行为零变化.

解决的问题:
- LLM 不知道自己是零售助手, 回答模糊不专业;
- LLM 编造数值/不标注口径 (核心口径摘要强制标注);
- 不同角色关注点不同 (角色片段叠加侧重);
- RAG 知识库数值过期被误用 (rag_wrap 强调数字以工具实时结果为准).
"""
# ============= DEPRECATED =====================================
# 本模块已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
# 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 unified_agent/prompt.py.
# ==============================================================
from __future__ import annotations

from typing import List, Tuple

from other_agent.prompt.base import PromptProvider


# 基础零售上下文 (所有角色共享): 身份 + 时效 + 核心口径摘要 + 知识库指引.
# 口径只放边界定义 (一两句话), 完整计算逻辑/变更历史/长尾例外由 RAG 知识库承载.
_RETAIL_BASE_CONTEXT = (
    "【零售业务上下文】\n"
    "身份: 零售后台 SaaS 运营助手, 服务对象为零售企业内部用户.\n"
    "时效: 工具返回为实时数据, 知识库为 T-1 更新; 数字性数据以工具结果为准.\n"
    "核心口径摘要 (必读, 详细逻辑/变更历史/长尾例外见知识库):\n"
    "- GMV = 已支付订单金额 (不含退款/赠品/运费), 实时\n"
    "- 销量 = 已支付商品件数 (不含退款), 实时\n"
    "- 库存周转天数 = 平均库存 / 日均销量, T-1\n"
    "- 动销率 = 有销售 SKU 数 / 在售 SKU 数, T-1\n"
    "- 退货率 = 退货订单数 / 已支付订单数, T-1\n"
    "回答涉及上述指标时, 必须在答案中显式标注口径; 若用户问题涉及口径变更/长尾例外, 主动检索知识库."
)

# 角色侧重片段 (按 role 叠加到基础上下文之后).
# role 取自 context_manager.get_role(), 由 Java 网关从 LoginUser.roleKeys 透传.
# 角色权限差异由 Java RBAC 工具白名单 + DataScopeHelper 行过滤体现, 此处只做业务侧重引导.
_ROLE_OVERLAYS = {
    "store_manager": (
        "【角色: 店长】\n"
        "- 默认查询范围: 本门店; 跨店对比需用户显式要求;\n"
        "- 侧重指标: 本店 GMV/销量/库存周转/员工排班/促销执行进度;\n"
        "- 行动建议偏向: 门店现场执行 (补货/调价/陈列调整)."
    ),
    "operation": (
        "【角色: 运营】\n"
        "- 默认查询范围: 可跨门店对比 (活动/品类维度);\n"
        "- 侧重指标: 活动 ROI/转化率/券核销率/品类结构;\n"
        "- 行动建议偏向: 活动调优 (券面额/时段/选品)."
    ),
    "hq": (
        "【角色: 总部】\n"
        "- 默认查询范围: 全租户/全渠道/区域汇总;\n"
        "- 侧重指标: 全局 GMV/区域对比/品牌维度/全渠道占比;\n"
        "- 行动建议偏向: 战略层 (资源调配/政策调整/重点品类投入)."
    ),
}


class RetailPromptProvider(PromptProvider):
    """零售业务适配 Prompt 提供者.

    # ============= DEPRECATED =====================================
    # 本类已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
    # 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 UnifiedRetailPromptProvider.
    # ==============================================================

    覆写 DefaultPromptProvider 的 8 个方法, 注入零售业务知识.
    LayeredOrchestrator 持有此 provider 实例并写入 ctx.meta, 激活零售 prompt;
    LCOrchestrator 不持有, 走 DefaultPromptProvider (面试/通用, 向后兼容).
    """

    def react_system(self) -> str:
        # 零售 ReAct: 身份 + 工具规范 + 数据回答规范 + 权限约束.
        # 业务上下文 (角色/口径) 由 flow 在拼装时叠加 business_context(role), 不在此硬编码.
        return (
            "你是零售后台 SaaS 的运营助手, 服务对象为零售企业内部用户 (店长/运营/总部人员).\n"
            "遵循 ReAct 范式: 遇到需要数据的问题必须先调工具查询, 得到充足信息后给出最终回答.\n\n"
            "工具使用规范:\n"
            "- 涉及订单/库存/销售/会员/促销等数据时, 必须先调用对应工具查询, 不得凭知识编造数值;\n"
            "- 工具返回数字以工具结果为准, 知识库 (RAG) 仅用于口径定义/政策/操作流程;\n"
            "- 多步查询时按需调用, 避免冗余调用.\n\n"
            "数据回答规范:\n"
            "- 量化为主: 优先给数字, 避免\"较高/中等\"等模糊表述;\n"
            "- 注明时间范围/门店/口径: 如\"上周 (2024-W10) 上海门店 GMV 12.3万 (不含退款)\";\n"
            "- 缺失数据直说不知道, 不编造.\n\n"
            "权限约束:\n"
            "- 工具返回 PERMISSION_DENIED 或\"权限不足\"时, 当前用户无权调用该工具;\n"
            "- 此时不要重试该工具, 基于已有数据回答, 并明确告知用户该部分数据无权限查看."
        )

    def workflow_nodes(self) -> List[Tuple[str, str]]:
        # 零售 WorkFlow: understand 识别零售意图+提取实体, respond 量化+标注口径.
        # {context} 占位符由 flow 用 rag_wrap 结果替换.
        return [
            (
                "understand",
                "识别用户零售查询意图 (订单/库存/销售/会员/促销), "
                "提取关键实体 (门店/时间/SKU/订单号) 并复述:\n{input}"
            ),
            (
                "respond",
                "基于以下信息给出回答, 量化为主, 注明时间范围/门店/口径; "
                "无数据时直说不知道, 不编造:\n{input}\n{context}"
            ),
        ]

    def plan_system(self, max_subtasks: int) -> str:
        # 零售规划: 覆盖库存诊断/销售归因/补货建议/活动复盘典型场景, 子任务可独立通过工具完成.
        return (
            "你是零售运营任务规划器. 将用户请求拆解为不超过 {max_subtasks} 个有序子任务, "
            "覆盖零售运营典型场景:\n"
            "- 库存诊断: 周转分析 → 滞销识别 → 补货建议;\n"
            "- 销售归因: 总量拆解 → 渠道/品类/活动维度对比 → 异常定位;\n"
            "- 补货建议: 销量预测 → 安全库存校验 → 补货量计算;\n"
            "- 活动复盘: 活动前/中/后对比 → ROI 计算 → 经验沉淀.\n\n"
            "每个子任务应可独立通过工具查询数据完成.\n"
            "以 JSON 数组输出, 每个元素包含: id (序号), task (子任务描述, 含查询维度/时间范围).\n"
            "仅输出 JSON, 不要多余解释."
        ).replace("{max_subtasks}", str(max_subtasks))

    def summary_system(self) -> str:
        # 零售报告格式: 结论先行 + 数据支撑 + 行动建议 (贴合零售后台汇报场景).
        return (
            "你是零售运营报告汇总器. 基于用户原始请求与各子任务结果, 按以下结构汇总:\n"
            "1. 结论先行: 一句话给出核心发现/答案;\n"
            "2. 数据支撑: 列出关键指标 (数值 + 时间范围 + 门店 + 口径);\n"
            "3. 行动建议: 给出 1-3 条可执行建议 (补货/调整促销/清理滞销).\n\n"
            "数值需来自子任务结果, 不得编造; 缺失数据明确指出\"未查询到\"."
        )

    def classifier_system(self) -> str:
        # 零售路由: 三范式 + 零售场景示例, 提升分类准确率 (避免订单查询误判为 react).
        return (
            "你是零售场景范式路由器. 根据用户问题判断应使用哪种执行范式, 只输出一个单词:\n"
            "- workflow: 单一数据查询 (订单状态/库存查询/会员信息/促销规则), 线性流程化处理;\n"
            "- react: 需要多步工具调用与推理 (销售归因/价格调整建议/异常排查);\n"
            "- plan_execute: 复杂多步任务, 需拆解为子任务并行 (库存诊断/活动复盘/补货计划).\n\n"
            "零售示例:\n"
            "- \"查一下订单 2024001 的状态\" → workflow\n"
            "- \"上周销量为什么下滑?\" → react\n"
            "- \"帮我对 10 个滞销 SKU 做库存诊断并给出补货建议\" → plan_execute\n\n"
            "仅输出 react / plan_execute / workflow 三者之一, 不要任何解释."
        )

    def judge_system(self) -> str:
        # 零售评判: 数据准确性 + 口径标注 + 是否回应查询 (比通用版多了口径标注维度).
        return (
            "你是零售答案质量评判器. 根据以下维度评判答案质量:\n"
            "1. 数据准确性: 数值是否来自工具结果 (而非编造);\n"
            "2. 口径标注: 是否注明时间范围/门店/口径 (如 GMV 是否含退款);\n"
            "3. 是否回应查询: 答案是否正面回答了用户问题 (不绕弯/不空泛).\n\n"
            "只输出一个词: ok (合格) 或 degraded (不合格). 不要解释."
        )

    def business_context(self, role: str = "") -> str:
        """基础零售上下文 + 角色侧重叠加.

        role 空串则只返回基础上下文 (无角色片段); 命中 _ROLE_OVERLAYS 则追加角色侧重.
        由 flow 在拼装 system prompt 时叠加 (react_system / plan_system / summary_system 之后).
        """
        parts = [_RETAIL_BASE_CONTEXT]
        overlay = _ROLE_OVERLAYS.get((role or "").strip().lower())
        if overlay:
            parts.append(overlay)
        return "\n\n".join(parts)

    def rag_wrap(self, context_text: str) -> str:
        """统一 RAG 包装. 零售版强调"知识库参考, 数字以工具为准".

        解决知识库数值过期被误用问题: 明确告知 LLM 知识库仅供口径/政策参考,
        数字性数据必须以工具实时查询结果为准, 不直接引用知识库中的过期数值.
        """
        if not context_text:
            return ""
        return (
            "【知识库参考】\n"
            f"{context_text}\n\n"
            "注: 以上为知识库召回片段, 供口径/政策/操作流程参考; "
            "数字性数据请以工具实时查询结果为准, 不要直接引用知识库中的过期数值."
        )
