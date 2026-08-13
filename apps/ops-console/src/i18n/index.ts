export const LOCALES = ['zh-CN', 'en-US'] as const;

export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = 'zh-CN';
export const LOCALE_STORAGE_KEY = 'risk-aiops.locale';

const zhCN = {
  'document.title': '金融交易风控智能运维助手',
  'brand.tagline': '生产级运维助手',
  'nav.overview': '总览',
  'nav.accountRisk': '账户风险',
  'nav.reconciliation': '对账',
  'nav.diagnosis': '诊断',
  'nav.reports': '报告',
  'nav.audit': '审计',
  'header.readOnly': '只读',
  'header.anonymous': '匿名用户',
  'header.login': 'OIDC 登录',
  'header.logout': '退出登录',
  'header.language': '语言',
  'language.zhCN': '中文',
  'language.enUS': 'English',
  'boundary.title': '只读边界',
  'boundary.description': '不具备交易写入能力',
  'page.dashboard.title': 'Risk AIOps 总览',
  'page.dashboard.subtitle': '只读生产运维控制台。不提供下单、撤单、持仓或限额变更控制。',
  'page.accountRisk.title': '账户风险',
  'page.accountRisk.subtitle': '金融数值以确定性后端计算得到的十进制字符串展示。',
  'page.reconciliation.title': '对账差异',
  'page.reconciliation.subtitle': '用于运营审查的只读 OMS 与券商成交比对。',
  'page.diagnosis.title': '诊断时间线',
  'page.diagnosis.subtitle': '固定的只读 DAG，包含持久化状态、证据校验和策略检查。',
  'page.reports.title': '受治理报告',
  'page.reports.subtitle': '包含经办复核、乐观锁和不可变报告内容的草稿与审批流程。',
  'page.audit.title': '审计检索',
  'page.audit.subtitle': '带有哈希链验证字段的仅追加审计事件。',
  'page.authCallback.title': '正在完成 OIDC + PKCE 登录',
  'metric.grossExposure': '总敞口',
  'metric.netExposure': '净敞口',
  'metric.leverage': '杠杆率',
  'metric.marginUtilization': '保证金使用率',
  'metric.activeBreaches': '活跃超限',
  'metric.reconciliationBreaks': '对账差异',
  'metric.dataQuality': '数据质量',
  'section.openIncidents': '未关闭事件',
  'section.positions': '持仓',
  'section.limitBreaches': '限额超限',
  'column.severity': '严重级别',
  'column.status': '状态',
  'column.account': '账户',
  'column.title': '标题',
  'column.evidence': '证据',
  'column.instrument': '合约',
  'column.side': '方向',
  'column.quantity': '数量',
  'column.marketValue': '市值',
  'column.unrealizedPnl': '未实现盈亏',
  'column.limit': '限额',
  'column.actual': '实际值',
  'column.limitValue': '限额值',
  'column.type': '类型',
  'column.order': '订单',
  'column.execution': '成交',
  'column.expected': '预期值',
  'column.currency': '币种',
  'column.report': '报告',
  'column.creator': '创建人',
  'column.version': '版本',
  'column.action': '操作',
  'column.time': '时间',
  'column.subject': '主体',
  'column.eventHash': '事件哈希',
  'button.createDiagnosis': '创建诊断',
  'button.approve': '批准',
  'empty.incidents': 'API 未返回事件',
  'empty.positions': 'API 未返回持仓',
  'empty.limitBreaches': 'API 未返回超限记录',
  'empty.reconciliation': 'API 未返回对账差异',
  'empty.diagnosis': '当前会话尚未创建诊断',
  'empty.reports': 'API 未返回报告',
  'empty.auditEvents': 'API 未返回审计事件',
  'error.apiUnavailable': '接口暂时不可用',
  'error.diagnosisFailed': '诊断创建失败',
  'error.approvalFailed': '报告审批失败',
  'error.loginFailed': 'OIDC 登录失败',
} as const;

export type MessageKey = keyof typeof zhCN;
type Messages = Record<MessageKey, string>;

const enUS: Messages = {
  'document.title': 'Financial Trading Risk AIOps Assistant',
  'brand.tagline': 'Production-grade assistant',
  'nav.overview': 'Overview',
  'nav.accountRisk': 'Account Risk',
  'nav.reconciliation': 'Reconciliation',
  'nav.diagnosis': 'Diagnosis',
  'nav.reports': 'Reports',
  'nav.audit': 'Audit',
  'header.readOnly': 'READ ONLY',
  'header.anonymous': 'anonymous',
  'header.login': 'OIDC login',
  'header.logout': 'Logout',
  'header.language': 'Language',
  'language.zhCN': '中文',
  'language.enUS': 'English',
  'boundary.title': 'Read-only boundary',
  'boundary.description': 'No trading write capability',
  'page.dashboard.title': 'Risk AIOps Overview',
  'page.dashboard.subtitle': 'Read-only production operations console. No order, cancel, position or limit mutation controls are exposed.',
  'page.accountRisk.title': 'Account Risk',
  'page.accountRisk.subtitle': 'Financial values are displayed as decimal strings from deterministic backend computation.',
  'page.reconciliation.title': 'Reconciliation Breaks',
  'page.reconciliation.subtitle': 'Read-only OMS and broker execution comparison for operations review.',
  'page.diagnosis.title': 'Diagnosis Timeline',
  'page.diagnosis.subtitle': 'Fixed read-only DAG with persisted state, evidence verification and policy check.',
  'page.reports.title': 'Governed Reports',
  'page.reports.subtitle': 'Draft and approval workflow with maker-checker, optimistic locking and immutable report content.',
  'page.audit.title': 'Audit Search',
  'page.audit.subtitle': 'Append-only audit events with hash-chain verification fields.',
  'page.authCallback.title': 'Completing OIDC + PKCE login',
  'metric.grossExposure': 'Gross exposure',
  'metric.netExposure': 'Net exposure',
  'metric.leverage': 'Leverage',
  'metric.marginUtilization': 'Margin utilization',
  'metric.activeBreaches': 'Active breaches',
  'metric.reconciliationBreaks': 'Reconciliation breaks',
  'metric.dataQuality': 'Data quality',
  'section.openIncidents': 'Open incidents',
  'section.positions': 'Positions',
  'section.limitBreaches': 'Limit breaches',
  'column.severity': 'Severity',
  'column.status': 'Status',
  'column.account': 'Account',
  'column.title': 'Title',
  'column.evidence': 'Evidence',
  'column.instrument': 'Instrument',
  'column.side': 'Side',
  'column.quantity': 'Quantity',
  'column.marketValue': 'Market value',
  'column.unrealizedPnl': 'Unrealized PnL',
  'column.limit': 'Limit',
  'column.actual': 'Actual',
  'column.limitValue': 'Limit value',
  'column.type': 'Type',
  'column.order': 'Order',
  'column.execution': 'Execution',
  'column.expected': 'Expected',
  'column.currency': 'Currency',
  'column.report': 'Report',
  'column.creator': 'Creator',
  'column.version': 'Version',
  'column.action': 'Action',
  'column.time': 'Time',
  'column.subject': 'Subject',
  'column.eventHash': 'Event hash',
  'button.createDiagnosis': 'Create diagnosis',
  'button.approve': 'Approve',
  'empty.incidents': 'No incidents returned by API',
  'empty.positions': 'No positions returned by API',
  'empty.limitBreaches': 'No breaches returned by API',
  'empty.reconciliation': 'No reconciliation breaks returned by API',
  'empty.diagnosis': 'No diagnosis created in this session',
  'empty.reports': 'No reports returned by API',
  'empty.auditEvents': 'No audit events returned by API',
  'error.apiUnavailable': 'API is temporarily unavailable',
  'error.diagnosisFailed': 'Failed to create diagnosis',
  'error.approvalFailed': 'Failed to approve report',
  'error.loginFailed': 'OIDC login failed',
};

const messages: Record<Locale, Messages> = { 'zh-CN': zhCN, 'en-US': enUS };

export type EnumGroup = 'auditOutcome' | 'diagnosisState' | 'incidentStatus' | 'positionSide' | 'qualityStatus' | 'reconciliationType' | 'reportStatus' | 'severity';

const enumMessages: Record<Locale, Record<EnumGroup, Record<string, string>>> = {
  'zh-CN': {
    severity: { INFO: '提示', WARNING: '警告', CRITICAL: '严重' },
    incidentStatus: { OPEN: '未关闭', ACKED: '已确认', CLOSED: '已关闭' },
    reportStatus: { DRAFT: '草稿', APPROVED: '已批准', REJECTED: '已拒绝' },
    diagnosisState: { QUEUED: '已排队', TRIAGE: '分诊', MARKET: '市场数据', RISK: '风险计算', RECONCILIATION: '对账', EVIDENCE_VERIFY: '证据校验', REPORT_SYNTHESIS: '报告生成', POLICY_CHECK: '策略检查', COMPLETED: '已完成', NEEDS_REVIEW: '需要复核', FAILED: '失败', CANCELLED: '已取消' },
    positionSide: { LONG: '多头', SHORT: '空头' },
    qualityStatus: { FRESH: '最新', HEALTHY: '健康', DEGRADED: '已降级', STALE: '过期', UNKNOWN: '未知' },
    reconciliationType: { ORPHAN_EXECUTION: '孤立成交', MISSING_EXECUTION: '缺失成交', QUANTITY_MISMATCH: '数量不匹配', PRICE_MISMATCH: '价格不匹配', STATUS_MISMATCH: '状态不匹配', CURRENCY_MISMATCH: '币种不匹配' },
    auditOutcome: { success: '成功', SUCCESS: '成功', failure: '失败', FAILURE: '失败', FAILED: '失败' },
  },
  'en-US': {
    severity: { INFO: 'Info', WARNING: 'Warning', CRITICAL: 'Critical' },
    incidentStatus: { OPEN: 'Open', ACKED: 'Acknowledged', CLOSED: 'Closed' },
    reportStatus: { DRAFT: 'Draft', APPROVED: 'Approved', REJECTED: 'Rejected' },
    diagnosisState: { QUEUED: 'Queued', TRIAGE: 'Triage', MARKET: 'Market', RISK: 'Risk', RECONCILIATION: 'Reconciliation', EVIDENCE_VERIFY: 'Evidence verification', REPORT_SYNTHESIS: 'Report synthesis', POLICY_CHECK: 'Policy check', COMPLETED: 'Completed', NEEDS_REVIEW: 'Needs review', FAILED: 'Failed', CANCELLED: 'Cancelled' },
    positionSide: { LONG: 'Long', SHORT: 'Short' },
    qualityStatus: { FRESH: 'Fresh', HEALTHY: 'Healthy', DEGRADED: 'Degraded', STALE: 'Stale', UNKNOWN: 'Unknown' },
    reconciliationType: { ORPHAN_EXECUTION: 'Orphan execution', MISSING_EXECUTION: 'Missing execution', QUANTITY_MISMATCH: 'Quantity mismatch', PRICE_MISMATCH: 'Price mismatch', STATUS_MISMATCH: 'Status mismatch', CURRENCY_MISMATCH: 'Currency mismatch' },
    auditOutcome: { success: 'Success', SUCCESS: 'Success', failure: 'Failure', FAILURE: 'Failure', FAILED: 'Failed' },
  },
};

export function isLocale(value: string | null | undefined): value is Locale {
  return value !== undefined && value !== null && (LOCALES as readonly string[]).includes(value);
}

export function readStoredLocale(storage: Pick<Storage, 'getItem'> | undefined = getBrowserStorage()): Locale {
  try {
    const savedLocale = storage?.getItem(LOCALE_STORAGE_KEY);
    return isLocale(savedLocale) ? savedLocale : DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
}

export function saveLocale(locale: Locale, storage: Pick<Storage, 'setItem'> | undefined = getBrowserStorage()): void {
  try {
    storage?.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Blocked browser storage must not prevent a session-only language change.
  }
}

export function translate(locale: Locale, key: MessageKey): string {
  return messages[locale][key];
}

export function translateEnum(locale: Locale, group: EnumGroup, value: unknown): string {
  const rawValue = String(value ?? '');
  return enumMessages[locale][group][rawValue] ?? rawValue;
}

function getBrowserStorage(): Pick<Storage, 'getItem' | 'setItem'> | undefined {
  try {
    return typeof localStorage === 'undefined' ? undefined : localStorage;
  } catch {
    return undefined;
  }
}
