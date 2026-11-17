<template>
  <div class="dashboard">
    <!-- ═══ 页面头部 ═══ -->
    <div class="dash-header">
      <div class="header-left">
        <h1 class="header-greeting">您好，{{ username }}，欢迎回到综合监控中心</h1>
        <p class="header-subtitle">安全提示：请定期检查设备状态，及时处理告警，确保场所安全。</p>
      </div>
      <div class="header-right">
        <span class="update-time" v-if="lastUpdateTime">
          <el-icon><Clock /></el-icon>
          数据更新时间：{{ lastUpdateTime }}
        </span>
        <el-button :icon="RefreshRight" @click="loadAll" :loading="loading" size="default">
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- ═══ KPI 统计卡片 ═══ -->
    <div class="stat-row">
      <StatCard
        :icon="icons.odometer"
        label="设备总数"
        :value="stats.deviceTotal"
        variant="primary"
        :trend="trends.device"
        :loading="loading"
      />
      <StatCard
        :icon="icons.monitor"
        label="在线设备"
        :value="stats.onlineDeviceCount"
        variant="success"
        :trend="trends.online"
        :loading="loading"
      />
      <StatCard
        :icon="icons.warningFilled"
        label="今日告警"
        :value="stats.todayAlarmCount"
        variant="danger"
        :trend="trends.alarm"
        :loading="loading"
      />
      <StatCard
        :icon="icons.lock"
        label="布防设备"
        :value="stats.defenseDeviceCount"
        variant="warning"
        :trend="trends.defense"
        :loading="loading"
      />
    </div>

    <!-- ═══ 图表行：告警趋势 + 设备在线率 ═══ -->
    <div class="chart-row">
      <div class="card chart-card chart-card--trend">
        <div class="card-header">
          <h3 class="card-title">告警趋势</h3>
          <div class="period-tabs">
            <button
              v-for="p in periods"
              :key="p.value"
              :class="['period-tab', { 'period-tab--active': trendPeriod === p.value }]"
              @click="trendPeriod = p.value"
            >
              {{ p.label }}
            </button>
          </div>
        </div>
        <div ref="trendRef" class="chart-box" />
      </div>
      <div class="card chart-card chart-card--pie">
        <div class="card-header">
          <h3 class="card-title">设备在线率</h3>
        </div>
        <div ref="pieRef" class="chart-box" />
      </div>
    </div>

    <!-- ═══ 底部行：快捷操作 + 最近告警 ═══ -->
    <div class="bottom-row">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">快捷操作</h3>
        </div>
        <div class="quick-grid">
          <button
            v-for="m in quickModules"
            :key="m.key"
            class="quick-item"
            @click="switchTo(m.key)"
          >
            <span class="quick-icon" :style="{ background: m.bg, color: m.color }">
              <el-icon :size="20"><component :is="m.icon" /></el-icon>
            </span>
            <span class="quick-label">{{ m.label }}</span>
          </button>
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">最近告警</h3>
          <a class="view-all" @click="switchTo('alarm')">查看全部</a>
        </div>
        <div v-if="recentAlarms.length" class="alarm-list">
          <div v-for="a in recentAlarms.slice(0, 5)" :key="a.id" class="alarm-row">
            <StatusTag :status="a.alarmLevel" />
            <span class="alarm-content">{{ a.alarmContent }}</span>
            <span class="alarm-device">{{ a.deviceCode || a.deviceName || '-' }}</span>
            <span class="alarm-time">{{ fmt(a.alarmTime) }}</span>
          </div>
        </div>
        <EmptyState v-else text="暂无告警" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, onActivated, nextTick, inject } from 'vue'
import * as echarts from 'echarts'
import {
  RefreshRight, Clock, Odometer, Monitor, WarningFilled, Lock,
  Cpu, Camera, Warning, Headset, ChatDotRound,
} from '@element-plus/icons-vue'
import StatCard from '@/views/unified/components/StatCard.vue'
import StatusTag from '@/views/unified/components/StatusTag.vue'
import EmptyState from '@/views/unified/components/EmptyState.vue'
import { getOverviewStats, getAlarmTrend, getAlarms } from '@/api/humanSecurity'
import { useAuthStore } from '@/stores/auth'
import type { Alarm } from '@/types'

const icons = { odometer: Odometer, monitor: Monitor, warningFilled: WarningFilled, lock: Lock }
const switchModule = inject<(key: string) => void>('switchModule', () => {})

const authStore = useAuthStore()
const username = computed(() => authStore.user?.realName || authStore.user?.username || '管理员')

const loading = ref(false)
const lastUpdateTime = ref('')
const stats = reactive({
  deviceTotal: 0,
  onlineDeviceCount: 0,
  todayAlarmCount: 0,
  defenseDeviceCount: 0,
  offlineDeviceCount: 0,
  deviceOnlineRate: 0,
})
const trends = reactive({
  device: null as number | null,
  online: null as number | null,
  alarm: null as number | null,
  defense: null as number | null,
})
const trendData = ref<{ date: string; count: number }[]>([])
const recentAlarms = ref<Alarm[]>([])

const trendRef = ref<HTMLElement>()
const pieRef = ref<HTMLElement>()

let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const trendPeriod = ref(7)
const periods = [
  { label: '近7天', value: 7 },
  { label: '近30天', value: 30 },
  { label: '近90天', value: 90 },
]

const filteredTrendData = computed(() => {
  if (!trendData.value.length) return [] as { date: string; count: number }[]
  const days = trendPeriod.value
  const cutoff = new Date()
  cutoff.setDate(cutoff.getDate() - days)
  cutoff.setHours(0, 0, 0, 0)
  return trendData.value.filter(item => new Date(item.date) >= cutoff)
})

const quickModules = [
  { key: 'monitor', label: '实时监测', icon: Camera,         bg: '#e8f0fe', color: '#2468f2' },
  { key: 'pir',     label: 'PIR监测',  icon: WarningFilled, bg: '#fef3c7', color: '#f59e0b' },
  { key: 'device',  label: '设备管理', icon: Cpu,            bg: '#e6f7ee', color: '#16b364' },
  { key: 'alarm',   label: '告警管理', icon: Warning,        bg: '#fee2e2', color: '#ef4444' },
  { key: 'sos',     label: '一键报警', icon: Headset,        bg: '#fdf2f8', color: '#db2777' },
]

onMounted(() => {
  loadAll()
  window.addEventListener('resize', resizeHandler)
  document.addEventListener('visibilitychange', visibilityHandler)
  startPolling()
})
onUnmounted(() => {
  window.removeEventListener('resize', resizeHandler)
  document.removeEventListener('visibilitychange', visibilityHandler)
  stopPolling()
})

onActivated(() => {
  nextTick(() => {
    trendChart?.resize()
    pieChart?.resize()
  })
})

// ── Polling: 3秒刷新 ──
let pollTimer: any = null
let requestInFlight = false

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (document.hidden || requestInFlight) return
    refreshData()
  }, 3000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function visibilityHandler() {
  if (document.hidden) {
    stopPolling()
  } else {
    refreshData()
    startPolling()
  }
}

async function refreshData() {
  if (requestInFlight) return
  requestInFlight = true
  try {
    const [sr, tr] = await Promise.all([
      getOverviewStats(authStore.user?.userId, authStore.user?.role).catch(() => null),
      getAlarmTrend(trendPeriod.value, authStore.user?.userId, authStore.user?.role).catch(() => null),
    ])
    if (sr) {
      const d = sr.data?.data || sr.data
      if (d) {
        const dx: any = d
        Object.assign(stats, {
          deviceTotal: dx.deviceTotal || dx.totalDevices || 0,
          onlineDeviceCount: dx.deviceOnline || dx.onlineDevices || dx.online || 0,
          todayAlarmCount: dx.todayAlarmCount || dx.todayAlarms || 0,
          defenseDeviceCount: dx.defenseDeviceCount || dx.armedDevices || 0,
          offlineDeviceCount: dx.deviceOffline || dx.offlineDevices || dx.offline || 0,
          deviceOnlineRate: dx.deviceOnlineRate || 0,
        })
      }
    }
    if (tr) {
      const d = tr.data?.data || tr.data
      if (Array.isArray(d)) trendData.value = d
    }
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    await nextTick()
    renderCharts()
  } catch { /* ignore */ }
  requestInFlight = false
}

// 时间段切换时重新加载趋势数据
watch(trendPeriod, (days) => {
  loadTrendForPeriod(days)
})

async function loadTrendForPeriod(days: number) {
  try {
    const tr = await getAlarmTrend(days, authStore.user?.userId, authStore.user?.role).catch(() => null)
    if (tr) {
      const d = tr.data?.data || tr.data
      if (Array.isArray(d)) trendData.value = d
    }
    await nextTick()
    renderCharts()
  } catch { /* ignore */ }
}

async function loadAll() {
  loading.value = true
  try {
    const [sr, tr, ar] = await Promise.all([
      getOverviewStats(authStore.user?.userId, authStore.user?.role).catch(() => null),
      getAlarmTrend(trendPeriod.value, authStore.user?.userId, authStore.user?.role).catch(() => null),
      getAlarms({ page: 1, pageSize: 5, userId: authStore.user?.userId || authStore.user?.id || 1, role: authStore.user?.role || 'admin' }).catch(() => null),
    ])

    if (sr) {
      const d = sr.data?.data || sr.data
      if (d) {
        const dx: any = d
        // 兼容新旧字段名
        Object.assign(stats, {
          deviceTotal: dx.deviceTotal || dx.totalDevices || 0,
          onlineDeviceCount: dx.deviceOnline || dx.onlineDevices || dx.online || 0,
          todayAlarmCount: dx.todayAlarmCount || dx.todayAlarms || dx.alarmCount || 0,
          defenseDeviceCount: dx.defenseDeviceCount || dx.armedDevices || dx.defense || 0,
          offlineDeviceCount: dx.deviceOffline || dx.offlineDevices || dx.offline || 0,
          deviceOnlineRate: dx.deviceOnlineRate || 0,
        })
        // 计算较昨日的变化百分比
        const yDevice  = dx.yesterdayDeviceTotal  ?? dx.yesterdayDevice  ?? null
        const yOnline  = dx.yesterdayOnlineCount  ?? dx.yesterdayOnline  ?? null
        const yAlarm   = dx.yesterdayAlarmCount   ?? dx.yesterdayAlarm   ?? null
        const yDefense = dx.yesterdayDefenseCount ?? dx.yesterdayDefense ?? null
        trends.device  = calcTrend(stats.deviceTotal, yDevice)
        trends.online  = calcTrend(stats.onlineDeviceCount, yOnline)
        trends.alarm   = yAlarm != null ? calcTrend(stats.todayAlarmCount, yAlarm) : null
        trends.defense = calcTrend(stats.defenseDeviceCount, yDefense)
      }
    }

    if (tr) {
      const d = tr.data?.data || tr.data
      if (Array.isArray(d)) trendData.value = d
    }

    if (ar) {
      const d = ar.data?.data
      recentAlarms.value = d?.records || d || []
    }

    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    /* ignore */
  }
  loading.value = false
  await nextTick()
  renderCharts()
}

function calcTrend(current: number, yesterday: number | null): number | null {
  if (yesterday == null || yesterday === 0) return null
  return Math.round(((current - yesterday) / yesterday) * 100)
}

function renderCharts() {
  const trendDataToUse = filteredTrendData.value

  if (trendRef.value) {
    if (!trendChart) trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      grid: { top: 12, right: 20, bottom: 28, left: 44 },
      xAxis: {
        type: 'category',
        data: trendDataToUse.length
          ? trendDataToUse.map(i => i.date?.slice(5) || '')
          : ['暂无'],
        axisLabel: { fontSize: 11, color: '#98a2b3' },
        axisLine: { lineStyle: { color: '#e8edf5' } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        axisLabel: { fontSize: 11, color: '#98a2b3' },
        splitLine: { lineStyle: { color: '#f2f5fa', type: 'dashed' } },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#ffffff',
        borderColor: '#e8edf5',
        borderWidth: 1,
        textStyle: { color: '#13213c', fontSize: 12 },
        boxShadow: '0 4px 20px rgba(27,52,89,.06)',
      },
      series: [{
        data: trendDataToUse.length
          ? trendDataToUse.map(i => i.count)
          : [0],
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { color: '#2468f2', width: 2.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(36,104,242,.18)' },
            { offset: 1, color: 'rgba(36,104,242,0)' },
          ]),
        },
        itemStyle: { color: '#2468f2' },
      }],
    })
  }

  if (pieRef.value) {
    if (!pieChart) pieChart = echarts.init(pieRef.value)
    const online = stats.onlineDeviceCount || 0
    const total = stats.deviceTotal || 0
    const offline = total > 0 ? Math.max(total - online, 0) : 0

    const rateText = total === 0 ? '0%' : `${stats.deviceOnlineRate || Math.round(online * 100 / total)}%`

    pieChart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: '#ffffff',
        borderColor: '#e8edf5',
        borderWidth: 1,
        textStyle: { color: '#13213c', fontSize: 12 },
        boxShadow: '0 4px 20px rgba(27,52,89,.06)',
      },
      series: [{
        type: 'pie',
        radius: ['58%', '80%'],
        center: ['50%', '52%'],
        avoidLabelOverlap: false,
        label: {
          show: true,
          position: 'center',
          formatter: total === 0
            ? '{b|0%}\n{a|设备在线率}'
            : `{b|${rateText}}\n{a|设备在线率}`,
          rich: {
            b: { fontSize: 28, fontWeight: 700, color: total === 0 ? '#98a2b3' : '#13213c', lineHeight: 36 },
            a: { fontSize: 13, color: '#98a2b3', lineHeight: 20 },
          },
        },
        emphasis: {
          scale: false,
          label: { show: false },
        },
        data: total === 0
          ? [{ value: 1, name: '无设备', itemStyle: { color: '#e8edf5' } }]
          : [
              { value: online || 0, name: `在线 ${online}`, itemStyle: { color: '#16b364' } },
              { value: offline || 0, name: `离线 ${offline}`, itemStyle: { color: '#e8edf5' } },
            ],
      }],
    })
  }
}

function switchTo(key: string) {
  switchModule(key)
}

function fmt(t?: string) {
  return t ? new Date(t).toLocaleString('zh-CN').slice(5, 16) : '-'
}

const resizeHandler = () => {
  trendChart?.resize()
  pieChart?.resize()
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   Dashboard — Enterprise SaaS Styling
   ═══════════════════════════════════════════════════ */

.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
  min-height: 100%;
}

/* ── 页面头部 ── */
.dash-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.header-greeting {
  font-size: var(--app-font-size-xl);
  font-weight: 700;
  color: var(--app-text-primary);
  margin: 0;
  line-height: 1.4;
}
.header-subtitle {
  font-size: var(--app-font-size-base);
  color: var(--app-text-muted);
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: var(--app-space-4);
  flex-shrink: 0;
}
.update-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--app-font-size-sm);
  color: var(--app-text-muted);
  white-space: nowrap;
}

/* ── KPI 统计行 ── */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--app-space-4);
}

/* ── 图表行 ── */
.chart-row {
  display: grid;
  grid-template-columns: 65fr 35fr;
  gap: var(--app-space-4);
}

/* ── 底部行 ── */
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--app-space-4);
}

/* ── 通用卡片 ── */
.card {
  background: var(--app-card-bg);
  border-radius: var(--app-radius-lg);
  border: 1px solid var(--app-border-light);
  box-shadow: var(--app-shadow-sm);
  padding: var(--app-space-5);
  display: flex;
  flex-direction: column;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--app-space-4);
}
.card-title {
  font-size: var(--app-font-size-md);
  font-weight: 600;
  color: var(--app-text-primary);
  margin: 0;
}

/* ── 图表卡片 ── */
.chart-card--trend {
  min-width: 0;
  min-height: 340px;
}
.chart-card--pie {
  min-width: 0;
  min-height: 340px;
}
.chart-box {
  width: 100%;
  height: 260px;
  min-height: 260px;
  flex: 1;
}

/* ── 时间段切换标签 ── */
.period-tabs {
  display: flex;
  gap: 2px;
  background: var(--app-bg);
  border-radius: var(--app-radius-sm);
  padding: 3px;
}
.period-tab {
  padding: 4px 14px;
  border: none;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-sm);
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
  line-height: 1.6;
}
.period-tab:hover {
  color: var(--app-text-secondary);
}
.period-tab--active {
  background: var(--app-card-bg);
  color: var(--app-primary);
  font-weight: 600;
  box-shadow: var(--app-shadow-xs);
}

/* ── 快捷操作 ── */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--app-space-3);
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: var(--app-space-4) 6px;
  border-radius: var(--app-radius-md);
  cursor: pointer;
  border: 1px solid var(--app-border-light);
  background: var(--app-card-bg);
  transition: all 0.15s;
  font-family: inherit;
}
.quick-item:hover {
  border-color: var(--app-border);
  background: var(--app-card-hover);
  box-shadow: var(--app-shadow-xs);
}
.quick-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--app-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.quick-label {
  font-size: var(--app-font-size-sm);
  color: var(--app-text-secondary);
  font-weight: 500;
}

/* ── 最近告警列表 ── */
.view-all {
  font-size: var(--app-font-size-sm);
  color: var(--app-primary);
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.15s;
}
.view-all:hover {
  color: var(--app-primary-hover);
}
.alarm-list {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  flex: 1;
}
.alarm-row {
  display: flex;
  align-items: center;
  gap: var(--app-space-3);
  padding: var(--app-space-2) 0;
  border-bottom: 1px solid var(--app-border-light);
}
.alarm-row:last-child {
  border-bottom: none;
}
.alarm-content {
  flex: 1;
  font-size: var(--app-font-size-base);
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alarm-device {
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
  white-space: nowrap;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.alarm-time {
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
  white-space: nowrap;
}

/* ── 响应式 ── */
@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-row,
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .dash-header {
    flex-direction: column;
    gap: var(--app-space-3);
  }
  .header-right {
    width: 100%;
    justify-content: space-between;
  }
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }
  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
