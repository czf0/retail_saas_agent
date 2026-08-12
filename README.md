<div align="center">

# Retail SaaS Agent

> 零售行业多租户 SaaS 业务管理平台 + AI 智能体（Agent）底座
>
> 以 **Java 业务网关**为数据中枢、**Python AI Agent**为智能引擎、**Vue 前端**为交互入口，构建「业务系统 + 对话式智能助手」一体化的企业级解决方案。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)
![Python](https://img.shields.io/badge/Python-3.x-3776AB)
![FastAPI](https://img.shields.io/badge/FastAPI-0.110-009688)
![LangGraph](https://img.shields.io/badge/LangGraph-ReAct/RAG-1C3C3C)
![Vue](https://img.shields.io/badge/Vue-3.4-42b883)
![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)
![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Observability-5C4EE5)
![License](https://img.shields.io/badge/License-TBD-lightgrey)

</div>

---

## 📑 目录

- [✨ 核心特性](#-核心特性)
- [🏗️ 项目简介与架构](#️-项目简介与架构)
- [🛠 技术栈](#-技术栈)
- [📁 目录结构](#-目录结构)
- [🚀 快速开始](#-快速开始)
- [🔌 API 概览](#-api-概览)
- [🔐 配置与安全](#-配置与安全)
- [🧪 测试](#-测试)
- [🗺️ 路线图](#️-路线图)
- [🤝 贡献](#-贡献)
- [❓ 常见问题](#-常见问题)
- [📄 License](#-license)

---

## ✨ 核心特性

- **🌐 多租户 / 多门店 SaaS**：数据隔离由拦截器自动注入过滤条件，业务侧零手写 SQL；Sa-Token 动态权限校验。
- **🤖 对话式智能 Agent**：自然语言驱动经营查数、报表生成与业务操作（订单 / 商品 / 会员 / 库存 / 营销 / 退款）。
- **🛠 动态工具平台**：Java 侧注解声明工具定义（SSOT），Python 侧动态装配调用，新增工具零成本接入、与 RBAC 权限天然对齐。
- **🧠 检索增强生成（RAG）**：Chroma 向量 + BM25 关键词混合检索，RRF 融合 + 重排，按租户隔离、答案可溯源。
- **✅ 人工审批（HITL）**：库存调整、退款等不可逆操作执行前需用户确认，状态持久化、可中断续接。
- **⚡ SSE 流式对话**：三端（Java / Python / Vue）端到端流式应答，边生成边渲染。
- **📊 全链路可观测**：OpenTelemetry 打通 Trace / Metrics / Logs，配套 Grafana 统一大盘。
- **🔐 企业级安全治理**：多租户隔离、权限白名单、LLM 预算限流、降级收紧、统一错误码。

---

## 🏗️ 项目简介与架构

本项目是一套**零售多租户 SaaS + 通用 AI Agent 框架**，采用前后端分离 + 异构微服务架构：

- **后端业务**：以 Spring Boot 实现多租户、多门店隔离的完整零售业务域（商品、订单、会员、促销、库存、报表等），并作为 **Agent 的数据与工具中枢（SSOT）**，为 AI 提供统一的数据访问与权限校验能力。
- **AI 引擎**：以 FastAPI 实现的通用 Agent 框架，内置 **RAG 检索增强生成、LLM 工具调用、多轮记忆、流式编排（ReAct/Plan-Exec/Workflow）**，通过 SSE 流式接口对外服务。
- **交互前端**：Vue 3 + Element Plus 的零售业务管理台 + Agent 对话界面。
- **可观测体系**：基于 OpenTelemetry（OTel）打通 Trace / Metrics / Logs，配套 Grafana 统一大盘。

```mermaid
flowchart LR
    subgraph FE["前端 frontend (:5173)"]
        UI["Vue3 + Element Plus<br/>业务管理台 + Agent 对话"]
    end
    subgraph BE["后端业务 service-java-business (:8080)"]
        GW["Spring Boot 业务网关<br/>RBAC + 多租户/门店隔离"]
        DB[("MySQL 业务库<br/>+ Redis 缓存")]
        KB["知识库管理<br/>data/kb_files"]
    end
    subgraph PY["AI 引擎 service-python-agent (:8000)"]
        SSE["SSE 流式对话<br/>/api/v1/agent/stream/chat"]
        ORC["编排器 new_agent<br/>ReAct / Plan-Exec / Workflow"]
        RAG["RAG 引擎<br/>Chroma 向量 + BM25"]
        MEM["多轮记忆"]
        TOOL["工具调用 → Java 后端"]
    end
    subgraph OBS["可观测 obs-stack"]
        COL["OTel Collector :4317"]
        TEMPO["Tempo Trace :3200"]
        PROM["Prometheus :9090"]
        GRAF["Grafana :3000"]
    end

    UI -->|HTTP /api/v1| GW
    UI -->|SSE| SSE
    GW --> DB
    GW --> KB
    GW -->|内部 HTTP :8000| PY
    SSE --> ORC --> RAG
    ORC --> MEM
    ORC --> TOOL -->|X-Internal-Secret| GW
    PY -->|OTLP :4317| COL --> TEMPO
    PY -->|/metrics| PROM
    COL --> PROM
    PROM --> GRAF
    TEMPO --> GRAF
```

---

## 🛠 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| **后端** | Java / Spring Boot / Spring Web | 17 / 3.2.5 |
| | MyBatis-Plus（多租户自动过滤、逻辑删除、枚举处理） | 3.5.6 |
| | MySQL | 8.x |
| | Redis（缓存 / 会话） | — |
| | Sa-Token（JWT 鉴权 + 权限） | 1.37.0 |
| | MapStruct（编译期实体↔DTO 转换） / Lombok / Hutool | 1.5.5.Final / — / 5.8.27 |
| **AI 引擎** | Python / FastAPI / Uvicorn | 3.x / 0.110.0 / 0.27.1 |
| | SSE（流式对话） / Pydantic / httpx / Redis | sse-starlette 2.0.0 |
| | Chroma 向量库 / BM25（jieba 分词）/ NumPy | — |
| | OpenTelemetry SDK + FastAPI/httpx/Redis 插桩 | 1.33.0 / 0.54b0 |
| **前端** | Vue / Vite / TypeScript | 3.4 / 5.2 / 5.3 |
| | Element Plus / Pinia / Vue Router / ECharts | 2.7 / 2.1 / 4.3 / 5.5 |
| **可观测** | OTel Collector / Tempo / Prometheus / Grafana（obs-stack） | 0.103.0 / 2.5.0 / 2.53.0 / 11.1.0 |

---

## 📁 目录结构

```
retail_saas_agent/
├── service-java-business/        # 后端业务服务（Spring Boot, :8080）
│   ├── src/main/java/com/retail/
│   │   ├── business/             # 零售业务域（商品/会员/订单/促销/库存/报表/知识库…）
│   │   └── rbac/                 # 权限体系（用户/角色/菜单/门店，Sa-Token 多租户）
│   ├── src/main/resources/
│   │   ├── application.yml       # 核心配置（数据源/Redis/多租户/门店隔离/文件上传）
│   │   └── sql/retail_business.sql  # 数据库初始化脚本
│   └── data/kb_files/            # 知识库种子文档（启动时幂等灌入）
├── service-python-agent/         # AI 引擎（FastAPI, :8000）
│   ├── main.py                   # 唯一启动入口，SSE 流式接口
│   ├── new_agent/                # 当前活跃编排器（对象化重构版，复刻 Unified 行为）
│   ├── agent/ other_agent/ unified_agent/ flow_architecture/  # 历史/演进版本
│   ├── config/                   # 分层配置（base/llm/storage/observability）
│   ├── rag/  memory/  tool/  skill/  api/  core/  infra/
│   ├── requirements.txt
│   └── data/                     # 运行时数据（chroma/ bm25/，不入库）
├── frontend/                     # 前端管理台（Vite, :5173）
│   └── src/views/                # 页面：系统管理 / 业务管理 / Agent 对话 / 知识库 / 大盘
├── obs-stack/                    # 可观测：OTel Collector + Tempo + Prometheus + Grafana
└── otel-lgtm/                    # 轻量可观测替代方案（Grafana LGTM 一体镜像）
```

> **说明**：`service-python-agent` 下存在多个 Agent 实现目录（`agent` / `new_agent` / `other_agent` / `unified_agent` / `flow_architecture`），当前**活跃编排器锁定为 `new_agent`**（见 [main.py](service-python-agent/main.py) 顶部注释），其余为历史演进版本，作为参考保留。

---

## 🚀 快速开始

### 前置依赖

| 依赖 | 说明 |
|------|------|
| JDK 17+ | 后端运行环境 |
| Maven 3.8+ | 后端构建 |
| Python 3.9+ | AI 引擎 |
| Node.js 18+ | 前端 |
| MySQL 8.x | 业务数据 |
| Redis | 缓存 / 会话 / Agent 状态持久化 |
| Docker + Docker Compose | 可观测体系（可选） |

### 1. 数据库初始化

```bash
# 创建数据库
CREATE DATABASE retail_business DEFAULT CHARACTER SET utf8mb4;

# 导入初始化脚本（表结构 + 种子数据）
mysql -uroot -p retail_business < service-java-business/src/main/resources/sql/retail_business.sql
```

### 2. 启动后端（Java, :8080）

```bash
cd service-java-business

# 1) 按需修改 src/main/resources/application.yml 的数据源 / Redis 配置
# 2) 构建并启动
mvn spring-boot:run
```

### 3. 启动 AI 引擎（Python, :8000）

```bash
cd service-python-agent

# 1) 安装依赖
pip install -r requirements.txt

# 2) 配置环境变量（参考 .env 模板；不泄露真实密钥，从 .env 读取）
#    LLM_API_KEY / LLM_BASE_URL / LLM_MODEL / INTERNAL_SECRET /
#    Redis 地址 / Java 基地址 / OTel 导出地址 等

# 3) 启动（启动时会 fail-fast 校验必配项，缺失会阻止启动）
uvicorn main:app --host 127.0.0.1 --port 8000
```

> 启动会同步 Java `/tools/registry` 工具定义并执行 LLM / 内部密钥等配置校验；Java 不可用时降级不阻断启动。

### 4. 启动前端（:5173）

```bash
cd frontend

npm install
npm run dev        # 开发模式，代理 /api → http://127.0.0.1:8080
# npm run build    # 生产构建（vue-tsc 类型检查 + vite build）
```

### 5. 启动可观测体系（可选）

```bash
# 方案一：完整可观测栈（OTel Collector + Tempo + Prometheus + Grafana）
cd obs-stack && docker compose up -d

# 方案二：轻量一体镜像（Grafana LGTM）
cd otel-lgtm && docker compose up -d
```

---

## 🔌 API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agent/stream/chat` | 流式对话（SSE） |
| POST | `/api/v1/agent/stream/resume` | HITL 审批恢复（SSE） |
| GET | `/api/v1/agent/tools` | 已注册工具列表（调试） |
| GET | `/api/v1/agent/skills` | 已注册 Skill 列表（调试） |
| GET | `/api/v1/agent/metrics` | 内存指标快照（调试） |
| GET | `/metrics` | Prometheus exposition 格式指标 |
| GET | `/health` | 健康检查 |

### 服务端口

| 服务 | 地址 | 说明 |
|------|------|------|
| 后端业务 | http://localhost:8080 | Spring Boot 业务网关 |
| AI 引擎 | http://localhost:8000 | FastAPI；`/health` 探活 |
| 前端 | http://localhost:5173 | Vite Dev Server |
| OTel Collector | :4317 / :4318 / :8889 | OTLP gRPC / HTTP / Prom exporter |
| Tempo | http://localhost:3200 | Trace 查询（经 Grafana） |
| Prometheus | http://localhost:9090 | 指标 |
| Grafana | http://localhost:3000 | 大盘（obs-stack 默认 `admin/admin`） |
| Redis | :6379（RedisInsight :8001） | 缓存 / 会话 |

> 另含 Java → Python 内部服务接口（知识库同步 / 文件解析 / 记忆抽取），仅供后端内部调用，通过 `X-Internal-Secret` 鉴权。

---

## 🔐 配置与安全

- **密钥不入库**：`service-python-agent/.env` 与 `.env.prod` 包含 `LLM_API_KEY`、`INTERNAL_SECRET` 等敏感项，已被 `.gitignore` 排除，**必须从本地 `.env` 读取**。
- **内部调用鉴权**：Python 回调 Java 使用 `X-Internal-Secret` 建立临时登录态，避免透传用户 Token，仅限内网。
- **生产环境**：建议关闭知识库种子灌入开关（`kb.seed.enabled=false`），并将 `APP_ENV=prod` 读取 `.env.prod`。
- **多租户/门店隔离**：数据访问由拦截器自动注入过滤条件，业务侧无需手动拼 SQL。

---

## 🧪 测试

| 模块 | 命令 | 说明 |
|------|------|------|
| 后端（Java） | `cd service-java-business && mvn test` | 单元测试 |
| 前端（Vue） | `cd frontend && npm run build` | 生产构建（含 `vue-tsc` 类型检查 + Vite build） |
| AI 引擎（Python） | `cd service-python-agent && uvicorn main:app --port 8000` | 启动即 `fail-fast` 校验必配项与 LLM/密钥连通性 |

> Python 侧当前以「启动配置校验 + 运行期接口自检」为主，尚未引入独立单测框架；如需补充可纳入 [路线图](#️-路线图)。

---

## 🗺️ 路线图

> 基于项目经验与扩展点提炼，标注状态：✅ 已完成 · 🚧 进行中 · 📋 规划中

- ✅ **三端 SSE 流式问答链路**：Java 业务后端 / Python 编排 / Vue 前端端到端打通。
- ✅ **动态工具平台 + HITL 人工审批**：破坏性操作默认不放行、状态可恢复。
- ✅ **混合检索 RAG**：向量 + BM25 + 融合 + 重排，多租户隔离、答案可溯源。
- ✅ **可观测与审计**：OpenTelemetry 全链路 + 审计留痕。
- 🚧 **token 预算真正限流**：将 token 预算接入执行器，实现按请求截断/限流（当前为预留未消费）。
- 📋 **多租户 / 角色差异化预算**：按 `tenant_id` / `role` 分级限流。
- 📋 **长期记忆升级**：显式记忆抽取（摘要/实体）+ 按用户维度向量记忆。
- 📋 **评测与回归闭环**：沉淀坏例标注 + 自动回归数据集，量化幻觉抑制与意图路由准确率。
- 📋 **多 Agent 协同**：规划器 + 多执行器编排，配全局预算控制。
- 📋 **观察体系大盘指标**：搭建P95、P99、多维度Token损耗、RAG召回准确率等大盘指标。
---

## ❓ 常见问题

- **后端启动失败**：检查 MySQL / Redis 连接配置，及是否已执行数据库初始化脚本。
- **Python 启动报 `FATAL`**：为启动前配置校验，按提示补齐 `LLM_API_KEY`、`INTERNAL_SECRET` 等必配项。
- **前端接口 404**：确认后端端口与 `vite.config.ts` 代理 `target` 一致（默认 `http://127.0.0.1:8080`）。
- **Trace 看不到**：确认 `obs-stack` 已启动且 Python 的 OTel 导出地址指向 `127.0.0.1:4317`。

<div align="center">

**Retail SaaS Agent** · 让运营用一句话搞定经营分析与业务操作

</div>
