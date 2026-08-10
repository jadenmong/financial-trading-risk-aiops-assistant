# ADR-0003：受保护调用的审计 fail closed

- 状态：Accepted
- 日期：2026-08-08

授权决策、工具调用、Agent step、报告决定在返回业务数据前写 append-only 审计。写入失败时不返回受保护数据。事件包含前序哈希和自身哈希，每日 manifest 写入对象存储。生产应用账号不具备审计表 UPDATE/DELETE 权限。
