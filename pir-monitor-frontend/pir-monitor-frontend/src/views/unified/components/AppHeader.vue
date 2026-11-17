<template>
  <header class="app-header">
    <div class="header-left">
      <div class="header-logo">
        <svg viewBox="0 0 32 32" width="28" height="28">
          <path d="M16 2L4 8v10c0 7.2 5.1 13.9 12 15.5 6.9-1.6 12-8.3 12-15.5V8L16 2z" fill="currentColor" opacity="0.9"/>
          <path d="M14 20l-3-3 1.4-1.4L14 17.2l5.6-5.6L21 13l-7 7z" fill="#fff"/>
        </svg>
      </div>
      <span class="header-title">智慧安防 <span class="title-sep">·</span> <span class="title-sub">综合监控中心</span></span>
    </div>
    <div class="header-right">
      <NotificationBell />
      <el-divider direction="vertical" />
      <el-tag type="info" size="small" effect="plain" class="role-tag">{{ roleLabel }}</el-tag>
      <span class="header-user">{{ username }}</span>
      <el-button type="danger" text size="small" @click="handleLogout">退出</el-button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import NotificationBell from '@/components/shared/NotificationBell.vue'

const authStore = useAuthStore()
const username = computed(() => authStore.user?.realName || authStore.user?.username || 'admin')
const roleLabel = computed(() => {
  const map: Record<string, string> = { admin: '管理员', guard: '保安', resident: '住户' }
  return map[authStore.user?.role || ''] || '用户'
})
function handleLogout() { authStore.logout(); window.location.reload() }
</script>

<style scoped>
.app-header {
  display: flex; align-items: center; justify-content: space-between;
  height: var(--header-h); padding: 0 var(--space-5);
  background: var(--color-header-bg); color: #fff;
  flex-shrink: 0; position: relative; z-index: 100;
}
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-logo { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; color: var(--color-primary); }
.header-title { font-size: var(--font-lg); font-weight: 700; letter-spacing: 0.5px; color: #fff; }
.title-sep { color: rgba(255,255,255,0.3); margin: 0 2px; }
.title-sub { font-weight: 400; font-size: var(--font-md); opacity: 0.75; }
.header-right { display: flex; align-items: center; gap: var(--space-3); }
.role-tag { background: rgba(255,255,255,0.1) !important; border-color: rgba(255,255,255,0.2) !important; color: rgba(255,255,255,0.85) !important; font-size: var(--font-xs); }
.header-user { font-size: var(--font-base); color: rgba(255,255,255,0.9); }
</style>
