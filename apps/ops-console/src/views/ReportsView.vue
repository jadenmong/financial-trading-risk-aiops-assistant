<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api/client.js';
import { getReportContent, listReports, type Report } from '../api/operations.js';
import { type MessageKey } from '../i18n/index.js';
import { useI18n } from '../i18n/use-i18n.js';
import ContentPanel from '../components/ContentPanel.vue';
import PageIntro from '../components/PageIntro.vue';
import StatusBadge from '../components/StatusBadge.vue';

const reports = ref<Report[]>([]);
const error = ref<MessageKey>();
const previewOpen = ref(false);
const previewHtml = ref('');
const previewReportId = ref('');
const contentLoading = ref<string>();
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

async function viewReport(row: Report) {
  contentLoading.value = row.id;
  error.value = undefined;
  try {
    previewHtml.value = await (await getReportContent(row.id, 'html')).text();
    previewReportId.value = row.id;
    previewOpen.value = true;
  } catch {
    error.value = 'error.reportContentFailed';
  } finally {
    contentLoading.value = undefined;
  }
}

async function downloadReport(row: Report) {
  contentLoading.value = row.id;
  error.value = undefined;
  try {
    const blob = await getReportContent(row.id, 'html');
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `risk-report-${row.id}.html`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
  } catch {
    error.value = 'error.reportContentFailed';
  } finally {
    contentLoading.value = undefined;
  }
}

onMounted(async () => {
  try { await refresh(); }
  catch { error.value = 'error.apiUnavailable'; }
});
</script>

<template>
  <PageIntro :title="t('page.reports.title')" :subtitle="t('page.reports.subtitle')" />
  <el-alert v-if="error" type="error" :title="t(error)" show-icon class="panel-alert" />
  <ContentPanel>
    <el-table :data="reports" :empty-text="t('empty.reports')">
      <el-table-column prop="id" :label="t('column.report')" width="300" />
      <el-table-column prop="accountId" :label="t('column.account')" width="150" />
      <el-table-column prop="creator" :label="t('column.creator')" width="180" />
      <el-table-column width="120" :label="t('column.status')"><template #default="{ row }"><StatusBadge :label="enumText('reportStatus', row.status)" :value="row.status" /></template></el-table-column>
      <el-table-column prop="version" :label="t('column.version')" width="100" />
      <el-table-column :label="t('column.action')" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" type="primary" size="small" :loading="contentLoading === row.id" @click="approve(row)">{{ t('button.approve') }}</el-button>
          <template v-else-if="row.status === 'APPROVED'">
            <el-button type="primary" text size="small" :loading="contentLoading === row.id" @click="viewReport(row)">{{ t('button.viewReport') }}</el-button>
            <el-button type="primary" text size="small" :loading="contentLoading === row.id" @click="downloadReport(row)">{{ t('button.downloadReport') }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </ContentPanel>
  <el-dialog v-model="previewOpen" :title="`${t('button.viewReport')} · ${previewReportId}`" width="min(900px, 92vw)" destroy-on-close>
    <iframe class="report-preview" :srcdoc="previewHtml" sandbox="" :title="previewReportId" />
  </el-dialog>
</template>
