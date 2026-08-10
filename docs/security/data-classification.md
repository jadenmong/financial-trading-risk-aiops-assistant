# 数据分类

| 等级 | 示例 | 仓库策略 | 运行策略 |
| --- | --- | --- | --- |
| Public | 源码、公开行情、文档 | 可提交 | 可公开 |
| Synthetic | 虚构账户/订单/持仓 | 可提交，必须显式标注 | 仅演示/测试 |
| Internal | 指标、非敏感配置 | 不默认公开 | 最小权限 |
| Confidential | 真实账户、订单、人员、客户 | 禁止提交 | 加密、ABAC/RLS、审计 |
| Secret | token、API key、私钥 | 永不提交 | Secret manager、轮换、禁止日志 |

日志和审计只记录脱敏标识、哈希和必要授权上下文；不记录 bearer token、密钥、完整 prompt 原文或异常堆栈。
