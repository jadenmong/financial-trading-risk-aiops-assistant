# 模型卡：诊断解释层 v1

- 用途：解释确定性行情、风险和对账结果，生成报告预览。
- 禁止用途：交易建议、收益预测、下单/撤改、风险限额修改、授权决策、独立金融计算。
- 主模型：DeepSeek Chat Completions API，默认 `deepseek-v4-pro`，基地址 `https://api.deepseek.com`，使用 JSON Output 和低/中 reasoning effort。
- 模型仅用于日报预览与对账差异解释；所有金融数值和证据均来自 Risk Core，模型返回的证据 ID 必须属于输入证据集。
- CI：deterministic fake model；公开仓库与默认 Compose 不需要真实密钥。
- 预算：8 秒/调用；12 steps、6 model calls、30 秒、估算 0.25 USD/运行。timeout、429 与 5xx 标记为可重试，鉴权和 Schema 错误不降级。
- 人工接管：证据不一致、预算超限、策略拒绝、低质量/陈旧数据均进入 `NEEDS_REVIEW`。
- 评测目标：工具选择 ≥95%，结构化输出 ≥99%，数值/证据一致率 100%，越权/注入泄漏 0。
