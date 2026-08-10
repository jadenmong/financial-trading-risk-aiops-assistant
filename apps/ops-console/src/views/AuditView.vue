<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listAuditEvents, type AuditEvent } from '../api/operations.js';

const events = ref<AuditEvent[]>([]);
const error = ref('');

onMounted(async () => {
  try { events.value = await listAuditEvents(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'API unavailable'; }
});
</script>

<template>
  <h1 class="page-title">Audit Search</h1>
  <p class="page-subtitle">Append-only audit events with hash-chain verification fields.</p>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="panel">
    <el-table :data="events" empty-text="No audit events returned by API">
      <el-table-column prop="occurredAt" label="Time" width="230" />
      <el-table-column prop="subject" label="Subject" width="180" />
      <el-table-column prop="action" label="Action" width="220" />
      <el-table-column prop="outcome" label="Outcome" width="120" />
      <el-table-column prop="eventHash" label="Event hash" />
    </el-table>
  </div>
</template>
