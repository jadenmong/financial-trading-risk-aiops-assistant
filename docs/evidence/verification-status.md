# 验证状态

更新时间：2026-08-10

| 项目 | 状态 | 证据位置 |
| --- | --- | --- |
| Node 类型检查/单测/构建 | PASS：8 suites / 20 tests、Gateway/Console 构建通过 | Vitest 与 Vite/tsc 本地输出 |
| Java 风险公式单测/构建 | PASS：6 单元测试；1 个 Docker 集成测试明确跳过；可执行 JAR 构建通过 | Surefire 报告 |
| Risk Core reference HTTP smoke | PASS：readiness、风险查询、优雅关闭 | `grossExposure=174797500.0000000000`，trace ID 已验证 |
| Python 只读适配器 | PASS：1 test | pytest 本地输出 |
| MCP stdio/in-memory smoke | PASS：四工具发现/调用、只读注解、审计与 trace | Vitest + `npm run smoke` |
| 100 条越权/提示注入 fake eval | PASS：50/50 拒绝，泄漏 0 | `evals/results/fake-model-security.json` |
| PostgreSQL 18.4 Testcontainers migration | SKIPPED_LOCAL：未安装 Docker | CI 在有 Docker runner 上执行 |
| Docker Compose config/up | BLOCKED_LOCAL：未安装 Docker | `compose-config` CI job / 后续环境 |
| k6 100 RPS 10 分钟 | NOT RUN | `load-tests/` 场景，不虚构结果 |
| kind/Helm smoke | NOT RUN | CI workflow / 后续环境 |
| 备份恢复 RTO/RPO | NOT RUN | 演练手册，不虚构结果 |
| 真实 OpenAI/Anthropic smoke | NOT RUN | 仅手工、外部 secret 环境 |

本表必须随验证更新；“设计目标”“测试脚本存在”和“实测通过”是不同状态。
