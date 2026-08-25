<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  label: string;
  value: string;
}>();

const tone = computed(() => {
  const value = props.value.toUpperCase();
  if (['CRITICAL', 'HIGH', 'FAILED', 'REJECTED', 'ERROR', 'OPEN'].includes(value)) return 'danger';
  if (['WARNING', 'MEDIUM', 'PENDING', 'DRAFT', 'IN_PROGRESS'].includes(value)) return 'warning';
  if (['COMPLETED', 'APPROVED', 'CLOSED', 'SUCCESS', 'PASS'].includes(value)) return 'success';
  return 'neutral';
});
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">{{ label }}</span>
</template>
