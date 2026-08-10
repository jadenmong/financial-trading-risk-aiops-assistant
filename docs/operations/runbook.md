# 运行手册

## 启动检查

1. 校验镜像 digest、SBOM/签名和 Secret 引用；不得使用浮动标签。
2. 检查 PostgreSQL/Flyway、Redpanda topic、MinIO bucket/object lock、Keycloak realm 与 OTel exporter。
3. 验证 Gateway issuer/audience/Host/Origin allowlist 和 Risk Core RLS。
4. 使用 fake model 完成四工具 smoke、审计哈希链和 maker-checker 负向测试。

## 常见告警

- `audit_write_failed`：立即阻断受保护请求，检查 PostgreSQL 权限/容量；不可临时关闭审计。
- `market_data_stale`：确认回放/行情适配器和消费者 lag；陈旧行情不能标记 GOOD。
- `model_provider_circuit_open`：保持确定性查询可用，Agent 转人工；仅允许规定失败类型降级。
- `redpanda_consumer_lag`：查询可继续读取最后物化状态并标记数据时间；恢复后由 eventId 幂等追平。
- `minio_write_failed`：允许报告预览，禁止报告批准落版。

## 优雅关闭

先停止接收新诊断，等待最长 30 秒完成/转 `NEEDS_REVIEW`；flush outbox/audit/telemetry 后退出。不得在处理中强行确认 offset。
