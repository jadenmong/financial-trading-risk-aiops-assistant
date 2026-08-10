<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getReconciliationBreaks, type ReconciliationBreaks, type ToolEnvelope } from '../api/operations.js';

const breaks = ref<ToolEnvelope<ReconciliationBreaks>>();
const error = ref('');

onMounted(async () => {
  try { breaks.value = await getReconciliationBreaks(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'API unavailable'; }
});
</script>

<template>
  <h1 class="page-title">Reconciliation Breaks</h1>
  <p class="page-subtitle">Read-only OMS and broker execution comparison for operations review.</p>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="panel">
    <el-table :data="breaks?.data?.differences ?? []" empty-text="No reconciliation breaks returned by API">
      <el-table-column prop="type" label="Type" width="220" />
      <el-table-column prop="severity" label="Severity" width="120" />
      <el-table-column prop="orderId" label="Order" width="160" />
      <el-table-column prop="executionId" label="Execution" width="160" />
      <el-table-column prop="expected" label="Expected" />
      <el-table-column prop="actual" label="Actual" />
      <el-table-column prop="currency" label="Currency" width="100" />
    </el-table>
  </div>
</template>
