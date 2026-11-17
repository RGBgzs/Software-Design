<template>
  <div class="guard-alarm-log">
    <h3>告警日志</h3>
    <el-card shadow="never" class="filter-bar">
      <el-form :inline="true">
        <el-form-item label="状态"><el-select v-model="filterStatus" placeholder="全部" clearable style="width:140px" @change="fetch"><el-option label="未处理" :value="0" /><el-option label="已处理" :value="1" /><el-option label="误报" :value="2" /></el-select></el-form-item>
        <el-form-item label="设备"><el-select v-model="filterDevice" placeholder="全部" clearable style="width:180px" @change="fetch"><el-option v-for="d in deviceStore.devices" :key="d.deviceCode" :label="`${d.deviceName} (${d.deviceCode})`" :value="d.deviceCode" /></el-select></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" v-loading="loading">
      <el-table :data="alarms" stripe>
        <el-table-column prop="deviceName" label="设备" width="140" />
        <el-table-column prop="alarmType" label="类型" width="100" />
        <el-table-column label="等级" width="80"><template #default="{row}"><el-tag :type="lv(row.alarmLevel)">{{row.alarmLevel}}</el-tag></template></el-table-column>
        <el-table-column label="告警时间" width="180"><template #default="{row}">{{fmt(row.alarmTime)}}</template></el-table-column>
        <el-table-column label="处理" width="100"><template #default="{row}"><el-tag :type="st(row.handleStatus)">{{stt(row.handleStatus)}}</el-tag></template></el-table-column>
        <el-table-column prop="alarmContent" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" link @click="viewDetail(row.id)">详情</el-button>
            <el-button v-if="row.handleStatus===0" size="small" type="success" link @click="handleResolve(row.id,1)">处理</el-button>
            <el-button v-if="row.handleStatus===0" size="small" type="warning" link @click="handleResolve(row.id,2)">误报</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total>10" v-model:current-page="page" :page-size="10" :total="total" layout="prev,pager,next" @current-change="fetch" class="pagination" />
      <el-empty v-if="!loading&&alarms.length===0" description="暂无告警" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Alarm, HandleStatus } from '@/types'
import { getAlarmsApi, handleAlarmApi } from '@/api/alarm'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const deviceStore = useDeviceStore()
const authStore = useAuthStore()
const loading = ref(false)
const alarms = ref<Alarm[]>([])
const total = ref(0)
const page = ref(1)
const filterStatus = ref<HandleStatus | null>(null)
const filterDevice = ref('')
const myDeviceCodes = ref<string[]>([])

onMounted(async () => {
  const uid = authStore.user?.userId
  if (uid) await deviceStore.fetchMyDevices(uid)
  myDeviceCodes.value = deviceStore.devices.map(d => d.deviceCode)
  fetch()
})

async function fetch() {
  loading.value = true
  try {
    const p: any = { page: page.value, pageSize: 10 }
    if (filterStatus.value !== null) p.handleStatus = filterStatus.value
    if (filterDevice.value) p.deviceCode = filterDevice.value
    const res = await getAlarmsApi(p)
    let items = res.data.data.records
    if (!filterDevice.value) {
      items = items.filter(r => myDeviceCodes.value.includes(r.deviceCode))
    }
    alarms.value = items; total.value = items.length
  } finally { loading.value = false }
}

function viewDetail(id: number) { router.push(`/resident/alarms/${id}`) }
async function handleResolve(id: number, s: 1|2) { try { await handleAlarmApi(id, { handleStatus: s, handleUser: 'guard' }); fetch(); ElMessage.success('操作成功') } catch { ElMessage.error('操作失败') } }
function lv(l: string) { return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[l] as string }
function st(s: HandleStatus) { return { 0: 'warning', 1: 'success', 2: 'info' }[s] as string }
function stt(s: HandleStatus) { return { 0: '未处理', 1: '已处理', 2: '误报' }[s] }
function fmt(t: string) { return new Date(t).toLocaleString('zh-CN') }
</script>

<style scoped>
.guard-alarm-log h3 { margin-bottom: 16px; }
.filter-bar { margin-bottom: 16px; }
.pagination { margin-top: 20px; justify-content: center; display: flex; }
</style>
