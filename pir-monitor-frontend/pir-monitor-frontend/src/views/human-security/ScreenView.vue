<template>
  <div class="hs-screen">
    <!-- Header -->
    <header class="screen-header">
      <div class="header-left">
        <span class="header-dot"></span>
        <span class="header-title">智慧安防监控大屏</span>
      </div>
      <div class="header-center">{{ clock }}</div>
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
          <div ref="trendChartRef" class="chart-box"></div>
        </div>
        <div class="chart-panel">
          <div class="panel-title">设备布防分布</div>
          <div ref="defenseChartRef" class="chart-box"></div>
        </div>
        <div class="chart-panel">
          <div class="panel-title">设备在线分布</div>
          <div ref="onlineChartRef" class="chart-box"></div>
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
                  <td>
                    <span :class="r.humanDetected === 1 ? 'tag tag-red' : 'tag tag-green'">
                      {{ r.humanDetected === 1 ? '有人' : '无人' }}
                    </span>
                  </td>
                  <td>
                    <span :class="r.defenseStatus === 1 ? 'tag tag-yellow' : 'tag tag-gray'">
                      {{ r.defenseStatus === 1 ? '布防' : '撤防' }}
                    </span>
                  </td>
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
                  <td>{{ fmt(a.alarmTime) }}</td>
                  <td>{{ a.deviceCode }}</td>
                  <td><span class="tag tag-red">{{ a.alarmType || '入侵告警' }}</span></td>
                  <td>
                    <span :class="a.handleStatus === 0 ? 'tag tag-red' : 'tag tag-green'">
                      {{ a.handleStatus === 0 ? '未处理' : a.handleStatus === 1 ? '已处理' : '误报' }}
                    </span>
                  </td>
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
import { getOverviewStats, getAlarmTrend, getPirRecords, getAlarms } from '@/api/humanSecurity'
import type { HumanRecord, Alarm } from '@/types'

const clock = ref('')

const stats = reactive({
  totalDevices: 0,
  onlineDevices: 0,
  armedDevices: 0,
  todayAlarms: 0,
})

const records = ref<HumanRecord[]>([])
const alarms = ref<Alarm[]>([])

const trendChartRef = ref<HTMLElement | null>(null)
const defenseChartRef = ref<HTMLElement | null>(null)
const onlineChartRef = ref<HTMLElement | null>(null)

let timer: ReturnType<typeof setInterval> | null = null
let trendInstance: echarts.ECharts | null = null
let defenseInstance: echarts.ECharts | null = null
let onlineInstance: echarts.ECharts | null = null

async function loadData() {
  try {
    // Stats
    const statsRes = await getOverviewStats()
    if (statsRes.data?.code === 200 && statsRes.data?.data) {
      const d: any = statsRes.data.data
      stats.totalDevices = d.deviceTotal || 0
      stats.onlineDevices = d.onlineDeviceCount || 0
      stats.armedDevices = d.defenseDeviceCount || 0
      stats.todayAlarms = d.todayAlarmCount || 0
    }

    // Trend
    const trendRes = await getAlarmTrend()
    if (trendRes.data?.code === 200) {
      renderTrendChart(trendRes.data.data || [])
    }
  } catch { /* silent */ }

  // Fallback: load records and alarms separately
  try {
    const [recordRes, alarmRes] = await Promise.all([
      getPirRecords({ pageSize: 10 }),
      getAlarms({ pageSize: 10 }),
    ])
    if (recordRes.data?.code === 200) {
      records.value = recordRes.data.data?.records || recordRes.data.data || []
    }
    if (alarmRes.data?.code === 200) {
      alarms.value = alarmRes.data.data?.records || alarmRes.data.data || []
    }
  } catch { /* silent */ }
}

function renderTrendChart(data: any[]) {
  if (!trendChartRef.value) return
  if (!trendInstance) trendInstance = echarts.init(trendChartRef.value)
  const keys = data.map((d: any) => d.date || d.day || '')
  const values = data.map((d: any) => d.count || d.total || 0)
  trendInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { data: keys, axisLine: { lineStyle: { color: '#0e5c8a' } }, axisLabel: { color: '#aed8f2', fontSize: 11 } },
    yAxis: { minInterval: 1, splitLine: { lineStyle: { color: 'rgba(14,92,138,0.3)' } }, axisLabel: { color: '#aed8f2' } },
    series: [{
      type: 'line', data: values, smooth: true,
      lineStyle: { color: '#00d4ff', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.4)' },
          { offset: 1, color: 'rgba(0,212,255,0.02)' },
        ]),
      },
      itemStyle: { color: '#00d4ff' },
    }],
  })

  // Defense chart
  if (defenseChartRef.value) {
    if (!defenseInstance) defenseInstance = echarts.init(defenseChartRef.value)
    defenseInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['50%', '75%'],
        data: [
          { value: stats.armedDevices, name: '布防', itemStyle: { color: '#f59e0b' } },
          { value: Math.max(0, stats.totalDevices - stats.armedDevices), name: '撤防', itemStyle: { color: '#1e3a5f' } },
        ],
        label: { color: '#aed8f2', fontSize: 12 },
      }],
    })
  }

  // Online chart
  if (onlineChartRef.value) {
    if (!onlineInstance) onlineInstance = echarts.init(onlineChartRef.value)
    onlineInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['50%', '75%'],
        data: [
          { value: stats.onlineDevices, name: '在线', itemStyle: { color: '#10b981' } },
          { value: Math.max(0, stats.totalDevices - stats.onlineDevices), name: '离线', itemStyle: { color: '#1e3a5f' } },
        ],
        label: { color: '#aed8f2', fontSize: 12 },
      }],
    })
  }
}

function updateClock() {
  clock.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', weekday: 'short',
  })
}

function fmt(t: string | Date): string {
  if (!t) return '-'
  const d = new Date(t)
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
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

<style scoped>
.hs-screen {
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

/* Header */
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
.header-title { font-size: 24px; font-weight: 700; letter-spacing: 4px; background: linear-gradient(90deg, #00d4ff, #00a6ff); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.header-dot { width: 10px; height: 10px; border-radius: 50%; background: #00d4ff; }
.header-dot.live { animation: blink 2s infinite; }
@keyframes blink { 0%,100%{opacity:1;} 50%{opacity:0.2;} }
.header-center { font-size: 18px; color: #aed8f2; letter-spacing: 2px; }
.header-status { color: #10b981; font-size: 14px; }

/* Body */
.screen-body {
  flex: 1;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

/* Stat cards */
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

/* Charts */
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

/* Tables */
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
