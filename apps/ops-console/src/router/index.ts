import { createRouter, createWebHistory } from 'vue-router';
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/auth/callback', component: () => import('../views/AuthCallbackView.vue'), meta: { title: 'OIDC 登录回调' } },
  { path: '/', component: () => import('../views/DashboardView.vue'), meta: { title: '风险概览' } },
  { path: '/account-risk', component: () => import('../views/AccountRiskView.vue'), meta: { title: '账户风险' } },
  { path: '/reconciliation', component: () => import('../views/ReconciliationView.vue'), meta: { title: '对账差异' } },
  { path: '/diagnoses', component: () => import('../views/DiagnosisTimelineView.vue'), meta: { title: 'Agent 诊断时间线' } },
  { path: '/reports', component: () => import('../views/ReportsView.vue'), meta: { title: '报告草稿与双人审批' } },
  { path: '/audit', component: () => import('../views/AuditView.vue'), meta: { title: '审计查询' } },
] });
