<template>
  <span class="status-tag" :class="[`status-tag--${type}`]">
    <span class="status-dot" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status?: string | number; type?: string }>()

const type = computed(() => props.type || mapStatus(props.status))
const label = computed(() => props.status !== undefined ? statusText(props.status) : '')

function mapStatus(s?: string | number): string {
  if (s === undefined || s === null) return 'default'
  const str = String(s).toUpperCase()
  if (['ONLINE', '1', 'ACTIVE', 'MATCHED', 'HANDLED', 'ACKNOWLEDGED', 'FINISHED'].includes(str)) return 'success'
  if (['OFFLINE', 'FAILED'].includes(str)) return 'danger'
  if (['UNHANDLED', 'PENDING', 'PROCESSING'].includes(str)) return 'warning'
  if (['HIGH', 'INTRUSION', 'FACE_UNKNOWN', 'FACE_SPOOF', 'CRITICAL'].includes(str)) return 'danger'
  if (['MEDIUM', 'MIDDLE'].includes(str)) return 'warning'
  if (['LOW'].includes(str)) return 'info'
  if (['MISREPORT', '2', 'FALSE_ALARM'].includes(str)) return 'info'
  return 'default'
}
function statusText(s?: string | number): string {
  const map: Record<string, string> = {
    '1': '在线', '0': '离线', 'ONLINE': '在线', 'OFFLINE': '离线',
    'HIGH': '高危', 'MEDIUM': '中危', 'LOW': '低危',
    'HANDLED': '已处理', 'UNHANDLED': '未处理', 'MISREPORT': '误报',
    'INTRUSION': '入侵', 'FACE_UNKNOWN': '陌生人', 'FACE_SPOOF': '假体攻击',
  }
  return map[String(s)] || String(s ?? '')
}
</script>

<style scoped>
.status-tag {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 10px; border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs); font-weight: 600;
  line-height: 1.4;
}
.status-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.status-tag--default { background: #f1f5f9; color: #64748b; } .status-tag--default .status-dot { background: #94a3b8; }
.status-tag--success { background: #f0fdf4; color: #16a34a; } .status-tag--success .status-dot { background: #22c55e; }
.status-tag--danger  { background: #fef2f2; color: #dc2626; } .status-tag--danger  .status-dot { background: #ef4444; }
.status-tag--warning { background: #fffbeb; color: #d97706; } .status-tag--warning .status-dot { background: #f59e0b; }
.status-tag--info    { background: #eff6ff; color: #3b82f6; } .status-tag--info    .status-dot { background: #60a5fa; }
</style>
