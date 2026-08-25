<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getReconciliationBreaks, type ReconciliationBreaks, type ToolEnvelope } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';
import ContentPanel from '../components/ContentPanel.vue';
import PageIntro from '../components/PageIntro.vue';
import StatusBadge from '../components/StatusBadge.vue';

const breaks = ref<ToolEnvelope<ReconciliationBreaks>>();
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

onMounted(async () => {
  try { breaks.value = await getReconciliationBreaks(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <PageIntro :title="t('page.reconciliation.title')" :subtitle="t('page.reconciliation.subtitle')" />
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <ContentPanel>
    <el-table :data="breaks?.data?.differences ?? []" :empty-text="t('empty.reconciliation')">
      <el-table-column width="220" :label="t('column.type')"><template #default="{ row }">{{ enumText('reconciliationType', row.type) }}</template></el-table-column>
      <el-table-column width="120" :label="t('column.severity')"><template #default="{ row }"><StatusBadge :label="enumText('severity', row.severity)" :value="row.severity" /></template></el-table-column>
      <el-table-column prop="orderId" :label="t('column.order')" width="160" />
      <el-table-column prop="executionId" :label="t('column.execution')" width="160" />
      <el-table-column prop="expected" :label="t('column.expected')" />
      <el-table-column prop="actual" :label="t('column.actual')" />
      <el-table-column prop="currency" :label="t('column.currency')" width="100" />
    </el-table>
  </ContentPanel>
</template>
