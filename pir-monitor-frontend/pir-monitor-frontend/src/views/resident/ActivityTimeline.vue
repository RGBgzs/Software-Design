<template>
  <div class="activity-timeline">
    <h3>活动记录</h3>
    <el-card class="filter-bar" shadow="never">
      <el-form :inline="true" :model="filter">
        <el-form-item v-if="!isFromDevice" label="设备"><el-select v-model="filter.deviceCode" placeholder="全部" clearable style="width:200px" @change="search"><el-option v-for="d in deviceStore.devices" :key="d.deviceCode" :label="`${d.deviceName} · ${d.deviceCode}`" :value="d.deviceCode" /></el-select></el-form-item>
        <el-form-item v-else label="设备"><el-tag>{{ deviceLabel }}</el-tag></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="filter.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset" :disabled="isFromDevice">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12"><el-card shadow="never"><template #header><span>📊 各设备活动次数</span><el-tag size="small" style="margin-left:8px">近24小时</el-tag></template><div ref="pieChartRef" class="chart-container"></div></el-card></el-col>
      <el-col :xs="24" :lg="12"><el-card shadow="never"><template #header><span>📈 时段活动分布</span><el-tag size="small" style="margin-left:8px">近24小时</el-tag></template><div ref="barChartRef" class="chart-container"></div></el-card></el-col>
    </el-row>
    <el-card shadow="never" v-loading="loading">
      <div v-if="records.length"><el-timeline><el-timeline-item v-for="r in records" :key="r.id" :timestamp="fmt(r.reportTime)" :type="r.humanDetected?'warning':'info'" placement="top"><el-card shadow="hover" class="tl-card"><div class="tl-title"><strong>{{ r.deviceName || r.deviceCode }}</strong><el-tag :type="r.humanDetected?'warning':'info'" size="small">{{ r.humanDetected?'有人':'无人' }}</el-tag><el-tag v-if="r.defenseStatus" type="warning" size="small">布防中</el-tag><el-tag v-if="r.isAlarm" type="danger" size="small">触发告警</el-tag></div></el-card></el-timeline-item></el-timeline><el-pagination v-if="total>filter.pageSize" v-model:current-page="filter.page" :page-size="filter.pageSize" :total="total" layout="prev,pager,next" @current-change="fetch" class="pagination" /></div>
      <el-empty v-else description="暂无活动记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { HumanRecord } from '@/types'
import { getRecordsApi } from '@/api/activity'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'

echarts.use([PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const route = useRoute()
const deviceStore = useDeviceStore()
const authStore = useAuthStore()
const loading = ref(false)
const records = ref<HumanRecord[]>([])
const total = ref(0)
const filter = reactive({ deviceCode: '', dateRange: null as [string, string] | null, page: 1, pageSize: 10 })
const myDeviceCodes = ref<string[]>([])
const isFromDevice = computed(() => !!route.query.deviceCode)
const deviceLabel = computed(() => {
  if (!route.query.deviceCode) return ''
  const d = deviceStore.devices.find(d => d.deviceCode === route.query.deviceCode)
  return d ? `${d.deviceName} · ${d.deviceCode}` : String(route.query.deviceCode)
})
const pieChartRef = ref<HTMLDivElement>()
const barChartRef = ref<HTMLDivElement>()
let pieInst: echarts.ECharts | null = null, barInst: echarts.ECharts | null = null

onMounted(async () => {
  const uid = authStore.user?.userId
  if (uid) await deviceStore.fetchMyDevices(uid)
  myDeviceCodes.value = deviceStore.devices.map(d => d.deviceCode)
  if (route.query.deviceCode) filter.deviceCode = route.query.deviceCode as string
  await fetch(); await nextTick(); loadCharts()
})

async function fetch() {
  loading.value = true
  try {
    const p: any = { page: filter.page, pageSize: filter.pageSize }
    if (filter.deviceCode) {
      p.deviceCode = filter.deviceCode
    }
    if (filter.dateRange) { p.startTime = filter.dateRange[0] + ' 00:00:00'; p.endTime = filter.dateRange[1] + ' 23:59:59' }
    const res = await getRecordsApi(p)
    let items = res.data.data.records
    if (!filter.deviceCode) {
      items = items.filter(r => myDeviceCodes.value.includes(r.deviceCode))
    }
    records.value = items
    total.value = items.length
  } finally { loading.value = false }
}

function search() { filter.page = 1; fetch(); loadCharts() }
function reset() { filter.deviceCode = ''; filter.dateRange = null; filter.page = 1; fetch(); loadCharts() }

async function loadCharts() {
  try {
    const params: any = { page: 1, pageSize: 9999 }
    if (filter.deviceCode) params.deviceCode = filter.deviceCode
    const res = await getRecordsApi(params)
    let all = res.data.data.records
    if (!filter.deviceCode) {
      all = all.filter(r => myDeviceCodes.value.includes(r.deviceCode))
    }
    renderPie(all); renderBar(all)
  } catch { /* */ }
}

function renderPie(data: HumanRecord[]) {
  if (!pieChartRef.value) return; if (!pieInst) pieInst = echarts.init(pieChartRef.value)
  const map = new Map<string, number>()
  data.filter(d => d.humanDetected).forEach(d => map.set(d.deviceName || d.deviceCode, (map.get(d.deviceName || d.deviceCode) || 0) + 1))
  pieInst.setOption({ tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['45%','75%'], center: ['50%','50%'], itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 }, label: { formatter: '{b}\n{d}%' }, data: Array.from(map.entries()).map(([n, v]) => ({ name: n, value: v })) }] })
}

function renderBar(data: HumanRecord[]) {
  if (!barChartRef.value) return; if (!barInst) barInst = echarts.init(barChartRef.value)
  const hours = new Array(24).fill(0)
  data.forEach(d => { const h = new Date(d.reportTime).getHours(); hours[h]++ })
  barInst.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 40, right: 20, top: 10, bottom: 30 }, xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`), axisLabel: { rotate: 45, fontSize: 10 } }, yAxis: { type: 'value', name: '次数' }, series: [{ type: 'bar', data: hours, itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#409eff' }, { offset: 1, color: '#79bbff' }]), borderRadius: [4, 4, 0, 0] } }] })
}

function fmt(iso: string) { return new Date(iso).toLocaleString('zh-CN') }
</script>

<style scoped>
.activity-timeline h3 { margin-bottom: 16px; }
.filter-bar { margin-bottom: 16px; }
.chart-row { margin-bottom: 16px; }
.chart-container { width: 100%; height: 320px; }
.tl-card { border: none; }
.tl-title { display: flex; align-items: center; gap: 8px; }
.pagination { margin-top: 20px; justify-content: center; display: flex; }
</style>
