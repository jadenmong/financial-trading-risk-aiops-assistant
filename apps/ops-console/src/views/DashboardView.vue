<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getReconciliationBreaks, getRiskSnapshot, listIncidents, type Incident, type ReconciliationBreaks, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

const risk = ref<ToolEnvelope<RiskSnapshot>>();
const reconciliation = ref<ToolEnvelope<ReconciliationBreaks>>();
const incidents = ref<Incident[]>([]);
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

onMounted(async () => {
  try {
    const [riskResult, reconciliationResult, incidentResult] = await Promise.all([getRiskSnapshot(), getReconciliationBreaks(), listIncidents()]);
    risk.value = riskResult;
    reconciliation.value = reconciliationResult;
    incidents.value = incidentResult;
  } catch {
    error.value = 'error.apiUnavailable';
  }
});

const breachCount = computed(() => risk.value?.data?.limitBreaches.length ?? 0);
const breakCount = computed(() => reconciliation.value?.data?.differences.length ?? 0);
const quality = computed(() => enumText('qualityStatus', risk.value?.meta?.qualityStatus ?? 'UNKNOWN'));
</script>

<template>
  <h1 class="page-title">{{ t('page.dashboard.title') }}</h1>
  <p class="page-subtitle">{{ t('page.dashboard.subtitle') }}</p>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="metric-grid">
    <div class="metric"><span>{{ t('metric.grossExposure') }}</span><strong>{{ risk?.data?.grossExposure ?? '--' }}</strong></div>
    <div class="metric"><span>{{ t('metric.activeBreaches') }}</span><strong class="critical">{{ breachCount }}</strong></div>
    <div class="metric"><span>{{ t('metric.reconciliationBreaks') }}</span><strong class="warning">{{ breakCount }}</strong></div>
    <div class="metric"><span>{{ t('metric.dataQuality') }}</span><strong>{{ quality }}</strong></div>
  </div>
  <div class="panel">
    <h3>{{ t('section.openIncidents') }}</h3>
    <el-table :data="incidents" :empty-text="t('empty.incidents')">
      <el-table-column width="120" :label="t('column.severity')"><template #default="{ row }">{{ enumText('severity', row.severity) }}</template></el-table-column>
      <el-table-column width="120" :label="t('column.status')"><template #default="{ row }">{{ enumText('incidentStatus', row.status) }}</template></el-table-column>
      <el-table-column prop="accountId" :label="t('column.account')" width="150" />
      <el-table-column prop="title" :label="t('column.title')" />
      <el-table-column prop="evidenceId" :label="t('column.evidence')" width="220" />
    </el-table>
  </div>
</template>
