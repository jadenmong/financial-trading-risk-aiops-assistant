# Security Policy

请勿在公开 Issue 中披露漏洞、密钥、真实账户或客户数据。安全问题请通过 GitHub Security Advisory 私下报告，并提供受影响版本、复现条件和影响。维护者目标是在 2 个工作日内确认收件；这不是合同式 SLA。

支持范围为最新发布版本。项目仅允许虚构或公开数据，任何真实敏感数据都应视为安全事件并立即移除、轮换相关凭据和执行历史清理。

## 供应链门禁

CI 强制执行 npm audit、pip-audit、Trivy 文件系统/POM 扫描、Gitleaks、CodeQL，以及镜像 digest 和 GitHub Action commit pin 校验。OWASP Dependency-Check 需要仓库 Secret `NVD_API_KEY`；未配置时不调用不稳定的匿名 NVD API，Java 依赖仍由 Trivy 强制扫描并在 HIGH/CRITICAL 级别阻断。
