<template>
  <div class="empty-state">
    <el-icon :size="44" color="#cbd5e1"><component :is="icon" /></el-icon>
    <p class="empty-text">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'
withDefaults(defineProps<{ icon?: any; text?: string }>(), { icon: () => Picture, text: '暂无数据' })
</script>

<style scoped>
.empty-state { text-align: center; padding: 40px var(--app-space-5); }
.empty-text { color: var(--app-text-muted); font-size: var(--app-font-size-base); margin: var(--app-space-3) 0 var(--app-space-4); }
</style>
