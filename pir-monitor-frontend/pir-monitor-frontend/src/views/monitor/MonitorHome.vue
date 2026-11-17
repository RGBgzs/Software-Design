<template>
  <div class="home-container">
    <h2 class="page-title">设备监控</h2>

    <!-- 设备状态卡片 -->
    <el-row :gutter="20" class="status-row">
      <el-col :span="6">
        <el-card shadow="hover" class="status-card">
          <div class="card-label">设备名称</div>
          <div class="card-value">{{ deviceData.deviceName || '客厅人体感应器' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="status-card">
          <div class="card-label">设备编号</div>
          <div class="card-value">{{ deviceData.deviceCode || 'human_001' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="status-card">
          <div class="card-label">在线状态</div>
          <div class="card-value">
            <el-tag :type="isOnline ? 'success' : 'danger'" effect="dark">
              {{ isOnline ? '在线' : '离线' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="status-card">
          <div class="card-label">安防状态</div>
          <div class="card-value">
            <el-tag
              :type="defenseStatus === 1 ? 'danger' : 'info'"
              effect="dark"
              size="large"
            >
              <span class="defense-tag">
                <span class="defense-dot" :class="defenseStatus === 1 ? 'dot-active' : 'dot-inactive'"></span>
                {{ defenseStatus === 1 ? '布防中' : '已撤防' }}
              </span>
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 布防 / 撤防 按钮 -->
    <el-row :gutter="20" class="action-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="defense-section">
            <h3>安防控制</h3>
            <div class="defense-buttons">
              <el-button
                type="danger"
                size="large"
                :icon="Lock"
                :loading="arming"
                :disabled="defenseStatus === 1"
                @click="handleArm"
              >
                {{ arming ? '指令下发中...' : '开启布防' }}
              </el-button>
              <el-button
                type="success"
                size="large"
                :icon="Unlock"
                :loading="disarming"
                :disabled="defenseStatus === 0"
                @click="handleDisarm"
              >
                {{ disarming ? '指令下发中...' : '关闭布防' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="info-section">
            <h3>统计信息</h3>
            <div class="info-items">
              <div class="info-item">
                <span class="info-label">最近上报</span>
                <span class="info-val">{{ lastReportTime || '暂无' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">今日告警</span>
                <el-tag type="danger" effect="dark">{{ todayAlarmCount }}</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 告警弹窗 -->
    <el-dialog
      v-model="alarmVisible"
      title="⚠️ 入侵告警"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="alarm-dialog-content">
        <div class="alarm-icon">🚨</div>
        <p class="alarm-text">检测到入侵告警！</p>
        <p class="alarm-detail">{{ latestAlarm?.alarmContent }}</p>
      </div>
      <template #footer>
        <el-button type="danger" @click="alarmVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Unlock } from '@element-plus/icons-vue'
import http from '@/api/index'
import type { ApiResponse } from '@/types'

interface DeviceStatusData {
  deviceCode?: string
  deviceName?: string
  defenseStatus?: number
  lastOnline?: string
  lastReportTime?: string
}

interface AlarmItem {
  id: number
  alarmContent?: string
  createTime?: string
  alarmType?: string
}

const deviceData = ref<DeviceStatusData>({})
const defenseStatus = ref<number>(0)
const isOnline = ref<boolean>(false)
const arming = ref<boolean>(false)
const disarming = ref<boolean>(false)
const lastReportTime = ref<string>('')
const todayAlarmCount = ref<number>(0)
const latestAlarm = ref<AlarmItem | null>(null)
const alarmVisible = ref<boolean>(false)
const lastAlarmId = ref<number>(0)

let refreshTimer: ReturnType<typeof setInterval> | null = null
let alarmTimer: ReturnType<typeof setInterval> | null = null

/** Load device status */
const loadStatus = async () => {
  try {
    const res = await http.get<ApiResponse<DeviceStatusData>>('/devices/human_001/status')
    if (res.data.code === 200 && res.data.data) {
      const d = res.data.data
      deviceData.value = d
      defenseStatus.value = d.defenseStatus ?? 0
      isOnline.value = !!d.lastOnline
      lastReportTime.value = d.lastOnline
        ? new Date(d.lastOnline).toLocaleString('zh-CN')
        : '暂无'
    }
  } catch (e) {
    console.error('获取设备状态失败', e)
  }
}

/** Arm */
const handleArm = async () => {
  arming.value = true
  try {
    const res = await http.post<ApiResponse<null>>('/devices/human_001/defense/on')
    if (res.data.code === 200) {
      ElMessage.success('布防指令已下发')
      setTimeout(loadStatus, 1500)
    } else {
      ElMessage.error('指令发送失败')
    }
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    arming.value = false
  }
}

/** Disarm */
const handleDisarm = async () => {
  disarming.value = true
  try {
    const res = await http.post<ApiResponse<null>>('/devices/human_001/defense/off')
    if (res.data.code === 200) {
      ElMessage.success('撤防指令已下发')
      setTimeout(loadStatus, 1500)
    } else {
      ElMessage.error('指令发送失败')
    }
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    disarming.value = false
  }
}

/** Poll for new alarms every 5s */
const checkAlarms = async () => {
  try {
    const res = await http.get<ApiResponse<AlarmItem[]>>('/alarms', { params: { limit: 1 } })
    if (res.data.code === 200 && res.data.data && res.data.data.length > 0) {
      const latest = res.data.data[0]
      if (latest.id > lastAlarmId.value) {
        lastAlarmId.value = latest.id
        latestAlarm.value = latest
        alarmVisible.value = true
      }
    }

    // Also get count
    const allRes = await http.get<ApiResponse<AlarmItem[]>>('/alarms', { params: { limit: 100 } })
    if (allRes.data.code === 200 && allRes.data.data) {
      const today = new Date().toDateString()
      todayAlarmCount.value = allRes.data.data.filter((a: AlarmItem) =>
        a.createTime && new Date(a.createTime).toDateString() === today
      ).length
    }
  } catch (e) {
    // silent
  }
}

onMounted(() => {
  loadStatus()
  checkAlarms()
  refreshTimer = setInterval(loadStatus, 5000)
  alarmTimer = setInterval(checkAlarms, 5000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (alarmTimer) clearInterval(alarmTimer)
})
</script>

<style scoped>
.home-container { padding: 0; }
.page-title { font-size: 22px; margin-bottom: 24px; color: #303133; }

.status-row { margin-bottom: 20px; }
.status-card { text-align: center; border-radius: 10px; }
.card-label { color: #909399; font-size: 13px; margin-bottom: 10px; }
.card-value { font-size: 18px; font-weight: 600; color: #303133; }

.defense-tag { display: flex; align-items: center; gap: 6px; }
.defense-dot {
  width: 8px; height: 8px; border-radius: 50%;
}
.dot-active { background: #fff; animation: pulse 1.5s infinite; }
.dot-inactive { background: #c0c4cc; }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.action-row { margin-bottom: 20px; }
.defense-section h3, .info-section h3 {
  font-size: 16px; margin-bottom: 16px; color: #303133;
}
.defense-buttons { display: flex; gap: 16px; }

.info-items { display: flex; flex-direction: column; gap: 12px; }
.info-item { display: flex; justify-content: space-between; align-items: center; }
.info-label { color: #909399; }
.info-val { color: #303133; font-weight: 500; }

.alarm-dialog-content { text-align: center; padding: 20px; }
.alarm-icon { font-size: 56px; margin-bottom: 16px; }
.alarm-text { font-size: 20px; font-weight: 700; color: #f56c6c; margin-bottom: 8px; }
.alarm-detail { font-size: 14px; color: #606266; }
</style>
