# ADR-0001：Monorepo 与最终授权边界

- 状态：Accepted
- 日期：2026-08-08

## 决策

使用 npm workspaces 组织前端和 Gateway，Java/Python 保留各自锁文件；公共契约集中在 `contracts/`。Gateway 处理 OIDC/MCP 和 token exchange，但 Risk Core 必须重新验证 JWT、执行 RBAC+ABAC，并通过 PostgreSQL RLS 收口。

## 原因与后果

同仓可原子演进契约、测试和部署，同时服务仍可独立构建。双层授权增加配置成本，但避免只依赖前端/Gateway 过滤。跨服务传递的是最小 scope 的交换 token，不透传原用户 token。
