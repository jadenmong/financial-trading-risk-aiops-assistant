# 金融交易风控智能运维助手

这是一个机构级、生产化设计的**可运行参考基线**：面向交易运营、风控分析、报告审批、审计和平台值班人员，以只读方式聚合行情、持仓风险、订单成交对账和诊断证据。它提供可部署、可鉴权、可审计、可评测和可演练的工程骨架，但不声称已经获得任何机构、监管或长期生产认证。

> 所有内置账户、交易、行情和异常均为虚构数据。系统没有下单、撤单、改单、持仓修改或风险限额修改能力，不预测或承诺收益，也不构成投资建议。

## 已交付的纵向基线

- `apps/risk-core`：Java 21 / Spring Boot 4.1，使用 `BigDecimal` 实现 A 股与股指期货的确定性风险公式、六类限额、双来源订单对账、maker-checker 报告控制和 append-only 哈希审计。
- `apps/ai-gateway`：Node.js 22 / TypeScript / MCP SDK v2，提供受保护的 `/mcp` Streamable HTTP 与本地 stdio，四个工具全部为业务只读；按调用者 scope 过滤工具发现。
- `apps/ops-console`：Vue 3 运维控制台，包含风险概览、账户风险、对账差异、Agent 时间线、报告审批和审计查询六个页面。
- `apps/market-adapter`：可选 Python 3.12 FastAPI 适配器骨架；默认关闭，AKShare 只能补充公开行情。
- `contracts`：OpenAPI、AsyncAPI 和版本化 JSON Schema；金融数值在 JSON 中一律使用十进制字符串。
- `infra`：Compose、Keycloak realm、OTel/Grafana 基线与 Helm chart；默认模型为 deterministic fake provider。
- `evals`、`load-tests`、`docs/operations`：越权/提示注入集、k6 场景、恢复与故障演练手册。

## 信任边界

```text
Vue 3 Console -- OIDC Authorization Code + PKCE --> Keycloak
       |                                              |
       +---- bearer token ----> AI/MCP Gateway -------+ token exchange
                                  | down-scoped token
                                  v
                           Java Risk Core -- RLS --> PostgreSQL
                                  |  |  |
                           Redpanda MinIO audit/evidence
```

Gateway 校验 `Origin`、`Host`、issuer、audience、expiry 和 scope；生产配置禁止用户 token 直接透传。Risk Core 再执行 RBAC+ABAC，并将 PostgreSQL RLS 作为最终数据边界。审计不可写时，受保护调用 fail closed。Agent 只能调用固定只读工具，不具备 shell、网页、文件系统或交易写权限。

## MCP 工具

| 工具 | Scope | 说明 |
| --- | --- | --- |
| `get_market_snapshot` | `market:read` | 行情快照、新鲜度、质量与证据 |
| `get_position_risk` | `risk:read` | 仓位、敞口、PnL、保证金和限额突破 |
| `reconcile_orders` | `reconciliation:read` | OMS/券商订单成交差异与证据 |
| `generate_daily_report` | `report:preview` | 不落盘的报告预览；正式草稿走 REST |

所有响应均为 `{schemaVersion:"1.0", ok, data/error, meta}`。`meta.evidenceRefs` 中保存内容寻址的 `sha256`、版本与观测时间，便于复核。

## 本地验证

要求 Node.js 22、Java 21、Maven 3.9+；完整环境另需 Docker Compose。

```bash
npm ci
npm run check
npm run smoke

# 完整平台（默认 fake model，不需要模型密钥）
docker compose up --build
```

控制台默认 `http://localhost:5173`，Gateway 为 `http://localhost:3000`，Risk Core 为 `http://localhost:8080`，Keycloak 为 `http://localhost:8081`。参考 profile 只允许本机来源；非本地环境必须设置真实 issuer、audience、客户端密钥引用和严格 allowlist。

本地 MCP 客户端可通过 stdio 启动：

```json
{
  "mcpServers": {
    "risk-aiops": {
      "command": "npm",
      "args": ["run", "start:stdio", "-w", "@risk-aiops/ai-gateway"],
      "cwd": "D:/Jaden/project_github/my-github/financial-trading-risk-aiops-assistant"
    }
  }
}
```

## 生产控制与证据状态

- SLO 设计目标：确定性查询可用性 99.9%、p95 `<500ms`；fake-model Agent p95 `<30s`；RTO 30 分钟、RPO 5 分钟。
- 模型预算：单次 8 秒，OpenAI 最多两次、Anthropic 降级一次；每次运行最多 12 step、6 次模型调用、30 秒、估算 0.25 美元。
- 只有 timeout、429、5xx 或熔断可触发模型降级；安全拒绝、鉴权和 Schema 错误不会换模型绕过。
- `docs/evidence/verification-status.md` 区分“已在本机验证”“需要 Docker/CI 执行”“设计目标”，避免把模板写成实测结果。

## 文档入口

- [架构与边界](docs/architecture.md)
- [威胁模型](docs/security/threat-model.md)
- [数据分类](docs/security/data-classification.md)
- [运行手册](docs/operations/runbook.md)
- [事故响应](docs/operations/incident-response.md)
- [模型卡](docs/model-card.md)
- [SLO 与恢复](docs/operations/slo-and-recovery.md)
- [不宣称认证](docs/no-certification.md)

## 许可证

MIT。公开贡献前请阅读 `SECURITY.md` 与 `CONTRIBUTING.md`。
