<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listAuditEvents, type AuditEvent } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

const events = ref<AuditEvent[]>([]);
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

onMounted(async () => {
  try { events.value = await listAuditEvents(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <h1 class="page-title">{{ t('page.audit.title') }}</h1>
  <p class="page-subtitle">{{ t('page.audit.subtitle') }}</p>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="panel">
    <el-table :data="events" :empty-text="t('empty.auditEvents')">
      <el-table-column prop="occurredAt" :label="t('column.time')" width="230" />
      <el-table-column prop="subject" :label="t('column.subject')" width="180" />
      <el-table-column prop="action" :label="t('column.action')" width="220" />
      <el-table-column width="120" :label="t('column.status')"><template #default="{ row }">{{ enumText('auditOutcome', row.outcome) }}</template></el-table-column>
      <el-table-column prop="eventHash" :label="t('column.eventHash')" />
    </el-table>
  </div>
</template>
