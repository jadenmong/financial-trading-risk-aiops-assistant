import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/callback', component: () => import('../views/AuthCallbackView.vue'), meta: { title: 'OIDC callback' } },
    { path: '/', component: () => import('../views/DashboardView.vue'), meta: { title: 'Risk overview' } },
    { path: '/account-risk', component: () => import('../views/AccountRiskView.vue'), meta: { title: 'Account risk' } },
    { path: '/reconciliation', component: () => import('../views/ReconciliationView.vue'), meta: { title: 'Reconciliation breaks' } },
    { path: '/diagnoses', component: () => import('../views/DiagnosisTimelineView.vue'), meta: { title: 'Diagnosis timeline' } },
    { path: '/reports', component: () => import('../views/ReportsView.vue'), meta: { title: 'Reports' } },
    { path: '/audit', component: () => import('../views/AuditView.vue'), meta: { title: 'Audit' } },
  ],
});
