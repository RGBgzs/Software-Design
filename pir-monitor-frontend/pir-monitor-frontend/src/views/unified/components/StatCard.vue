<template>
  <div class="stat-card" :class="[`stat-card--${variant}`]">
    <div class="stat-icon" :style="{ background: iconBg, color: iconColor }">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-value">
        <span v-if="loading" class="stat-skeleton" />
        <span v-else>{{ formatted }}</span>
      </div>
      <div class="stat-label">{{ label }}</div>
    </div>
    <div v-if="trend != null" class="stat-trend" :class="{ up: (trend || 0) > 0, down: (trend || 0) < 0 }">
      <span class="trend-arrow">{{ (trend || 0) > 0 ? '▲' : '▼' }}</span>
      <span>{{ Math.abs(trend || 0) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  icon: any; label: string; value?: number | string
  variant?: 'default' | 'primary' | 'success' | 'danger' | 'warning'
  trend?: number | null; loading?: boolean
}>(), { variant: 'default', value: 0, trend: null })

const formatted = computed(() => {
  const v = props.value ?? 0
  if (typeof v === 'number' && v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return String(v)
})

const colors: Record<string, { bg: string; color: string }> = {
  default:  { bg: '#f1f5f9', color: '#64748b' },
  primary:  { bg: '#eff6ff', color: '#2563eb' },
  success:  { bg: '#f0fdf4', color: '#16a34a' },
  danger:   { bg: '#fef2f2', color: '#dc2626' },
  warning:  { bg: '#fffbeb', color: '#d97706' },
}
const iconBg = computed(() => colors[props.variant]?.bg || colors.default.bg)
const iconColor = computed(() => colors[props.variant]?.color || colors.default.color)
</script>

<style scoped>
.stat-card {
  background: var(--app-card-bg); border-radius: var(--app-radius-lg);
  padding: var(--app-space-5); display: flex; align-items: center; gap: 14px;
  box-shadow: var(--app-shadow-sm); transition: box-shadow 0.2s, transform 0.2s;
  min-width: 0; border: 1px solid var(--app-border-light);
  position: relative; overflow: hidden;
}
.stat-card:hover { box-shadow: var(--app-shadow-md); transform: translateY(-1px); }
.stat-card--primary::before, .stat-card--success::before,
.stat-card--danger::before, .stat-card--warning::before {
  content: ''; position: absolute; left: 0; top: 12px; bottom: 12px;
  width: 3px; border-radius: 0 2px 2px 0;
}
.stat-card--primary::before { background: var(--app-primary); }
.stat-card--success::before { background: var(--app-success); }
.stat-card--danger::before  { background: var(--app-danger); }
.stat-card--warning::before { background: var(--app-warning); }
.stat-icon {
  width: 46px; height: 46px; border-radius: var(--app-radius-md);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.stat-body { flex: 1; min-width: 0; }
.stat-value {
  font-size: var(--app-font-size-3xl); font-weight: 800;
  color: var(--app-text-primary); line-height: 1.15;
}
.stat-label { font-size: var(--app-font-size-sm); color: var(--app-text-muted); margin-top: 3px; }
.stat-trend {
  font-size: var(--app-font-size-sm); font-weight: 600;
  display: flex; align-items: center; gap: 2px; flex-shrink: 0;
}
.stat-trend.up { color: #16a34a; }
.stat-trend.down { color: #dc2626; }
.stat-skeleton {
  display: inline-block; width: 56px; height: 28px;
  background: var(--app-border-light); border-radius: 4px;
  animation: stat-pulse 1.5s infinite;
}
@keyframes stat-pulse { 0%,100% { opacity: 1 } 50% { opacity: .35 } }
</style>
