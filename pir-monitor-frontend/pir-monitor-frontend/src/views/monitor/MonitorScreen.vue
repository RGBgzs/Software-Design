<template>
  <div class="screen">
    <!-- Header -->
    <header class="screen-header">
      <div class="header-left">
        <span class="header-dot"></span>
        <span class="header-title">人体安防监控大屏</span>
      </div>
      <div class="header-center" id="screen-clock">{{ clock }}</div>
      <div class="header-right">
        <span class="header-status">系统运行中</span>
        <span class="header-dot live"></span>
      </div>
    </header>

    <!-- Main Grid -->
    <div class="screen-body">
      <!-- Row 1: 4 stat cards -->
      <div class="stat-cards">
        <div class="stat-card blue">
          <div class="stat-icon">📡</div>
          <div class="stat-info">
            <div class="stat-num">{{ stats.totalDevices }}</div>
            <div class="stat-label">设备总数</div>
          </div>
        </div>
        <div class="stat-card green">
          <div class="stat-icon">🟢</div>
          <div class="stat-info">
            <div class="stat-num">{{ stats.onlineDevices }}</div>
            <div class="stat-label">在线设备</div>
          </div>
        </div>
        <div class="stat-card orange">
          <div class="stat-icon">🛡️</div>
          <div class="stat-info">
            <div class="stat-num">{{ stats.armedDevices }}</div>
            <div class="stat-label">布防设备</div>
          </div>
        </div>
        <div class="stat-card red">
          <div class="stat-icon">🚨</div>
          <div class="stat-info">
            <div class="stat-num">{{ stats.todayAlarms }}</div>
            <div class="stat-label">今日告警</div>
          </div>
        </div>
      </div>

      <!-- Row 2: Charts -->
      <div class="chart-row">
        <div class="chart-panel">
          <div class="panel-title">近7天告警趋势</div>
          <div ref="trendChart" class="chart-box"></div>
        </div>
        <div class="chart-panel">
          <div class="panel-title">设备布防分布</div>
          <div ref="defenseChart" class="chart-box"></div>
        </div>
        <div class="chart-panel">
          <div class="panel-title">设备在线分布</div>
          <div ref="onlineChart" class="chart-box"></div>
        </div>
      </div>

      <!-- Row 3: Tables -->
      <div class="table-row">
        <div class="chart-panel">
          <div class="panel-title">人体感应记录</div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>时间</th><th>设备</th><th>人体</th><th>布防</th></tr></thead>
              <tbody>
                <tr v-for="r in records" :key="r.id">
                  <td>{{ fmt(r.reportTime) }}</td>
                  <td>{{ r.deviceCode }}</td>
                  <td><span :class="r.humanDetected === 1 ? 'tag tag-red' : 'tag tag-green'">{{ r.humanDetected === 1 ? '有人' : '无人' }}</span></td>
                  <td><span :class="r.defenseStatus === 1 ? 'tag tag-yellow' : 'tag tag-gray'">{{ r.defenseStatus === 1 ? '布防' : '撤防' }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="chart-panel">
          <div class="panel-title">告警记录</div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>时间</th><th>设备</th><th>类型</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="a in alarms" :key="a.id">
                  <td>{{ fmt(a.alarmTime || a.createTime) }}</td>
                  <td>{{ a.deviceCode }}</td>
                  <td><span class="tag tag-red">{{ a.alarmType }}</span></td>
                  <td><span :class="(a.handleStatus === 0) ? 'tag tag-red' : 'tag tag-green'">{{ (a.handleStatus === 0) ? '未处理' : '已处理' }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import http from '@/api/index'
import type { ApiResponse, HumanRecord } from '@/types'

interface StatsOverview {
  totalDevices?: number
  deviceTotal?: number
  onlineDevices?: number
  onlineDeviceCount?: number
  armedDevices?: number
  defenseDeviceCount?: number
  todayAlarms?: number
  todayAlarmCount?: number
  alarmTrend?: Record<string, number>
  defenseDistribution?: { armed?: number; disarmed?: number }
  onlineDistribution?: { online?: number; offline?: number }
}

interface AlarmRow {
  id: number
  deviceCode: string
  alarmType: string
  alarmContent?: string
  handleStatus: number
  alarmTime?: string
  createTime?: string
}

const clock = ref<string>('')
const stats = reactive({
  totalDevices: 0,
  onlineDevices: 0,
  armedDevices: 0,
  todayAlarms: 0,
})
const records = ref<HumanRecord[]>([])
const alarms = ref<AlarmRow[]>([])
const trendChart = ref<HTMLDivElement | null>(null)
const defenseChart = ref<HTMLDivElement | null>(null)
const onlineChart = ref<HTMLDivElement | null>(null)

let timer: ReturnType<typeof setInterval> | null = null
let trendInstance: echarts.ECharts | null = null
let defenseInstance: echarts.ECharts | null = null
let onlineInstance: echarts.ECharts | null = null

const loadData = async () => {
  try {
    const [statsRes, recordRes, alarmRes] = await Promise.all([
      http.get<ApiResponse<StatsOverview>>('/stats'),
      http.get<ApiResponse<HumanRecord[]>>('/records', { params: { limit: 10 } }),
      http.get<ApiResponse<AlarmRow[]>>('/alarms', { params: { limit: 10 } }),
    ])

    const s: StatsOverview = statsRes.data.data || {}
    if (statsRes.data.code === 200) {
      stats.totalDevices = s.totalDevices ?? s.deviceTotal ?? 0
      stats.onlineDevices = s.onlineDevices ?? s.onlineDeviceCount ?? 0
      stats.armedDevices = s.armedDevices ?? s.defenseDeviceCount ?? 0
      stats.todayAlarms = s.todayAlarms ?? s.todayAlarmCount ?? 0
      renderTrendChart(s.alarmTrend || {})
      renderDefenseChart(s.defenseDistribution || { armed: 0, disarmed: 0 })
      renderOnlineChart(s.onlineDistribution || { online: 0, offline: 0 })
    }
    if (recordRes.data.code === 200) records.value = recordRes.data.data || []
    if (alarmRes.data.code === 200) alarms.value = alarmRes.data.data || []
  } catch (e) {
    console.error('大屏数据加载失败', e)
  }
}

const renderTrendChart = (data: Record<string, number>) => {
  if (!trendChart.value) return
  if (!trendInstance) trendInstance = echarts.init(trendChart.value)
  const keys = Object.keys(data)
  const values = Object.values(data)
  trendInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { data: keys, axisLine: { lineStyle: { color: '#0e5c8a' } }, axisLabel: { color: '#aed8f2', fontSize: 11 } },
    yAxis: { minInterval: 1, splitLine: { lineStyle: { color: 'rgba(14,92,138,0.3)' } }, axisLabel: { color: '#aed8f2' } },
    series: [{
      type: 'line', data: values, smooth: true,
      lineStyle: { color: '#00d4ff', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(0,212,255,0.4)' }, { offset: 1, color: 'rgba(0,212,255,0.02)' },
      ]) },
      itemStyle: { color: '#00d4ff' },
    }],
  })
}

const renderDefenseChart = (data: { armed?: number; disarmed?: number }) => {
  if (!defenseChart.value) return
  if (!defenseInstance) defenseInstance = echarts.init(defenseChart.value)
  defenseInstance.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['50%', '75%'],
      data: [
        { value: data.armed || 0, name: '布防', itemStyle: { color: '#f59e0b' } },
        { value: data.disarmed || 0, name: '撤防', itemStyle: { color: '#1e3a5f' } },
      ],
      label: { color: '#aed8f2', fontSize: 12 },
    }],
  })
}

const renderOnlineChart = (data: { online?: number; offline?: number }) => {
  if (!onlineChart.value) return
  if (!onlineInstance) onlineInstance = echarts.init(onlineChart.value)
  onlineInstance.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['50%', '75%'],
      data: [
        { value: data.online || 0, name: '在线', itemStyle: { color: '#10b981' } },
        { value: data.offline || 0, name: '离线', itemStyle: { color: '#1e3a5f' } },
      ],
      label: { color: '#aed8f2', fontSize: 12 },
    }],
  })
}

const fmt = (t: string | undefined) => {
  if (!t) return '-'
  const d = new Date(t)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const updateClock = () => {
  clock.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', weekday: 'short',
  })
}

onMounted(() => {
  updateClock()
  loadData()
  timer = setInterval(() => { updateClock(); loadData() }, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  trendInstance?.dispose()
  defenseInstance?.dispose()
  onlineInstance?.dispose()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { overflow: hidden; }
</style>

<style scoped>
.screen {
  width: 100vw;
  height: 100vh;
  background: #0a1a2e;
  color: #fff;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 9999;
}

/* --- Header --- */
.screen-header {
  height: 64px;
  background: linear-gradient(180deg, rgba(0,20,40,1) 0%, rgba(10,26,46,1) 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  border-bottom: 2px solid rgba(0,212,255,0.3);
  flex-shrink: 0;
}
.header-left, .header-right { display: flex; align-items: center; gap: 12px; }
.header-title { font-size: 24px; font-weight: 700; letter-spacing: 4px; background: linear-gradient(90deg, #00d4ff, #00a6ff); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.header-dot { width: 10px; height: 10px; border-radius: 50%; background: #00d4ff; }
.header-dot.live { animation: blink 2s infinite; }
@keyframes blink { 0%,100%{opacity:1;} 50%{opacity:0.2;} }
.header-center { font-size: 18px; color: #aed8f2; letter-spacing: 2px; }
.header-status { color: #10b981; font-size: 14px; }

/* --- Body --- */
.screen-body {
  flex: 1;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

/* --- Stat cards --- */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  flex-shrink: 0;
}
.stat-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.05), rgba(255,255,255,0.02));
  border: 1px solid rgba(0,212,255,0.2);
  border-radius: 8px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}
.stat-icon { font-size: 40px; }
.stat-num { font-size: 36px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #aed8f2; margin-top: 4px; }
.stat-card.blue .stat-num { color: #00d4ff; }
.stat-card.green .stat-num { color: #10b981; }
.stat-card.orange .stat-num { color: #f59e0b; }
.stat-card.red .stat-num { color: #ef4444; }

/* --- Charts --- */
.chart-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.chart-panel {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(0,212,255,0.15);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}
.panel-title {
  font-size: 14px;
  color: #00d4ff;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0,212,255,0.15);
  margin-bottom: 12px;
  flex-shrink: 0;
}
.chart-box { flex: 1; min-height: 200px; }

/* --- Tables --- */
.table-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  flex-shrink: 0;
  height: 260px;
}
.table-wrap { overflow-y: auto; flex: 1; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
thead th {
  background: rgba(0,212,255,0.1);
  color: #00d4ff;
  padding: 8px 12px;
  text-align: left;
  font-weight: 500;
  position: sticky; top: 0;
}
tbody td { padding: 7px 12px; border-bottom: 1px solid rgba(255,255,255,0.05); color: #c8dbe8; }

.tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.tag-red { background: rgba(239,68,68,0.25); color: #f87171; }
.tag-green { background: rgba(16,185,129,0.25); color: #34d399; }
.tag-yellow { background: rgba(245,158,11,0.25); color: #fbbf24; }
.tag-gray { background: rgba(148,163,184,0.2); color: #94a3b8; }

/* Scrollbar */
.table-wrap::-webkit-scrollbar { width: 4px; }
.table-wrap::-webkit-scrollbar-thumb { background: rgba(0,212,255,0.3); border-radius: 2px; }
</style>
