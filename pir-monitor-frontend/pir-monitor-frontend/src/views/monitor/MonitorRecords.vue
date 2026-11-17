<template>
  <div class="record-container">
    <div class="page-header">
      <h2>历史记录</h2>
      <el-button type="primary" @click="loadRecords" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="records" stripe border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deviceCode" label="设备编号" width="140" />
      <el-table-column label="人体检测" width="130">
        <template #default="{ row }">
          <el-tag :type="row.humanDetected === 1 ? 'danger' : 'success'" effect="dark">
            {{ row.humanDetected === 1 ? '有人' : '无人' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="布防状态" width="130">
        <template #default="{ row }">
          <el-tag :type="row.defenseStatus === 1 ? 'warning' : 'info'" effect="dark">
            {{ row.defenseStatus === 1 ? '布防' : '撤防' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上报时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.reportTime) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRecordsApi } from '@/api/activity'
import type { HumanRecord } from '@/types'

const records = ref<HumanRecord[]>([])
const loading = ref<boolean>(false)

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await getRecordsApi({ pageSize: 50 })
    records.value = res.data.data?.records || []
  } catch (e) {
    console.error('加载记录失败', e)
  } finally {
    loading.value = false
  }
}

const formatTime = (t: string | undefined) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(loadRecords)
</script>

<style scoped>
.record-container { background: #fff; padding: 20px; border-radius: 8px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; }
</style>
