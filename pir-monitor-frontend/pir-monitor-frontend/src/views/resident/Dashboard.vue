<template>
  <div class="dashboard">
    <div class="header-row">
      <h3>感应监测</h3>
      <el-button type="primary" size="small" @click="showBind = true"><el-icon><Plus /></el-icon>绑定设备</el-button>
    </div>
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="24" :md="12" v-for="dev in deviceStore.devices" :key="dev.deviceCode">
        <el-card class="device-card" :class="{ pir_active: getStatus(dev.deviceCode).humanDetected === 1 }">
          <div class="device-header">
            <el-tag :type="getStatus(dev.deviceCode).online ? 'success' : 'danger'" size="small">{{ getStatus(dev.deviceCode).online ? '在线' : '离线' }}</el-tag>
            <span class="device-name">{{ dev.deviceName }}</span>
            <el-button size="small" type="danger" link @click="handleUnbind(dev)" style="margin-left:auto">解绑</el-button>
          </div>
          <div v-if="getStatus(dev.deviceCode).online" class="pir-status">
            <div class="pir-indicator" :class="{ active: getStatus(dev.deviceCode).humanDetected === 1 }">
              <el-icon :size="48"><UserFilled v-if="getStatus(dev.deviceCode).humanDetected === 1" /><Moon v-else /></el-icon>
            </div>
            <div class="pir-text">{{ getStatus(dev.deviceCode).humanDetected === 1 ? '检测到人体移动' : '无人' }}</div>
          </div>
          <div v-else class="pir-status"><span class="offline-text">设备离线</span></div>
          <el-divider />
          <div class="device-actions">
            <div class="arm-control">
              <span>布防状态：</span>
              <el-switch :model-value="getStatus(dev.deviceCode).defenseStatus === 1" @change="(val:boolean) => handleDefense(dev, val)" active-text="已布防" inactive-text="未布防" :loading="armingMap[dev.deviceCode]" :disabled="!getStatus(dev.deviceCode).online" />
            </div>
            <div style="display:flex;gap:6px">
              <el-button size="small" @click="viewActivities(dev.deviceCode)"><el-icon><Clock /></el-icon>活动记录</el-button>
              <el-button size="small" @click="viewLocalCamera"><el-icon><VideoCamera /></el-icon>摄像头</el-button>
            </div>
          </div>
          <div class="device-meta">
            <span>编号：{{ dev.deviceCode }}</span>
            <span>位置：{{ dev.location }}</span>
            <span v-if="dev.lastReportTime">上报：{{ fmt(dev.lastReportTime) }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && deviceStore.devices.length === 0" description="暂无设备，请先绑定设备" />

    <FaceCapturePanel />

    <el-dialog v-model="showBind" title="绑定设备" width="460px">
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="设备编号" prop="deviceCode">
          <el-input v-model="bindForm.deviceCode" placeholder="请输入设备编号" />
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="bindForm.deviceName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="bindForm.location" placeholder="请输入安装位置" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBind = false">取消</el-button>
        <el-button type="primary" @click="handleBind" :loading="binding">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UserFilled, Moon, VideoCamera, Clock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Device } from '@/types'
import { useDeviceStore } from '@/stores/device'
import { useMqttStore } from '@/stores/mqtt'
import { useAuthStore } from '@/stores/auth'
import FaceCapturePanel from '@/components/shared/FaceCapturePanel.vue'

const router = useRouter()
const deviceStore = useDeviceStore()
const mqttStore = useMqttStore()
const authStore = useAuthStore()
const loading = ref(false)
const armingMap = reactive<Record<string, boolean>>({})

function getUserId() { return authStore.user?.userId || authStore.user?.id }

function getStatus(code: string) { return deviceStore.getRealtimeStatus(code) }

let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try { mqttStore.connect() } catch {}
  await loadDevices()
  if (!mqttStore.connected) {
    pollTimer = setInterval(() => loadDevices(), 5000)
  }
})

onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })

async function loadDevices() {
  const uid = getUserId()
  if (!uid) return
  loading.value = true
  try { await deviceStore.fetchMyDevices(uid) } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

// ─── 绑定设备 ───

const showBind = ref(false)
const binding = ref(false)
const bindFormRef = ref<FormInstance>()
const bindForm = reactive({ deviceCode: '', deviceName: '', location: '' })
const bindRules: FormRules = {
  deviceCode: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入安装位置', trigger: 'blur' }],
}

async function handleBind() {
  const valid = await bindFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const uid = getUserId()
  if (!uid) { ElMessage.error('用户信息异常'); return }
  binding.value = true
  try {
    await deviceStore.bindDevice({ ...bindForm, userId: uid })
    ElMessage.success('绑定成功')
    showBind.value = false
    bindForm.deviceCode = ''; bindForm.deviceName = ''; bindForm.location = ''
    await loadDevices()
  } catch { ElMessage.error('绑定失败') }
  finally { binding.value = false }
}

// ─── 解绑设备 ───

async function handleUnbind(dev: Device) {
  try {
    await ElMessageBox.confirm(`确认解绑设备「${dev.deviceName}」？`, '提示', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    await deviceStore.unbindDevice(dev.id)
    ElMessage.success('已解绑')
    await loadDevices()
  } catch { /* 取消或失败 */ }
}

// ─── 布防/撤防 ───

async function handleDefense(dev: Device, defense: boolean) {
  armingMap[dev.deviceCode] = true
  try {
    await deviceStore.toggleDefense(dev.id, dev.deviceCode, defense ? 'on' : 'off')
    ElMessage.success(defense ? '布防成功' : '已撤防')
  } catch (e: any) {
    ElMessage.error(e?.message || '布防操作失败')
  }
  finally { armingMap[dev.deviceCode] = false }
}

function viewActivities(code: string) { router.push(`/resident/activities?deviceCode=${code}`) }
function viewLocalCamera() { router.push('/common/local-camera') }
function fmt(iso: string) { return new Date(iso).toLocaleString('zh-CN') }
</script>

<style scoped>
.dashboard h3 { margin-bottom: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-row h3 { margin-bottom: 0; }
.device-card { margin-bottom: 16px; border: 2px solid #e4e7ed; transition: border-color .3s; }
.device-card.pir_active { border-color: #e6a23c; background: #fdf6ec; }
.device-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.device-name { font-size: 16px; font-weight: 600; }
.pir-status { display: flex; align-items: center; gap: 16px; padding: 16px 0; }
.pir-indicator { width: 80px; height: 80px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: #67c23a22; color: #67c23a; transition: all .3s; }
.pir-indicator.active { background: #e6a23c22; color: #e6a23c; animation: pulse 1.5s infinite; }
.pir-text { font-size: 20px; font-weight: 600; }
@keyframes pulse { 0%,100%{box-shadow:0 0 0 0 rgba(230,162,60,.4)}50%{box-shadow:0 0 0 12px rgba(230,162,60,0)} }
.device-actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.offline-text { font-size: 16px; color: #909399; }
.device-meta { margin-top: 8px; color: #909399; font-size: 13px; display: flex; gap: 16px; flex-wrap: wrap; }
</style>
