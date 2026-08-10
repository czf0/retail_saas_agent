"""
unified_agent/prompt.py
Unified ReAct+Plan Agent 的 Prompt 提供者.

设计说明:
- 独立定义 PromptProvider ABC, 不继承 other_agent.prompt.base.PromptProvider
  (unified_agent 内部组件全部重新构建, 仅依赖纯基础设施);
- 通用实现 UnifiedPromptProvider: 面试/通用场景, 无零售业务知识, 保证向后兼容;
- 零售实现 UnifiedRetailPromptProvider: 注入零售业务专业度, 对齐并超越现有
  RetailPromptProvider 标准 — 在统一 ReAct+Plan 范式下融合零售运营全链路知识;
- get_provider(ctx_or_state): 优先 ctx.meta/state 透传 (per-request 隔离),
  回退默认零售单例, 解决多 orchestrator 共存时的 provider 污染问题.

零售 Prompt 专业度标准 (对齐项目硬约束):
- unified_system: 零售身份 + ReAct 范式 + 工具规范 + 数据回答规范 + 权限约束 + 回答结构规范;
- plan_judge_system: 零售场景 yes/no 判定 + 边界案例 (判定更准确, 减少 LLM 抖变);
- plan_generate_system: 覆盖零售运营典型场景 + 工具可用性约束 + 数据粒度引导;
- plan_inject_format: 任务清单格式化注入 (参考性, 非强制, 可动态调整);
- judge_system: 数据准确性 + 口径标注 + 时效标注 + 查询回应 (四维度评判);
- business_context: 基础上下文 (5 核心口径, 每指标 1-2 句, 控制 token) + 角色侧重叠加;
- rag_wrap: 【知识库参考】标注 + 强调数字以工具实时为准 + 优先级说明.

解决的问题:
- LLM 不知道自己是零售助手, 回答模糊不专业 → unified_system 注入零售身份与规范;
- LLM 编造数值/不标注口径与时效 → 数据回答规范强制标注 + judge_system 四维度校验;
- Plan 生成不考虑工具可用性与数据粒度 → plan_generate_system 增加约束引导;
- 不同角色关注点不同 → business_context 按店长/运营/总部叠加侧重;
- RAG 知识库数值过期被误用 → rag_wrap 强调数字以工具实时结果为准.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional, Tuple

from unified_agent.flow_types import FlowContext


# Prompt 版本号: 每次文本/结构变更后递增, 用于 otel_metrics 分桶做回归对比
PROMPT_VERSION: str = "v1.1.0"


def build_tool_shortlist_prompt(max_tools: int = 100) -> str:
    """构建 plan 节点注入的【工具名+业务域+短描述简表】（~20 token/工具，不注入长描述）"""
    try:
        from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
        from tool.base.tool_registry import tool_registry
        allowed = tool_registry.get_allowed_tools()
        if allowed:
            tool_names = list(allowed)[:max_tools]
        else:
            all_tools = dynamic_java_tool_loader.list_definitions() or []
            tool_names = [getattr(d, "name", "") for d in all_tools[:max_tools]]
        lines = ["【当前角色可用工具简表 (plan tool_hint 参考)】"]
        lines.append("格式: 工具名 | 业务域 | 短说明")
        lines.append("说明: tool_hint 仅能从此清单中选择, 不允许编造; 若无法确定可留空字符串.")
        for name in tool_names:
            if not name:
                continue
            try:
                defn = dynamic_java_tool_loader.get_definition(name)
            except Exception:
                defn = None
            if defn is not None:
                group = getattr(defn, "business", "") or getattr(defn, "group", "") or "通用"
                desc = getattr(defn, "description", "") or getattr(defn, "desc", "") or ""
                short_desc = desc[:30].replace("\n", " ") if desc else ""
            else:
                group, short_desc = "通用", ""
            lines.append(f"- {name} | {group} | {short_desc}")
        if len(tool_names) == 0:
            lines.append("(工具清单加载中或不可用, 暂为空)")
        return "\n".join(lines)
    except Exception as e:
        return f"【当前角色可用工具简表】加载失败 {e}; 请以实际可调用工具为准."


# ============================================================================
# PromptProvider 抽象基类
# ============================================================================

class PromptProvider(ABC):
    """统一 Agent Prompt 提供者抽象 (独立定义, 不依赖 other_agent.prompt.base).

    方法分三类:
    - 范式 prompt (unified_system): 描述"怎么做" — ReAct 范式 + 工具/数据/权限/结构规范;
    - 辅助 prompt (plan_judge_system / plan_generate_system / plan_inject_format /
      judge_system): 意图路由 + Plan 生成与注入 + 答案评判;
    - 可组合片段 (business_context / rag_wrap): 由 graph 在拼装 system prompt 时叠加,
      business_context 按角色分化, rag_wrap 包装 RAG 召回片段.
    """

    @abstractmethod
    def unified_system(self) -> str:
        """统一 ReAct 系统提示: 身份 + ReAct 范式 + 工具规范 + 数据回答规范 + 权限约束 + 回答结构.

        业务上下文 (角色/口径) 由 graph 在拼装时叠加 business_context(role), 不在此硬编码.
        若存在参考任务清单, 由 graph 追加 plan_inject_format(tasks) 片段.
        """

    @abstractmethod
    def plan_judge_system(self) -> str:
        """意图路由判定提示: 输出 yes/no, 判定是否需要先制定任务清单.

        规则路由先行 (关键词/场景/长度), 不明确时 LLM 兜底判定, 走此 prompt.
        """

    @abstractmethod
    def plan_generate_system(self, max_tasks: int) -> str:
        """Plan 生成提示: 输出 JSON 任务清单, 覆盖零售运营典型场景.

        max_tasks 渲染到模板, 限制任务数量以控制 LLM 扇出.
        """

    def plan_generate_structured_system(self, max_tasks: int) -> str:
        """结构化 Plan 生成提示 (新增方法, 与老方法分离, 避免解析漂移).

        内含: 扩展场景列表 + 工具简表注入 + JSON few-shot 示例 + deps 字段说明.
        默认实现委托 plan_generate_system + 追加 few-shot, 子类可覆写注入业务场景.
        """
        base = self.plan_generate_system(max_tasks)
        shortlist = build_tool_shortlist_prompt()
        few_shot = (
            "\n\n===== JSON 输出示例 =====\n"
            "示例 1 (多步分析):\n"
            '[{{"id":1,"task":"查询本店近30天库存周转天数（门店:默认角色默认范围,时间范围:近30天）","tool_hint":"","deps":[]}},'
            '{{"id":2,"task":"识别本店滞销SKU（库存>=30天未动销）","tool_hint":"","deps":[1]}},'
            '{{"id":3,"task":"结合前两步给出建议","tool_hint":"","deps":[1,2]}}]\n'
            "示例 2 (单步写操作):\n"
            '[{{"id":1,"task":"新增会员 张三 13812345678（门店:默认角色默认范围）","tool_hint":"","deps":[]}}]\n'
            "===== 字段说明 =====\n"
            "- id: 正整数, 从 1 递增\n"
            "- task: 任务描述, 必须包含查询维度/时间范围/门店（若缺省注明\"默认角色默认范围\"）\n"
            "- tool_hint: 工具名, 必须选自【当前角色可用工具简表】, 不确定则留空字符串 \"\"\n"
            "- deps: 数字数组, 表示本任务依赖前置任务的 id, 无依赖则空数组 []\n"
            "仅输出 JSON 数组, 不要解释, 不要 ```markdown 包裹."
        )
        return f"{base}\n\n{shortlist}{few_shot}"

    @abstractmethod
    def plan_inject_format(self, tasks: list) -> str:
        """Plan 注入 system prompt 的格式化片段: 任务清单 → 参考文本.

        返回空串则 graph 跳过注入 (无 plan 场景). 非强制清单, ReAct 可动态调整.
        """

    @abstractmethod
    def judge_system(self) -> str:
        """答案质量评判提示: 输出 ok/degraded.

        评判维度: 数据准确性 + 口径标注 + 时效标注 + 查询回应.
        """

    def judge_structured_system(self) -> str:
        """结构化答案质量评判提示 (新增 Task 6 方法, 与老 judge_system 独立).

        输出 JSON 格式含 verdict/dimensions/fix_suggestion 三个字段, 四维度 pass/fail 评判.
        默认实现给出通用 JSON 结构化模板; 子类(零售版)可覆写注入业务维度与 few-shot 示例.
        """
        return (
            "你是答案质量评判器. 根据【用户问题】【答案】【工具返回观测值清单】三方对比, "
            "从四维度评判并输出结构化 JSON.\n\n"
            "评判维度 (每维 pass/fail):\n"
            "1. accuracy (数据准确性): 答案中的数字/结论与工具返回观测值是否一致; 数值编造/不符 -> fail\n"
            "2. caliber (口径标注): 涉及核心指标时是否标注口径+时效+维度\n"
            "3. timeliness (时效标注): 是否标注数据时效, 避免混用过期知识库数值\n"
            "4. responsive (回应相关性): 是否正面回答用户问题, 不绕弯/不空泛\n\n"
            "输出格式 (仅输出 JSON 对象, 不要解释, 不要 markdown code fence):\n"
            "{\n"
            '  "verdict": "ok" | "degraded",\n'
            '  "dimensions": {\n'
            '    "accuracy": "pass" | "fail",\n'
            '    "caliber":  "pass" | "fail",\n'
            '    "timeliness": "pass" | "fail",\n'
            '    "responsive": "pass" | "fail"\n'
            "  },\n"
            '  "fix_suggestion": "一句话修复建议, 若ok写空字符串"\n'
            "}\n\n"
            "判定规则:\n"
            "- 任一维度 fail 即 verdict=degraded, 否则 verdict=ok\n"
            "- accuracy 优先: 数值错即 degraded (即使其他维度 pass)\n"
        )

    @abstractmethod
    def business_context(self, role: str = "") -> str:
        """业务上下文片段, 按角色分化. 由 graph 在拼装 system prompt 时叠加.

        通用实现返回空串 (graph 拼装时自然跳过); 业务实现返回角色相关的业务知识.
        """

    @abstractmethod
    def rag_wrap(self, context_text: str) -> str:
        """RAG 上下文包装格式. 空串入参返回空串 (供 graph 判空跳过注入).

        统一 RAG 注入格式, 零售版强调知识库仅供口径/政策参考, 数字以工具为准.
        """

    @property
    def prompt_version(self) -> str:
        """Prompt 版本号 (对应模块级 PROMPT_VERSION 常量, 供 orchestrator 读取打标)."""
        return PROMPT_VERSION

    def memory_wrap(self, memory_text: str) -> str:
        """长期记忆包装格式 (非抽象, 默认返回空串).

        由 graph 在拼装 system prompt 时叠加. 子类可覆写以定制记忆注入的措辞与优先级说明.
        默认实现返回空串, 保证未覆写 memory_wrap 的历史实现类零破坏 (不强制要求抽象实现).
        """
        if not memory_text:
            return ""
        return f"【用户长期偏好】\n{memory_text}\n\n请在下一次回答中主动遵循以上用户偏好 (仅作约束, 不改变数据查询逻辑)."


# ============================================================================
# 通用实现 UnifiedPromptProvider
# ============================================================================

class UnifiedPromptProvider(PromptProvider):
    """通用默认实现 (面试/通用场景, 无零售业务知识).

    内容保持简洁通用, 作为 unified_agent 的基线 provider.
    零售场景由 UnifiedRetailPromptProvider 覆写, 注入业务知识.
    """

    def unified_system(self) -> str:
        return (
            "你是一个严格遵循 ReAct 范式的通用助手.\n"
            "遇到需要数据的问题时必须先调用工具查询, 得到充足信息后给出最终回答.\n"
            "如果已提供参考任务清单, 按清单顺序推进, 但可根据工具返回结果动态调整.\n\n"
            "工具使用规范:\n"
            "- 涉及数据查询时必须调用工具, 不得凭知识编造数值;\n"
            "- 多步查询时按需调用, 避免冗余调用.\n\n"
            "数据回答规范:\n"
            "- 量化为主: 优先给数字, 避免\"较高/中等\"等模糊表述;\n"
            "- 缺失数据直说不知道, 不编造.\n\n"
            "权限约束:\n"
            "- 工具返回 PERMISSION_DENIED 或\"权限不足\"时, 当前用户无权调用该工具;\n"
            "- 不要重试该工具, 基于已有数据回答, 并明确告知用户该部分数据无权限查看."
        )

    def plan_judge_system(self) -> str:
        return (
            "你是任务复杂度判定器. 判断用户问题是否需要先制定任务清单再执行.\n"
            "- yes: 复杂多步任务 (需要多个工具配合, 如对比分析/方案制定/诊断评估)\n"
            "- no: 简单查询或单步任务 (如查订单/查库存/查政策)\n\n"
            "只输出 yes 或 no, 不要解释."
        )

    def plan_generate_system(self, max_tasks: int) -> str:
        return (
            "你是任务规划器. 请将用户请求拆分为不超过 {max_tasks} 个有序任务步骤, "
            "以 JSON 数组输出, 每个元素包含字段: id (序号), task (任务描述, 含查询维度/时间范围), "
            "tool_hint (建议工具名, 可空).\n"
            "每个步骤应可独立通过工具查询数据完成.\n"
            "仅输出 JSON, 不要多余解释."
        ).replace("{max_tasks}", str(max_tasks))

    def plan_inject_format(self, tasks: list) -> str:
        if not tasks:
            return ""
        lines = ["【参考任务清单】(可按需调整执行顺序, 非强制)"]
        for t in tasks:
            tid = t.get("id", "")
            desc = t.get("task", "")
            hint = t.get("tool_hint", "")
            lines.append(f"  {tid}. {desc}" + (f" [建议工具: {hint}]" if hint else ""))
        return "\n".join(lines)

    def judge_system(self) -> str:
        return (
            "你是答案质量评判器. 根据以下维度评判答案质量:\n"
            "1. 是否回应了用户问题;\n"
            "2. 是否基于工具返回的数据 (而非编造).\n"
            "只输出一个词: ok (合格) 或 degraded (不合格). 不要解释."
        )

    def business_context(self, role: str = "") -> str:
        return ""  # 通用版无业务上下文

    def rag_wrap(self, context_text: str) -> str:
        if not context_text:
            return ""
        return f"参考以下上下文回答问题：\n{context_text}"


# ============================================================================
# 零售实现 UnifiedRetailPromptProvider
# ============================================================================

# ---- 基础零售上下文常量 (所有角色共享) ----
# 口径只放边界定义 (1-2 句), 完整计算逻辑/变更历史/长尾例外由 RAG 知识库承载.
# 对齐项目硬约束: Prompt '必读摘要' 每指标 1-2 句, 控制 token 用量.
_RETAIL_BASE_CONTEXT = (
    "【零售业务上下文】\n"
    "身份: 零售后台 SaaS 运营助手, 服务对象为零售企业内部用户.\n"
    "数据查询范围以 Java RBAC DataScope 实取值为准, 本提示侧重业务职责不做范围硬约束.\n"
    "时效: 工具返回为实时数据, 知识库为 T-1 更新; 数字性数据以工具结果为准."
)


def _build_caliber_section() -> str:
    """从配置 METRIC_CALIBERS 动态生成【核心口径摘要】段落"""
    from config.agent_flow_settings import agent_flow_settings
    data = getattr(agent_flow_settings, "METRIC_CALIBERS", None)
    if not data:
        return (
            "【核心口径摘要】\n"
            "口径定义详见知识库检索（RAG）; 数值以工具实时查询结果为准.\n"
        )
    lines = ["【核心口径摘要 (必读, 详细逻辑见知识库)】"]
    from datetime import date
    today = date.today().isoformat()
    for metric_name, versions in data.items():
        if not isinstance(versions, list) or not versions:
            continue
        effective = None
        for v in sorted(versions, key=lambda x: x.get("effective_from", "0000-00-00"), reverse=True):
            ef = v.get("effective_from", "0000-00-00")
            if ef <= today:
                effective = v
                break
        if effective is None:
            effective = versions[0]
        formula = effective.get("formula", "")
        freshness = effective.get("freshness", "")
        lines.append(f"- {metric_name}: {formula}{'，时效:' + freshness if freshness else ''}")
    lines.append("回答涉及上述指标时, 必须在答案中显式标注口径; 指标口径变更历史/长尾例外/跨期对比需注明口径差异.")
    return "\n".join(lines)

# ---- 角色侧重片段 (按 role 叠加到基础上下文之后) ----
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
    "regional_manager": """【角色: 区域经理】管辖多门店(按区域RBAC自动过滤), 关注区域汇总/门店对比/异常门店诊断; 指标侧重: 区域GMV达成率/门店排名/短板门店环比/活动执行一致性; 行动偏向: 资源调配/跨店支援/督办整改""",
    "purchasing": """【角色: 采购】面向供应商补货/供应链优化, 关注: 商品采购成本/补货周期/供应商交付/库存深度缺货率; 指标侧重: 采购满足率/在途库存/供应商账期/毛利贡献; 行动偏向: 供应商谈判/补货下达/滞销退货/新品引入""",
    "merchandising": """【角色: 商品运营】面向选品/品类/毛利结构优化, 关注: 品类结构/SKU生命周期/价格带/动销; 指标侧重: 品类GMV占比/新品动销率/滞销占比/毛利额; 行动偏向: 汰换SKU/价格调整/品类组合促销/上新节奏""",
    "finance": """【角色: 财务】面向经营分析/对账/成本核算, 关注: 收入确认/成本结转/毛利/费用/回款; 指标侧重: 综合毛利率/净利率/销售费用率/账款回收; 行动偏向: 经营诊断/预算差异分析/合规风险提示""",
    "clerk": """【角色: 店辅/店员】面向门店现场执行, 本店范围, 关注: 单品库存/收银流水/会员开卡/促销执行; 指标侧重: 当班GMV/客单/券核销率/缺货报单; 行动偏向: 现场执行类操作(盘点/调拨/开卡/报缺货)""",
}


class UnifiedRetailPromptProvider(UnifiedPromptProvider):
    """零售行业统一 Prompt Provider.

    覆写通用版方法, 注入零售业务专业度, 对齐并超越现有 RetailPromptProvider 标准.
    在统一 ReAct+Plan 范式下融合零售运营全链路知识:

    - unified_system: 零售身份 + ReAct 范式 + 工具规范 + 数据回答规范 (口径/时效/维度/表格化)
      + 权限约束 + 回答结构规范 (结论先行+数据支撑+行动建议);
    - plan_judge_system: 零售场景 yes/no 判定 + 6 个边界案例 (减少 LLM 抖变);
    - plan_generate_system: 覆盖 6 类零售运营典型场景 + 工具可用性约束 + 数据粒度引导;
    - plan_inject_format: 任务清单格式化注入 (参考性, 非强制, 可动态调整);
    - judge_system: 四维度评判 (数据准确性 + 口径标注 + 时效标注 + 查询回应);
    - business_context: 基础上下文 (5 核心口径) + 角色侧重 (店长/运营/总部);
    - rag_wrap: 【知识库参考】标注 + 强调数字以工具实时为准 + 优先级说明.

    使用方式: UnifiedOrchestrator 持有此 provider 实例并写入 ctx.meta / PreflightState,
    激活零售 prompt; 通用场景 (面试/测试) 走 UnifiedPromptProvider.
    """

    def unified_system(self) -> str:
        # 零售统一 ReAct: 身份 + 范式 + 工具规范 + 数据回答规范 + 权限约束 + 回答结构.
        # 业务上下文 (角色/口径) 由 graph 在拼装时叠加 business_context(role), 不在此硬编码.
        # 参考任务清单由 graph 追加 plan_inject_format(tasks) 片段, 此处只做范式引导.
        return (
            "你是零售后台 SaaS 的运营助手, 服务对象为零售企业内部用户 (店长/运营/总部人员/区域经理/采购/商品运营/财务/店员).\n"
            "遵循 ReAct 范式: 遇到需要数据的问题必须先调工具查询, 得到充足信息后给出最终回答.\n"
            "如果已提供参考任务清单, 按清单顺序推进, 但可根据工具返回结果动态调整执行顺序与内容.\n\n"
            "工具使用规范:\n"
            "- 涉及订单/库存/销售/会员/促销等数据时, 必须先调用对应工具查询, 不得凭知识编造数值;\n"
            "- 工具返回数字以工具结果为准, 知识库 (RAG) 仅用于口径定义/政策/操作流程参考;\n"
            "- 多步查询时按需调用, 避免冗余调用; 同一数据不重复查询.\n\n"
            "写操作规范 (创建/修改/删除/状态变更类操作, 必须调工具):\n"
            "- 判断标准: 当请求与 destructive=true 的业务工具在名称/operation/语义上匹配, 或与写操作动作词(新增/修改/删除/上架/下架/调价/审批/发货/作废/退款/退货/冲销/停用/启用/调拨/入库/出库/盘点/合并/转移 等)匹配, 视为写操作, 必须调用对应业务工具, 不得直接宣称成功;\n"
            "- 只有工具返回 success=true 且无 PERMISSION_DENIED/审批中断, 才能告知用户操作完成; 工具返回\"权限不足/触发审批/HITL待办\"如实告知, 不得伪造成功。\n\n"
            "数据回答规范:\n"
            "- 量化为主: 优先给数字, 避免\"较高/中等\"等模糊表述;\n"
            "- 注明口径: 涉及 GMV/销量/库存周转等指标时, 必须标注口径 (如\"不含退款\");\n"
            "- 注明时效: 标注数据是实时还是 T-1, 如\"实时 GMV\"或\"T-1 库存周转天数\";\n"
            "- 注明维度: 时间范围/门店/渠道/品类等查询维度必须明确, "
            "如\"上周 (2024-W10) 上海门店 GMV 12.3万 (不含退款)\";\n"
            "- 多维度数据用表格呈现, 关键指标在工具返回提供了对比基数(同比/环比数据)时标注同比/环比; 无对比数据则省略, 不编造;\n"
            "- 缺失数据或权限受限(工具返回PERMISSION_DENIED/空结果) 明示\"当前权限无法查询该维度数据/暂无该数据\", 明确标注\"无法查询\", 不编造.\n\n"
            "回答结构规范:\n"
            "1. 结论先行: 一句话给出核心发现/答案;\n"
            "2. 数据支撑: 列出关键指标 (数值 + 时间范围 + 门店 + 口径 + 时效);\n"
            "3. 行动建议: 给出 1-3 条可执行建议 (行动建议按当前角色侧重生成, 店长→现场执行, 运营→活动调优, 总部→战略/资源, 采购→供应链, 财务→经营风险), 无建议则省略.\n\n"
            "权限约束:\n"
            "- 工具返回 PERMISSION_DENIED 或\"权限不足\"时, 当前用户无权调用该工具;\n"
            "- 不要重试该工具, 基于已有数据回答, 并明确告知用户该部分数据无权限查看."
        )

    def plan_judge_system(self) -> str:
        # 零售场景 yes/no 判定: 6 个边界案例覆盖典型场景, 减少 LLM 抖变.
        # yes = 复杂多步任务 (需多个工具配合); no = 简单查询或单步任务.
        # 写操作修正: 凡涉及创建/修改/删除/状态变更等"动作请求", 即使单步也必须调用工具,
        #   判为 yes (需工具执行), 防止 LLM 把写操作当简单查询直接回答而漏调工具.
        return (
            "你是零售场景任务复杂度判定器. 判断用户请求是否需要先制定任务清单/调用工具再执行.\n"
            "- yes: 复杂多步任务 (需多个工具配合, 如: 销售对比分析/促销方案制定/"
            "库存诊断/盘点汇总/补货计划/多店对比/趋势归因)\n"
            "- yes: **写操作/动作请求** (如: 新增会员/创建商品/上架下架/调价/删除/修改/"
            "调整等级/入库出库/盘点/调拨/发货/完成订单/审批/发放券/退款), 此类必须调用对应工具执行;\n"
            "- no: 只读查询 (如: 查订单状态/查库存数量/查促销规则/查政策/查单品价格)\n\n"
            "零售示例:\n"
            "- \"查一下订单 2024001 的状态\" → no (单步查询)\n"
            "- \"上海门店上周 GMV 多少\" → no (单工具单指标)\n"
            "- \"新增一个会员张三 13812345678\" → yes (写操作, 必须调工具新增会员)\n"
            "- \"把李四升到金卡\" → yes (写操作, 必须调工具调整等级)\n"
            "- \"上周销量为什么下滑?\" → yes (需多步归因分析)\n"
            "- \"帮我对 10 个滞销 SKU 做库存诊断并给出补货建议\" → yes (多步多工具)\n"
            "- \"对比上海和北京门店上周销售情况\" → yes (多店多指标对比)\n"
            "- \"查一下会员等级规则\" → no (单步政策查询)\n\n"
            "只输出 yes 或 no, 不要解释."
        )

    def plan_generate_system(self, max_tasks: int) -> str:
        # 零售规划: 覆盖 6 类零售运营典型场景, 增加工具可用性与数据粒度约束.
        # 每个步骤需含查询维度/时间范围, 可独立通过工具完成.
        return (
            "你是零售运营任务规划器. 将用户请求拆解为不超过 {max_tasks} 个有序任务步骤, "
            "覆盖零售运营典型场景:\n"
            "- 库存诊断: 周转分析 → 滞销识别 → 补货建议;\n"
            "- 销售归因: 总量拆解 → 渠道/品类/活动维度对比 → 异常定位;\n"
            "- 补货建议: 销量预测 → 安全库存校验 → 补货量计算;\n"
            "- 活动复盘: 活动前/中/后对比 → ROI 计算 → 经验沉淀;\n"
            "- 多店对比: 指标拉取 → 差异计算 → 排名/归类;\n"
            "- 会员分析: 活跃度统计 → 消费分层 → 复购率计算.\n\n"
            "约束:\n"
            "- 每个步骤应可独立通过工具查询数据完成, 需含查询维度/时间范围/门店范围;\n"
            "- 仅规划当前角色有权执行的任务 (工具权限由 Java RBAC 控制, 无法执行的任务不要规划);\n"
            "- 数据粒度与角色匹配: 店长默认本店, 运营可跨店, 总部可全租户.\n\n"
            "以 JSON 数组输出, 每个元素包含: id (序号), task (任务描述, 含维度/时间/门店), "
            "tool_hint (建议工具, 可空).\n"
            "仅输出 JSON, 不要多余解释."
        ).replace("{max_tasks}", str(max_tasks))

    def plan_generate_structured_system(self, max_tasks: int) -> str:
        # 零售结构化规划: 扩展 11 类场景 + 工具简表 + JSON few-shot + 字段说明.
        # 与老方法分离, 避免解析漂移.
        base = (
            "你是零售运营任务规划器. 将用户请求拆解为不超过 {max_tasks} 个有序任务步骤, "
            "覆盖零售运营典型场景:\n"
            "- 库存诊断: 周转分析 → 滞销识别 → 补货建议;\n"
            "- 销售归因: 总量拆解 → 渠道/品类/活动维度对比 → 异常定位;\n"
            "- 补货建议: 销量预测 → 安全库存校验 → 补货量计算;\n"
            "- 活动复盘: 活动前/中/后对比 → ROI 计算 → 经验沉淀;\n"
            "- 多店对比: 指标拉取 → 差异计算 → 排名/归类;\n"
            "- 会员分析: 活跃度统计 → 消费分层 → 复购率计算;\n"
            "- 采购补货场景: 销量预测 → 在途库存校验 → 采购单生成 → 供应商分配;\n"
            "- 供应商对账: 入库明细拉取 → 退货明细拉取 → 差异核对 → 对账单生成;\n"
            "- 会员生命周期: 新客注册 → 首购转化 → 复购激活 → 沉睡召回;\n"
            "- 促销组合: 券 ROI 测算 → 满减/折扣组合 → 选品匹配 → 活动预算校验;\n"
            "- 财务毛利分析: 收入拆解 → 成本结转 → 费用分摊 → 毛利/净利测算.\n\n"
            "约束:\n"
            "- 每个步骤应可独立通过工具查询数据完成, 需含查询维度/时间范围/门店范围;\n"
            "- 仅规划当前角色有权执行的任务 (工具权限由 Java RBAC 控制, 无法执行的任务不要规划);\n"
            "- 数据粒度与角色匹配: 店长默认本店, 运营可跨店, 总部可全租户.\n"
        ).replace("{max_tasks}", str(max_tasks))
        shortlist = build_tool_shortlist_prompt()
        few_shot = (
            "\n\n===== JSON 输出示例 =====\n"
            "示例 1 (库存诊断):\n"
            '[{"id":1,"task":"查询本店近30天库存周转天数（门店:北京朝阳店,时间范围:近30天）","tool_hint":"inventory_turnover","deps":[]},'
            '{"id":2,"task":"识别本店滞销SKU（库存>=30天未动销）","tool_hint":"inventory_stagnant_list","deps":[1]},'
            '{"id":3,"task":"结合前两步给出补货量建议","tool_hint":"inventory_replenishment_suggest","deps":[1,2]}]\n'
            "示例 2 (单步写操作, 无 plan 实际用途, 仅演示字段):\n"
            '[{"id":1,"task":"新增会员 张三 13812345678（门店:上海徐汇店）","tool_hint":"member_create","deps":[]}]\n'
            "===== 字段说明 =====\n"
            "- id: 正整数, 从 1 递增\n"
            "- task: 任务描述, 必须包含查询维度/时间范围/门店（若缺省注明\"默认角色默认范围\"）\n"
            "- tool_hint: 工具名, 必须选自【当前角色可用工具简表】, 不确定则留空字符串 \"\"\n"
            "- deps: 数字数组, 表示本任务依赖前置任务的 id, 无依赖则空数组 []\n"
            "仅输出 JSON 数组, 不要解释, 不要 ```markdown 包裹."
        )
        return f"{base}\n{shortlist}{few_shot}"

    def plan_inject_format(self, tasks: list) -> str:
        # 任务清单格式化: 参考性, 非强制, ReAct 可按工具返回结果动态调整.
        if not tasks:
            return ""
        lines = ["【参考任务清单】(可按需调整执行顺序, 非强制, 可根据工具返回结果动态调整)"]
        for t in tasks:
            tid = t.get("id", "")
            desc = t.get("task", "")
            hint = t.get("tool_hint", "")
            lines.append(f"  {tid}. {desc}" + (f" [建议工具: {hint}]" if hint else ""))
        return "\n".join(lines)

    def judge_system(self) -> str:
        # 零售评判: 四维度 (数据准确性 + 口径标注 + 时效标注 + 查询回应).
        # 比通用版多口径标注与时效标注维度, 贴合零售合规要求.
        return (
            "你是零售答案质量评判器. 根据以下维度评判答案质量:\n"
            "1. 数据准确性: 数值是否来自工具结果 (而非编造);\n"
            "2. 口径标注: 是否注明口径 (如 GMV 是否含退款) 与时间范围/门店;\n"
            "3. 时效标注: 是否标注数据时效 (实时/T-1), 避免混用过期知识库数值;\n"
            "4. 是否回应查询: 答案是否正面回答了用户问题 (不绕弯/不空泛).\n\n"
            "只输出一个词: ok (合格) 或 degraded (不合格). 不要解释."
        )

    def judge_structured_system(self) -> str:
        # 零售结构化评判: JSON 输出 verdict/dimensions/fix_suggestion + 四维度 + few-shot.
        # 与老 judge_system() 独立, 老方法保持不变, 配置 REFLECT_STRUCTURED=true 时走此方法.
        return (
            "你是零售答案质量评判器. 根据【用户问题】【答案】【工具返回观测值清单】三方对比, "
            "从四维度评判并输出结构化 JSON.\n\n"
            "评判维度 (每维 pass/fail):\n"
            "1. accuracy (数据准确性): 答案中的数字/结论与工具返回观测值是否一致; 数值编造/不符 -> fail\n"
            "2. caliber (口径标注): 涉及GMV/销量/库存周转等指标时是否标注口径+时效+维度\n"
            "3. timeliness (时效标注): 是否标注数据时效(实时/T-1), 避免混用过期知识库数值\n"
            "4. responsive (回应相关性): 是否正面回答用户问题, 不绕弯/不空泛\n\n"
            "输出格式 (仅输出 JSON 对象, 不要解释, 不要 markdown code fence):\n"
            "{\n"
            '  "verdict": "ok" | "degraded",\n'
            '  "dimensions": {\n'
            '    "accuracy": "pass" | "fail",\n'
            '    "caliber":  "pass" | "fail",\n'
            '    "timeliness": "pass" | "fail",\n'
            '    "responsive": "pass" | "fail"\n'
            "  },\n"
            '  "fix_suggestion": "一句话修复建议, 若ok写空字符串"\n'
            "}\n\n"
            "判定规则:\n"
            "- 任一维度 fail 即 verdict=degraded, 否则 verdict=ok\n"
            "- accuracy 优先: 数值错即 degraded (即使其他维度 pass)\n\n"
            "few-shot:\n"
            "【合格答案示例】\n"
            "用户问题: 上海门店上周GMV\n"
            "答案: 上海门店上周GMV为12.3万元 (GMV=已支付金额不含退款, 实时数据, 时间范围:上周)\n"
            '观测值: 1. [sales_query] {"store":"上海门店","period":"上周","gmv":123000,"payment_type":"paid"}\n'
            '判定输出: {"verdict":"ok","dimensions":{"accuracy":"pass","caliber":"pass","timeliness":"pass","responsive":"pass"},"fix_suggestion":""}\n\n'
            "【不合格答案示例】\n"
            "用户问题: 上海门店上周GMV\n"
            "答案: 上海门店上周GMV约15万, 销售情况良好\n"
            '观测值: 1. [sales_query] {"store":"上海门店","period":"上周","gmv":123000}\n'
            '判定输出: {"verdict":"degraded","dimensions":{"accuracy":"fail","caliber":"fail","timeliness":"fail","responsive":"pass"},"fix_suggestion":"更正GMV数值为12.3万元, 并标注GMV口径和数据时效"}\n'
        )

    def business_context(self, role: str = "") -> str:
        """基础零售上下文 + 角色侧重叠加 + 动态口径段注入.

        role 空串则只返回基础上下文 (无角色片段); 命中 _ROLE_OVERLAYS 则追加角色侧重.
        未命中角色映射时返回通用零售从业者 fallback.
        由 graph 在拼装 system prompt 时叠加 (unified_system 之后).
        """
        parts = [_RETAIL_BASE_CONTEXT]
        parts.append(_build_caliber_section())
        role_key = (role or "").strip().lower()
        overlay = _ROLE_OVERLAYS.get(role_key)
        if overlay:
            parts.append(overlay)
        elif role_key:
            parts.append("【角色: 通用零售从业者】不特定限制业务范围, 工具数据范围以RBAC白名单返回为准; 涉及未授权数据直接告知权限不足, 不要重试/编造.")
        return "\n\n".join(parts)

    def rag_wrap(self, context_text: str) -> str:
        """零售 RAG 包装: 【知识库参考】标注 + 优先级说明 (工具>偏好>知识库) + 使用规则 + 引用标注指令.

        解决知识库数值过期被误用问题: 明确告知 LLM 知识库仅供口径/政策参考,
        数字性数据必须以工具实时查询结果为准; 工具数据与知识库冲突时, 以工具为准.

        D1 决策 8: 增加引用标注指令, 要求 LLM 在引用知识库内容处标注 [序号],
        序号对应知识库参考片段 (格式 [1]《文档名》: 内容), 供前端渲染来源标签.
        """
        if not context_text:
            return ""
        return (
            "【知识库参考】\n"
            "【优先级】工具实时返回数值 > 用户长期偏好(格式/风格) > 知识库口径定义/政策/操作流程; 冲突时以前者为准.\n"
            "使用规则:\n"
            "- 以上片段仅用于口径定义/政策/操作流程参考, 数字性数据不要直接引用;\n"
            "- 召回片段按先后顺序编号为 [1][2]..., 回答引用时在原文出处标注编号, 便于追溯;\n"
            "- 当工具数据与知识库内容冲突时, 以工具实时返回结果为准.\n\n"
            f"{context_text}"
        )

    def memory_wrap(self, memory_text: str) -> str:
        """零售长期记忆包装: 标注为用户稳定偏好, 区分格式/风格与查询范围两类.

        下一条回答主动遵循; 涉及数据口径/数值仍以工具实时结果为准.
        """
        if not memory_text:
            return ""
        return (
            "【用户长期偏好】(跨会话稳定, 请主动遵循)\n"
            f"{memory_text}\n\n"
            "以上为用户长期偏好约束, 分两类处理:\n"
            "1) 格式/风格偏好 (如\"只要表格/用简洁风格/只看近7天默认范围/中文数字写法\") → 严格遵循, 体现在最终回答结构/措辞中;\n"
            "2) 查询范围偏好 (如\"只看本店/默认上周\") → 指导工具调用时的时间/门店筛选构造 (不覆盖用户本次显式指定范围);\n"
            "涉及数据口径/数值/权限仍以工具实时返回为准, 主观偏好不改数据结论."
        )


# ============================================================================
# Provider 取值入口
# ============================================================================

def get_provider(ctx_or_state) -> PromptProvider:
    """统一取 provider: 优先 ctx.meta/state 透传, 回退默认零售单例.

    存在该 helper 的原因: UnifiedOrchestrator 独立持有 UnifiedRetailPromptProvider 实例
    并写入 ctx.meta / PreflightState, 实现 per-request 隔离, 避免单例污染.
    通用场景 (面试/测试) 可通过 ctx.meta 透传 UnifiedPromptProvider 切换.

    Args:
        ctx_or_state: FlowContext (graph 内) 或 dict (PreflightState / UnifiedState 等图状态).

    Returns:
        PromptProvider 实例 (ctx.meta/state 指定优先, 否则默认零售单例).
    """
    _default = UnifiedRetailPromptProvider()
    if isinstance(ctx_or_state, FlowContext):
        return (ctx_or_state.meta or {}).get("prompt_provider") or _default
    elif isinstance(ctx_or_state, dict):
        return ctx_or_state.get("prompt_provider") or _default
    return _default
