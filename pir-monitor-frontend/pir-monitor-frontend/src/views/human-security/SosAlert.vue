<template>
  <div class="sos-page">
    <PageHeader :title="isGuard ? '报警记录' : '一键报警'" :desc="isGuard ? '查看居民的报警记录' : '紧急情况一键求助，通知将直接发送给您的保安'" />

    <!-- 居民：SOS + 表单 -->
    <el-row v-if="!isGuard" :gutter="20">
      <!-- SOS 一键求助按钮 -->
      <el-col :span="8" :xs="24" :sm="8">
        <div class="card sos-card">
          <div class="sos-title">🆘 一键求救</div>
          <p class="sos-desc">长按3秒发送紧急求助信号给值班保安</p>
          <div
            class="sos-hold-btn"
            :class="{ holding: isHolding, fired: isFired }"
            @pointerdown.prevent="startHold"
            @pointerup.prevent="cancelHold"
            @pointerleave="cancelHold"
            @pointercancel="cancelHold"
          >
            <svg class="sos-ring" viewBox="0 0 100 100">
              <circle class="ring-track" cx="50" cy="50" r="44" />
              <circle class="ring-fill" :class="{ danger: holdProgress > 0.8 }" cx="50" cy="50" r="44"
                :style="{ strokeDashoffset: ringOffset }" />
            </svg>
            <div class="sos-btn-content">
              <span class="sos-icon">{{ isHolding ? '⏳' : 'SOS' }}</span>
              <span class="sos-label" v-if="!isHolding">按住3秒</span>
              <span class="sos-label" v-else>{{ countdownText }}</span>
            </div>
          </div>
          <div class="sos-hint">{{ isHolding ? '松手取消' : '长按3秒发起紧急求助' }}</div>
          <div v-if="sosSent" class="sos-ok">✅ 报警信号已发送给值班保安</div>
        </div>
      </el-col>

      <!-- 求助表单 -->
      <el-col :span="16">
        <div class="card">
          <h3 class="card-title">求助详情</h3>
          <el-form :model="helpForm" label-width="80px" size="default">
            <el-form-item label="求助类型">
              <el-select v-model="helpForm.helpType" style="width: 100%">
                <el-option label="安全异常" value="SECURITY" />
                <el-option label="突发疾病" value="SUDDEN_ILLNESS" />
                <el-option label="火灾危险" value="FIRE" />
                <el-option label="跌倒受伤" value="FALL_INJURY" />
                <el-option label="其他紧急" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联设备">
              <el-select v-model="helpForm.deviceCode" placeholder="选择设备" style="width: 100%" clearable>
                <el-option v-for="d in myDevices" :key="d.deviceCode" :label="d.deviceName || d.deviceCode" :value="d.deviceCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联告警">
              <el-select v-model="helpForm.alarmId" placeholder="选择一条告警（可选）" style="width: 100%" clearable>
                <el-option v-for="a in filteredAlarms" :key="a.id" :label="'[' + alarmTypeLabel(a.alarmType) + '] ' + (a.alarmContent || '').substring(0, 40)" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input v-model="helpForm.description" type="textarea" :rows="2" placeholder="请描述具体情况" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" :loading="sendingDetail" @click="sendDetailHelp">
                🆘 发送报警
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <!-- 报警记录 -->
    <div class="card" style="margin-top: 20px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <h3 class="card-title" style="margin:0">{{ isGuard ? '居民报警记录' : '我的报警记录' }}</h3>
        <el-button size="small" :icon="RefreshRight" @click="loadRecords" :loading="recordLoading">刷新</el-button>
      </div>
      <el-table :data="alertRecords" stripe v-loading="recordLoading" empty-text="暂无报警记录">
        <el-table-column label="发起人" width="90">
          <template #default="{ row }">{{ row.userName || row.userId }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.helpType)" size="small">{{ typeLabel(row.helpType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联设备" width="130">
          <template #default="{ row }">{{ row.deviceCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FINISHED' ? 'success' : 'danger'" size="small">
              {{ row.status === 'FINISHED' ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报警说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="155">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column v-if="isGuard" label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'FINISHED'" size="small" type="primary" @click="handleAlert(row.id)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import PageHeader from '@/views/unified/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useDeviceStore } from '@/stores/device'
import { sendEmergencyHelp, getAlarms } from '@/api/humanSecurity'
import http from '@/api/index'

const authStore = useAuthStore()
const deviceStore = useDeviceStore()
const isGuard = computed(() => authStore.user?.role === 'guard')
const myUserId = computed(() => authStore.user?.userId || authStore.user?.id || 1)

const myDevices = ref<any[]>([])
const myAlarms = ref<any[]>([])

// 根据已选设备过滤告警列表
const filteredAlarms = computed(() => {
  if (!helpForm.deviceCode) return myAlarms.value
  return myAlarms.value.filter((a: any) => a.deviceCode === helpForm.deviceCode)
})

const helpForm = reactive({
  helpType: 'SECURITY',
  deviceCode: '',
  alarmId: null as number | null,
  description: '',
})

const sendingDetail = ref(false)
const sosSent = ref(false)
const recordLoading = ref(false)
const alertRecords = ref<any[]>([])

// ── Load devices & alarms ──
async function loadDevicesAndAlarms() {
  try {
    const uid = authStore.user?.userId || authStore.user?.id || 1
    const role = authStore.user?.role || 'user'
    // Only need device codes for dropdown
    myDevices.value = deviceStore.devices.length > 0 ? deviceStore.devices : []
    if (myDevices.value.length === 0) {
      await deviceStore.fetchDevices()
      myDevices.value = deviceStore.devices
    }
    // Load recent alarms
    const res = await getAlarms({ pageSize: 10, userId: uid, role })
    if (res.data?.code === 200) {
      const data = res.data.data
      const list = Array.isArray(data) ? data : (data?.records || [])
      myAlarms.value = list.filter((a: any) => a.handleStatus === 0) // only unhandled
    }
  } catch { /* silent */ }
}

// ── SOS Hold Button ──
const HOLD_DURATION = 3000
const CIRCUMFERENCE = 2 * Math.PI * 44
const isHolding = ref(false)
const isFired = ref(false)
const holdProgress = ref(0)
const holdElapsed = ref(0)
let animId: any = null
let holdStart = 0

const ringOffset = computed(() => CIRCUMFERENCE * (1 - holdProgress.value))
const countdownText = computed(() => '松手(' + Math.max(0, Math.ceil((HOLD_DURATION - holdElapsed.value) / 1000)) + 's)')

function startHold() {
  if (isFired.value) return
  isHolding.value = true; holdProgress.value = 0; holdElapsed.value = 0; holdStart = performance.now()
  function tick() {
    holdElapsed.value = performance.now() - holdStart
    holdProgress.value = Math.min(1, holdElapsed.value / HOLD_DURATION)
    if (holdProgress.value >= 1) {
      isHolding.value = false; isFired.value = true
      setTimeout(() => { isFired.value = false }, 600)
      doQuickSos()
      return
    }
    if (isHolding.value) animId = requestAnimationFrame(tick)
  }
  animId = requestAnimationFrame(tick)
}

function cancelHold() {
  if (!isHolding.value) return
  isHolding.value = false; holdProgress.value = 0; holdElapsed.value = 0
  if (animId) { cancelAnimationFrame(animId); animId = null }
}

async function doQuickSos() {
  try {
    await ElMessageBox.confirm('确定发送紧急求助给值班保安吗？', '确认发送', {
      confirmButtonText: '确定发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    // 自动选择最近一次报警的设备
    const lastAlarm = filteredAlarms.value[0]
    const deviceCode = lastAlarm?.deviceCode || myDevices.value[0]?.deviceCode || ''
    const desc = lastAlarm
      ? '关联告警：' + (lastAlarm.alarmContent || '').substring(0, 50) + '。最高等级，需立即处理'
      : '最高等级，需立即处理'

    const res = await sendEmergencyHelp({
      userId: myUserId.value,
      helpType: helpForm.helpType,
      deviceCode,
      description: desc,
    })
    if (res.data?.code === 200) {
      sosSent.value = true
      ElMessage.success('报警信号已发送给保安')
      await loadRecords()
      setTimeout(() => { sosSent.value = false }, 5000)
    } else {
      ElMessage.warning('发送失败，请稍后重试')
    }
  } catch { ElMessage.error('发送失败') }
}

async function sendDetailHelp() {
  try {
    await ElMessageBox.confirm('确定发送报警给值班保安吗？', '确认发送', {
      confirmButtonText: '确认发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  sendingDetail.value = true
  try {
    const selAlarm = helpForm.alarmId ? myAlarms.value.find((a: any) => a.id === helpForm.alarmId) : null
    const desc = helpForm.description + (selAlarm ? '。关联告警：' + (selAlarm.alarmContent || '').substring(0, 50) : '')
    const res = await sendEmergencyHelp({
      userId: myUserId.value,
      helpType: helpForm.helpType,
      deviceCode: helpForm.deviceCode,
      description: desc,
    })
    if (res.data?.code === 200) {
      ElMessage.success('报警已发送给保安')
      helpForm.description = ''
      helpForm.alarmId = null
      await loadRecords()
    } else {
      ElMessage.warning('发送失败')
    }
  } catch { ElMessage.error('发送失败') }
  finally { sendingDetail.value = false }
}

async function loadRecords() {
  recordLoading.value = true
  try {
    if (isGuard.value) {
      const guardId = authStore.user?.userId || authStore.user?.id
      const res = await http.get('/sos/alerts', { params: { guardId } })
      alertRecords.value = res.data?.data || []
    } else {
      const res = await http.get('/sos/my-alerts', { params: { userId: myUserId.value } })
      alertRecords.value = res.data?.data || []
    }
  } catch { /* silent */ }
  finally { recordLoading.value = false }
}

async function handleAlert(id: number) {
  try { await http.post('/sos/' + id + '/handle?status=FINISHED'); ElMessage.success('已标记为处理'); loadRecords() }
  catch { ElMessage.error('操作失败') }
}

function typeTag(t?: string): string {
  const m: Record<string, string> = { SECURITY: 'danger', FIRE: 'danger', SUDDEN_ILLNESS: 'warning', FALL_INJURY: 'warning' }
  return m[t || ''] || 'info'
}
function typeLabel(t?: string): string {
  const m: Record<string, string> = { SECURITY: '安全异常', FIRE: '火灾危险', SUDDEN_ILLNESS: '突发疾病', FALL_INJURY: '跌倒受伤', OTHER: '其他' }
  return m[t || ''] || t || '未知'
}
function alarmTypeLabel(t?: string): string {
  const m: Record<string, string> = { INTRUSION: '入侵', FACE_UNKNOWN: '陌生人脸', FACE_SPOOF: '假体攻击', DEVICE_OFFLINE: '设备离线', PIR_HUMAN_DETECTED: 'PIR人体' }
  return m[t || ''] || t || '告警'
}
function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }

onMounted(() => { loadDevicesAndAlarms(); loadRecords() })
</script>

<style scoped>
.sos-page { display: flex; flex-direction: column; gap: var(--app-space-4); }
.card { background: var(--app-card-bg); border: 1px solid var(--app-border-light); border-radius: var(--app-radius-lg); box-shadow: var(--app-shadow-sm); padding: var(--app-space-5); }
.card-title { font-size: 16px; font-weight: 600; margin: 0 0 16px; }

.sos-card { text-align: center; }
.sos-title { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
.sos-desc { font-size: 13px; color: var(--app-text-muted); margin: 0 0 20px; }
.sos-hold-btn {
  width: 130px; height: 130px; margin: 0 auto; border-radius: 50%; position: relative;
  cursor: pointer; user-select: none; transition: transform .15s;
}
.sos-hold-btn.holding { transform: scale(1.05); }
.sos-ring { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-track { fill: none; stroke: #f1f5f9; stroke-width: 6; }
.ring-fill { fill: none; stroke: #ef4444; stroke-width: 6; stroke-dasharray: 276.46; stroke-dashoffset: 0; transition: stroke-dashoffset .1s; stroke-linecap: round; }
.ring-fill.danger { stroke: #dc2626; animation: ring-pulse .5s infinite; }
@keyframes ring-pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
.sos-btn-content { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.sos-icon { font-size: 28px; font-weight: 800; color: #ef4444; }
.sos-label { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.sos-hint { font-size: 12px; color: #94a3b8; margin-top: 12px; }
.sos-ok { color: #16b364; font-size: 13px; font-weight: 600; margin-top: 12px; padding: 6px 12px; background: #f0fdf6; border-radius: 8px; }
</style>
