<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getReconciliationBreaks, getRiskSnapshot, listIncidents, type Incident, type ReconciliationBreaks, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';

const risk = ref<ToolEnvelope<RiskSnapshot>>();
const reconciliation = ref<ToolEnvelope<ReconciliationBreaks>>();
const incidents = ref<Incident[]>([]);
const error = ref('');

onMounted(async () => {
  try {
    const [riskResult, reconciliationResult, incidentResult] = await Promise.all([
      getRiskSnapshot(),
      getReconciliationBreaks(),
      listIncidents(),
    ]);
    risk.value = riskResult;
    reconciliation.value = reconciliationResult;
    incidents.value = incidentResult;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'API unavailable';
  }
});

const breachCount = computed(() => risk.value?.data?.limitBreaches.length ?? 0);
const breakCount = computed(() => reconciliation.value?.data?.differences.length ?? 0);
const quality = computed(() => risk.value?.meta?.qualityStatus ?? 'UNKNOWN');
</script>

<template>
  <h1 class="page-title">Risk AIOps Overview</h1>
  <p class="page-subtitle">Read-only production operations console. No order, cancel, position or limit mutation controls are exposed.</p>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="metric-grid">
    <div class="metric"><span>Gross exposure</span><strong>{{ risk?.data?.grossExposure ?? '--' }}</strong></div>
    <div class="metric"><span>Active breaches</span><strong class="critical">{{ breachCount }}</strong></div>
    <div class="metric"><span>Reconciliation breaks</span><strong class="warning">{{ breakCount }}</strong></div>
    <div class="metric"><span>Data quality</span><strong>{{ quality }}</strong></div>
  </div>
  <div class="panel">
    <h3>Open incidents</h3>
    <el-table :data="incidents" empty-text="No incidents returned by API">
      <el-table-column prop="severity" label="Severity" width="120" />
      <el-table-column prop="status" label="Status" width="120" />
      <el-table-column prop="accountId" label="Account" width="150" />
      <el-table-column prop="title" label="Title" />
      <el-table-column prop="evidenceId" label="Evidence" width="220" />
    </el-table>
  </div>
</template>
