<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useSessionStore } from './stores/session.js';
import { beginLogin } from './api/auth.js';
const route = useRoute();
const session = useSessionStore();
const navigation = [
  ['/', '风险概览'], ['/account-risk', '账户风险'], ['/reconciliation', '对账差异'],
  ['/diagnoses', 'Agent 时间线'], ['/reports', '报告审批'], ['/audit', '审计查询'],
];
</script>

<template>
  <el-container class="shell">
    <el-aside width="240px" class="sidebar">
      <div class="brand"><span class="brand-mark">R</span><div><strong>Risk AIOps</strong><small>机构级参考基线</small></div></div>
      <el-menu router :default-active="route.path">
        <el-menu-item v-for="[path, label] in navigation" :key="path" :index="path">{{ label }}</el-menu-item>
      </el-menu>
      <div class="boundary">只读诊断边界<br><small>无任何交易写能力</small></div>
    </el-aside>
    <el-container>
      <el-header><div><strong>{{ route.meta.title }}</strong><span class="synthetic">SYNTHETIC DATA</span></div><div class="operator">DESK_ALPHA · {{ session.subject ?? '未登录' }} <el-button v-if="!session.accessToken" size="small" @click="beginLogin">OIDC 登录</el-button><el-button v-else size="small" @click="session.clear">退出</el-button></div></el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
