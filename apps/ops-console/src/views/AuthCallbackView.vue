<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { completeLogin } from '../api/auth.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

const router = useRouter();
const error = ref<MessageKey>();
const { t } = useI18n();

onMounted(async () => {
  try { await completeLogin(); await router.replace('/'); }
  catch { error.value = 'error.loginFailed'; }
});
</script>

<template>
  <div class="panel">
    <h2>{{ t('page.authCallback.title') }}</h2>
    <el-alert v-if="error" type="error" :title="t(error)" :closable="false" />
    <el-skeleton v-else animated :rows="3" />
  </div>
</template>
