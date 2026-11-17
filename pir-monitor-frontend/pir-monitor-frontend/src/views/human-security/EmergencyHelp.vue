<template>
  <div class="help-page">
    <PageHeader title="紧急求助" desc="一键求助、紧急联系人、求助记录">
      <span class="update-time" v-if="lastUpdateTime">
        <el-icon><Clock /></el-icon> {{ lastUpdateTime }}
      </span>
      <el-button :icon="RefreshRight" @click="loadAll" :loading="loading">刷新</el-button>
    </PageHeader>

    <el-row :gutter="20">
      <!-- SOS 一键求助按钮 -->
      <el-col :span="8" :xs="24" :sm="8">
        <div class="card sos-card">
          <div class="sos-title">🆘 一键求救</div>
          <p class="sos-desc">长按3秒发送紧急求助信号给所有紧急联系人</p>
          <div
            class="sos-hold-btn"
            :class="{ holding: isHolding, fired: isFired }"
            @pointerdown.prevent="startHold"
            @pointerup.prevent="cancelHold"
            @pointerleave="cancelHold"
            @pointercancel="cancelHold"
          >
            <svg class="sos-ring" viewBox="0 0 100 100">
              <circle class="ring-track" cx="50" cy="50" r="44" />
              <circle
                class="ring-fill"
                :class="{ danger: holdProgress > 0.8 }"
                cx="50" cy="50" r="44"
                :style="{ strokeDashoffset: ringOffset }"
              />
            </svg>
            <div class="sos-btn-content">
              <span class="sos-icon">{{ isHolding ? '⏳' : 'SOS' }}</span>
              <span class="sos-label" v-if="!isHolding">按住3秒</span>
              <span class="sos-label" v-else>{{ countdownText }}</span>
            </div>
          </div>
          <div class="sos-hint">{{ isHolding ? '松手取消' : '长按3秒发起紧急求助' }}</div>
          <div v-if="sosSent" class="sos-ok">✅ 求助信号已发送给 {{ emergencyContacts.length }} 位联系人</div>
        </div>
      </el-col>

      <!-- 紧急联系人 + 求助表单 -->
      <el-col :span="16">
        <div class="card">
          <div class="contacts-header">
            <h3 class="card-title">紧急联系人 ({{ emergencyContacts.length }})</h3>
            <el-button size="small" type="primary" :icon="Plus" @click="showAddContact = true">添加</el-button>
          </div>
          <div v-if="emergencyContacts.length === 0" class="no-contacts">
            <el-empty description="暂无紧急联系人，请先添加" :image-size="48" />
          </div>
          <div v-else class="contact-grid">
            <div v-for="c in emergencyContacts" :key="c.id" class="contact-chip">
              <el-avatar :size="36">{{ (c.contactNickname || c.contactUsername || '?')[0] }}</el-avatar>
              <div class="chip-info">
                <div class="chip-name">{{ c.contactNickname || c.contactUsername }}</div>
                <div class="chip-rel">{{ c.relation || '联系人' }}</div>
              </div>
              <el-button size="small" type="danger" link :icon="Delete" @click="doDeleteContact(c.id)" />
            </div>
          </div>
        </div>

        <div class="card" style="margin-top: 16px">
          <h3 class="card-title">求助详情</h3>
          <el-form :model="helpForm" label-width="80px" size="default">
            <el-form-item label="求助类型">
              <el-select v-model="helpForm.helpType" style="width: 100%">
                <el-option label="安全异常" value="SECURITY" />
                <el-option label="突发疾病" value="SUDDEN_ILLNESS" />
                <el-option label="火灾危险" value="FIRE" />
                <el-option label="跌倒受伤" value="FALL_INJURY" />
                <el-option label="其他紧急" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备编号">
              <el-input v-model="helpForm.deviceCode" placeholder="如：human_001" />
            </el-form-item>
            <el-form-item label="位置">
              <el-input v-model="helpForm.location" placeholder="如：3栋1单元501客厅" />
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input v-model="helpForm.description" type="textarea" :rows="2" placeholder="请描述具体情况" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" :loading="sendingDetail" @click="sendDetailHelp" :disabled="emergencyContacts.length === 0">
                发送求助 (通知 {{ emergencyContacts.length }} 位联系人)
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <!-- 求助记录 -->
    <div class="card" style="margin-top: 20px">
      <h3 class="card-title">求助记录</h3>
      <el-table :data="helpRecords" stripe v-loading="recordLoading" empty-text="暂无求助记录">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="recordTypeTag(row)" size="small">{{ recordTypeLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceCode" label="设备编号" width="130" />
        <el-table-column prop="location" label="位置" width="180" show-overflow-tooltip />
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.notes || row.description || '').substring(0, 60) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status === 'FINISHED' ? 'HANDLED' : (row.status === 'SENT' ? 'PENDING' : 'UNHANDLED')" />
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt || row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 添加联系人弹窗 -->
    <el-dialog v-model="showAddContact" title="添加紧急联系人" width="460px" :close-on-click-modal="false">
      <el-form @submit.prevent="doAddContact">
        <el-form-item label="用户名">
          <el-input v-model="searchUsername" placeholder="请输入对方用户名" :prefix-icon="Search" @keyup.enter="doAddContact" />
        </el-form-item>
        <el-form-item label="关系" style="margin-top:12px">
          <el-select v-model="addRelation" placeholder="选择关系" style="width:100%">
            <el-option label="家人" value="家人" />
            <el-option label="同事" value="同事" />
            <el-option label="朋友" value="朋友" />
            <el-option label="邻居" value="邻居" />
            <el-option label="保安" value="保安" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px">
          <el-button @click="showAddContact = false">取消</el-button>
          <el-button type="primary" @click="doAddContact" :loading="addingContact">确认添加</el-button>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, Clock, Plus, Delete, Search } from '@element-plus/icons-vue'
import PageHeader from '@/views/unified/components/PageHeader.vue'
import StatusTag from '@/views/unified/components/StatusTag.vue'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import { useAuthStore } from '@/stores/auth'
import { sendEmergencyHelp, getEmergencyRecords } from '@/api/humanSecurity'
import { getContactList, addEmergencyContact, deleteEmergencyContact } from '@/api/messageCenter'

const voiceStore = useVoiceActionStore()
const authStore = useAuthStore()

const myUserId = computed(() => {
  const uid = authStore.user?.id || authStore.user?.userId
  return uid ? Number(uid) : 1
})

// ── State ──
const loading = ref(false)
const lastUpdateTime = ref('')
const sending = ref(false)
const sosSent = ref(false)
const sendingDetail = ref(false)
const recordLoading = ref(false)
const helpRecords = ref<any[]>([])
const emergencyContacts = ref<any[]>([])
const showAddContact = ref(false)
const searchUsername = ref('')
const addingContact = ref(false)
const addRelation = ref('')

const helpForm = reactive({
  helpType: 'SECURITY',
  deviceCode: 'human_001',
  location: '',
  description: '',
})

// ── SOS Hold Button ──
const HOLD_DURATION = 3000
const CIRCUMFERENCE = 2 * Math.PI * 44
const isHolding = ref(false)
const isFired = ref(false)
const holdProgress = ref(0)
const holdElapsed = ref(0)
let animId: any = null
let holdStart = 0

const ringOffset = computed(() => CIRCUMFERENCE * (1 - holdProgress.value))
const countdownText = computed(() => `松开取消(${Math.max(0, Math.ceil((HOLD_DURATION - holdElapsed.value) / 1000))}s)`)

// ── Polling ──
let pollTimer: any = null

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    await loadRecords()
  }, 5000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ── Data ──
async function loadAll() {
  loading.value = true
  await Promise.all([loadContacts(), loadRecords()])
  lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  loading.value = false
}

async function loadContacts() {
  try {
    const res = await getContactList(myUserId.value)
    emergencyContacts.value = Array.isArray(res?.data) ? res.data : (Array.isArray(res?.data?.data) ? res.data.data : [])
  } catch { emergencyContacts.value = [] }
}

async function loadRecords() {
  try {
    const res = await getEmergencyRecords({ userId: myUserId.value })
    const data = res?.data?.data || res?.data
    helpRecords.value = Array.isArray(data?.records) ? data.records : (Array.isArray(data) ? data : [])
  } catch { /* silent */ }
}

// ── SOS ──
function startHold() {
  if (isFired.value || sending.value) return
  isHolding.value = true; holdProgress.value = 0; holdElapsed.value = 0; holdStart = performance.now()
  function tick() {
    holdElapsed.value = performance.now() - holdStart
    holdProgress.value = Math.min(1, holdElapsed.value / HOLD_DURATION)
    if (holdProgress.value >= 1) {
      isHolding.value = false; isFired.value = true
      setTimeout(() => { isFired.value = false }, 600)
      confirmQuickHelp()
      return
    }
    if (isHolding.value) animId = requestAnimationFrame(tick)
  }
  animId = requestAnimationFrame(tick)
}

function cancelHold() {
  if (!isHolding.value) return
  isHolding.value = false; holdProgress.value = 0; holdElapsed.value = 0
  if (animId) { cancelAnimationFrame(animId); animId = null }
}

async function confirmQuickHelp() {
  try {
    await ElMessageBox.confirm('确定发送紧急求助给所有紧急联系人吗？', '确认发送', {
      confirmButtonText: '确定发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }

  sending.value = true
  try {
    const res = await sendEmergencyHelp({ userId: myUserId.value })
    if (res.data?.code === 200 || (res.data as any)?.success) {
      sosSent.value = true
      ElMessage.success('紧急求助已发送')
      await loadAll()
      setTimeout(() => { sosSent.value = false }, 5000)
    } else {
      ElMessage.warning(res.data?.message || '求助发送失败')
    }
  } catch { ElMessage.error('紧急求助发送失败') }
  finally { sending.value = false }
}

async function sendDetailHelp() {
  if (emergencyContacts.value.length === 0) {
    ElMessage.warning('请先添加紧急联系人')
    return
  }
  try {
    await ElMessageBox.confirm('确定发送紧急求助吗？', '确认发送', {
      confirmButtonText: '确认发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }

  sendingDetail.value = true
  try {
    const res = await sendEmergencyHelp({
      userId: myUserId.value,
      helpType: helpForm.helpType,
      deviceCode: helpForm.deviceCode,
      location: helpForm.location,
      description: helpForm.description,
    })
    if (res.data?.code === 200 || (res.data as any)?.success) {
      ElMessage.success('紧急求助已发送')
      helpForm.description = ''
      await loadAll()
    } else {
      ElMessage.error(res.data?.message || '求助发送失败')
    }
  } catch { ElMessage.error('发送失败') }
  finally { sendingDetail.value = false }
}

// ── Contacts ──
async function doAddContact() {
  const uname = searchUsername.value.trim()
  if (!uname) { ElMessage.warning('请输入用户名'); return }
  addingContact.value = true
  try {
    const res = await addEmergencyContact({
      userId: myUserId.value,
      currentUserId: myUserId.value,
      contactUsername: uname,
      username: uname,
      relation: addRelation.value
    })
    if (res.data?.code === 200 || (res.data as any)?.success) {
      ElMessage.success('添加成功')
      searchUsername.value = ''; addRelation.value = ''; showAddContact.value = false
      await loadContacts()
    } else {
      ElMessage.error(res.data?.message || '添加失败')
    }
  } catch { ElMessage.error('添加失败') }
  finally { addingContact.value = false }
}

async function doDeleteContact(id: number) {
  try {
    const res = await deleteEmergencyContact(id, myUserId.value)
    if (res.data?.code === 200 || (res.data as any)?.success) {
      ElMessage.success('已删除')
      await loadContacts()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch { ElMessage.error('删除失败') }
}

// ── Voice Actions ──
function onVoiceHelp() { confirmQuickHelp() }

// ── Helpers ──
function recordTypeTag(r: any): string {
  const t = r.eventType || r.helpType || r.type || ''
  const map: Record<string, string> = { SECURITY: 'danger', FIRE: 'danger', SUDDEN_ILLNESS: 'warning', FALL_INJURY: 'warning' }
  return map[t] || 'info'
}
function recordTypeLabel(r: any): string {
  const t = r.eventType || r.helpType || r.type || ''
  const map: Record<string, string> = { SECURITY: '安全异常', SUDDEN_ILLNESS: '突发疾病', FIRE: '火灾危险', FALL_INJURY: '跌倒受伤', OTHER: '其他' }
  return map[t] || t || '紧急求助'
}
function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }

// ── Lifecycle ──
onMounted(() => {
  loadAll()
  startPolling()
  voiceStore.onAction('SEND_HELP', onVoiceHelp)
  voiceStore.onAction('REFRESH_CURRENT', loadAll)
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) stopPolling()
    else { startPolling(); loadRecords() }
  })
})

onUnmounted(() => {
  stopPolling()
  if (animId) cancelAnimationFrame(animId)
  voiceStore.offAction('SEND_HELP', onVoiceHelp)
  voiceStore.offAction('REFRESH_CURRENT', loadAll)
})
</script>

<style scoped>
.help-page { width: 100%; }
.card {
  background: #fff; border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0,0,0,.04); padding: 20px;
}
.card-title { font-size: 14px; font-weight: 600; color: #0f172a; margin: 0 0 16px; }
.contacts-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.contacts-header .card-title { margin: 0; }
.no-contacts { padding: 20px 0; }
.contact-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.contact-chip {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border: 1px solid #f1f5f9; border-radius: 10px; background: #fafbfc;
}
.chip-info { min-width: 60px; }
.chip-name { font-size: 13px; font-weight: 600; color: #0f172a; }
.chip-rel { font-size: 11px; color: #94a3b8; }

/* SOS */
.sos-card { text-align: center; padding: 30px 20px; }
.sos-title { font-size: 20px; font-weight: 700; color: #0f172a; }
.sos-desc { font-size: 13px; color: #64748b; margin: 8px 0 20px; }
.sos-hold-btn {
  position: relative; width: 120px; height: 120px; margin: 0 auto;
  cursor: pointer; user-select: none; -webkit-user-select: none;
  display: flex; align-items: center; justify-content: center; border-radius: 50%;
}
.sos-hold-btn.holding { cursor: grabbing; }
.sos-ring { position: absolute; top: 0; left: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-track { fill: none; stroke: #e2e8f0; stroke-width: 5; }
.ring-fill {
  fill: none; stroke: #ef4444; stroke-width: 5; stroke-linecap: round;
  stroke-dasharray: 276.46; transition: none;
}
.ring-fill.danger { stroke: #b91c1c; }
.sos-btn-content { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; }
.sos-icon { font-size: 28px; font-weight: 900; color: #ef4444; }
.sos-label { font-size: 11px; color: #64748b; margin-top: 2px; white-space: nowrap; }
.sos-hint { font-size: 11px; color: #94a3b8; margin-top: 8px; }
.sos-ok { color: #16a34a; margin-top: 12px; font-weight: 600; font-size: 13px; }
.update-time { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: #94a3b8; }
</style>
