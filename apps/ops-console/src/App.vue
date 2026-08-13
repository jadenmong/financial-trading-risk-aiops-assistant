<script setup lang="ts">
import { computed, watch } from 'vue';
import { en, zhCn } from 'element-plus/es/locale/index';
import { useRoute } from 'vue-router';
import { beginLogin } from './api/auth.js';
import { type Locale, type MessageKey } from './i18n/index.js';
import { useI18n } from './i18n/use-i18n.js';
import { useSessionStore } from './stores/session.js';

const route = useRoute();
const session = useSessionStore();
const { locale, setLocale, t } = useI18n();
const navigation: ReadonlyArray<readonly [string, MessageKey]> = [
  ['/', 'nav.overview'],
  ['/account-risk', 'nav.accountRisk'],
  ['/reconciliation', 'nav.reconciliation'],
  ['/diagnoses', 'nav.diagnosis'],
  ['/reports', 'nav.reports'],
  ['/audit', 'nav.audit'],
];
const pageTitle = computed(() => t((route.meta.titleKey as MessageKey | undefined) ?? 'page.dashboard.title'));
const elementLocale = computed(() => locale.value === 'zh-CN' ? zhCn : en);

watch(locale, (currentLocale) => {
  document.documentElement.lang = currentLocale;
  document.title = t('document.title');
}, { immediate: true });

function changeLocale(nextLocale: Locale) {
  setLocale(nextLocale);
}
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <el-container class="shell">
      <el-aside width="240px" class="sidebar">
        <div class="brand">
          <span class="brand-mark">R</span>
          <div><strong>Risk AIOps</strong><small>{{ t('brand.tagline') }}</small></div>
        </div>
        <el-menu router :default-active="route.path">
          <el-menu-item v-for="[path, labelKey] in navigation" :key="path" :index="path">{{ t(labelKey) }}</el-menu-item>
        </el-menu>
        <div class="boundary">{{ t('boundary.title') }}<br><small>{{ t('boundary.description') }}</small></div>
      </el-aside>
      <el-container>
        <el-header>
          <div><strong>{{ pageTitle }}</strong><span class="synthetic">{{ t('header.readOnly') }}</span></div>
          <div class="operator">
            <el-select :model-value="locale" :aria-label="t('header.language')" class="language-select" size="small" @update:model-value="changeLocale">
              <el-option label="中文" value="zh-CN" />
              <el-option label="English" value="en-US" />
            </el-select>
            <span>DESK_ALPHA / {{ session.subject ?? t('header.anonymous') }}</span>
            <el-button v-if="!session.accessToken" size="small" @click="beginLogin">{{ t('header.login') }}</el-button>
            <el-button v-else size="small" @click="session.clear">{{ t('header.logout') }}</el-button>
          </div>
        </el-header>
        <el-main><router-view /></el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>
