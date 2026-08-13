<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api/client.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

interface DiagnosisEvent { sequence: number; state: string; at: string; detail: string }
interface DiagnosisRun { id: string; accountId: string; tradeDate: string; state: string; events: DiagnosisEvent[] }

const run = ref<DiagnosisRun>();
const error = ref<MessageKey>();
const loading = ref(false);
const { t, enumText } = useI18n();

async function createDiagnosis() {
  loading.value = true;
  error.value = undefined;
  try {
    const key = 'ops-console-ACC_ALPHA_01-2026-08-07';
    run.value = await api<DiagnosisRun>('/api/v1/diagnoses', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }),
    });
  } catch {
    error.value = 'error.diagnosisFailed';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <h1 class="page-title">{{ t('page.diagnosis.title') }}</h1>
  <p class="page-subtitle">{{ t('page.diagnosis.subtitle') }}</p>
  <el-button type="primary" :loading="loading" @click="createDiagnosis">{{ t('button.createDiagnosis') }}</el-button>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="panel timeline-panel">
    <el-timeline v-if="run">
      <el-timeline-item v-for="event in run.events" :key="event.sequence" :timestamp="event.at" :type="event.state === 'COMPLETED' ? 'success' : 'primary'">
        <strong>{{ enumText('diagnosisState', event.state) }}</strong>
        <div>{{ event.detail }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else :description="t('empty.diagnosis')" />
  </div>
</template>
