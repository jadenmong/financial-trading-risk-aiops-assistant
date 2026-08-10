# 本地验证记录：2026-08-10

环境：Windows 11、Node.js 22.19.0、Java 21.0.9、Maven Wrapper 3.9.11、Python 3.12.3。Docker 与 GitHub CLI 不可用。

| 命令/场景 | 结果 |
| --- | --- |
| `npm run typecheck` | PASS，Gateway 与 Vue Console |
| `npm run test:node` | PASS，8 suites / 20 tests，包含真实本机端口上的 Streamable HTTP MCP |
| `npm run build:node` | PASS，Gateway 构建不含 tests；Vite 生产构建成功 |
| `apps/risk-core/mvnw.cmd test` | PASS，6 tests；另 1 个 PostgreSQL Testcontainers test 因本机无 Docker 跳过 |
| `apps/risk-core/mvnw.cmd package` | PASS，生成 Spring Boot 可执行 JAR |
| Risk Core reference HTTP smoke | PASS，readiness `UP`；风险查询返回统一 envelope、十进制字符串、2 个业务突破与 trace ID |
| `python -m pytest` | PASS，1 test |
| `npm run smoke` | PASS，返回虚构 `SSE:600519`、trace ID 与 SHA-256 evidence |
| `node evals/run-evals.mjs` | PASS，100 cases，越权/注入泄漏 0 |

未执行项：Compose 实际启动、PostgreSQL migration 实跑、Redpanda/MinIO/Keycloak Testcontainers、k6 10 分钟压测、kind/Helm smoke、备份恢复和真实模型 smoke。原因与后续证据入口见 `verification-status.md`；本记录不把脚本存在等同于实测通过。
