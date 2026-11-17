<template>
  <div class="device-manage">
    <h3>设备管理</h3>
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="searchKey" placeholder="搜索设备名称/编号" clearable style="width:260px" />
        <el-button type="primary" @click="showAdd = true"><el-icon><Plus /></el-icon>添加设备</el-button>
      </div>
      <el-table :data="filtered" stripe v-loading="loading">
        <el-table-column prop="deviceCode" label="设备编号" width="160" />
        <el-table-column prop="deviceName" label="名称" width="160" />
        <el-table-column prop="location" label="位置" width="180" />
        <el-table-column label="在线" width="80"><template #default="{row}"><el-tag :type="row.onlineStatus?'success':'danger'" size="small">{{row.onlineStatus?'在线':'离线'}}</el-tag></template></el-table-column>
        <el-table-column label="人体" width="80"><template #default="{row}"><el-tag :type="row.humanDetected?'warning':'info'" size="small">{{row.humanDetected?'有人':'无人'}}</el-tag></template></el-table-column>
        <el-table-column label="布防" width="80"><template #default="{row}"><el-tag :type="row.defenseStatus?'warning':'info'" size="small">{{row.defenseStatus?'布防':'撤防'}}</el-tag></template></el-table-column>
        <el-table-column label="上报时间" width="180"><template #default="{row}">{{row.lastReportTime?fmt(row.lastReportTime):'-'}}</template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{row}"><el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)"><template #reference><el-button size="small" type="danger" link>删除</el-button></template></el-popconfirm></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAdd" title="添加设备" width="460px">
      <el-form ref="formRef" :model="addForm" :rules="rules" label-width="100px">
        <el-form-item label="设备编号" prop="deviceCode"><el-input v-model="addForm.deviceCode" placeholder="如 human_001" /></el-form-item>
        <el-form-item label="设备名称" prop="deviceName"><el-input v-model="addForm.deviceName" placeholder="如 客厅感应器" /></el-form-item>
        <el-form-item label="安装位置" prop="location"><el-input v-model="addForm.location" placeholder="如 1栋101客厅" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAdd=false">取消</el-button><el-button type="primary" @click="handleAdd" :loading="adding">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { AddDeviceForm } from '@/types'
import { useDeviceStore } from '@/stores/device'
import { useMqttStore } from '@/stores/mqtt'
import { addDeviceApi, deleteDeviceApi } from '@/api/device'

const deviceStore = useDeviceStore()
const mqttStore = useMqttStore()
const loading = ref(false)
const searchKey = ref('')
const showAdd = ref(false)
const adding = ref(false)
const formRef = ref<FormInstance>()

const addForm = reactive<AddDeviceForm>({ deviceCode: '', deviceName: '', location: '' })
const rules: FormRules = {
  deviceCode: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入安装位置', trigger: 'blur' }],
}

const filtered = computed(() => {
  if (!searchKey.value) return deviceStore.devices
  const k = searchKey.value.toLowerCase()
  return deviceStore.devices.filter(d => d.deviceCode.toLowerCase().includes(k) || d.deviceName.toLowerCase().includes(k) || d.location.toLowerCase().includes(k))
})

onMounted(async () => { mqttStore.connect(); loading.value = true; try { await deviceStore.fetchDevices() } finally { loading.value = false } })

async function handleAdd() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  adding.value = true
  try { await addDeviceApi({ ...addForm }); ElMessage.success('添加成功'); showAdd.value = false; await deviceStore.fetchDevices() }
  catch { ElMessage.error('添加失败') }
  finally { adding.value = false }
}

async function handleDelete(id: number) {
  try { await deleteDeviceApi(id); ElMessage.success('已删除'); await deviceStore.fetchDevices() }
  catch { ElMessage.error('删除失败') }
}

function fmt(t: string) { return new Date(t).toLocaleString('zh-CN') }
</script>

<style scoped>
.device-manage h3 { margin-bottom: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
