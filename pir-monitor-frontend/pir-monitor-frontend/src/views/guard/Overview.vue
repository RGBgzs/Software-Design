<template>
  <div class="overview">
    <h3>安防总览</h3>
    <el-row :gutter="16" class="stats-row" v-loading="loading">
      <el-col :span="4" v-for="s in statsCards" :key="s.label"><div class="stat-card"><span class="stat-num" :style="{color:s.color}">{{ s.value }}</span><span class="stat-label">{{ s.label }}</span></div></el-col>
    </el-row>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="24"><el-card shadow="never"><template #header><span>📈 近7天告警趋势</span></template><div ref="trendRef" class="chart-container"></div></el-card></el-col>
    </el-row>
    <el-card shadow="never" v-loading="pointsLoading">
      <template #header><span>点位状态</span><el-button size="small" style="float:right" @click="refresh">刷新</el-button></template>
      <div class="point-grid" v-if="deviceStore.devices.length">
        <el-card v-for="p in deviceStore.overviewPoints" :key="p.deviceCode" shadow="hover" class="point-card" :class="{ pir_active:p.humanDetected, offline:!p.online }">
          <div class="point-header"><span class="point-name">{{ p.deviceName }}</span><el-tag :type="p.online?'success':'danger'" size="small">{{ p.online?'在线':'离线' }}</el-tag></div>
          <div class="point-body"><div class="point-indicator" :class="{ active:p.humanDetected }"><el-icon :size="32"><UserFilled v-if="p.humanDetected" /><Moon v-else /></el-icon></div><div class="point-text">{{ p.humanDetected?'有人':'无人' }}</div></div>
          <div class="point-footer"><el-tag :type="p.defense?'warning':'info'" size="small">{{ p.defense?'已布防':'未布防' }}</el-tag><span class="point-loc">{{ p.location }}</span><el-button size="small" link style="margin-left:auto" @click="openLocalCamera"><el-icon><Camera /></el-icon></el-button></div>
        </el-card>
      </div>
      <el-empty v-else description="暂无设备" />
    </el-card>

    <!-- SOS 求助通知弹窗 -->
    <el-dialog v-model="sosVisible" title="🆘 紧急求助通知" width="500px" :close-on-click-modal="false">
      <div style="text-align:center;padding:16px 0">
        <div style="font-size:48px;margin-bottom:12px">🆘</div>
        <h3 style="margin:0 0 12px">{{ sosMsg?.title || '紧急求助' }}</h3>
        <p style="white-space:pre-wrap;color:#475569;line-height:1.7;text-align:left">{{ sosMsg?.content }}</p>
        <div style="display:flex;gap:10px;justify-content:center;margin-top:16px">
          <el-button type="danger" @click="ackSos">确认收到</el-button>
          <el-button type="primary" @click="replySos">回复消息</el-button>
          <el-button @click="sosVisible = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { UserFilled, Moon, Camera } from '@element-plus/icons-vue'
import { useDeviceStore } from '@/stores/device'
import { useMqttStore } from '@/stores/mqtt'
import { useAuthStore } from '@/stores/auth'
import { getOverviewStatsApi, getAlarmTrendApi } from '@/api/statistics'
import http from '@/api/index'
import { ElMessage } from 'element-plus'

echarts.use([LineChart, TitleComponent, TooltipComponent, GridComponent, CanvasRenderer])

const deviceStore = useDeviceStore()
const mqttStore = useMqttStore()
const router = useRouter()
const loading = ref(false)
const pointsLoading = ref(false)
const trendRef = ref<HTMLDivElement>()
let trendInst: echarts.ECharts | null = null

const statsCards = ref([
  { label: '设备总数', value: 0, color: '#409eff' },
  { label: '在线设备', value: 0, color: '#67c23a' },
  { label: '离线设备', value: 0, color: '#f56c6c' },
  { label: '今日告警', value: 0, color: '#e6a23c' },
  { label: '布防设备', value: 0, color: '#909399' },
])

let pollTimer: ReturnType<typeof setInterval> | null = null
let sosTimer: ReturnType<typeof setInterval> | null = null
const authStore = useAuthStore()
const sosVisible = ref(false)
const sosMsg = ref<any>(null)
const lastSosId = ref(0)

// ─── SOS 求助通知轮询 ───

async function checkSosMessages() {
  try {
    const guardId = authStore.user?.userId || authStore.user?.id
    const res = await http.get('/messages/inbox', { params: { userId: guardId } })
    const messages = res.data?.data || []
    const unread = messages.filter(
      (m: any) => (m.messageType === 'SOS' || m.messageType === 'HELP') && m.isEmergency === 1 && !m.readTime
    )
    if (unread.length > 0 && !sosVisible.value) {
      const latest = unread[0]
      if (latest.id > lastSosId.value) {
        lastSosId.value = latest.id
        sosMsg.value = latest
        sosVisible.value = true
      }
    }
  } catch { /* silent */ }
}

async function ackSos() {
  if (sosMsg.value) {
    try { await http.post(`/messages/${sosMsg.value.id}/read`) } catch {}
  }
  sosVisible.value = false
  ElMessage.success('已确认收到求助')
}

function replySos() {
  sosVisible.value = false
  // 切换到消息中心页面
  window.dispatchEvent(new CustomEvent('assistant:open-module', { detail: { module: 'sos' } }))
}

// ─── 原有逻辑 ───

onMounted(async () => {
  mqttStore.connect()
  loading.value = true; pointsLoading.value = true
  try {
    const [stats] = await Promise.all([getOverviewStatsApi(), deviceStore.fetchDevices(), loadTrend()])
    const d = stats.data.data
    statsCards.value[0].value = d.deviceTotal
    statsCards.value[1].value = d.onlineDeviceCount
    statsCards.value[2].value = d.offlineDeviceCount
    statsCards.value[3].value = d.todayAlarmCount
    statsCards.value[4].value = d.defenseDeviceCount
  } catch { /* */ }
  finally { loading.value = false; pointsLoading.value = false }
  if (!mqttStore.connected) {
    pollTimer = setInterval(() => deviceStore.fetchDevices(), 5000)
  }
  sosTimer = setInterval(checkSosMessages, 3000)
})

onUnmounted(() => { if (pollTimer) clearInterval(pollTimer); if (sosTimer) clearInterval(sosTimer) })

async function loadTrend() {
  try {
    const res = await getAlarmTrendApi()
    await nextTick()
    if (!trendRef.value) return
    if (!trendInst) trendInst = echarts.init(trendRef.value)
    const data = res.data.data
    trendInst.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: data.map(d => d.date.slice(5)) },
      yAxis: { type: 'value', name: '告警数' },
      series: [{ type: 'line', data: data.map(d => d.count), smooth: true, itemStyle: { color: '#e6a23c' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(230,162,60,0.3)' }, { offset: 1, color: 'rgba(230,162,60,0)' }]) } }],
    })
  } catch { /* */ }
}

function refresh() { deviceStore.fetchDevices() }
function openLocalCamera() { router.push('/common/local-camera') }
</script>

<style scoped>
.overview h3 { margin-bottom: 16px; }
.stats-row { margin-bottom: 16px; }
.chart-row { margin-bottom: 16px; }
.chart-container { width: 100%; height: 280px; }
.stat-card { background: #fff; border-radius: 8px; padding: 16px; text-align: center; border: 1px solid #e4e7ed; }
.stat-num { display: block; font-size: 28px; font-weight: 700; }
.stat-label { font-size: 14px; color: #909399; }
.point-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.point-card { border: 2px solid #e4e7ed; transition: border-color .3s, background .3s; }
.point-card.pir_active { border-color: #e6a23c; background: #fdf6ec; }
.point-card.offline { opacity: 0.6; }
.point-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.point-name { font-weight: 600; }
.point-body { display: flex; align-items: center; gap: 16px; padding: 12px 0; }
.point-indicator { width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: #67c23a22; color: #67c23a; transition: all .3s; }
.point-indicator.active { background: #e6a23c22; color: #e6a23c; }
.point-text { font-size: 18px; font-weight: 600; }
.point-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.point-loc { font-size: 12px; color: #909399; }
</style>
