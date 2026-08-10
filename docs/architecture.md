# 架构与关键边界

系统采用可迁移 monorepo。Gateway 是 MCP/OIDC 边界和 Agent 编排入口；Risk Core 是授权、金融计算、报告治理和审计的最终执行点。PostgreSQL 是物化状态与审计真相源，Redpanda 承载版本化事件，MinIO 保存不可变报告和每日审计 manifest。

## 数据流

1. 仿真回放器发布带全局唯一 `eventId`、版本、来源时间的 JSON 事件。
2. Risk Core 以 `eventId` 幂等消费，拒绝不可接受的未来时间，标记陈旧/乱序数据，并写入物化表。
3. 服务自产事件与状态变更在同一事务写 transactional outbox，再由发布器投递。
4. Gateway 校验 token 并交换为 down-scoped Risk Core token；Risk Core 再执行 RBAC、账户 ABAC 和 RLS。
5. 所有工具结果引用 evidence ID 与 SHA-256。数值 verifier 不接受模型生成的未对齐金融结论。

## Agent 状态机

```text
QUEUED -> TRIAGE -> MARKET / RISK / RECONCILIATION
       -> EVIDENCE_VERIFY -> REPORT_SYNTHESIS -> POLICY_CHECK
       -> COMPLETED | NEEDS_REVIEW | FAILED | CANCELLED
```

并行只发生在固定 DAG 的三个确定性读取节点。Supervisor、Market Context、Risk Analysis、Reconciliation、Evidence Verifier、Report 各自拥有显式工具 allowlist。预算、失败类型和转换表由代码决定，不由模型自由决定。

## 精度与时间

- 数据库使用 `DECIMAL(38,10)`；Java 使用 `BigDecimal` 并显式 rounding mode。
- JSON 金融数值全部为字符串；TypeScript 不进行金额、价格、数量、敞口或比率计算。
- 服务内部使用 UTC `Instant`；交易日使用 `Asia/Shanghai` 和 ISO 日期，不依赖宿主默认时区。

## 生产差异

reference profile 使用固定仿真数据和 fake model，便于离线复现。production profile 必须启用 PostgreSQL、Keycloak、MinIO、Redpanda、外部 secrets、TLS 和严格网络策略；禁止启用本地开发身份头或内存持久化。
