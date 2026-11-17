<template>
  <div class="pir-monitor">

    <!-- Page Header -->
    <PageHeader title="PIR人体感应监测" desc="实时监测人体活动，智能布防管控" />

    <!-- 设备选择器 -->
    <div class="device-selector-row">
      <div class="device-selector">
        <span class="device-selector-label">当前设备：</span>
        <el-select
          v-model="selectedDeviceId"
          placeholder="请选择设备"
          @change="onDeviceChange"
          style="width: 320px"
          :loading="deviceListLoading"
        >
          <el-option
            v-for="d in deviceList"
            :key="d.id || d.deviceCode"
            :label="`${d.deviceName || d.deviceCode} (${d.deviceCode})`"
            :value="d.deviceCode"
          />
        </el-select>
      </div>
      <div class="device-selector-hint" v-if="deviceList.length > 0">
        共 {{ deviceList.length }} 台设备
      </div>
    </div>

    <!-- Status Cards Row -->
    <div class="stat-cards-row">
      <div class="stat-card">
        <div class="stat-card-icon stat-card-icon--primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="20" height="20"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-label">设备名称</span>
          <span class="stat-card-value">{{ currentDeviceName }}</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-card-icon stat-card-icon--info">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="20" height="20"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-label">设备编号</span>
          <span class="stat-card-value stat-card-value--mono">{{ deviceCode }}</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-card-icon" :class="isOnline ? 'stat-card-icon--success' : 'stat-card-icon--muted'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="20" height="20"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-label">在线状态</span>
          <span class="stat-card-value">
            <span class="status-tag" :class="isOnline ? 'status-tag--online' : 'status-tag--offline'">
              <span class="status-dot" :class="isOnline ? 'status-dot--online' : 'status-dot--offline'"></span>
              {{ isOnline ? '在线' : '离线' }}
            </span>
          </span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-card-icon" :class="humanDetected ? 'stat-card-icon--danger' : 'stat-card-icon--success'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="20" height="20"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-label">人体感应</span>
          <span class="stat-card-value">
            <span class="status-tag" :class="humanDetected ? 'status-tag--human' : 'status-tag--none'">
              <span class="status-dot" :class="humanDetected ? 'status-dot--pulse' : 'status-dot--offline'"></span>
              {{ humanDetected ? '有人' : '无人' }}
            </span>
          </span>
        </div>
      </div>
    </div>

    <!-- Two-Column Layout: Defense Control + Statistics -->
    <div class="two-col">
      <div class="defense-panel">
        <div class="panel-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="18" height="18" class="panel-header-icon"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          <h3>安防控制</h3>
        </div>
        <div class="defense-actions">
          <div
            class="action-card action-card--arm"
            :class="{ 'action-card--disabled': defenseStatus === 1, 'action-card--loading': arming }"
            @click="defenseStatus !== 1 && !arming && handleArm()"
          >
            <div class="action-card-bg"></div>
            <div class="action-card-content">
              <div class="action-card-icon-wrap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="26" height="26"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
              </div>
              <div class="action-card-text">
                <span class="action-card-title">开启布防</span>
                <span class="action-card-desc">{{ arming ? '指令下发中...' : (defenseStatus === 1 ? '当前已布防' : '点击启用安防布控') }}</span>
              </div>
              <span v-if="arming" class="action-card-spinner"></span>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18" class="action-card-arrow"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>

          <div
            class="action-card action-card--disarm"
            :class="{ 'action-card--disabled': defenseStatus !== 1, 'action-card--loading': disarming }"
            @click="defenseStatus === 1 && !disarming && handleDisarm()"
          >
            <div class="action-card-bg"></div>
            <div class="action-card-content">
              <div class="action-card-icon-wrap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="26" height="26"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 019.9-1"/></svg>
              </div>
              <div class="action-card-text">
                <span class="action-card-title">关闭布防</span>
                <span class="action-card-desc">{{ disarming ? '指令下发中...' : (defenseStatus !== 1 ? '当前已撤防' : '点击关闭安防布控') }}</span>
              </div>
              <span v-if="disarming" class="action-card-spinner"></span>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18" class="action-card-arrow"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>
        </div>
      </div>

      <div class="stats-panel">
        <div class="panel-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="18" height="18" class="panel-header-icon"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
          <h3>统计信息</h3>
          <span class="last-update" v-if="lastUpdate">最近更新: {{ lastUpdate }}</span>
        </div>
        <div class="stats-list">
          <div class="stats-item">
            <span class="stats-item-label">布防状态</span>
            <span class="status-tag" :class="defenseStatus === 1 ? 'status-tag--armed' : 'status-tag--disarmed'">
              {{ defenseStatus === 1 ? '布防中' : '已撤防' }}
            </span>
          </div>
          <div class="stats-item">
            <span class="stats-item-label">最近上报</span>
            <span class="stats-item-value">{{ lastReportTime || '暂无' }}</span>
          </div>
          <div class="stats-item">
            <span class="stats-item-label">今日告警</span>
            <span class="stats-item-count" :class="{ 'stats-item-count--alert': todayAlarmCount > 0 }">{{ todayAlarmCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Human Detection Records Table -->
    <div class="records-section">
      <div class="records-header">
        <div class="records-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="18" height="18"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          <h3>人体感应记录</h3>
        </div>
        <el-button size="small" @click="loadRecords" :loading="recordLoading" :icon="RefreshIcon" text>刷新</el-button>
      </div>

      <el-table :data="records" stripe border style="width: 100%" v-loading="recordLoading" max-height="440" class="records-table">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="deviceCode" label="设备编号" width="150" />
        <el-table-column label="人体检测" width="110" align="center">
          <template #default="{ row }">
            <span class="inline-tag" :class="row.humanDetected === 1 ? 'inline-tag--danger' : 'inline-tag--success'">
              {{ row.humanDetected === 1 ? '有人' : '无人' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="布防状态" width="110" align="center">
          <template #default="{ row }">
            <span class="inline-tag" :class="row.defenseStatus === 1 ? 'inline-tag--warning' : 'inline-tag--info'">
              {{ row.defenseStatus === 1 ? '布防' : '撤防' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="上报时间" min-width="180">
          <template #default="{ row }">
            {{ fmt(row.reportTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="records-pagination" v-if="recordTotal > 0">
        <el-pagination
          v-model:current-page="recordPage"
          :page-size="recordPageSize"
          :total="recordTotal"
          layout="total, prev, pager, next"
          @current-change="loadRecords"
        />
      </div>
    </div>

    <!-- Alarm Dialog -->
    <el-dialog v-model="alarmVisible" :title="alarmDialogTitle" width="440px" :close-on-click-modal="false" class="alarm-dialog">
      <div class="alarm-dialog-body">
        <div class="alarm-dialog-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="48" height="48"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        </div>
        <p class="alarm-dialog-title">{{ alarmDialogTitle }}</p>
        <p class="alarm-dialog-detail">{{ latestAlarm?.alarmContent || '请及时处理' }}</p>
      </div>
      <template #footer>
        <el-button type="danger" @click="alarmVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight as RefreshIcon } from '@element-plus/icons-vue'
import { getPirDeviceStatus, armDevice, disarmDevice, getPirRecords, getAlarms, getDevices } from '@/api/humanSecurity'
import { useMqtt } from '@/composables/useMqtt'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import PageHeader from '@/views/unified/components/PageHeader.vue'
import type { Device, HumanRecord, Alarm } from '@/types'

const voiceStore = useVoiceActionStore()
const authStore = useAuthStore()

// ════════════════ MQTT 实时通道 ════════════════
useMqtt() // 页面挂载时自动连接 MQTT，全局共享
const deviceStore = useDeviceStore()

// ════════════════ 设备选择 ════════════════
const deviceCode = ref('')
const selectedDeviceId = ref('')
const deviceList = ref<Device[]>([])
const deviceListLoading = ref(false)

const currentDeviceName = computed(() => {
  if (!deviceCode.value) return '未选择设备'
  const found = deviceList.value.find(d => d.deviceCode === deviceCode.value)
  return found?.deviceName || deviceCode.value
})

// ════════════════ 实时状态（MQTT 优先，HTTP 兜底） ════════════════
const isOnline = computed(() => {
  if (!deviceCode.value) return false
  const rt = deviceStore.getRealtimeStatus(deviceCode.value)
  if (rt.online) return true
  // fallback to device list
  const dev = deviceList.value.find(d => d.deviceCode === deviceCode.value)
  return dev?.onlineStatus === 1
})

const humanDetected = computed(() => {
  if (!deviceCode.value) return false
  const rt = deviceStore.getRealtimeStatus(deviceCode.value)
  return rt.humanDetected === 1
})

const defenseStatus = computed(() => {
  if (!deviceCode.value) return 0
  const rt = deviceStore.getRealtimeStatus(deviceCode.value)
  if (rt.defenseStatus !== undefined && rt.defenseStatus !== 0) return rt.defenseStatus
  const dev = deviceList.value.find(d => d.deviceCode === deviceCode.value)
  return dev?.defenseStatus ?? 0
})

const deviceData = ref<Device>({
  id: 0, deviceCode: '', deviceName: '', location: '',
  onlineStatus: 0, humanDetected: 0, defenseStatus: 0, lastReportTime: '',
})
const arming = ref(false)
const disarming = ref(false)
const lastReportTime = ref('')
const lastUpdate = ref('')
const todayAlarmCount = ref(0)

// 记录
const records = ref<HumanRecord[]>([])
const recordLoading = ref(false)
const recordPage = ref(1)
const recordPageSize = ref(20)
const recordTotal = ref(0)

// 告警弹窗
const latestAlarm = ref<Alarm | null>(null)
const alarmVisible = ref(false)
const lastAlarmId = ref(0)

const alarmDialogTitle = computed(() => {
  const alarm = latestAlarm.value
  if (!alarm) return '告警通知'
  const type = alarm.alarmType || ''
  switch (type) {
    case 'INTRUSION': case 'PIR_HUMAN_DETECTED': return '入侵告警'
    case 'FACE_UNKNOWN': return '陌生人脸告警'
    case 'FACE_SPOOF': return '假体攻击告警'
    case 'FACE_KNOWN': return '已登记人员提醒'
    case 'DEVICE_OFFLINE': return '设备离线告警'
    default: return type || '告警通知'
  }
})

let refreshTimer: ReturnType<typeof setInterval> | null = null
let alarmTimer: ReturnType<typeof setInterval> | null = null

// ════════════════ 加载设备列表 ════════════════
async function loadDeviceList() {
  deviceListLoading.value = true
  try {
    const uid = authStore.user?.userId || authStore.user?.id || 1
    const role = authStore.user?.role || 'admin'
    const res = await getDevices({ pageSize: 100, userId: uid, role: role })
    if (res.data?.code === 200) {
      const data = res.data.data
      deviceList.value = Array.isArray(data) ? data : (data?.records || [])

      // 恢复上次选择的设备
      const saved = localStorage.getItem('pir_selected_device')
      if (saved && deviceList.value.some(d => d.deviceCode === saved)) {
        selectedDeviceId.value = saved
        deviceCode.value = saved
      } else if (deviceList.value.length > 0) {
        selectedDeviceId.value = deviceList.value[0].deviceCode
        deviceCode.value = deviceList.value[0].deviceCode
      }

      // 同步到 deviceStore（始终更新，避免防守状态过时）
      deviceList.value.forEach(d => {
        const prev = deviceStore.getRealtimeStatus(d.deviceCode)
        deviceStore.statusMap[d.deviceCode] = {
          humanDetected: d.humanDetected ?? prev?.humanDetected ?? 0,
          defenseStatus: d.defenseStatus ?? prev?.defenseStatus ?? 0,
          online: d.onlineStatus === 1,
        }
      })

      if (deviceCode.value) {
        loadStatus()
        loadRecords()
        checkAlarms()
      }
    }
  } catch {
    console.error('加载设备列表失败')
  } finally {
    deviceListLoading.value = false
  }
}

function onDeviceChange(code: string) {
  deviceCode.value = code
  localStorage.setItem('pir_selected_device', code)
  loadStatus()
  loadRecords()
  checkAlarms()
}

// ════════════════ HTTP 轮询（兜底） ════════════════
async function loadStatus() {
  if (!deviceCode.value) return
  try {
    const res = await getPirDeviceStatus(deviceCode.value)
    if (res.data?.code === 200 && res.data?.data) {
      const d = res.data.data
      deviceData.value = d
      // 更新 deviceStore（MQTT 断开时用 HTTP 兜底）
      deviceStore.statusMap[d.deviceCode || deviceCode.value] = {
        humanDetected: d.humanDetected ?? 0,
        defenseStatus: d.defenseStatus ?? 0,
        online: d.onlineStatus === 1,
      }
      lastReportTime.value = d.lastReportTime
        ? new Date(d.lastReportTime).toLocaleString('zh-CN')
        : '暂无'
      lastUpdate.value = new Date().toLocaleTimeString('zh-CN')
    }
  } catch {
    console.error('获取设备状态失败')
  }
}

async function handleArm(skipConfirm = false) {
  if (!deviceCode.value) return
  if (defenseStatus.value === 1) { ElMessage.warning('设备已处于布防状态'); return }
  if (!skipConfirm) {
    try {
      await ElMessageBox.confirm(
        '确认开启布防？开启后将实时监测人体活动并触发告警。',
        '开启布防',
        { confirmButtonText: '确认开启', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }
  arming.value = true
  try {
    const res = await armDevice(deviceCode.value)
    if (res.data?.code === 200 || res.data?.code === 0) {
      ElMessage.success('布防指令已下发')
      setTimeout(loadStatus, 1500)
    } else {
      ElMessage.error(res.data?.message || '指令发送失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '请求失败')
  } finally { arming.value = false }
}

async function handleDisarm(skipConfirm = false) {
  if (!deviceCode.value) return
  if (defenseStatus.value === 0) { ElMessage.warning('设备已处于撤防状态'); return }
  if (!skipConfirm) {
    try {
      await ElMessageBox.confirm(
        '确认关闭布防？关闭后将停止人体活动监测和告警。',
        '关闭布防',
        { confirmButtonText: '确认关闭', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }
  disarming.value = true
  try {
    const res = await disarmDevice(deviceCode.value)
    if (res.data?.code === 200 || res.data?.code === 0) {
      ElMessage.success('撤防指令已下发')
      setTimeout(loadStatus, 1500)
    } else {
      ElMessage.error(res.data?.message || '指令发送失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '请求失败')
  } finally { disarming.value = false }
}

async function loadRecords() {
  if (!deviceCode.value) return
  recordLoading.value = true
  try {
    const res = await getPirRecords({ deviceCode: deviceCode.value, page: recordPage.value, pageSize: recordPageSize.value })
    if (res.data?.code === 200) {
      const data = res.data.data
      records.value = data?.records || data || []
      recordTotal.value = data?.total || records.value.length
    }
  } catch {
    console.error('加载记录失败')
  } finally { recordLoading.value = false }
}

async function checkAlarms() {
  if (!deviceCode.value) return
  try {
    const res = await getAlarms({ pageSize: 1, deviceCode: deviceCode.value })
    if (res.data?.code === 200) {
      const list = res.data.data?.records || res.data.data || []
      if (list.length > 0) {
        const latest = list[0] as Alarm
        if (latest.id! > lastAlarmId.value) {
          lastAlarmId.value = latest.id!
          latestAlarm.value = latest
          alarmVisible.value = true
        }
      }
    }
    const allRes = await getAlarms({ pageSize: 100 })
    if (allRes.data?.code === 200) {
      const all = allRes.data.data?.records || allRes.data.data || []
      const today = new Date().toDateString()
      todayAlarmCount.value = all.filter((a: Alarm) => new Date(a.alarmTime!).toDateString() === today).length
    }
  } catch { /* silent */ }
}

function fmt(t: string | Date): string {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(() => {
  loadDeviceList()
  refreshTimer = setInterval(() => { loadStatus(); loadRecords() }, 2000)
  alarmTimer = setInterval(checkAlarms, 3000)
  voiceStore.onAction('ARM_DEVICE', onVoiceArm)
  voiceStore.onAction('DISARM_DEVICE', onVoiceDisarm)
  voiceStore.onAction('REFRESH_CURRENT', onVoiceRefresh)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (alarmTimer) clearInterval(alarmTimer)
  voiceStore.offAction('ARM_DEVICE', onVoiceArm)
  voiceStore.offAction('DISARM_DEVICE', onVoiceDisarm)
  voiceStore.offAction('REFRESH_CURRENT', onVoiceRefresh)
})

function onVoiceArm() { handleArm(true) }
function onVoiceDisarm() { handleDisarm(true) }
function onVoiceRefresh() { loadStatus(); loadRecords() }
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   PIR Monitor — Enterprise SaaS Styling
   ═══════════════════════════════════════════════════ */

.pir-monitor {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
}

/* ── Device Selector ── */
.device-selector-row {
  display: flex;
  align-items: center;
  gap: var(--app-space-4);
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  padding: var(--app-space-3) var(--app-space-5);
}
.device-selector {
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
}
.device-selector-label {
  font-size: var(--app-font-size-sm);
  font-weight: 600;
  color: var(--app-text-secondary);
  white-space: nowrap;
}
.device-selector-hint {
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
}

/* ── Status Cards Row ── */
.stat-cards-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--app-space-4);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--app-space-4);
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  padding: var(--app-space-4) var(--app-space-5);
  box-shadow: var(--app-shadow-sm);
  transition: box-shadow 0.2s, transform 0.15s;
}
.stat-card:hover {
  box-shadow: var(--app-shadow-md);
  transform: translateY(-1px);
}

.stat-card-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--app-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-card-icon--primary { background:#EDF4FF; color:var(--color-primary); }
.stat-card-icon--info    { background:#F1F0FF; color:#6C65E8; }
.stat-card-icon--success { background:var(--color-success-bg); color:var(--color-success); }
.stat-card-icon--danger  { background:var(--color-danger-bg); color:var(--color-danger); }
.stat-card-icon--muted   { background:#F0F4F8; color:#69809F; }

.stat-card-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.stat-card-label {
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.stat-card-value {
  font-size: var(--app-font-size-md);
  font-weight: 700;
  color: var(--app-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-card-value--mono {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  letter-spacing: 0.3px;
}

/* ── Status Tags ── */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--online { background: var(--app-success); }
.status-dot--offline { background: var(--app-text-muted); }
.status-dot--pulse {
  background: var(--app-danger);
  animation: dot-pulse 1.4s ease-in-out infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { opacity: 0.5; box-shadow: 0 0 0 5px rgba(239, 68, 68, 0); }
}

.status-tag--online  { background:var(--color-success-bg); color:var(--color-success); }
.status-tag--offline { background:#FBF3F4; color:#C75961; }
.status-tag--human   { background:var(--color-danger-bg); color:var(--color-danger); }
.status-tag--none    { background:var(--color-success-bg); color:var(--color-success); }
.status-tag--armed   { background:var(--color-info-bg); color:var(--color-info); }
.status-tag--disarmed{ background:var(--color-neutral-bg); color:var(--color-neutral); }

/* ── Last Update ── */
.last-update {
  margin-left: auto;
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
}

/* ── Two-Column Layout ── */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--app-space-5);
}

.defense-panel,
.stats-panel {
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  padding: var(--app-space-5);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
  margin-bottom: var(--app-space-4);
}
.panel-header h3 {
  font-size: var(--app-font-size-md);
  font-weight: 700;
  color: var(--app-text-primary);
  margin: 0;
}
.panel-header-icon {
  color: var(--app-primary);
  flex-shrink: 0;
}

.defense-actions {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
}

.action-card {
  position: relative;
  border-radius: var(--app-radius-md);
  cursor: pointer;
  overflow: hidden;
  border: 1px solid transparent;
  transition: transform 0.15s, box-shadow 0.2s, border-color 0.2s;
  user-select: none;
}
.action-card:hover:not(.action-card--disabled):not(.action-card--loading) {
  transform: translateY(-2px);
  box-shadow: var(--app-shadow-md);
}
.action-card--disabled { cursor: not-allowed; opacity: 0.55; filter: grayscale(0.3); }
.action-card--loading { cursor: wait; opacity: 0.8; }

.action-card-bg { position: absolute; inset: 0; }
.action-card--arm .action-card-bg {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.06), rgba(245, 158, 11, 0.06));
  border-left: 3px solid var(--app-danger);
}
.action-card--disarm .action-card-bg {
  background: linear-gradient(135deg, rgba(22, 179, 100, 0.06), rgba(34, 197, 94, 0.06));
  border-left: 3px solid var(--app-success);
}

.action-card-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--app-space-4);
  padding: var(--app-space-4) var(--app-space-5);
  z-index: 1;
}
.action-card-icon-wrap {
  width: 46px; height: 46px;
  border-radius: var(--app-radius-md);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.action-card--arm .action-card-icon-wrap { background: var(--app-danger-light); color: var(--app-danger); }
.action-card--disarm .action-card-icon-wrap { background: var(--app-success-light); color: var(--app-success); }

.action-card-text { display: flex; flex-direction: column; gap: 1px; flex: 1; }
.action-card-title { font-size: var(--app-font-size-md); font-weight: 700; color: var(--app-text-primary); }
.action-card-desc { font-size: var(--app-font-size-xs); color: var(--app-text-muted); }

.action-card-arrow { color: var(--app-text-muted); flex-shrink: 0; transition: transform 0.2s; }
.action-card:hover:not(.action-card--disabled):not(.action-card--loading) .action-card-arrow {
  transform: translateX(3px); color: var(--app-text-secondary);
}

.action-card-spinner {
  width: 18px; height: 18px;
  border: 2px solid var(--app-border); border-top-color: var(--app-primary);
  border-radius: 50%; animation: spin 0.7s linear infinite; flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Stats Panel ── */
.stats-list { display: flex; flex-direction: column; gap: var(--app-space-3); }
.stats-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: var(--app-space-3) var(--app-space-4);
  background: var(--app-input-bg); border-radius: var(--app-radius-sm);
}
.stats-item-label { font-size: var(--app-font-size-sm); color: var(--app-text-secondary); }
.stats-item-value { font-size: var(--app-font-size-sm); color: var(--app-text-primary); font-weight: 500; }
.stats-item-count { font-size: var(--app-font-size-lg); font-weight: 800; color: var(--app-text-primary); min-width: 28px; text-align: center; }
.stats-item-count--alert { color: var(--app-danger); }

/* ── Records ── */
.records-section {
  background: var(--app-card-bg); border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg); box-shadow: var(--app-shadow-sm); overflow: hidden;
}
.records-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: var(--app-space-4) var(--app-space-5); border-bottom: 1px solid var(--app-border-light);
}
.records-header-left { display: flex; align-items: center; gap: var(--app-space-2); color: var(--app-text-primary); }
.records-header-left h3 { font-size: var(--app-font-size-md); font-weight: 700; margin: 0; }
.records-table { border-radius: 0 !important; border: none !important; }
.records-table :deep(.el-table__inner-wrapper) { border: none; }
.records-pagination {
  display: flex; justify-content: flex-end;
  padding: var(--app-space-3) var(--app-space-5) var(--app-space-4);
  border-top: 1px solid var(--app-border-light);
}

.inline-tag {
  display: inline-block; padding: 2px 10px; border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs); font-weight: 600;
}
.inline-tag--danger  { background: var(--app-danger-light); color: var(--app-danger); }
.inline-tag--success { background: var(--app-success-light); color: var(--app-success); }
.inline-tag--warning { background: var(--app-warning-light); color: #b45309; }
.inline-tag--info    { background: #e8f0fe; color: var(--app-text-secondary); }

/* ── Alarm Dialog ── */
.alarm-dialog :deep(.el-dialog__header) { border-bottom: 1px solid var(--app-border-light); padding: var(--app-space-4) var(--app-space-5); }
.alarm-dialog :deep(.el-dialog__footer) { border-top: 1px solid var(--app-border-light); padding: var(--app-space-3) var(--app-space-5); }
.alarm-dialog-body { text-align: center; padding: var(--app-space-6) var(--app-space-4); }
.alarm-dialog-icon { color: var(--app-danger); margin-bottom: var(--app-space-4); display: flex; justify-content: center; }
.alarm-dialog-title { font-size: var(--app-font-size-lg); font-weight: 700; color: var(--app-danger); margin: 0 0 var(--app-space-2); }
.alarm-dialog-detail { font-size: var(--app-font-size-base); color: var(--app-text-secondary); margin: 0; line-height: 1.6; }

@media (max-width: 1200px) { .stat-cards-row { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .stat-cards-row { grid-template-columns: 1fr; } .two-col { grid-template-columns: 1fr; } }
</style>
