# STRIDE 威胁模型

| 威胁 | 主要场景 | 控制 | 验证 |
| --- | --- | --- | --- |
| Spoofing | 伪造 JWT/主体 | issuer、audience、签名、expiry；Token Exchange | 过期/错误 aud/iss 契约测试 |
| Tampering | 修改证据或报告 | SHA-256、审计哈希链、批准版本不可变 | 篡改验证测试、MinIO object lock 生产配置 |
| Repudiation | 否认工具/审批操作 | append-only 审计、主体/client/scope/trace | 哈希链校验与每日 manifest |
| Information disclosure | 跨 desk/account、提示注入外泄 | RBAC+ABAC+RLS、最小 scope、输出 Schema | 100 条越权/注入集，目标泄漏 0 |
| Denial of service | 模型/事件/HTTP 耗尽 | 超时、限流、熔断、预算、HPA/PDB | k6 与故障演练 |
| Elevation of privilege | token passthrough、工具越权 | 标准 token exchange/downscope、按 scope 发现工具 | 负向 MCP 集成测试 |

剩余风险包括 reference 环境的共享开发凭据、单节点内存状态和未执行的容器演练；它们不得带入生产 profile。模型输出视为不可信输入，不能改变授权、预算或交易状态。
