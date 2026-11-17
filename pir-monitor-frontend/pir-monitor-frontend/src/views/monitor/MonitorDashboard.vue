<template>
  <div class="dashboard-container">
    <h2>数据统计</h2>

    <!-- Stats -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-alarm">
          <div class="stat-value">{{ stats.todayAlarms }}</div>
          <div class="stat-label">今日告警</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-human">
          <div class="stat-value">{{ stats.todayRecords }}</div>
          <div class="stat-label">今日人体感应</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-online">
          <div class="stat-value">{{ stats.onlineDevices }}</div>
          <div class="stat-label">在线设备</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-defense">
          <div class="stat-value">{{ stats.armedDevices }}</div>
          <div class="stat-label">布防设备</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="chartAlarmRef" style="height:320px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="chartRecordRef" style="height:320px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import * as echarts from 'echarts'
import http from '@/api/index'
import type { ApiResponse, HumanRecord } from '@/types'

interface AlarmRow {
  id: number
  createTime?: string
  alarmTime?: string
}

interface Stats {
  todayAlarms: number
  todayRecords: number
  onlineDevices: number
  armedDevices: number
}

const chartAlarmRef = ref<HTMLDivElement | null>(null)
const chartRecordRef = ref<HTMLDivElement | null>(null)

let alarmChart: echarts.ECharts | null = null
let recordChart: echarts.ECharts | null = null

const stats = reactive<Stats>({
  todayAlarms: 0,
  todayRecords: 0,
  onlineDevices: 1,
  armedDevices: 0,
})

const loadStats = async () => {
  try {
    const [alarmRes, recordRes] = await Promise.all([
      http.get<ApiResponse<AlarmRow[]>>('/alarms', { params: { limit: 200 } }),
      http.get<ApiResponse<HumanRecord[]>>('/records', { params: { limit: 200 } }),
    ])

    const today = new Date().toDateString()
    const alarms: AlarmRow[] = alarmRes.data.data || []
    const records: HumanRecord[] = recordRes.data.data || []

    stats.todayAlarms = alarms.filter(a => {
      const t = a.createTime || a.alarmTime
      return t && new Date(t).toDateString() === today
    }).length

    stats.todayRecords = records.filter(r =>
      r.reportTime && new Date(r.reportTime).toDateString() === today
    ).length

    stats.armedDevices = records.filter(r => r.defenseStatus === 1).length

    renderAlarmChart(alarms)
    renderRecordChart(records)
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

const renderAlarmChart = (alarms: AlarmRow[]) => {
  if (!chartAlarmRef.value) return
  if (!alarmChart) alarmChart = echarts.init(chartAlarmRef.value)

  const map: Record<string, number> = {}
  const last7: string[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const key = d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    map[key] = 0
    last7.push(key)
  }
  alarms.forEach(a => {
    const t = a.createTime || a.alarmTime
    if (t) {
      const key = new Date(t).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
      if (map[key] !== undefined) map[key]++
    }
  })

  alarmChart.setOption({
    title: { text: '近7天告警趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: { data: last7 },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line',
      data: last7.map(k => map[k]),
      smooth: true,
      areaStyle: { color: 'rgba(245,108,108,0.2)' },
      itemStyle: { color: '#f56c6c' },
    }],
  })
}

const renderRecordChart = (records: HumanRecord[]) => {
  if (!chartRecordRef.value) return
  if (!recordChart) recordChart = echarts.init(chartRecordRef.value)

  let humanCount = 0
  let noHumanCount = 0
  records.forEach(r => {
    if (r.humanDetected === 1) humanCount++
    else noHumanCount++
  })

  recordChart.setOption({
    title: { text: '人体感应统计', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: humanCount, name: '有人', itemStyle: { color: '#f56c6c' } },
        { value: noHumanCount, name: '无人', itemStyle: { color: '#67c23a' } },
      ],
      label: { show: true, formatter: '{b}: {c} 次 ({d}%)' },
    }],
  })
}

onMounted(loadStats)
</script>

<style scoped>
.dashboard-container h2 { font-size: 20px; margin-bottom: 20px; }
.stats-row { margin-bottom: 20px; }
.stat-card { text-align: center; border-radius: 10px; }
.stat-value { font-size: 32px; font-weight: 700; }
.stat-label { color: #909399; font-size: 13px; margin-top: 8px; }
.stat-alarm .stat-value { color: #f56c6c; }
.stat-human .stat-value { color: #e6a23c; }
.stat-online .stat-value { color: #67c23a; }
.stat-defense .stat-value { color: #409eff; }

.charts-row { margin-bottom: 20px; }
</style>
