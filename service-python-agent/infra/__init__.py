"""
infra/ 包: 外部服务基础设施层 (Agent 无关, 跨 Agent 复用).

关键边界约束: infra 层禁止反向 import graph / state / orchestrator 等业务层组件,
保证基础设施独立稳定, 不被具体 Agent 的实现细节污染.
"""