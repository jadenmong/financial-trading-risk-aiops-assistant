<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api/client.js';

interface DiagnosisEvent { sequence: number; state: string; at: string; detail: string }
interface DiagnosisRun { id: string; accountId: string; tradeDate: string; state: string; events: DiagnosisEvent[] }

const run = ref<DiagnosisRun>();
const error = ref('');
const loading = ref(false);

async function createDiagnosis() {
  loading.value = true;
  error.value = '';
  try {
    const key = `ops-console-ACC_ALPHA_01-2026-08-07`;
    run.value = await api<DiagnosisRun>('/api/v1/diagnoses', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }),
    });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Diagnosis failed';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <h1 class="page-title">Diagnosis Timeline</h1>
  <p class="page-subtitle">Fixed read-only DAG with persisted state, evidence verification and policy check.</p>
  <el-button type="primary" :loading="loading" @click="createDiagnosis">Create diagnosis</el-button>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="panel timeline-panel">
    <el-timeline v-if="run">
      <el-timeline-item v-for="event in run.events" :key="event.sequence" :timestamp="event.at" :type="event.state === 'COMPLETED' ? 'success' : 'primary'">
        <strong>{{ event.state }}</strong>
        <div>{{ event.detail }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="No diagnosis created in this session" />
  </div>
</template>
