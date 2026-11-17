<template>
  <div class="device-manage">
    <!-- ═══ 页面头部 ═══ -->
    <div class="app-page-header">
      <div>
        <h1 class="app-page-title">设备管理</h1>
        <p class="app-page-subtitle">统一管理所有前端设备，实时掌握设备运行状态与防护情况。</p>
      </div>
    </div>

    <!-- ═══ 统计概览卡 ═══ -->
    <div class="stat-overview" v-if="!loadError">
      <div class="app-stat-card">
        <div class="app-stat-icon" style="background:#EAF2FF;color:var(--app-primary)">
          <el-icon :size="22"><Odometer /></el-icon>
        </div>
        <div class="app-stat-body">
          <div class="app-stat-value">{{ total }}</div>
          <div class="app-stat-label">{{ total === devices.length ? '设备总数' : '当前页设备' }}</div>
        </div>
      </div>
      <div class="app-stat-card">
        <div class="app-stat-icon" style="background:#EAF8F1;color:var(--app-success)">
          <el-icon :size="22"><Monitor /></el-icon>
        </div>
        <div class="app-stat-body">
          <div class="app-stat-value">{{ onlineCount }}</div>
          <div class="app-stat-label">在线设备</div>
        </div>
      </div>
      <div class="app-stat-card">
        <div class="app-stat-icon" style="background:#FFF7E6;color:var(--app-warning)">
          <el-icon :size="22"><Lock /></el-icon>
        </div>
        <div class="app-stat-body">
          <div class="app-stat-value">{{ defenseCount }}</div>
          <div class="app-stat-label">已布防设备</div>
        </div>
      </div>
      <div class="app-stat-card">
        <div class="app-stat-icon" style="background:#FFF0F2;color:var(--app-danger)">
          <el-icon :size="22"><WarningFilled /></el-icon>
        </div>
        <div class="app-stat-body">
          <div class="app-stat-value">{{ humanCount }}</div>
          <div class="app-stat-label">当前有人设备</div>
        </div>
      </div>
    </div>

    <!-- ═══ 工具栏 ═══ -->
    <div class="app-toolbar">
      <el-input
        v-model="searchText" placeholder="搜索设备编号或名称" clearable
        :prefix-icon="Search" style="width:260px"
      />
      <el-select v-model="onlineStatusFilter" placeholder="在线状态" clearable style="width:130px">
        <el-option label="全部" value="" />
        <el-option label="在线" :value="1" />
        <el-option label="离线" :value="0" />
      </el-select>
      <el-select v-model="defenseStatusFilter" placeholder="布防状态" clearable style="width:130px">
        <el-option label="全部" value="" />
        <el-option label="布防" :value="1" />
        <el-option label="撤防" :value="0" />
      </el-select>
      <span class="app-toolbar-spacer"></span>
      <span class="update-time" v-if="lastUpdateTime">更新于 {{ lastUpdateTime }}</span>
      <el-button @click="loadDevices" :loading="loading" :icon="RefreshRight">刷新</el-button>
      <el-button type="primary" @click="showAddDialog" :icon="Plus">新增设备</el-button>
    </div>

    <!-- ═══ 错误状态 ═══ -->
    <div v-if="loadError" class="app-card" style="text-align:center;padding:48px">
      <el-icon :size="44" color="#AAB4C4"><WarningFilled /></el-icon>
      <p style="color:var(--app-text-secondary);margin:12px 0 16px">设备列表加载失败，请确认后端服务已启动</p>
      <el-button type="primary" @click="loadDevices" :loading="loading">重试</el-button>
    </div>

    <!-- ═══ 空状态 ═══ -->
    <div v-else-if="!loading && filteredDevices.length === 0" class="app-card" style="text-align:center;padding:48px">
      <el-icon :size="44" color="#AAB4C4"><Odometer /></el-icon>
      <p style="color:var(--app-text-secondary);margin:12px 0 0">{{ searchText || onlineStatusFilter !== '' || defenseStatusFilter !== '' ? '未找到匹配的设备' : '暂无设备，请点击"新增设备"添加' }}</p>
    </div>

    <!-- ═══ 表格 ═══ -->
    <template v-else>
      <div class="app-table-card">
        <el-table :data="pagedDevices" stripe v-loading="loading" highlight-current-row style="width:100%">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="deviceCode" label="设备编号" width="150" show-overflow-tooltip />
          <el-table-column prop="deviceName" label="设备名称" min-width="170" show-overflow-tooltip />
          <el-table-column label="在线状态" width="100" align="center">
            <template #default="{ row }">
              <span class="app-tag" :class="row.onlineStatus === 1 ? 'app-tag-success' : 'app-tag-offline'">
                <span class="app-tag-dot"></span>{{ row.onlineStatus === 1 ? '在线' : '离线' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="布防状态" width="100" align="center">
            <template #default="{ row }">
              <span class="app-tag" :class="row.defenseStatus === 1 ? 'app-tag-info' : 'app-tag-neutral'">
                <span class="app-tag-dot"></span>{{ row.defenseStatus === 1 ? '布防' : '撤防' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="人体检测" width="100" align="center">
            <template #default="{ row }">
              <span class="app-tag" :class="row.humanDetected === 1 ? 'app-tag-danger' : 'app-tag-success'">
                <span class="app-tag-dot"></span>{{ row.humanDetected === 1 ? '有人' : '无人' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="位置/备注" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.location || '暂无' }}</template>
          </el-table-column>
          <el-table-column label="最后在线" width="175">
            <template #default="{ row }">
              <span style="font-size:var(--app-font-sm);color:var(--app-text-secondary)">{{ row.lastOnline ? fmt(row.lastOnline) : '暂无' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <button class="app-link-edit" @click="showEditDialog(row)" title="编辑设备">编辑</button>
              <button class="app-link-delete" @click="handleDelete(row)" title="删除设备" style="margin-left:12px">删除</button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="app-pagination-bar" v-if="total > 0">
          <span class="app-pagination-total">共 {{ total }} 台设备</span>
          <el-pagination
            v-model:current-page="currentPage" v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="total"
            layout="sizes, prev, pager, next, jumper" background
            @size-change="currentPage = 1"
          />
        </div>
      </div>
    </template>

    <!-- ═══ 新增/编辑对话框 ═══ -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="500px" :close-on-click-modal="false" destroy-on-close>
      <el-form :model="form" label-width="90px" label-position="left">
        <el-form-item label="设备编号">
          <el-input v-model="form.deviceCode" :disabled="isEdit" placeholder="例如 human_001" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="form.deviceName" placeholder="例如 客厅人体感应器" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="例如 客厅" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ isEdit ? '更新' : '添加' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Plus, Odometer, Monitor, Lock, WarningFilled } from '@element-plus/icons-vue'
import { getDevices, addDevice, updateDevice, deleteDevice } from '@/api/humanSecurity'
import { useAuthStore } from '@/stores/auth'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import type { Device } from '@/types'

const voiceStore = useVoiceActionStore()
const authStore = useAuthStore()

const devices = ref<Device[]>([])
const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)
const searchText = ref('')
const onlineStatusFilter = ref<number | ''>('')
const defenseStatusFilter = ref<number | ''>('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const lastUpdateTime = ref('')

const form = reactive({ deviceCode: '', deviceName: '', location: '' })

// ── Computed ──
const filteredDevices = computed(() => {
  let list = devices.value
  const s = searchText.value.toLowerCase()
  if (s) list = list.filter(d => (d.deviceCode || '').toLowerCase().includes(s) || (d.deviceName || '').toLowerCase().includes(s))
  if (onlineStatusFilter.value !== '') list = list.filter(d => d.onlineStatus === onlineStatusFilter.value)
  if (defenseStatusFilter.value !== '') list = list.filter(d => d.defenseStatus === defenseStatusFilter.value)
  return list
})
const total = computed(() => filteredDevices.value.length)
const pagedDevices = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDevices.value.slice(start, start + pageSize.value)
})
// Stat overview (computed from all loaded devices — represents current page if API doesn't return total)
const onlineCount = computed(() => devices.value.filter(d => d.onlineStatus === 1).length)
const defenseCount = computed(() => devices.value.filter(d => d.defenseStatus === 1).length)
const humanCount = computed(() => devices.value.filter(d => d.humanDetected === 1).length)

// ── CRUD ──
async function loadDevices() {
  loading.value = true; loadError.value = false
  try {
    const uid = authStore.user?.userId || authStore.user?.id || 1
    const role = authStore.user?.role || 'admin'
    const res = await getDevices({ pageSize: 200, userId: uid, role: role })
    if (res.data?.code === 200) {
      devices.value = res.data.data?.records || res.data.data || []
    }
    currentPage.value = 1
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    loadError.value = true
  } finally { loading.value = false }
}

function showAddDialog() {
  isEdit.value = false; editId.value = null
  form.deviceCode = ''; form.deviceName = ''; form.location = ''
  dialogVisible.value = true
}
function showEditDialog(row: Device) {
  isEdit.value = true; editId.value = row.id!
  form.deviceCode = row.deviceCode || ''; form.deviceName = row.deviceName || ''; form.location = row.location || ''
  dialogVisible.value = true
}
async function handleSave() {
  if (!form.deviceCode || !form.deviceName) { ElMessage.warning('设备编号和名称不能为空'); return }
  saving.value = true
  try {
    const data = { deviceCode: form.deviceCode, deviceName: form.deviceName, location: form.location, userId: authStore.user?.userId || authStore.user?.id || 1 }
    if (isEdit.value && editId.value) { await updateDevice(editId.value, data); ElMessage.success('设备已更新') }
    else { await addDevice(data); ElMessage.success('设备已添加') }
    dialogVisible.value = false; loadDevices()
  } catch { ElMessage.error('操作失败') }
  finally { saving.value = false }
}
async function handleDelete(row: Device) {
  try {
    await ElMessageBox.confirm(`确定删除设备 ${row.deviceCode} 吗？`, '确认删除', { type: 'warning' })
    await deleteDevice(row.id!); ElMessage.success('设备已删除'); loadDevices()
  } catch { /* cancelled */ }
}
function fmt(t: string | Date): string { if (!t) return '-'; return new Date(t).toLocaleString('zh-CN') }

// ── 轮询机制：2秒刷新（Issue 3）──
let pollTimer: any = null
let requestInFlight = false
let consecutiveFailures = 0
const POLL_INTERVAL = 2000
const SLOW_INTERVAL = 8000

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (document.hidden || requestInFlight) return
    refreshDevicesSilent()
  }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function refreshDevicesSilent() {
  if (requestInFlight) return
  requestInFlight = true
  try {
    const uid = authStore.user?.userId || authStore.user?.id || 1
    const role = authStore.user?.role || 'admin'
    const res = await getDevices({ pageSize: 200, userId: uid, role: role })
    if (res.data?.code === 200) {
      devices.value = res.data.data?.records || res.data.data || []
      consecutiveFailures = 0
      lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      // 成功后恢复2秒
      if (pollTimer) { stopPolling(); startPolling() }
    } else {
      consecutiveFailures++
    }
  } catch {
    consecutiveFailures++
  } finally { requestInFlight = false }

  // 连续失败3次后降频到8秒
  if (consecutiveFailures >= 3 && pollTimer) {
    stopPolling()
    pollTimer = setInterval(() => {
      if (document.hidden || requestInFlight) return
      refreshDevicesSilent()
    }, SLOW_INTERVAL)
  }
}

function visibilityHandler() {
  if (document.hidden) {
    stopPolling()
  } else {
    refreshDevicesSilent()
    startPolling()
  }
}

onMounted(() => {
  loadDevices()
  startPolling()
  voiceStore.onAction('REFRESH_CURRENT', loadDevices)
  document.addEventListener('visibilitychange', visibilityHandler)
  window.addEventListener('assistant:refresh-devices', loadDevices as any)
})
onUnmounted(() => {
  stopPolling()
  voiceStore.offAction('REFRESH_CURRENT', loadDevices)
  document.removeEventListener('visibilitychange', visibilityHandler)
  window.removeEventListener('assistant:refresh-devices', loadDevices as any)
})
</script>

<style scoped>
.device-manage { display:flex; flex-direction:column; gap:var(--app-space-4); }
.stat-overview { display:grid; grid-template-columns:repeat(4,1fr); gap:var(--app-space-4); }
.update-time { font-size: 12px; color: #94a3b8; white-space: nowrap; }
@media (max-width:1100px) { .stat-overview { grid-template-columns:repeat(2,1fr); } }
@media (max-width:640px) { .stat-overview { grid-template-columns:1fr; } }
</style>
