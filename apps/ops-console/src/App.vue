<script setup lang="ts">
import { useRoute } from 'vue-router';
import { beginLogin } from './api/auth.js';
import { useSessionStore } from './stores/session.js';

const route = useRoute();
const session = useSessionStore();
const navigation = [
  ['/', 'Overview'],
  ['/account-risk', 'Account Risk'],
  ['/reconciliation', 'Reconciliation'],
  ['/diagnoses', 'Diagnosis'],
  ['/reports', 'Reports'],
  ['/audit', 'Audit'],
] as const;
</script>

<template>
  <el-container class="shell">
    <el-aside width="240px" class="sidebar">
      <div class="brand">
        <span class="brand-mark">R</span>
        <div><strong>Risk AIOps</strong><small>Production-grade assistant</small></div>
      </div>
      <el-menu router :default-active="route.path">
        <el-menu-item v-for="[path, label] in navigation" :key="path" :index="path">{{ label }}</el-menu-item>
      </el-menu>
      <div class="boundary">Read-only boundary<br><small>No trading write capability</small></div>
    </el-aside>
    <el-container>
      <el-header>
        <div><strong>{{ route.meta.title }}</strong><span class="synthetic">READ ONLY</span></div>
        <div class="operator">
          DESK_ALPHA / {{ session.subject ?? 'anonymous' }}
          <el-button v-if="!session.accessToken" size="small" @click="beginLogin">OIDC login</el-button>
          <el-button v-else size="small" @click="session.clear">Logout</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
