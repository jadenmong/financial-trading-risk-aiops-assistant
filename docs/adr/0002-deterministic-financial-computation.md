# ADR-0002：确定性金融计算与模型隔离

- 状态：Accepted
- 日期：2026-08-08

所有风险、盈亏、保证金、集中度和对账分类由 Java `BigDecimal`/规则服务生成。LLM 只能解释经过证据校验的结构化结果。任何模型数值若无法逐项对应 evidence ID 与 verifier 输出，运行进入 `NEEDS_REVIEW`。
