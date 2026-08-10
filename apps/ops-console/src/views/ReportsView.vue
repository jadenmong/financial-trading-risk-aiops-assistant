<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api/client.js';
import { listReports, type Report } from '../api/operations.js';

const reports = ref<Report[]>([]);
const error = ref('');

async function refresh() {
  reports.value = await listReports();
}

async function approve(row: Report) {
  try {
    await api(`/api/v1/reports/${row.id}/decisions`, {
      method: 'POST',
      headers: { 'If-Match': String(row.version) },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'Reviewed in operations console' }),
    });
    await refresh();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Approval failed';
  }
}

onMounted(async () => {
  try { await refresh(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'API unavailable'; }
});
</script>

<template>
  <h1 class="page-title">Governed Reports</h1>
  <p class="page-subtitle">Draft and approval workflow with maker-checker, optimistic locking and immutable report content.</p>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="panel">
    <el-table :data="reports" empty-text="No reports returned by API">
      <el-table-column prop="id" label="Report" width="300" />
      <el-table-column prop="accountId" label="Account" width="150" />
      <el-table-column prop="creator" label="Creator" width="180" />
      <el-table-column prop="status" label="Status" width="120" />
      <el-table-column prop="version" label="Version" width="100" />
      <el-table-column label="Action" width="160">
        <template #default="{ row }">
          <el-button :disabled="row.status !== 'DRAFT'" type="primary" size="small" @click="approve(row)">Approve</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
