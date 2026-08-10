<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getRiskSnapshot, type RiskSnapshot, type ToolEnvelope } from '../api/operations.js';

const snapshot = ref<ToolEnvelope<RiskSnapshot>>();
const error = ref('');

onMounted(async () => {
  try { snapshot.value = await getRiskSnapshot(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'API unavailable'; }
});
</script>

<template>
  <h1 class="page-title">Account Risk</h1>
  <p class="page-subtitle">Financial values are displayed as decimal strings from deterministic backend computation.</p>
  <el-alert v-if="error" type="error" :title="error" show-icon class="panel-alert" />
  <div class="metric-grid">
    <div class="metric"><span>Gross exposure</span><strong>{{ snapshot?.data?.grossExposure ?? '--' }}</strong></div>
    <div class="metric"><span>Net exposure</span><strong>{{ snapshot?.data?.netExposure ?? '--' }}</strong></div>
    <div class="metric"><span>Leverage</span><strong>{{ snapshot?.data?.leverage ?? '--' }}</strong></div>
    <div class="metric"><span>Margin utilization</span><strong class="critical">{{ snapshot?.data?.marginUtilization ?? '--' }}</strong></div>
  </div>
  <div class="panel">
    <h3>Positions</h3>
    <el-table :data="snapshot?.data?.positions ?? []" empty-text="No positions returned by API">
      <el-table-column prop="instrumentId" label="Instrument" width="180" />
      <el-table-column prop="side" label="Side" width="100" />
      <el-table-column prop="quantity" label="Quantity" width="180" />
      <el-table-column prop="marketValue" label="Market value" />
      <el-table-column prop="unrealizedPnl" label="Unrealized PnL" />
    </el-table>
  </div>
  <div class="panel">
    <h3>Limit breaches</h3>
    <el-table :data="snapshot?.data?.limitBreaches ?? []" empty-text="No breaches returned by API">
      <el-table-column prop="limitCode" label="Limit" />
      <el-table-column prop="severity" label="Severity" width="120" />
      <el-table-column prop="actual" label="Actual" />
      <el-table-column prop="limit" label="Limit value" />
    </el-table>
  </div>
</template>
