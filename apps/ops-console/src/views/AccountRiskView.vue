<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getRiskSnapshot, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';
import ContentPanel from '../components/ContentPanel.vue';
import MetricCard from '../components/MetricCard.vue';
import PageIntro from '../components/PageIntro.vue';
import StatusBadge from '../components/StatusBadge.vue';

const snapshot = ref<ToolEnvelope<RiskSnapshot>>();
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

onMounted(async () => {
  try { snapshot.value = await getRiskSnapshot(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <PageIntro :title="t('page.accountRisk.title')" :subtitle="t('page.accountRisk.subtitle')" />
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="metric-grid">
    <MetricCard :label="t('metric.grossExposure')" :value="snapshot?.data?.grossExposure ?? '--'" />
    <MetricCard :label="t('metric.netExposure')" :value="snapshot?.data?.netExposure ?? '--'" />
    <MetricCard :label="t('metric.leverage')" :value="snapshot?.data?.leverage ?? '--'" />
    <MetricCard :label="t('metric.marginUtilization')" :value="snapshot?.data?.marginUtilization ?? '--'" tone="critical" />
  </div>
  <ContentPanel :title="t('section.positions')">
    <el-table :data="snapshot?.data?.positions ?? []" :empty-text="t('empty.positions')">
      <el-table-column prop="instrumentId" :label="t('column.instrument')" width="180" />
      <el-table-column width="100" :label="t('column.side')"><template #default="{ row }">{{ enumText('positionSide', row.side) }}</template></el-table-column>
      <el-table-column prop="quantity" :label="t('column.quantity')" width="180" />
      <el-table-column prop="marketValue" :label="t('column.marketValue')" />
      <el-table-column prop="unrealizedPnl" :label="t('column.unrealizedPnl')" />
    </el-table>
  </ContentPanel>
  <ContentPanel :title="t('section.limitBreaches')">
    <el-table :data="snapshot?.data?.limitBreaches ?? []" :empty-text="t('empty.limitBreaches')">
      <el-table-column prop="limitCode" :label="t('column.limit')" />
      <el-table-column width="120" :label="t('column.severity')"><template #default="{ row }"><StatusBadge :label="enumText('severity', row.severity)" :value="row.severity" /></template></el-table-column>
      <el-table-column prop="actual" :label="t('column.actual')" />
      <el-table-column prop="limit" :label="t('column.limitValue')" />
    </el-table>
  </ContentPanel>
</template>
