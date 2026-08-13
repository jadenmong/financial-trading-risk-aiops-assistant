<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getRiskSnapshot, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

const snapshot = ref<ToolEnvelope<RiskSnapshot>>();
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

onMounted(async () => {
  try { snapshot.value = await getRiskSnapshot(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <h1 class="page-title">{{ t('page.accountRisk.title') }}</h1>
  <p class="page-subtitle">{{ t('page.accountRisk.subtitle') }}</p>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="metric-grid">
    <div class="metric"><span>{{ t('metric.grossExposure') }}</span><strong>{{ snapshot?.data?.grossExposure ?? '--' }}</strong></div>
    <div class="metric"><span>{{ t('metric.netExposure') }}</span><strong>{{ snapshot?.data?.netExposure ?? '--' }}</strong></div>
    <div class="metric"><span>{{ t('metric.leverage') }}</span><strong>{{ snapshot?.data?.leverage ?? '--' }}</strong></div>
    <div class="metric"><span>{{ t('metric.marginUtilization') }}</span><strong class="critical">{{ snapshot?.data?.marginUtilization ?? '--' }}</strong></div>
  </div>
  <div class="panel">
    <h3>{{ t('section.positions') }}</h3>
    <el-table :data="snapshot?.data?.positions ?? []" :empty-text="t('empty.positions')">
      <el-table-column prop="instrumentId" :label="t('column.instrument')" width="180" />
      <el-table-column width="100" :label="t('column.side')"><template #default="{ row }">{{ enumText('positionSide', row.side) }}</template></el-table-column>
      <el-table-column prop="quantity" :label="t('column.quantity')" width="180" />
      <el-table-column prop="marketValue" :label="t('column.marketValue')" />
      <el-table-column prop="unrealizedPnl" :label="t('column.unrealizedPnl')" />
    </el-table>
  </div>
  <div class="panel">
    <h3>{{ t('section.limitBreaches') }}</h3>
    <el-table :data="snapshot?.data?.limitBreaches ?? []" :empty-text="t('empty.limitBreaches')">
      <el-table-column prop="limitCode" :label="t('column.limit')" />
      <el-table-column width="120" :label="t('column.severity')"><template #default="{ row }">{{ enumText('severity', row.severity) }}</template></el-table-column>
      <el-table-column prop="actual" :label="t('column.actual')" />
      <el-table-column prop="limit" :label="t('column.limitValue')" />
    </el-table>
  </div>
</template>
