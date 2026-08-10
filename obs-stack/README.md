# obs-stack 可观测外部组件栈

零售 SaaS Agent 可观测可视化的外部组件编排: OTel Collector + Jaeger + Prometheus + Grafana.
覆盖 4 个维度: 链路时间线(Jaeger) / 指标大盘(Grafana) / 工具调用统计(Grafana+自建页) / 审计列表重放(自建页).

## 需要的外部软件支撑

| 软件 | 作用 | 是否必须 | 获取方式 |
|------|------|----------|----------|
| **Docker Desktop** | 运行 docker-compose 编排下列 4 个容器 | 必须 | Windows 手动安装 (含 WSL2 后端), https://www.docker.com/products/docker-desktop |
| OTel Collector Contrib | 接收 Python OTLP, 协议转换分发 traces/metrics | 必须 | `docker compose up` 自动拉取镜像 |
| Jaeger all-in-one | Trace 链路存储与可视化 | 必须 (链路时间线依赖) | 镜像自动拉取 |
| Prometheus | 指标抓取/存储/查询 | 必须 (指标大盘依赖) | 镜像自动拉取 |
| Grafana | 指标大盘可视化 | 必须 (大盘维度) | 镜像自动拉取 |

> 唯一需手动安装的是 **Docker Desktop**. 其余 4 个组件由 `docker compose up -d` 自动拉取镜像.
> Python 端依赖 (opentelemetry-sdk 等) 见项目根 `requirements.txt`, 已声明.

## 拓扑

```
Python (host :8000)
  ├─ OTLP gRPC traces+metrics ──► OTel Collector (:4317)
  │                                 ├─ traces  ──► Jaeger (UI :16666)
  │                                 └─ metrics ──► Prometheus exporter (:8889) ◄── Prometheus (:9090)
  └─ /metrics (Prom exposition) ────────────────────────────────────► Prometheus (直连, 互补)
Grafana (:3000) ◄── datasource: Jaeger + Prometheus
```

## 启动

```powershell
cd "c:\Users\JoFend\Desktop\Agent Project\retail_saas_agent\service-python-agent\obs-stack"
docker compose up -d          # 拉取镜像并启动 4 容器
docker compose ps             # 确认 4 容器均 Up
```

访问地址:
- Jaeger UI: http://localhost:16666 (Service 下拉选 service-python-agent-unified)
- Prometheus: http://localhost:9090 (Status → Targets 确认 otel-collector / python-agent-direct 均 UP)
- Grafana: http://localhost:3000 (admin/admin, Dashboards → Agent → Agent 可观测大盘)

## 停止与清理

```powershell
docker compose down           # 停止并删除容器 (保留数据卷)
docker compose down -v        # 同时删除 Prometheus/Grafana 数据卷 (开发期常用)
docker compose logs -f otel-collector   # 查看 Collector 日志调试
```

## 双抓取设计说明

Prometheus 抓取两个来源 (互补):
1. `otel-collector:8889` — OTel SDK 原生指标, 含直方图分桶 (`_bucket`), 支持 `histogram_quantile` 算 P99/P95;
2. `host.docker.internal:8000/metrics` — Python 进程内 mirror 快照, 含 `avg` 字段 (OTel 直方图原生不导出 avg).

工具统计自建页 (`/obs/dashboard/tools`) 优先读 mirror 的 avg; 精确分位数走 Grafana PromQL.

## 配置文件清单

| 文件 | 作用 |
|------|------|
| docker-compose.yml | 4 容器编排 |
| otel-collector-config.yaml | Collector 接收 OTLP, 分发 traces→Jaeger / metrics→Prom exporter |
| prometheus.yml | 双抓取配置 (Collector + Python 直连) |
| prometheus-rules.yml | 告警规则 (工具失败率/编排崩溃) |
| grafana/provisioning/datasources/datasources.yml | Jaeger + Prometheus 数据源自动注入 |
| grafana/provisioning/dashboards/dashboards.yml | dashboard 文件 provider |
| grafana/provisioning/dashboards/agent-overview.json | Agent 可观测大盘 (9 面板) |
