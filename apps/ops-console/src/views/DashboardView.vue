<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getReconciliationBreaks, getRiskSnapshot, listIncidents, type Incident, type ReconciliationBreaks, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';
import ContentPanel from '../components/ContentPanel.vue';
import MetricCard from '../components/MetricCard.vue';
import PageIntro from '../components/PageIntro.vue';
import StatusBadge from '../components/StatusBadge.vue';

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
  <PageIntro :title="t('page.dashboard.title')" :subtitle="t('page.dashboard.subtitle')" />
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="metric-grid">
    <MetricCard :label="t('metric.grossExposure')" :value="risk?.data?.grossExposure ?? '--'" />
    <MetricCard :label="t('metric.activeBreaches')" :value="breachCount" tone="critical" />
    <MetricCard :label="t('metric.reconciliationBreaks')" :value="breakCount" tone="warning" />
    <MetricCard :label="t('metric.dataQuality')" :value="quality" tone="success" />
  </div>
  <ContentPanel :title="t('section.openIncidents')">
    <el-table :data="incidents" :empty-text="t('empty.incidents')">
      <el-table-column width="120" :label="t('column.severity')"><template #default="{ row }"><StatusBadge :label="enumText('severity', row.severity)" :value="row.severity" /></template></el-table-column>
      <el-table-column width="120" :label="t('column.status')"><template #default="{ row }"><StatusBadge :label="enumText('incidentStatus', row.status)" :value="row.status" /></template></el-table-column>
      <el-table-column prop="accountId" :label="t('column.account')" width="150" />
      <el-table-column prop="title" :label="t('column.title')" />
      <el-table-column prop="evidenceId" :label="t('column.evidence')" width="220" />
    </el-table>
  </ContentPanel>
</template>
