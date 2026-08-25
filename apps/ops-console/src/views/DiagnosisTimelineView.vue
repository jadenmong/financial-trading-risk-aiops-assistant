<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/client.js';
import { createReport } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';
import ContentPanel from '../components/ContentPanel.vue';
import PageIntro from '../components/PageIntro.vue';
import StatusBadge from '../components/StatusBadge.vue';

interface DiagnosisEvent { sequence: number; state: string; at: string; detail: string }
interface DiagnosisRun { id: string; accountId: string; tradeDate: string; state: string; events: DiagnosisEvent[] }

const run = ref<DiagnosisRun>();
const error = ref<MessageKey>();
const loading = ref(false);
const { t, enumText } = useI18n();
const router = useRouter();

async function createDiagnosis() {
  loading.value = true;
  error.value = undefined;
  try {
    const key = 'ops-console-ACC_ALPHA_01-2026-08-07';
    const diagnosis = await api<DiagnosisRun>('/api/v1/diagnoses', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }),
    });
    run.value = diagnosis;
    await createReport(diagnosis.id);
    await router.push('/reports');
  } catch {
    error.value = 'error.diagnosisFailed';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <PageIntro :title="t('page.diagnosis.title')" :subtitle="t('page.diagnosis.subtitle')" />
  <div class="page-action-row"><el-button type="primary" :loading="loading" @click="createDiagnosis">{{ t('button.createDiagnosis') }}</el-button></div>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <ContentPanel class="timeline-panel">
    <el-timeline v-if="run">
      <el-timeline-item v-for="event in run.events" :key="event.sequence" :timestamp="event.at" :type="event.state === 'COMPLETED' ? 'success' : 'primary'">
        <StatusBadge :label="enumText('diagnosisState', event.state)" :value="event.state" />
        <div>{{ event.detail }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else :description="t('empty.diagnosis')" />
  </ContentPanel>
</template>
