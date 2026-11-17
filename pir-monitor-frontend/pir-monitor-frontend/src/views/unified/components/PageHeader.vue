<template>
  <div class="page-header">
    <div class="page-header-left">
      <h2 class="page-title">{{ title }}</h2>
      <p v-if="desc" class="page-desc">{{ desc }}</p>
    </div>
    <div class="page-header-right"><slot /></div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; desc?: string }>()
</script>

<style scoped>
.page-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin-bottom: var(--app-space-5); flex-wrap: wrap; gap: var(--app-space-3);
}
.page-title { font-size: var(--app-font-size-xl); font-weight: 700; color: var(--app-text-primary); margin: 0; }
.page-desc { font-size: var(--app-font-size-base); color: var(--app-text-muted); margin: 5px 0 0; }
</style>
