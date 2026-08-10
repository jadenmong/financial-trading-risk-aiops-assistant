# SLO 与恢复目标

| 服务 | 指标 | 目标 |
| --- | --- | --- |
| 确定性查询 | 月可用性 | 99.9%（设计目标） |
| 确定性查询 | p95 | <500ms |
| fake-model Agent | p95 | <30s |
| 恢复 | RTO | 30 分钟 |
| 恢复 | RPO | 5 分钟 |

RTO/RPO 通过 PostgreSQL PITR/WAL、MinIO versioning/replication、配置与镜像不可变部署实现。`scripts/drill.ps1` 定义 Redpanda、MinIO、审计、模型和恢复场景。每次演练应在 `docs/evidence/generated/` 保存开始/结束时间、命令、指标截图/导出、实际 RTO/RPO 和偏差。未执行记录不得标为通过。
