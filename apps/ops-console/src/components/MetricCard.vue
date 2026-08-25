<script setup lang="ts">
withDefaults(defineProps<{
  label: string;
  value: string | number;
  tone?: 'default' | 'critical' | 'warning' | 'success';
}>(), {
  tone: 'default',
});
</script>

<template>
  <section class="metric-card" :class="`metric-card--${tone}`">
    <span class="metric-card__label">{{ label }}</span>
    <strong class="metric-card__value">{{ value }}</strong>
  </section>
</template>
