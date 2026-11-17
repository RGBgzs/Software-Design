<template>
  <div class="alarm-manage">

    <!-- Page Header -->
    <PageHeader title="告警管理" desc="实时告警监控与处理中心" />

    <!-- Filter Toolbar -->
    <div class="filter-bar">
      <div class="filter-bar-left">
        <div class="filter-item">
          <label class="filter-label">处理状态</label>
          <el-select v-model="filterStatus" placeholder="全部" clearable size="default" @change="onFilterChange" style="width:130px">
            <el-option label="未处理" :value="0" />
            <el-option label="已处理" :value="1" />
            <el-option label="误报" :value="2" />
          </el-select>
        </div>
        <div class="filter-item">
          <label class="filter-label">告警级别</label>
          <el-select v-model="filterLevel" placeholder="全部" clearable size="default" @change="onFilterChange" style="width:110px">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </div>
        <div class="filter-item">
          <label class="filter-label">告警类型</label>
          <el-select v-model="filterType" placeholder="全部" clearable size="default" @change="onFilterChange" style="width:150px">
            <el-option label="入侵告警" value="INTRUSION" />
            <el-option label="陌生人脸告警" value="FACE_UNKNOWN" />
            <el-option label="假体攻击告警" value="FACE_SPOOF" />
            <el-option label="设备离线" value="DEVICE_OFFLINE" />
          </el-select>
        </div>
      </div>
      <div class="filter-bar-right">
        <el-button @click="loadAlarms" :loading="loading" size="default">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14" style="margin-right:4px"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
          刷新
        </el-button>
        <el-button type="danger" @click="markAllHandled" :disabled="!hasUnhandled" size="default">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14" style="margin-right:4px"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>
          全部确认处理
        </el-button>
      </div>
    </div>

    <!-- Alarms Table -->
    <div class="table-section">
      <el-table :data="filteredAlarms" stripe border style="width: 100%" v-loading="loading" class="alarms-table">
        <el-table-column prop="id" label="ID" width="75" align="center" />
        <el-table-column prop="deviceCode" label="设备编号" width="150" />
        <el-table-column label="告警级别" width="110" align="center">
          <template #default="{ row }">
            <span class="alarm-level-tag" :class="'alarm-level-tag--' + (row.alarmLevel || 'MEDIUM').toLowerCase()">
              <span class="alarm-level-dot"></span>
              {{ levelLabel(row.alarmLevel) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="告警类型" width="120" align="center">
          <template #default="{ row }">
            <span class="alarm-type-tag">{{ row.alarmType || '入侵告警' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="alarmContent" label="告警内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="处理状态" width="120" align="center">
          <template #default="{ row }">
            <span class="handle-status-tag" :class="'handle-status-tag--' + handleStatusCode(row.handleStatus)">
              {{ handleStatusLabel(row.handleStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.handleUser ? 'handle-user' : 'handle-user--empty'">{{ row.handleUser || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警时间" width="175">
          <template #default="{ row }">
            <span class="time-cell">{{ fmt(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.handleStatus === 0">
              <el-button type="primary" size="small" @click="confirmAlarm(row.id!)">确认处理</el-button>
              <el-button type="warning" size="small" text @click="markFalse(row.id!)">误报</el-button>
            </template>
            <template v-else>
              <span class="action-done" :class="row.handleStatus === 1 ? 'action-done--handled' : 'action-done--false'">
                <svg v-if="row.handleStatus === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><polyline points="20 6 9 17 4 12"/></svg>
                {{ row.handleStatus === 1 ? '已处理' : '误报' }}
              </span>
            </template>
            <el-button v-if="row.imagePath" size="small" type="info" text @click="showAlarmImage(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Pagination -->
    <div class="pagination-bar" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadAlarms"
      />
    </div>

    <!-- 图片预览弹窗 -->
    <el-dialog v-model="imageVisible" title="告警抓拍" width="500px" :close-on-click-modal="true">
      <div style="text-align:center">
        <img v-if="imageUrl" :src="imageUrl" style="max-width:100%;max-height:60vh;border-radius:8px" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAlarms, handleAlarm } from '@/api/humanSecurity'
import { useAuthStore } from '@/stores/auth'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import PageHeader from '@/views/unified/components/PageHeader.vue'
import type { Alarm } from '@/types'

const voiceStore = useVoiceActionStore()

const authStore = useAuthStore()

const imageVisible = ref(false)
const imageUrl = ref('')

function showAlarmImage(row: any) {
  if (row.imagePath) {
    imageUrl.value = '/' + row.imagePath
    imageVisible.value = true
  }
}

const alarms = ref<Alarm[]>([])
const loading = ref(false)
const filterStatus = ref<number | ''>('')
const filterLevel = ref('')
const filterType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const hasUnhandled = computed(() => alarms.value.some(a => a.handleStatus === 0))

const filteredAlarms = computed(() => {
  let list = alarms.value
  if (filterStatus.value !== '' && filterStatus.value != null) {
    list = list.filter(a => a.handleStatus === filterStatus.value)
  }
  if (filterLevel.value && filterLevel.value !== '') {
    list = list.filter(a => (a.alarmLevel || 'MEDIUM') === filterLevel.value)
  }
  if (filterType.value && filterType.value !== '') {
    list = list.filter(a => (a.alarmType || 'INTRUSION') === filterType.value)
  }
  return list
})


function levelLabel(level?: string): string {
  if (level === 'HIGH') return '高'
  if (level === 'MEDIUM') return '中'
  if (level === 'LOW') return '低'
  return '中'
}


function handleStatusCode(status?: number): string {
  if (status === 0) return 'unhandled'
  if (status === 1) return 'handled'
  if (status === 2) return 'false-alarm'
  return 'unknown'
}

function handleStatusLabel(status?: number): string {
  if (status === 0) return '未处理'
  if (status === 1) return '已处理'
  if (status === 2) return '误报'
  return '未知'
}

function onFilterChange() {
  page.value = 1
  loadAlarms()
}

async function loadAlarms() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (filterStatus.value !== '') params.handleStatus = filterStatus.value
    const uid = authStore.user?.userId || authStore.user?.id || 1
    const role = authStore.user?.role || 'admin'
    params.userId = uid
    params.role = role
    const res = await getAlarms(params)
    if (res.data?.code === 200) {
      const data = res.data.data
      alarms.value = data?.records || data || []
      total.value = data?.total || alarms.value.length
    }
  } catch {
    ElMessage.warning('告警记录加载失败')
  } finally {
    loading.value = false
  }
}

async function confirmAlarm(id: number) {
  try {
    const username = authStore.user?.username || 'admin'
    await handleAlarm(id, { handleStatus: 1, handleUser: username, remark: '已确认处理' })
    ElMessage.success('告警已确认处理')
    loadAlarms()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function markFalse(id: number) {
  try {
    const username = authStore.user?.username || 'admin'
    await handleAlarm(id, { handleStatus: 2, handleUser: username, remark: '确认为误报' })
    ElMessage.success('已标记为误报')
    loadAlarms()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function markAllHandled() {
  const unhandled = alarms.value.filter(a => a.handleStatus === 0)
  if (unhandled.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认将所有 ${unhandled.length} 条未处理告警标记为已处理？`,
      '批量处理确认',
      { confirmButtonText: '确认处理', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  for (const a of unhandled) {
    try {
      await confirmAlarm(a.id!)
    } catch { /* continue */ }
  }
  loadAlarms()
}

function fmt(t: string | Date): string {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAlarms()
  voiceStore.onAction('REFRESH_CURRENT', loadAlarms)
})
onUnmounted(() => {
  voiceStore.offAction('REFRESH_CURRENT', loadAlarms)
})
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   Alarm Manage — Enterprise SaaS Styling
   ═══════════════════════════════════════════════════ */

.alarm-manage {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
}

/* ── Filter Toolbar ── */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-3);
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  padding: var(--app-space-4) var(--app-space-5);
  box-shadow: var(--app-shadow-sm);
}

.filter-bar-left {
  display: flex;
  align-items: flex-end;
  gap: var(--app-space-4);
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-label {
  font-size: var(--app-font-size-xs);
  color: var(--app-text-muted);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  padding-left: 2px;
}

.filter-bar-right {
  display: flex;
  align-items: flex-end;
  gap: var(--app-space-2);
}

/* ── Table Section ── */
.table-section {
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  overflow: hidden;
}

.alarms-table {
  border-radius: 0 !important;
  border: none !important;
}
.alarms-table :deep(.el-table__inner-wrapper) {
  border: none;
}

/* ── Alarm Level Tags ── */
.alarm-level-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs);
  font-weight: 600;
}
.alarm-level-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.alarm-level-tag--high {
  background: var(--app-danger-light);
  color: var(--app-danger);
}
.alarm-level-tag--high .alarm-level-dot { background: var(--app-danger); }

.alarm-level-tag--medium {
  background: var(--app-warning-light);
  color: #b45309;
}
.alarm-level-tag--medium .alarm-level-dot { background: var(--app-warning); }

.alarm-level-tag--low {
  background: #e8f0fe;
  color: var(--app-primary);
}
.alarm-level-tag--low .alarm-level-dot { background: var(--app-primary); }

/* ── Alarm Type Tag ── */
.alarm-type-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs);
  font-weight: 600;
  background: var(--app-danger-light);
  color: var(--app-danger);
}

/* ── Handle Status Tags ── */
.handle-status-tag {
  display: inline-block;
  padding: 2px 12px;
  border-radius: var(--app-radius-full);
  font-size: var(--app-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}
.handle-status-tag--unhandled {
  background: #fef2f2;
  color: #dc2626;
}
.handle-status-tag--handled {
  background: #f0fdf6;
  color: #15803d;
}
.handle-status-tag--false-alarm {
  background: #fefce8;
  color: #a16207;
}
.handle-status-tag--unknown {
  background: #f1f5f9;
  color: var(--app-text-muted);
}

/* ── Handle User ── */
.handle-user {
  color: var(--app-text-primary);
  font-size: var(--app-font-size-sm);
}
.handle-user--empty {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-sm);
}

/* ── Time Cell ── */
.time-cell {
  font-size: var(--app-font-size-sm);
  color: var(--app-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ── Action Done State ── */
.action-done {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--app-font-size-sm);
  font-weight: 500;
}
.action-done--handled {
  color: var(--app-success);
}
.action-done--false {
  color: var(--app-warning);
}

/* ── Pagination Bar ── */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  padding: var(--app-space-3) var(--app-space-5);
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-bar-left {
    flex-direction: column;
  }
  .filter-bar-right {
    justify-content: flex-end;
  }
}
</style>
