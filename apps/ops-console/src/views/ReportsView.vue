<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api/client.js';
import { listReports, type Report } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';

const reports = ref<Report[]>([]);
const error = ref<MessageKey>();
const { t, enumText } = useI18n();

async function refresh() { reports.value = await listReports(); }

async function approve(row: Report) {
  try {
    await api(`/api/v1/reports/${row.id}/decisions`, {
      method: 'POST', headers: { 'If-Match': String(row.version) },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'Reviewed in operations console' }),
    });
    await refresh();
  } catch {
    error.value = 'error.approvalFailed';
  }
}

onMounted(async () => {
  try { await refresh(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <h1 class="page-title">{{ t('page.reports.title') }}</h1>
  <p class="page-subtitle">{{ t('page.reports.subtitle') }}</p>
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <div class="panel">
    <el-table :data="reports" :empty-text="t('empty.reports')">
      <el-table-column prop="id" :label="t('column.report')" width="300" />
      <el-table-column prop="accountId" :label="t('column.account')" width="150" />
      <el-table-column prop="creator" :label="t('column.creator')" width="180" />
      <el-table-column width="120" :label="t('column.status')"><template #default="{ row }">{{ enumText('reportStatus', row.status) }}</template></el-table-column>
      <el-table-column prop="version" :label="t('column.version')" width="100" />
      <el-table-column :label="t('column.action')" width="160">
        <template #default="{ row }">
          <el-button :disabled="row.status !== 'DRAFT'" type="primary" size="small" @click="approve(row)">{{ t('button.approve') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
