# 模型卡：诊断解释层 v1

- 用途：解释确定性行情、风险和对账结果，生成报告预览。
- 禁止用途：交易建议、收益预测、下单/撤改、风险限额修改、授权决策、独立金融计算。
- 主模型：OpenAI Responses API `gpt-5.6-terra`，结构化输出、`store:false`、低/中 reasoning effort、隐私化 `safety_identifier`。
- 降级模型：`claude-sonnet-5`，仅在 timeout、429、5xx 或主 Provider 熔断时使用。
- CI：deterministic fake model；公开仓库与默认 Compose 不需要真实密钥。
- 预算：8 秒/调用，主模型最多两次、降级一次；12 steps、6 model calls、30 秒、估算 0.25 USD/运行。
- 人工接管：证据不一致、预算超限、策略拒绝、低质量/陈旧数据均进入 `NEEDS_REVIEW`。
- 评测目标：工具选择 ≥95%，结构化输出 ≥99%，数值/证据一致率 100%，越权/注入泄漏 0。
