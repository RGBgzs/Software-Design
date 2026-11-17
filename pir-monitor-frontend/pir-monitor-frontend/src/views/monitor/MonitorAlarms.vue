<template>
  <div class="alarm-container">
    <div class="page-header">
      <h2>告警记录</h2>
      <el-button type="primary" @click="loadAlarms" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="alarms" stripe border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceCode" label="设备编号" width="140" />
      <el-table-column label="告警类型" width="120">
        <template #default="{ row }">
          <el-tag type="danger" effect="dark">{{ row.alarmType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="alarmContent" label="告警内容" min-width="280" show-overflow-tooltip />
      <el-table-column label="处理状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.handleStatus === 0 ? 'danger' : 'success'" effect="dark">
            {{ row.handleStatus === 0 ? '未处理' : '已处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="告警时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.alarmTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.handleStatus === 0"
            type="primary"
            size="small"
            @click="handleAlarm(row.id)"
          >
            确认处理
          </el-button>
          <span v-else style="color:#909399;font-size:13px">已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/index'
import type { ApiResponse } from '@/types'

interface AlarmRow {
  id: number
  deviceCode: string
  alarmType: string
  alarmContent: string
  alarmLevel: string
  handleStatus: number
  alarmTime: string
  createTime?: string
}

const alarms = ref<AlarmRow[]>([])
const loading = ref<boolean>(false)

const loadAlarms = async () => {
  loading.value = true
  try {
    const res = await http.get<ApiResponse<AlarmRow[]>>('/alarms', { params: { limit: 50 } })
    if (res.data.code === 200) alarms.value = res.data.data || []
  } catch (e) {
    console.error('加载告警失败', e)
  } finally {
    loading.value = false
  }
}

const handleAlarm = async (id: number) => {
  try {
    const res = await http.post<ApiResponse<null>>(`/alarms/${id}/handle`, {
      handleUser: localStorage.getItem('username') || 'admin',
      handleResult: '已确认处理',
    })
    if (res.data.code === 200) {
      ElMessage.success('告警已确认处理')
      loadAlarms()
    } else {
      ElMessage.error(res.data.message || '处理失败')
    }
  } catch (e) {
    ElMessage.error('请求失败')
  }
}

const formatTime = (t: string | undefined) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(loadAlarms)
</script>

<style scoped>
.alarm-container { background: #fff; padding: 20px; border-radius: 8px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; }
</style>
