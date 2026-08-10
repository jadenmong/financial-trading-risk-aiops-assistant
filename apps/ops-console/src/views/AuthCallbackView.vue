<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { completeLogin } from '../api/auth.js';

const router = useRouter();
const error = ref<string>();

onMounted(async () => {
  try { await completeLogin(); await router.replace('/'); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'OIDC login failed'; }
});
</script>

<template>
  <div class="panel">
    <h2>Completing OIDC + PKCE login</h2>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <el-skeleton v-else animated :rows="3" />
  </div>
</template>
