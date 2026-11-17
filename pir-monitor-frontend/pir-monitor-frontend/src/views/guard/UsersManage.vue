<template>
  <div class="users-manage">
    <div class="app-page-header">
      <div>
        <h1 class="app-page-title">管理用户</h1>
        <p class="app-page-subtitle">查看和管理您负责的居民用户及其设备</p>
      </div>
      <el-button type="primary" @click="showAddDialog">添加用户</el-button>
    </div>

    <div class="app-card">
      <el-table :data="users" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deviceCount" label="设备数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.deviceCount > 0 ? 'success' : 'info'">{{ row.deviceCount }} 台</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewUserDevices(row)">查看设备</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 添加用户弹窗 -->
    <el-dialog v-model="addDialog" title="添加用户" width="450px">
      <el-select v-model="selectedUserId" placeholder="搜索并选择用户" filterable style="width:100%">
        <el-option v-for="u in unassignedUsers" :key="u.id" :label="u.username + ' (' + (u.realName || '无姓名') + ')'" :value="u.id" />
      </el-select>
      <div style="margin-top:16px;text-align:right">
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" @click="doAssignUser" :loading="assigning" :disabled="!selectedUserId">确认添加</el-button>
      </div>
    </el-dialog>

    <!-- 查看设备弹窗 -->
    <el-dialog v-model="deviceDialog" :title="selectedUser?.username + ' 的设备'" width="700px">
      <el-table :data="userDevices" stripe v-loading="deviceLoading" max-height="400">
        <el-table-column prop="deviceCode" label="设备编号" width="160" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" />
        <el-table-column label="在线" width="80" align="center">
          <template #default="{ row: d }">
            <span :class="d.onlineStatus === 1 ? 'app-tag app-tag-success' : 'app-tag app-tag-offline'">
              <span class="app-tag-dot"></span>{{ d.onlineStatus === 1 ? '在线' : '离线' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="布防" width="80" align="center">
          <template #default="{ row: d }">{{ d.defenseStatus === 1 ? '布防' : '撤防' }}</template>
        </el-table-column>
        <el-table-column label="人体" width="80" align="center">
          <template #default="{ row: d }">
            <span :style="{ color: d.humanDetected === 1 ? '#ef4444' : '#16b364', fontWeight: 600 }">{{ d.humanDetected === 1 ? '有人' : '无人' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后上报" min-width="160">
          <template #default="{ row: d }">{{ d.lastReportTime ? new Date(d.lastReportTime).toLocaleString('zh-CN') : '从未' }}</template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="deviceDialog = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/index'
import { useAuthStore } from '@/stores/auth'
import { getDevicesApi } from '@/api/device'

const auth = useAuthStore()
const loading = ref(false)
const users = ref<any[]>([])
const selectedUser = ref<any>(null)
const deviceDialog = ref(false)
const userDevices = ref<any[]>([])
const deviceLoading = ref(false)
const addDialog = ref(false)
const selectedUserId = ref<number | null>(null)
const unassignedUsers = ref<any[]>([])
const assigning = ref(false)

async function loadManagedUsers() {
  loading.value = true
  try {
    const guardId = auth.user?.userId || auth.user?.id
    const res = await http.get('/guard/managed-users', { params: { guardId } })
    if (res.data?.code === 200) users.value = res.data.data || []
  } catch { ElMessage.error('加载用户列表失败') }
  finally { loading.value = false }
}

async function showAddDialog() {
  addDialog.value = true
  selectedUserId.value = null
  try {
    const res = await http.get('/users/unassigned')
    if (res.data?.code === 200) unassignedUsers.value = res.data.data || []
  } catch { unassignedUsers.value = [] }
}

async function doAssignUser() {
  if (!selectedUserId.value) return
  assigning.value = true
  try {
    const guardId = auth.user?.userId || auth.user?.id
    const res = await http.post('/guard/assign-user', { userId: selectedUserId.value, guardId })
    if (res.data?.code === 200) {
      ElMessage.success('已添加')
      addDialog.value = false
      loadManagedUsers()
    } else {
      ElMessage.error(res.data?.message || '添加失败')
    }
  } catch { ElMessage.error('操作失败') }
  finally { assigning.value = false }
}

async function viewUserDevices(user: any) {
  selectedUser.value = user
  deviceDialog.value = true
  deviceLoading.value = true
  try {
    const res = await getDevicesApi({ userId: user.id, role: 'guard', pageSize: 100 })
    const data = res.data.data
    userDevices.value = Array.isArray(data) ? data : (data?.records || [])
  } catch { ElMessage.error('加载设备失败') }
  finally { deviceLoading.value = false }
}

onMounted(() => { loadManagedUsers() })
</script>

<style scoped>
.users-manage { display: flex; flex-direction: column; gap: var(--app-space-4); }
.app-card { background: var(--app-card-bg); border: 1px solid var(--app-border-light); border-radius: var(--app-radius-lg); box-shadow: var(--app-shadow-sm); overflow: hidden; }
.app-page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 12px; }
.app-page-title { font-size: 20px; font-weight: 700; margin: 0 0 4px; }
.app-page-subtitle { font-size: 13px; color: var(--app-text-muted); margin: 0; }
.app-tag { display: inline-flex; align-items: center; gap: 5px; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.app-tag-success { background: #f0fdf6; color: #15803d; }
.app-tag-offline { background: #f1f5f9; color: #64748b; }
.app-tag-dot { width: 6px; height: 6px; border-radius: 50%; }
.app-tag-success .app-tag-dot { background: #16b364; }
.app-tag-offline .app-tag-dot { background: #94a3b8; }
</style>
