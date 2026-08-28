# 验证状态

更新时间：2026-08-27

| 项目 | 状态 | 证据位置 |
| --- | --- | --- |
| Node 类型检查/单测/构建 | PASS：Gateway 8 suites / 27 tests、Console 3 suites / 8 tests，Gateway/Console 构建通过 | 2026-08-27 本地 `npm run check` 输出；GitHub CI run 31364494317 |
| DeepSeek provider 契约测试 | PASS：端点、Bearer 鉴权、JSON Output、模型元数据、证据 ID 约束与生产门禁 | 2026-08-27 本地 Vitest 输出 |
| Java 风险公式/安全守卫单测与构建 | PASS_LOCAL：9 tests；1 个 Docker 集成测试明确跳过；可执行 JAR 构建通过 | Surefire 报告 |
| Risk Core reference HTTP smoke | PASS：readiness、风险查询、优雅关闭 | `grossExposure=174797500.0000000000`，trace ID 已验证 |
| Python 只读适配器 | PASS：1 test | pytest 本地输出 |
| MCP stdio/in-memory smoke | PASS：四工具发现/调用、只读注解、审计与 trace | Vitest + `npm run smoke` |
| 100 条越权/提示注入 fake eval | PASS：50/50 拒绝，泄漏 0 | `evals/results/fake-model-security.json` |
| PostgreSQL 18.4 Testcontainers migration | PASS_CI；SKIPPED_LOCAL：本机未安装 Docker | GitHub CI 中 migration 8.419s 通过 |
| Docker Compose config/build | PASS_CI；BLOCKED_LOCAL：本机未安装 Docker | GitHub CI 三个应用镜像构建通过 |
| Docker Compose 全栈 up | NOT RUN | 需要具备 Docker 的持久验证环境 |
| Gitleaks/Trivy/依赖审计/三语言 CodeQL | PASS_CI | GitHub Security run 31364494305 |
| k6 100 RPS 10 分钟 | NOT RUN | `load-tests/` 场景，不虚构结果 |
| kind/Helm smoke | NOT RUN | CI workflow / 后续环境 |
| 备份恢复 RTO/RPO | NOT RUN | 演练手册，不虚构结果 |
| 真实 DeepSeek smoke | NOT RUN | 仅手工、外部 secret 环境 |

本表必须随验证更新；“设计目标”“测试脚本存在”和“实测通过”是不同状态。
