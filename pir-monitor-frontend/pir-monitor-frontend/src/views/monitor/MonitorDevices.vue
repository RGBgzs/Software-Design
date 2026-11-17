<template>
  <div class="device-container">
    <div class="page-header">
      <h2>设备管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="loadDevices" :loading="loading">刷新</el-button>
        <el-button type="success" @click="showAddDialog">新增设备</el-button>
      </div>
    </div>

    <el-table :data="devices" stripe border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceCode" label="设备编号" width="150" />
      <el-table-column prop="deviceName" label="设备名称" width="180" />
      <el-table-column label="在线状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.onlineStatus === 1 ? 'success' : 'danger'" effect="dark">
            {{ row.onlineStatus === 1 ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="布防状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.defenseStatus === 1 ? 'warning' : 'info'" effect="dark">
            {{ row.defenseStatus === 1 ? '布防' : '撤防' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后上报" width="180">
        <template #default="{ row }">
          {{ row.lastReportTime ? new Date(row.lastReportTime).toLocaleString('zh-CN') : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="showEditDialog(row)">编辑</el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="设备编号">
          <el-input v-model="form.deviceCode" :disabled="isEdit" placeholder="例如 human_001" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="form.deviceName" placeholder="例如 客厅人体感应器" />
        </el-form-item>
        <el-form-item label="布防状态">
          <el-switch
            v-model="form.defenseStatusBool"
            active-text="布防"
            inactive-text="撤防"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ isEdit ? '更新' : '添加' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/index'
import type { ApiResponse, Device } from '@/types'

interface DeviceForm {
  deviceCode: string
  deviceName: string
  defenseStatusBool: number
}

const devices = ref<Device[]>([])
const loading = ref<boolean>(false)
const dialogVisible = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const editId = ref<number | null>(null)

const form = reactive<DeviceForm>({
  deviceCode: '',
  deviceName: '',
  defenseStatusBool: 0,
})

const loadDevices = async () => {
  loading.value = true
  try {
    const res = await http.get<ApiResponse<Device[]>>('/devices')
    if (res.data.code === 200) devices.value = res.data.data || []
  } catch (e) {
    ElMessage.warning('设备列表接口暂未就绪，请使用首页查看设备状态')
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  isEdit.value = false
  editId.value = null
  form.deviceCode = ''
  form.deviceName = ''
  form.defenseStatusBool = 0
  dialogVisible.value = true
}

const showEditDialog = (row: Device) => {
  isEdit.value = true
  editId.value = row.id
  form.deviceCode = row.deviceCode
  form.deviceName = row.deviceName
  form.defenseStatusBool = row.defenseStatus
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    const data = {
      deviceCode: form.deviceCode,
      deviceName: form.deviceName,
      defenseStatus: form.defenseStatusBool,
    }
    if (isEdit.value && editId.value) {
      await http.put<ApiResponse<null>>(`/devices/${editId.value}`, data)
      ElMessage.success('设备已更新')
    } else {
      await http.post<ApiResponse<{ id: number; deviceCode: string }>>('/devices', data)
      ElMessage.success('设备已添加')
    }
    dialogVisible.value = false
    loadDevices()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (row: Device) => {
  try {
    await ElMessageBox.confirm(`确定删除设备 ${row.deviceCode} 吗？`, '确认删除', { type: 'warning' })
    await http.delete<ApiResponse<boolean>>(`/devices/${row.id}`)
    ElMessage.success('设备已删除')
    loadDevices()
  } catch (e) {
    // cancelled or error
  }
}

onMounted(loadDevices)
</script>

<style scoped>
.device-container { background: #fff; padding: 20px; border-radius: 8px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; }
.header-actions { display: flex; gap: 10px; }
</style>
