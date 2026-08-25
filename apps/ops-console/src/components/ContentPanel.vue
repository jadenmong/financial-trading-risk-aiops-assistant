<script setup lang="ts">
defineProps<{
  title?: string;
}>();
</script>

<template>
  <section class="content-panel">
    <header v-if="title || $slots.actions" class="content-panel__header">
      <h2 v-if="title">{{ title }}</h2>
      <div v-if="$slots.actions" class="content-panel__actions"><slot name="actions" /></div>
    </header>
    <div class="content-panel__body"><slot /></div>
  </section>
</template>
