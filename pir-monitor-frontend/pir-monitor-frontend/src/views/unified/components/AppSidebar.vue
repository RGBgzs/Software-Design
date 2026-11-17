<template>
  <aside class="app-sidebar">
    <nav class="sidebar-nav">
      <div
        v-for="item in menuItems" :key="item.key"
        :class="['nav-item', { active: active === item.key }]"
        @click="$emit('change', item.key)"
      >
        <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
        <span class="nav-label">{{ item.label }}</span>
        <span v-if="item.key === 'alarm' && alarmUnhandled" class="nav-dot"></span>
        <span v-if="item.key === 'sos' && sosUnread" class="nav-dot"></span>
      </div>
      <div v-if="isGuard" :class="['nav-item', { active: active === 'users' }]" @click="$emit('change', 'users')">
        <el-icon class="nav-icon"><User /></el-icon>
        <span class="nav-label">管理用户</span>
      </div>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Monitor, Odometer, Warning, Camera, Bell, Headset, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{ active: string }>()
defineEmits<{ (e: 'change', key: string): void }>()

const authStore = useAuthStore()
const isGuard = computed(() => authStore.user?.role === 'guard')
const alarmUnhandled = ref(0)
const sosUnread = ref(false)

const menuItems = [
  { key: 'dashboard', label: '首页总控台',   icon: Monitor },
  { key: 'device',    label: '设备管理',     icon: Odometer },
  { key: 'pir',       label: 'PIR 人体感应', icon: Warning },
  { key: 'monitor',   label: '实时监测',     icon: Camera },
  { key: 'alarm',     label: '告警管理',     icon: Bell },
  { key: 'sos',       label: '一键报警',     icon: Headset },
]

function toggleXiaoAn() { window.dispatchEvent(new CustomEvent('assistant:toggle')) }
</script>

<style scoped>
.app-sidebar {
  width: var(--sidebar-w);
  background: linear-gradient(180deg, var(--color-sidebar-start) 0%, var(--color-sidebar-end) 100%);
  display: flex; flex-direction: column; flex-shrink: 0; overflow: hidden; user-select: none;
}
.sidebar-nav { flex: 1; padding: var(--space-2); display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex; align-items: center; gap: var(--space-3);
  padding: 10px var(--space-4); border-radius: var(--radius-md);
  cursor: pointer; color: var(--color-sidebar-text); transition: all 0.18s ease;
  font-size: var(--font-md); position: relative; font-weight: 500;
}
.nav-item:hover { color: var(--color-sidebar-text-active); background: var(--color-sidebar-hover); }
.nav-item.active {
  color: var(--color-sidebar-text-active); background: var(--color-sidebar-active);
  box-shadow: 0 2px 10px rgba(47,115,232,0.30), inset 0 1px 0 rgba(255,255,255,0.08); font-weight: 600;
}
.nav-icon { font-size: 18px; flex-shrink: 0; }
.nav-label { flex: 1; }
.nav-dot {
  width: 7px; height: 7px; border-radius: 50%; background: var(--color-danger);
  box-shadow: 0 0 5px rgba(232,76,91,0.4); animation: pulse-dot 2s infinite;
}
@keyframes pulse-dot { 0%,100%{opacity:1} 50%{opacity:.35} }
.sidebar-footer { padding: var(--space-3) var(--space-2); border-top: 1px solid rgba(255,255,255,0.06); }
.xiaoan-entry {
  display: flex; align-items: center; gap: var(--space-3);
  padding: 10px var(--space-4); border-radius: var(--radius-md);
  cursor: pointer; color: rgba(185,200,222,0.55); transition: all 0.18s;
}
.xiaoan-entry:hover { color: var(--color-sidebar-text); background: var(--color-sidebar-hover); }
.xiaoan-avatar { font-size: 18px; }
.xiaoan-label { flex: 1; font-size: var(--font-sm); }
.xiaoan-arrow { font-size: 12px; }
</style>
