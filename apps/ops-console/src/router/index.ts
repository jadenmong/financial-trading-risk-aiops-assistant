import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/callback', component: () => import('../views/AuthCallbackView.vue'), meta: { titleKey: 'page.authCallback.title' } },
    { path: '/', component: () => import('../views/DashboardView.vue'), meta: { titleKey: 'page.dashboard.title' } },
    { path: '/account-risk', component: () => import('../views/AccountRiskView.vue'), meta: { titleKey: 'page.accountRisk.title' } },
    { path: '/reconciliation', component: () => import('../views/ReconciliationView.vue'), meta: { titleKey: 'page.reconciliation.title' } },
    { path: '/diagnoses', component: () => import('../views/DiagnosisTimelineView.vue'), meta: { titleKey: 'page.diagnosis.title' } },
    { path: '/reports', component: () => import('../views/ReportsView.vue'), meta: { titleKey: 'page.reports.title' } },
    { path: '/audit', component: () => import('../views/AuditView.vue'), meta: { titleKey: 'page.audit.title' } },
  ],
});
