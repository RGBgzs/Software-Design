<template>
  <div class="face-records">
    <h3>人脸识别记录</h3>
    <el-card shadow="never" v-loading="loading">
      <el-table :data="records" stripe empty-text="暂无识别记录">
        <el-table-column label="姓名" prop="personName" width="100" />
        <el-table-column label="匹配" width="70">
          <template #default="{ row }">
            <el-tag :type="row.matched ? 'success' : 'danger'" size="small">{{ row.matched ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ (row.confidence * 100).toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column label="设备" prop="deviceCode" width="120" />
        <el-table-column label="位置" prop="location" width="120" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '-' }}</template>
        </el-table-column>
        <el-table-column label="风险" width="80">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'HIGH' ? 'danger' : 'info'" size="small">{{ row.riskLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getFaceRecordsApi } from '@/api/face'

const loading = ref(false)
const records = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await getFaceRecordsApi()
    const body = res.data as { code: number; data: any[] }
    records.value = body.data || []
  } catch { /* */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.face-records h3 { margin-bottom: 16px; }
</style>
