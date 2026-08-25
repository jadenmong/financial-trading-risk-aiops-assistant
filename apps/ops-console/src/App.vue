<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue';
import { ElMessage } from 'element-plus';
import { en, zhCn } from 'element-plus/es/locale/index';
import { useRoute } from 'vue-router';
import {
  Connection,
  DataAnalysis,
  DataLine,
  Document,
  Expand,
  Files,
  Fold,
  Menu,
  Monitor,
  Operation,
  SwitchButton,
  TrendCharts,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue';
import { beginLogin } from './api/auth.js';
import { type Locale, type MessageKey } from './i18n/index.js';
import { useI18n } from './i18n/use-i18n.js';
import { useSessionStore } from './stores/session.js';

const route = useRoute();
const session = useSessionStore();
const { locale, setLocale, t } = useI18n();
const navigation: ReadonlyArray<{ path: string; labelKey: MessageKey; icon: Component }> = [
  { path: '/', labelKey: 'nav.overview', icon: DataAnalysis },
  { path: '/account-risk', labelKey: 'nav.accountRisk', icon: Wallet },
  { path: '/reconciliation', labelKey: 'nav.reconciliation', icon: Connection },
  { path: '/diagnoses', labelKey: 'nav.diagnosis', icon: DataLine },
  { path: '/reports', labelKey: 'nav.reports', icon: Document },
  { path: '/audit', labelKey: 'nav.audit', icon: Files },
];

const pageTitle = computed(() => t((route.meta.titleKey as MessageKey | undefined) ?? 'page.dashboard.title'));
const elementLocale = computed(() => locale.value === 'zh-CN' ? zhCn : en);
const currentNavigation = computed(() => navigation.find(({ path }) => path === route.path) ?? navigation[0]);
const sidebarCollapsed = ref(localStorage.getItem('risk-aiops.sidebar-collapsed') === 'true');
const isMobile = ref(false);
const mobileMenuOpen = ref(false);
const sidebarClass = computed(() => ({
  'app-sidebar--collapsed': sidebarCollapsed.value && !isMobile.value,
  'app-sidebar--mobile-open': isMobile.value && mobileMenuOpen.value,
}));

watch(locale, (currentLocale) => {
  document.documentElement.lang = currentLocale;
  document.title = t('document.title');
}, { immediate: true });

function changeLocale(nextLocale: Locale) {
  setLocale(nextLocale);
}

function updateViewport() {
  isMobile.value = window.innerWidth <= 768;
  if (!isMobile.value) mobileMenuOpen.value = false;
}

function toggleNavigation() {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value;
    return;
  }
  sidebarCollapsed.value = !sidebarCollapsed.value;
  localStorage.setItem('risk-aiops.sidebar-collapsed', String(sidebarCollapsed.value));
}

function closeMobileNavigation() {
  if (isMobile.value) mobileMenuOpen.value = false;
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMobileNavigation();
}

async function handleSessionAction() {
  if (session.accessToken) {
    session.clear();
    return;
  }

  try {
    await beginLogin();
  } catch (error) {
    console.error('OIDC login redirect failed', error);
    ElMessage.error(t('error.loginFailed'));
  }
}

onMounted(() => {
  updateViewport();
  window.addEventListener('resize', updateViewport);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport);
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <div class="app-shell">
      <button v-if="isMobile && mobileMenuOpen" class="navigation-scrim" aria-label="Close navigation" @click="closeMobileNavigation" />

      <aside class="app-sidebar" :class="sidebarClass" aria-label="Application navigation">
        <div class="app-sidebar__header">
          <div class="brand">
            <span class="brand-mark"><el-icon><TrendCharts /></el-icon></span>
            <div class="brand-copy"><strong>Risk AIOps</strong><small>{{ t('brand.tagline') }}</small></div>
          </div>
          <button class="icon-button sidebar-toggle" :aria-label="sidebarCollapsed ? 'Expand navigation' : 'Collapse navigation'" @click="toggleNavigation">
            <el-icon><component :is="sidebarCollapsed && !isMobile ? Expand : Fold" /></el-icon>
          </button>
        </div>

        <nav class="app-navigation">
          <RouterLink v-for="item in navigation" :key="item.path" :to="item.path" class="app-navigation__item" :class="{ 'is-active': route.path === item.path }" :title="t(item.labelKey)" @click="closeMobileNavigation">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
          </RouterLink>
        </nav>

        <div class="app-sidebar__footer">
          <div class="boundary">
            <el-icon><Operation /></el-icon>
            <div><strong>{{ t('boundary.title') }}</strong><small>{{ t('boundary.description') }}</small></div>
          </div>
          <div class="operator">
            <div class="operator__identity"><el-icon><UserFilled /></el-icon><span>DESK_ALPHA / {{ session.subject ?? t('header.anonymous') }}</span></div>
            <el-select :model-value="locale" :aria-label="t('header.language')" class="language-select" size="small" @update:model-value="changeLocale">
              <el-option :label="t('language.zhCN')" value="zh-CN" />
              <el-option :label="t('language.enUS')" value="en-US" />
            </el-select>
            <el-button class="session-button" size="small" text @click="handleSessionAction">
              <el-icon><SwitchButton /></el-icon>{{ session.accessToken ? t('header.logout') : t('header.login') }}
            </el-button>
          </div>
        </div>
      </aside>

      <main class="app-main">
        <section class="app-page-panel">
          <header class="app-header">
            <div class="app-header__title">
              <button v-if="isMobile || sidebarCollapsed" class="icon-button app-header__menu" aria-label="Toggle navigation" @click="toggleNavigation"><el-icon><Menu /></el-icon></button>
              <el-icon><component :is="currentNavigation.icon" /></el-icon>
              <strong>{{ pageTitle }}</strong>
            </div>
            <span class="read-only-badge"><el-icon><Monitor /></el-icon>{{ t('header.readOnly') }}</span>
          </header>
          <div class="app-content"><router-view /></div>
        </section>
      </main>
    </div>
  </el-config-provider>
</template>
