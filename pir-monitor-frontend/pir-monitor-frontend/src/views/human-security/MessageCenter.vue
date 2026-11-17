<template>
  <div class="message-center">
    <!-- ===== Top: SOS Emergency Bar ===== -->
    <div class="mc-topbar">
      <div class="topbar-stats">
        <span class="stat-item">
          <el-icon><User /></el-icon>
          <span class="stat-label">紧急联系人</span>
          <strong>{{ emergencyContacts.length }}</strong>
        </span>
        <span v-if="activeSosCount > 0" class="stat-item stat-danger">
          <span class="pulse-dot"></span>
          <span>进行中求助</span>
          <strong>{{ activeSosCount }}</strong>
        </span>
        <span v-else class="stat-item stat-ok">
          <span class="green-dot"></span>
          <span>无进行中求助</span>
        </span>
      </div>
      <div class="sos-hold-area">
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
      </div>
    </div>

    <!-- ===== Three-Column Body ===== -->
    <div class="mc-body">
      <!-- Left: Contacts & Conversations -->
      <div class="mc-left">
        <div class="sidebar-tabs">
          <div :class="['tab', { active: activeTab === 'user' }]" @click="activeTab = 'user'">💬 用户</div>
          <div :class="['tab', { active: activeTab === 'emergency' }]" @click="activeTab = 'emergency'">
            紧急联系人
            <el-badge v-if="emergencyUnread" :value="emergencyUnread" class="tab-badge" />
          </div>
        </div>

        <div class="contact-list" v-loading="contactLoading">
          <!-- Emergency contacts -->
          <template v-if="activeTab === 'emergency'">
            <div class="list-header">
              <span>我的紧急联系人 ({{ emergencyContacts.length }})</span>
              <el-button size="small" type="primary" :icon="Plus" @click="showAddContact = true">添加</el-button>
            </div>
            <div v-if="emergencyContacts.length === 0 && !contactLoading" class="empty-mini">
              <el-empty description="暂无紧急联系人" :image-size="48" />
              <el-button size="small" type="primary" @click="showAddContact = true">添加紧急联系人</el-button>
            </div>
            <div v-for="c in emergencyContacts" :key="'ec-'+c.id"
              :class="['contact-item', { active: activeContact?.id === c.contactUserId && activeContact?.type === 'emergency' }]"
              @click="selectEmergencyContact(c)">
              <div class="avatar">{{ (c.contactNickname || c.contactUsername || '?')[0] }}</div>
              <div class="contact-info">
                <div class="contact-name">{{ c.contactNickname || c.contactUsername }}</div>
                <div class="contact-preview">@{{ c.contactUsername }}</div>
              </div>
              <el-popconfirm title="确定删除此紧急联系人？" @confirm="doDeleteContact(c.id)">
                <template #reference>
                  <el-button size="small" type="danger" link :icon="Delete" @click.stop />
                </template>
              </el-popconfirm>
            </div>
          </template>

          <!-- User conversations -->
          <template v-else>
            <div class="list-header">
              <span>消息会话</span>
              <el-button size="small" type="primary" :icon="Plus" @click="showSearchUser = true">新对话</el-button>
            </div>
            <div v-if="conversations.length === 0 && !contactLoading" class="empty-mini">
              <el-empty description="暂无消息会话" :image-size="48" />
              <el-button size="small" type="primary" @click="showSearchUser = true">开始新对话</el-button>
            </div>
            <div v-for="conv in conversations" :key="'conv-'+conv.contactId"
              :class="['contact-item', { active: activeContact?.id === conv.contactId && activeContact?.type === 'user' }]"
              @click="selectUserContact(conv)">
              <div class="avatar">{{ (conv.contactName || conv.contactUsername || '?')[0] }}</div>
              <div class="contact-info">
                <div class="contact-name-row">
                  <span class="contact-name">{{ conv.contactName || conv.contactUsername }}</span>
                  <el-badge v-if="conv.unreadCount" :value="conv.unreadCount" class="unread-badge" />
                </div>
                <div class="contact-preview">{{ conv.lastMessage || '暂无消息' }}</div>
              </div>
              <span class="contact-time">{{ fmtShort(conv.lastTime) }}</span>
            </div>
          </template>
        </div>
      </div>

      <!-- Center: Chat Area -->
      <div class="mc-center">
        <div class="chat-header" v-if="activeContact">
          <div class="chat-header-info">
            <div class="avatar">{{ (activeContact.name || activeContact.username || '?')[0] }}</div>
            <div>
              <div class="chat-contact-name">{{ activeContact.name || activeContact.username }}</div>
              <div class="chat-contact-sub">
                <template v-if="activeContact.type === 'emergency'">紧急联系人</template>
                <template v-else>@{{ activeContact.username }}</template>
              </div>
            </div>
          </div>
        </div>
        <div class="chat-header" v-else>
          <div class="no-contact-hint">选择联系人开始对话</div>
        </div>

        <div class="chat-messages" ref="msgContainerRef" v-if="activeContact">
          <div v-if="chatMessages.length === 0 && !msgLoading" class="chat-empty">
            <el-empty description="暂无消息记录" :image-size="48" />
            <p class="sub">发送消息开始对话</p>
          </div>
          <div v-for="msg in dedupedMessages" :key="msg.id"
            :class="['msg-bubble-row', msg.senderId === myUserId ? 'msg-mine' : 'msg-other']">
            <div :class="['msg-bubble', msg.senderId === myUserId ? 'bubble-mine' : 'bubble-other',
              (msg.isEmergency || msg.messageType === 'HELP' || msg.messageType === 'SOS') ? 'bubble-emergency' : '']">
              <div class="msg-meta" v-if="msg.senderId !== myUserId">{{ msg.senderName || msg.senderUsername }}</div>
              <div v-if="msg.messageType === 'HELP' || msg.messageType === 'SOS' || msg.isEmergency" class="emergency-tag">
                <el-icon><WarningFilled /></el-icon> 紧急求助
              </div>
              <div class="msg-text">{{ msg.content }}</div>
              <div class="msg-time-text">{{ fmt(msg.createTime) }}</div>
            </div>
          </div>
          <div v-if="msgLoading" class="chat-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </div>
        <div class="chat-messages chat-messages-empty" v-else>
          <div class="no-chat-placeholder">
            <el-icon :size="56" color="#cbd5e1"><ChatDotRound /></el-icon>
            <h3>消息中心</h3>
            <p>选择一个联系人查看对话，或添加紧急联系人</p>
          </div>
        </div>

        <div class="chat-input-area" v-if="activeContact">
          <el-input
            v-model="inputMsg"
            type="textarea"
            :rows="3"
            placeholder="输入消息... Shift+Enter 换行，Enter 发送"
            :disabled="sending"
            maxlength="500"
            show-word-limit
            resize="none"
            @keydown.enter.exact="sendMessage"
          />
          <el-button class="send-btn" type="primary" @click="sendMessage" :loading="sending" :disabled="!inputMsg.trim()">
            <el-icon><Promotion /></el-icon> 发送
          </el-button>
        </div>
      </div>

      <!-- Right: Emergency Help Panel -->
      <div class="mc-right" :class="{ focused: rightFocus }">
        <div class="right-scroll">
          <div class="right-header">
            <h3><el-icon><WarningFilled /></el-icon> 紧急求助</h3>
            <el-button :icon="RefreshRight" size="small" @click="loadHelpRecords" :loading="recordLoading">刷新</el-button>
          </div>

          <!-- SOS Quick Send -->
          <div class="quick-sos-card">
            <div class="sos-card-title">一键求助</div>
            <p class="sos-card-desc">发送紧急求助信号给所有紧急联系人</p>
            <el-button class="sos-card-btn" type="danger" :loading="sendingSOS" :disabled="emergencyContacts.length === 0" @click="confirmQuickHelp">
              {{ emergencyContacts.length === 0 ? '请先添加紧急联系人' : '发送紧急求助' }}
            </el-button>
            <div v-if="emergencyContacts.length === 0" class="no-contact-warn">
              你还没有添加紧急联系人，请先在左侧添加联系人。
            </div>
          </div>

          <!-- Help Form -->
          <div class="help-form-card">
            <h4 class="form-title">求助详情</h4>
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
              <el-form-item label="通知保安">
                <el-switch v-model="helpForm.notifySecurity" />
              </el-form-item>
              <el-form-item label="关联告警">
                <el-switch v-model="helpForm.relatePirAlert" />
              </el-form-item>
              <el-form-item>
                <el-button type="danger" :loading="sendingDetail" @click="sendDetailHelp" :disabled="emergencyContacts.length === 0" style="width: 100%">
                  发送求助 (通知 {{ emergencyContacts.length }} 位联系人)
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- Help History -->
          <div class="help-history-card">
            <h4 class="form-title">求助记录</h4>
            <div v-if="helpRecords.length === 0 && !recordLoading" class="no-records">
              <el-empty description="暂无求助记录" :image-size="40" />
            </div>
            <div v-else class="record-list">
              <div v-for="r in helpRecords" :key="r.id" class="record-item">
                <div class="record-header">
                  <el-tag :type="recordTypeTag(r)" size="small">{{ recordTypeLabel(r) }}</el-tag>
                  <el-tag :type="recordStatusTag(r.status)" size="small">{{ recordStatusLabel(r.status) }}</el-tag>
                </div>
                <div class="record-body">
                  <div v-if="r.deviceCode || r.linkedDeviceCode" class="record-row">
                    <span class="rl">设备</span>
                    <span class="rv">{{ r.linkedDeviceCode || r.deviceCode }}</span>
                  </div>
                  <div v-if="r.location" class="record-row">
                    <span class="rl">位置</span>
                    <span class="rv">{{ r.location }}</span>
                  </div>
                  <div v-if="r.notes || r.description" class="record-row">
                    <span class="rl">说明</span>
                    <span class="rv">{{ (r.notes || r.description || '').substring(0, 60) }}</span>
                  </div>
                </div>
                <div class="record-footer">
                  <span class="record-time">{{ fmt(r.createdAt || r.createTime) }}</span>
                  <span class="record-receivers">{{ r.receiverCount || 0 }} 位接收</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Dialogs ===== -->
    <!-- Add Contact Dialog -->
    <el-dialog v-model="showAddContact" title="添加紧急联系人" width="460px" :close-on-click-modal="false">
      <el-form @submit.prevent="doAddContact">
        <el-form-item label="用户名">
          <el-input v-model="searchUsername" placeholder="请输入对方用户名" :prefix-icon="Search"
            @input="onSearchInput" @keyup.enter="doAddContact" />
        </el-form-item>
        <div v-if="searchResults.length > 0" class="search-dropdown">
          <div v-for="u in searchResults" :key="u.id" class="search-item" @click="pickSearchResult(u)">
            <el-avatar :size="30">{{ (u.realName || u.username || '?')[0] }}</el-avatar>
            <div class="user-info">
              <div>{{ u.realName || u.username }}</div>
              <div class="sub">@{{ u.username }} · {{ u.role || '用户' }}</div>
            </div>
            <el-button size="small" type="primary">选择</el-button>
          </div>
        </div>
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
        <div class="dialog-actions">
          <el-button @click="showAddContact = false">取消</el-button>
          <el-button type="primary" @click="doAddContact" :loading="addingContact" :disabled="!searchUsername.trim()">
            确认添加
          </el-button>
        </div>
      </el-form>
      <div v-if="addResult" :class="['add-result', addResult.includes('成功') ? 'ok' : 'err']">{{ addResult }}</div>
    </el-dialog>

    <!-- Search User Dialog -->
    <el-dialog v-model="showSearchUser" title="搜索用户" width="420px" :close-on-click-modal="false">
      <el-input v-model="searchUsername2" placeholder="输入用户名搜索" :prefix-icon="Search" @keyup.enter="doSearchUser" />
      <div style="margin-top:12px">
        <div v-for="u in searchResults2" :key="u.id" class="search-user-item" @click="startChat(u)">
          <el-avatar :size="36">{{ (u.realName || u.username || '?')[0] }}</el-avatar>
          <div class="user-info">
            <div>{{ u.realName || u.username }}</div>
            <div class="sub">@{{ u.username }} · {{ u.role || '用户' }}</div>
          </div>
          <el-button size="small" type="primary">开始对话</el-button>
        </div>
        <div v-if="searched && searchResults2.length === 0" class="empty-mini">
          <el-empty description="未找到用户" :image-size="40" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, inject, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search, Loading, User, ChatDotRound, Promotion, WarningFilled, RefreshRight } from '@element-plus/icons-vue'
import http from '@/api/index'
import {
  getContactList, addEmergencyContact, deleteEmergencyContact,
  getConversation, getConversationList, sendNormalMessage, sendEmergencyHelp, searchUser
} from '@/api/messageCenter'
import { getEmergencyRecords } from '@/api/humanSecurity'
import { useAuthStore } from '@/stores/auth'
import { useSecuritySpeech } from '@/composables/useSecuritySpeech'

const authStore = useAuthStore()
const speech = useSecuritySpeech()

// ── Injected focus from ConsoleLayout ──
const messageFocus = inject<any>('messageFocus', ref('chat'))
const rightFocus = computed(() => messageFocus.value === 'help')

watch(rightFocus, (val) => {
  if (val) {
    loadHelpRecords()
    // Scroll right panel into view
    nextTick(() => {
      const right = document.querySelector('.mc-right') as HTMLElement
      if (right) right.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }
})

const myUserId = computed(() => {
  const uid = authStore.user?.id || authStore.user?.userId
  if (uid) return Number(uid)
  console.warn('[MessageCenter] 当前登录用户未解析，暂时使用开发用户 ID 1')
  return 1
})
const myUsername = computed(() => authStore.user?.username || '')

// ── Tabs ──
const activeTab = ref<'user' | 'emergency'>('emergency')

// ── Emergency Contacts ──
const emergencyContacts = ref<any[]>([])
const contactLoading = ref(false)
const showAddContact = ref(false)
const searchUsername = ref('')
const addingContact = ref(false)
const addResult = ref('')
const addRelation = ref('')
const emergencyUnread = ref(0)
let searchTimer: any = null
const searchResults = ref<any[]>([])

// ── Conversations ──
const conversations = ref<any[]>([])
const activeContact = ref<any>(null)
const chatMessages = ref<any[]>([])
const msgLoading = ref(false)
const inputMsg = ref('')
const sending = ref(false)
const msgContainerRef = ref<HTMLElement>()

// ── User Search ──
const showSearchUser = ref(false)
const searchUsername2 = ref('')
const searchResults2 = ref<any[]>([])
const searched = ref(false)

// ── Help Form ──
const sendingSOS = ref(false)
const sendingDetail = ref(false)
const recordLoading = ref(false)
const helpRecords = ref<any[]>([])
const helpForm = ref({
  helpType: 'SECURITY',
  deviceCode: 'human_001',
  location: '',
  description: '',
  notifySecurity: false,
  relatePirAlert: false
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

const activeSosCount = computed(() =>
  helpRecords.value.filter((r: any) =>
    r.status === 'SENT' || r.status === 'VIEWED' || r.status === 'RESPONDING'
  ).length
)

// ── Message dedup ──
const seenIds = new Set<number>()
const dedupedMessages = computed(() => {
  const result: any[] = []
  for (const m of chatMessages.value) {
    if (!seenIds.has(m.id)) {
      seenIds.add(m.id)
      result.push(m)
    }
  }
  return result
})

// ── Helpers ──
function extractData(res: any): any[] {
  if (Array.isArray(res?.data)) return res.data
  if (Array.isArray(res?.data?.data)) return res.data.data
  if (Array.isArray(res?.data?.list)) return res.data.list
  if (Array.isArray(res?.data?.records)) return res.data.records
  return []
}
function isSuccess(res: any): boolean { return res?.data?.code === 200 || res?.data?.success === true }
function getResMsg(res: any): string { return res?.data?.message || res?.data?.msg || '' }

// ── Data Loading ──
async function loadEmergencyContacts() {
  try {
    const res = await getContactList(myUserId.value)
    emergencyContacts.value = extractData(res)
  } catch (e) {
    console.error('[MessageCenter] 联系人加载失败:', e)
    emergencyContacts.value = []
  }
}

async function loadConversations() {
  try {
    const res = await getConversationList(myUserId.value)
    const raw = extractData(res)
    const convMap = new Map<string, any>()
    for (const msg of raw) {
      const isMe = msg.senderId === myUserId.value || msg.sender_id === myUserId.value
      const contactId = isMe ? (msg.receiverId || msg.receiver_id) : (msg.senderId || msg.sender_id)
      const contactName = isMe ? (msg.receiverName || msg.receiver_name) : (msg.senderName || msg.sender_name)
      const contactUsername = isMe
        ? (msg.receiverUsername || msg.receiver_username || msg.receiverName || msg.receiver_name || '')
        : (msg.senderUsername || msg.sender_username || msg.senderName || msg.sender_name || '')
      if (!convMap.has(String(contactId))) {
        convMap.set(String(contactId), {
          contactId, contactName: contactName || contactUsername,
          contactUsername: contactUsername || contactName,
          lastMessage: (msg.content || '').substring(0, 40),
          lastTime: msg.createTime || msg.create_time,
          unreadCount: 0
        })
      }
    }
    for (const ec of emergencyContacts.value) {
      if (!convMap.has(String(ec.contactUserId || ec.contact_user_id))) {
        const cuid = ec.contactUserId || ec.contact_user_id
        convMap.set(String(cuid), {
          contactId: cuid,
          contactName: ec.contactNickname || ec.contact_nickname || ec.contactUsername || ec.contact_username,
          contactUsername: ec.contactUsername || ec.contact_username,
          lastMessage: '紧急联系人',
          lastTime: ec.createdAt || ec.created_at,
          unreadCount: 0
        })
      }
    }
    conversations.value = Array.from(convMap.values()).sort((a, b) =>
      new Date(b.lastTime || 0).getTime() - new Date(a.lastTime || 0).getTime()
    )
  } catch (e) {
    console.error('[MessageCenter] 会话加载失败:', e)
    conversations.value = []
  }
}

async function loadMessages(targetUserId: number) {
  if (!targetUserId) return
  msgLoading.value = true; seenIds.clear()
  try {
    const res = await getConversation(myUserId.value, targetUserId)
    chatMessages.value = extractData(res)
  } catch (e) {
    console.error('[MessageCenter] 聊天记录加载失败:', e)
    chatMessages.value = []
  } finally { msgLoading.value = false; scrollToBottom() }
}

async function loadHelpRecords() {
  recordLoading.value = true
  try {
    const res = await getEmergencyRecords({ userId: myUserId.value })
    helpRecords.value = extractData(res)
  } catch (e) {
    console.error('[MessageCenter] 求助记录加载失败:', e)
    helpRecords.value = []
  } finally { recordLoading.value = false }
}

// ── Contact Selection ──
function selectEmergencyContact(c: any) {
  activeContact.value = {
    id: c.contactUserId || c.contact_user_id,
    name: c.contactNickname || c.contact_nickname || c.contactUsername || c.contact_username,
    username: c.contactUsername || c.contact_username,
    type: 'emergency'
  }
  loadMessages(activeContact.value.id)
}

function selectUserContact(conv: any) {
  activeContact.value = {
    id: conv.contactId,
    name: conv.contactName || conv.contactUsername,
    username: conv.contactUsername || conv.contactName,
    type: 'user'
  }
  loadMessages(conv.contactId)
}

// ── Send Message ──
async function sendMessage() {
  const text = inputMsg.value.trim()
  if (!text || !activeContact.value || sending.value) return
  sending.value = true
  try {
    const targetUsername = activeContact.value.username || ''
    const res = await sendNormalMessage({
      senderId: myUserId.value,
      receiverUsername: targetUsername,
      content: text
    })
    if (isSuccess(res)) {
      inputMsg.value = ''
      await loadMessages(activeContact.value.id)
      await loadConversations()
    } else {
      ElMessage.error(getResMsg(res) || '消息发送失败，请稍后重试')
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || ''
    console.error('[MessageCenter] 发送失败:', msg)
    ElMessage.error('消息发送失败，请稍后重试')
  } finally { sending.value = false }
}

// ── Help Actions ──
async function confirmQuickHelp() {
  try {
    await ElMessageBox.confirm('确定发送紧急求助给所有紧急联系人吗？', '确认发送', {
      confirmButtonText: '确定发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }

  sendingSOS.value = true
  try {
    const res = await sendEmergencyHelp(myUserId.value)
    if (isSuccess(res)) {
      ElMessage.success('紧急求助已发送')
      speech.speakText('求助信息已发送给所有紧急联系人，请保持冷静。')
      await Promise.all([loadEmergencyContacts(), loadConversations(), loadHelpRecords()])
      if (activeContact.value) await loadMessages(activeContact.value.id)
    } else {
      ElMessage.warning(getResMsg(res) || '求助发送失败')
    }
  } catch { ElMessage.error('紧急求助发送失败，请检查联系人和后端状态') }
  finally { sendingSOS.value = false }
}

async function sendDetailHelp() {
  if (emergencyContacts.value.length === 0) {
    ElMessage.warning('你还没有添加紧急联系人，请先添加联系人。')
    return
  }
  try {
    await ElMessageBox.confirm('确定发送紧急求助吗？所有紧急联系人将收到通知。', '确认发送', {
      confirmButtonText: '确认发送', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }

  sendingDetail.value = true
  try {
    const res = await http.post('/messages/help', {
      userId: myUserId.value,
      helpType: helpForm.value.helpType,
      deviceCode: helpForm.value.deviceCode,
      location: helpForm.value.location,
      description: helpForm.value.description,
      notifySecurity: helpForm.value.notifySecurity,
      relatePirAlert: helpForm.value.relatePirAlert
    })
    if (res.data?.code === 200 || res.data?.success) {
      ElMessage.success('紧急求助已发送')
      helpForm.value.description = ''
      await Promise.all([loadHelpRecords(), loadConversations()])
      if (activeContact.value) await loadMessages(activeContact.value.id)
    } else {
      ElMessage.error(res.data?.message || '求助发送失败')
    }
  } catch { ElMessage.error('紧急求助发送失败，请检查联系人和后端状态') }
  finally { sendingDetail.value = false }
}

// ── SOS Hold Button ──
function startHold() {
  if (isFired.value || sendingSOS.value) return
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

// ── Add Contact ──
function onSearchInput() {
  clearTimeout(searchTimer)
  const q = searchUsername.value.trim()
  if (q.length < 2) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchUser(q)
      const all = extractData(res)
      searchResults.value = all.filter((u: any) => u.id !== myUserId.value)
    } catch { searchResults.value = [] }
  }, 350)
}

function pickSearchResult(u: any) {
  searchUsername.value = u.username
  searchResults.value = []
}

function friendlyContactError(msg: string): string {
  if (!msg) return '服务器暂时不可用，请稍后重试'
  if (msg.includes('不存在')) return '用户不存在'
  if (msg.includes('自己')) return '不能添加自己为紧急联系人'
  if (msg.includes('已存在') || msg.includes('已是')) return '该联系人已存在'
  return msg
}

async function doAddContact() {
  const uname = searchUsername.value.trim()
  if (!uname) { addResult.value = '请输入联系人用户名'; return }
  if (uname === myUsername.value) { addResult.value = '不能添加自己为紧急联系人'; return }
  addingContact.value = true; addResult.value = ''
  try {
    const res = await addEmergencyContact({
      userId: myUserId.value,
      currentUserId: myUserId.value,
      contactUsername: uname,
      username: uname,
      relation: addRelation.value
    })
    if (isSuccess(res)) {
      addResult.value = '✅ 添加成功！'
      searchUsername.value = ''; addRelation.value = ''; searchResults.value = []
      await loadEmergencyContacts(); await loadConversations()
      setTimeout(() => { showAddContact.value = false; addResult.value = '' }, 1500)
    } else {
      addResult.value = friendlyContactError(getResMsg(res))
    }
  } catch (e: any) {
    const backendMsg = e?.response?.data?.message || e?.message || ''
    console.error('[MessageCenter] 添加联系人失败:', backendMsg)
    addResult.value = friendlyContactError(backendMsg)
  } finally { addingContact.value = false }
}

async function doDeleteContact(contactId: number) {
  try {
    const res = await deleteEmergencyContact(contactId, myUserId.value)
    if (isSuccess(res)) {
      ElMessage.success('已删除')
      await loadEmergencyContacts(); await loadConversations()
      if (activeContact.value?.type === 'emergency' && activeContact.value?.id === contactId) {
        activeContact.value = null; chatMessages.value = []
      }
    } else {
      ElMessage.error(getResMsg(res) || '删除失败')
    }
  } catch { ElMessage.error('删除失败，请检查后端服务') }
}

async function doSearchUser() {
  const q = searchUsername2.value.trim()
  if (!q) return
  searched.value = true
  try {
    const res = await searchUser(q)
    searchResults2.value = extractData(res).filter((u: any) => u.id !== myUserId.value)
  } catch { searchResults2.value = [] }
}

function startChat(user: any) {
  activeContact.value = { id: user.id, name: user.realName || user.username, username: user.username, type: 'user' }
  showSearchUser.value = false; searchUsername2.value = ''; searchResults2.value = []; searched.value = false
  loadMessages(user.id); loadConversations()
}

// ── Record Helpers ──
function recordTypeTag(r: any): string {
  const t = r.eventType || r.helpType || r.type || ''
  const map: Record<string, string> = { SECURITY: 'danger', FIRE: 'danger', SUDDEN_ILLNESS: 'warning', FALL_INJURY: 'warning' }
  return map[t] || 'info'
}
function recordTypeLabel(r: any): string {
  const t = r.eventType || r.helpType || r.type || ''
  const map: Record<string, string> = { SECURITY: '安全异常', SUDDEN_ILLNESS: '突发疾病', FIRE: '火灾危险', FALL_INJURY: '跌倒受伤', OTHER: '其他' }
  return map[t] || map[t.toLowerCase()] || t || '紧急求助'
}
function recordStatusTag(s: string): string {
  const map: Record<string, string> = { SENT: 'info', VIEWED: '', RESPONDING: 'warning', FINISHED: 'success', CLOSED: 'success', FALSE_ALARM: 'warning', CANCELLED: 'info', FAILED: 'danger' }
  return map[s] || 'info'
}
function recordStatusLabel(s: string): string {
  const map: Record<string, string> = { SENT: '已发送', VIEWED: '已查看', RESPONDING: '响应中', FINISHED: '已完成', CLOSED: '已完成', FALSE_ALARM: '误报', CANCELLED: '已取消', FAILED: '发送失败', DELIVERED: '已送达', READ: '已读', ACKNOWLEDGED: '已响应' }
  return map[s] || s || '未知'
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgContainerRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
function fmtShort(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleString('zh-CN').slice(5, 16)
}

// ── Polling ──
let pollTimer: any = null
const isAtBottom = ref(true)

function startPolling() {
  pollTimer = setInterval(async () => {
    if (activeContact.value) {
      const el = msgContainerRef.value
      isAtBottom.value = el ? el.scrollHeight - el.scrollTop - el.clientHeight < 60 : true
      await loadMessages(activeContact.value.id)
      if (isAtBottom.value) scrollToBottom()
    }
    await loadConversations()
  }, 4000)
}

// ── Lifecycle ──
onMounted(async () => {
  contactLoading.value = true
  await Promise.all([loadEmergencyContacts(), loadConversations(), loadHelpRecords()])
  contactLoading.value = false
  startPolling()

  // Pause polling when hidden
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { if (pollTimer) clearInterval(pollTimer) }
    else { startPolling() }
  })
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (animId) cancelAnimationFrame(animId)
})
</script>

<style scoped>
.message-center {
  height: calc(100vh - 180px); min-height: 560px;
  display: flex; flex-direction: column; overflow: hidden;
  background: #fff; border-radius: 14px; box-shadow: 0 2px 12px rgba(0,0,0,.04);
}

/* ===== Top Bar ===== */
.mc-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px; border-bottom: 1px solid #f1f5f9;
  background: #fafbfc; flex-shrink: 0;
}
.topbar-stats { display: flex; gap: 20px; }
.stat-item { display: flex; align-items: center; gap: 5px; font-size: 13px; color: #64748b; }
.stat-item strong { color: #0f172a; }
.stat-danger strong { color: #ef4444; }
.stat-ok strong { color: #16a34a; }
.pulse-dot { width: 8px; height: 8px; border-radius: 50%; background: #ef4444; animation: pulse 1.5s infinite; }
.green-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.3} }

/* SOS Hold Button */
.sos-hold-area { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.sos-hold-btn {
  position: relative; width: 80px; height: 80px; cursor: pointer;
  user-select: none; -webkit-user-select: none;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
}
.sos-hold-btn.holding { cursor: grabbing; }
.sos-hold-btn.fired .sos-btn-content .sos-icon { color: #ef4444; }
.sos-ring { position: absolute; top: 0; left: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-track { fill: none; stroke: #e2e8f0; stroke-width: 5; }
.ring-fill {
  fill: none; stroke: #3b82f6; stroke-width: 5; stroke-linecap: round;
  stroke-dasharray: 276.46; transition: none;
}
.ring-fill.danger { stroke: #ef4444; }
.sos-btn-content { position: relative; display: flex; flex-direction: column; align-items: center; z-index: 1; }
.sos-icon { font-size: 20px; font-weight: 900; color: #ef4444; }
.sos-label { font-size: 10px; color: #64748b; margin-top: 1px; white-space: nowrap; }
.sos-hint { font-size: 11px; color: #94a3b8; }

/* ===== Body ===== */
.mc-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }

/* ===== Left Column ===== */
.mc-left {
  width: 300px; border-right: 1px solid #f1f5f9;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-tabs { display: flex; border-bottom: 1px solid #f1f5f9; }
.tab {
  flex: 1; text-align: center; padding: 12px; font-size: 14px; font-weight: 600;
  cursor: pointer; color: #64748b; border-bottom: 2px solid transparent; transition: all .15s;
  position: relative;
}
.tab:hover { color: #334155; background: #f8fafc; }
.tab.active { color: #2563eb; border-bottom-color: #2563eb; }
.tab-badge { position: absolute; top: 6px; right: 12px; }
.contact-list { flex: 1; overflow-y: auto; }
.list-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; font-size: 12px; color: #64748b; border-bottom: 1px solid #f8fafc;
}
.contact-item {
  display: flex; align-items: center; gap: 10px; padding: 12px 14px;
  cursor: pointer; transition: background .12s; border-bottom: 1px solid #fafbfc;
}
.contact-item:hover { background: #f8fafc; }
.contact-item.active { background: #eff6ff; }
.avatar {
  width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; flex-shrink: 0;
}
.contact-info { flex: 1; min-width: 0; }
.contact-name { font-size: 14px; font-weight: 600; color: #0f172a; }
.contact-name-row { display: flex; align-items: center; gap: 6px; }
.contact-preview { font-size: 12px; color: #94a3b8; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.contact-time { font-size: 11px; color: #cbd5e1; flex-shrink: 0; }
.unread-badge { flex-shrink: 0; }
.empty-mini { text-align: center; padding: 30px 16px; color: #94a3b8; font-size: 13px; }

/* ===== Center Column ===== */
.mc-center { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px; border-bottom: 1px solid #f1f5f9; min-height: 62px;
}
.chat-header-info { display: flex; align-items: center; gap: 10px; }
.chat-contact-name { font-size: 15px; font-weight: 700; color: #0f172a; }
.chat-contact-sub { font-size: 12px; color: #94a3b8; }
.no-contact-hint { color: #94a3b8; font-size: 14px; text-align: center; width: 100%; }

.chat-messages {
  flex: 1; overflow-y: auto; padding: 16px 20px; background: #fafbfc;
}
.chat-messages-empty {
  flex: 1; display: flex; align-items: center; justify-content: center; background: #fafbfc;
}
.no-chat-placeholder { text-align: center; color: #cbd5e1; }
.no-chat-placeholder h3 { font-size: 18px; color: #64748b; margin: 8px 0 6px; }
.no-chat-placeholder p { font-size: 13px; color: #94a3b8; margin: 0; }
.chat-empty { text-align: center; padding: 60px 20px; }
.chat-empty p { color: #94a3b8; margin: 0; }
.chat-empty .sub { font-size: 12px; color: #cbd5e1; margin-top: 4px; }
.chat-loading { text-align: center; padding: 20px; color: #94a3b8; font-size: 13px; }

/* Messages */
.msg-bubble-row { display: flex; margin-bottom: 14px; }
.msg-mine { justify-content: flex-end; }
.msg-other { justify-content: flex-start; }
.msg-bubble { max-width: 65%; padding: 10px 14px; border-radius: 14px; position: relative; }
.bubble-mine { background: linear-gradient(135deg, #3b82f6, #2563eb); color: #fff; border-bottom-right-radius: 4px; }
.bubble-other { background: #fff; color: #0f172a; border-bottom-left-radius: 4px; box-shadow: 0 2px 6px rgba(0,0,0,.04); }
.bubble-emergency { background: linear-gradient(135deg, #ef4444, #dc2626) !important; color: #fff !important; border: 2px solid #fca5a5; animation: emergency-pulse 2s infinite; }
@keyframes emergency-pulse { 0%,100%{box-shadow: 0 0 4px rgba(239,68,68,.4)} 50%{box-shadow: 0 0 16px rgba(239,68,68,.7)} }
.emergency-tag { font-size: 11px; font-weight: 700; margin-bottom: 4px; opacity: .9; display: flex; align-items: center; gap: 3px; }
.msg-meta { font-size: 11px; color: #94a3b8; margin-bottom: 3px; }
.msg-text { font-size: 14px; line-height: 1.5; word-break: break-word; }
.msg-time-text { font-size: 10px; opacity: .6; margin-top: 4px; text-align: right; }

/* Input */
.chat-input-area { padding: 12px 20px; border-top: 1px solid #f1f5f9; background: #fff; display: flex; gap: 8px; align-items: flex-end; }
.send-btn { flex-shrink: 0; height: 72px; min-width: 80px; }

/* ===== Right Column ===== */
.mc-right {
  width: 320px; border-left: 1px solid #f1f5f9; flex-shrink: 0;
  display: flex; flex-direction: column; overflow: hidden;
}
.mc-right.focused { box-shadow: inset 0 0 0 2px #3b82f6; }
.right-scroll { flex: 1; overflow-y: auto; padding: 16px; }
.right-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.right-header h3 { font-size: 15px; font-weight: 700; color: #0f172a; margin: 0; display: flex; align-items: center; gap: 6px; }

.quick-sos-card {
  background: linear-gradient(135deg, #fef2f2, #fee2e2); border: 1px solid #fca5a5;
  border-radius: 12px; padding: 16px; text-align: center; margin-bottom: 16px;
}
.sos-card-title { font-size: 16px; font-weight: 700; color: #991b1b; }
.sos-card-desc { font-size: 12px; color: #b91c1c; margin: 6px 0 12px; }
.sos-card-btn { width: 100%; }
.no-contact-warn { font-size: 11px; color: #991b1b; margin-top: 8px; }

.help-form-card { background: #fff; border: 1px solid #f1f5f9; border-radius: 10px; padding: 14px; margin-bottom: 16px; }
.form-title { font-size: 13px; font-weight: 600; color: #334155; margin: 0 0 10px; }

.help-history-card { background: #fff; border: 1px solid #f1f5f9; border-radius: 10px; padding: 14px; }
.no-records { padding: 20px 0; }
.record-list { max-height: 300px; overflow-y: auto; }
.record-item { border-bottom: 1px solid #f5f5f5; padding: 10px 0; }
.record-item:last-child { border-bottom: none; }
.record-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.record-body { font-size: 12px; }
.record-row { display: flex; gap: 6px; padding: 1px 0; }
.rl { color: #94a3b8; flex-shrink: 0; }
.rv { color: #334155; }
.record-footer { display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8; margin-top: 4px; }

/* Dialogs */
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.add-result { margin-top: 10px; padding: 8px 12px; border-radius: 8px; font-size: 13px; }
.add-result.ok { background: #f0fdf4; color: #166534; }
.add-result.err { background: #fef2f2; color: #991b1b; }
.search-dropdown { max-height: 180px; overflow-y: auto; }
.search-item {
  display: flex; align-items: center; gap: 8px; padding: 8px;
  border: 1px solid #f1f5f9; border-radius: 8px; margin-bottom: 4px; cursor: pointer;
}
.search-item:hover { background: #f8fafc; }
.user-info { flex: 1; }
.user-info .sub { font-size: 11px; color: #94a3b8; }
.search-user-item {
  display: flex; align-items: center; gap: 10px; padding: 10px;
  border: 1px solid #f1f5f9; border-radius: 10px; margin-bottom: 6px;
  cursor: pointer; transition: background .12s;
}
.search-user-item:hover { background: #f8fafc; }

/* Responsive */
@media (max-width: 1400px) { .mc-left { width: 260px; } .mc-right { width: 280px; } }
@media (max-width: 1200px) {
  .mc-body { flex-wrap: wrap; }
  .mc-left { width: 240px; }
  .mc-right { width: 100%; max-height: 360px; border-left: none; border-top: 1px solid #f1f5f9; }
}
@media (max-width: 768px) {
  .message-center { height: auto; min-height: 100vh; }
  .mc-body { flex-direction: column; }
  .mc-left { width: 100%; max-height: 200px; border-right: none; border-bottom: 1px solid #f1f5f9; }
  .mc-center { min-height: 400px; }
  .mc-right { width: 100%; }
}
</style>
