<template>
  <div class="xiaoan-page">
    <div class="xa-header">
      <div class="xa-brand">
        <div :class="['xa-orb', voice.state.value]" @click="toggleVoice">
          <span class="xa-orb-icon">🎤</span>
          <span class="xa-orb-ring" v-if="voice.state.value==='listening'||voice.state.value==='speaking'" />
        </div>
        <div>
          <h2>小安 AI 语音助手</h2>
          <p>{{ statusLabel }}</p>
        </div>
      </div>
      <div class="xa-header-acts">
        <el-switch v-model="autoSpeak" active-text="自动播报" size="small" style="margin-right:8px" />
        <el-button size="small" @click="showKb=true">📚 知识库</el-button>
        <el-button size="small" @click="showHistory=true">📋 历史</el-button>
        <el-button size="small" @click="clearChat" :disabled="msgs.length===0">清空</el-button>
      </div>
    </div>

    <!-- Voice status panel -->
    <div class="xa-voice-panel">
      <div class="vp-row">
        <span class="vp-label">状态</span>
        <span :class="['vp-status', voice.state.value]">{{ statusLabel }}</span>
      </div>
      <div class="vp-acts">
        <el-button size="small" :type="voice.state.value==='listening'?'danger':''" @click="toggleVoice" :disabled="!voice.voiceOk.value && voice.state.value!=='disabled'">
          {{ voice.state.value === 'listening' ? '⏹ 关闭' : '🎤 开启语音' }}
        </el-button>
        <el-button size="small" @click="manualTalk">✍️ 手动说话</el-button>
        <el-button size="small" @click="speech.isSpeaking.value ? speech.stopSpeaking() : replayLast()">
          {{ speech.isSpeaking.value ? '⏹ 停止播报' : '📢 播报' }}
        </el-button>
      </div>
      <div v-if="voice.heardText.value" class="vp-log">
        <div class="vpl-item"><span class="vpl-label">🎧 听到</span>{{ voice.heardText.value }}</div>
        <div class="vpl-item"><span class="vpl-label">💬 回复</span>{{ voice.lastReply.value || '...' }}</div>
        <div class="vpl-item" v-if="voice.lastAction.value"><span class="vpl-label">⚡ 动作</span>{{ voice.lastAction.value }}</div>
        <div class="vpl-item" v-if="voice.nextSuggestion.value"><span class="vpl-label">💡 建议</span>{{ voice.nextSuggestion.value }}</div>
      </div>
      <div v-if="!voice.voiceOk.value && voice.state.value==='disabled'" class="vp-disabled">
        <p>⚠️ 当前环境不支持语音识别（非 HTTPS/localhost）。</p>
        <p>你仍可使用文字问答、手动说话和快捷操作。</p>
      </div>
      <div class="vp-wake-hint">唤醒词：<code>小安小安</code> · <code>小安</code> · <code>安防助手</code> · <code>智能管家</code></div>
    </div>

    <div class="xa-layout">
      <!-- Chat area -->
      <div class="xa-chat">
        <div class="xa-chat-body" ref="chatBody">
          <div v-if="!msgs.length" class="xa-welcome">
            <div :class="['xa-welcome-orb', voice.state.value]">
              <span>🎤</span>
            </div>
            <h3>你好，我是小安</h3>
            <p>点击麦克风或说「小安小安」唤醒我</p>
            <div class="xa-skills">
              <div v-for="s in skillCards" :key="s.q" class="skill-card" @click="sendMsg(s.q)">
                <span class="skill-icon">{{ s.icon }}</span><span>{{ s.label }}</span>
              </div>
            </div>
          </div>
          <div v-for="(m,i) in msgs" :key="i" :class="['xa-msg', m.role]">
            <div class="xa-bubble">{{ m.content }}</div>
            <div class="xa-meta"><span>{{ m.role==='user'?'你':'小安' }}</span><span class="xa-time">{{ m.time }}</span>
              <el-button v-if="m.role==='assistant'" link size="small" @click="speech.speakByBrowser(m.content)">🔊</el-button>
            </div>
          </div>
          <div v-if="typing" class="xa-msg bot"><div class="xa-bubble typing">小安思考中<span class="dots">...</span></div></div>
          <!-- Sensitive confirm -->
          <div v-if="pending" class="xa-msg bot"><div class="xa-bubble confirm-bubble">
            ⚠️ {{ pending.prompt }}
            <div class="confirm-btns"><el-button size="small" type="primary" @click="resolvePending(true)">确认执行</el-button><el-button size="small" @click="resolvePending(false)">取消</el-button></div>
          </div></div>
        </div>
        <div class="xa-input-row">
          <el-input v-model="input" placeholder="输入问题或说出指令..." @keydown.enter.exact.prevent="sendMsg()" :disabled="sending" class="xa-input" />
          <el-button type="primary" :icon="Promotion" @click="sendMsg()" :loading="sending" :disabled="!input.trim()" />
        </div>
      </div>

      <!-- Side -->
      <div class="xa-side">
        <div class="xa-panel"><h4>⚡ 快捷指令</h4>
          <div class="quick-list">
            <div v-for="q in quickList" :key="q" class="quick-item" @click="sendMsg(q)">{{ q }}</div>
          </div>
        </div>
        <div class="xa-panel"><h4>📊 系统概览</h4>
          <div class="sys-info">
            <div class="sys-row" @click="switchTo('device')"><span>设备</span><strong>{{ sys.devs }} 台</strong></div>
            <div class="sys-row" @click="switchTo('alarm')"><span>未处理告警</span><strong>{{ sys.alarms }} 条</strong></div>
            <div class="sys-row"><span>知识条目</span><strong>{{ sys.kb }} 条</strong></div>
          </div>
          <el-button size="small" @click="refreshSys" style="width:100%;margin-top:6px">刷新</el-button>
        </div>
        <div class="xa-panel"><h4>🤖 安防技能</h4>
          <div class="quick-list">
            <div class="quick-item" @click="switchTo('monitor')">📹 打开实时监测</div>
            <div class="quick-item" @click="switchTo('pir')">📡 打开 PIR</div>
            <div class="quick-item" @click="switchTo('alarm')">🚨 打开告警</div>
            <div class="quick-item" @click="switchTo('device')">📱 打开设备</div>
            <div class="quick-item" @click="switchTo('sos')">🆘 一键报警</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Knowledge Drawer -->
    <el-drawer v-model="showKb" title="📚 知识库管理" size="620px">
      <div style="padding:0 20px"><div style="display:flex;gap:10px;margin-bottom:14px"><el-input v-model="kbKw" placeholder="搜索..." clearable @clear="loadKb" @keyup.enter="loadKb" style="width:200px"/><el-button type="primary" @click="openKbAdd">新增</el-button><el-button @click="loadKb" :loading="kbLoading">刷新</el-button></div>
      <el-table :data="kbList" stripe size="small" v-loading="kbLoading" max-height="460"><el-table-column prop="id" label="ID" width="55"/><el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip/><el-table-column prop="category" label="分类" width="80"/><el-table-column label="内容" min-width="180" show-overflow-tooltip><template #default="{row}">{{(row.content||'').slice(0,50)}}</template></el-table-column>
      <el-table-column label="操作" width="110"><template #default="{row}"><el-button size="small" link @click="openKbEdit(row)">编辑</el-button><el-popconfirm title="确定删除？" @confirm="delKb(row.id!)"><template #reference><el-button size="small" type="danger" link>删除</el-button></template></el-popconfirm></template></el-table-column></el-table></div>
    </el-drawer>
    <el-dialog v-model="kbDlg" :title="kbEditId?'编辑':'新增'" width="480px"><el-form :model="kbForm" label-width="60px"><el-form-item label="标题"><el-input v-model="kbForm.title"/></el-form-item><el-form-item label="分类"><el-input v-model="kbForm.category"/></el-form-item><el-form-item label="内容"><el-input v-model="kbForm.content" type="textarea" :rows="5"/></el-form-item></el-form>
    <template #footer><el-button @click="kbDlg=false">取消</el-button><el-button type="primary" :loading="kbSaving" @click="saveKb">保存</el-button></template></el-dialog>

    <!-- History Drawer -->
    <el-drawer v-model="showHistory" title="📋 聊天历史" size="500px"><div style="padding:0 20px"><el-input v-model="histKw" placeholder="搜索..." clearable @clear="loadHistory" @keyup.enter="loadHistory" style="margin-bottom:12px"/><div v-loading="histLoading"><div v-if="histList.length" class="hist-list"><div v-for="h in histList" :key="h.id" class="hist-card" @click="restoreHistory(h)"><div class="hist-top"><span>{{h.userMessage||h.question||'-'}}</span><span class="hist-time">{{fmt(h.createTime)}}</span></div><div class="hist-bot">{{(h.aiReply||h.answer||'').slice(0,80)}}</div></div></div><div v-else style="text-align:center;padding:40px;color:#94a3b8">暂无聊天记录</div></div></div></el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { agentChat, getKnowledgeList, addKnowledge, updateKnowledge, deleteKnowledge, getChatRecords } from '@/api/humanSecurity'
import { getOverviewStats, getAlarms, armDevice, disarmDevice } from '@/api/humanSecurity'
import { useAuthStore } from '@/stores/auth'
import { useSecuritySpeech } from '@/composables/useSecuritySpeech'
import { useVoiceAssistant } from '@/composables/useVoiceAssistant'

const switchModule = inject<(key: string) => void>('switchModule', () => {})
const auth = useAuthStore()
const speech = useSecuritySpeech()
const voice = useVoiceAssistant()

const msgs = ref<{ role: string; content: string; time: string }[]>([])
const input = ref(''); const sending = ref(false); const typing = ref(false)
const chatBody = ref<HTMLElement>(); const autoSpeak = ref(true)
const pending = ref<{ prompt: string; type: string; deviceCode?: string } | null>(null)
const sys = ref({ devs: 0, alarms: 0, kb: 0 })

const statusLabel = computed(() => {
  const m: Record<string,string> = { idle:'就绪', listening:'🎧 正在聆听...', thinking:'🤔 思考中...', speaking:'🔊 播报中', confirming:'⚠️ 等待确认', disabled:'🔇 环境受限' }
  return m[voice.state.value] || '就绪'
})

const skillCards = [
  { icon:'🛡️', label:'当前安防状态', q:'我现在安全吗？' }, { icon:'🚶', label:'今日人体活动', q:'今天有没有人经过？' },
  { icon:'📡', label:'设备在线情况', q:'当前有哪些设备在线？' }, { icon:'🔔', label:'未处理告警', q:'有哪些待处理的告警？' },
  { icon:'💡', label:'安防建议', q:'给我一些安防建议。' }, { icon:'🛑', label:'布防状态', q:'当前布防状态是什么？' },
]
const quickList = ['当前有哪些设备在线？','今天发生了多少次告警？','最近一周的告警趋势如何？','给我一些安防建议。','当前布防状态是什么？','有哪些待处理的告警？','今天有没有人经过？']

onMounted(() => {
  voice.init()
  voice.onIntent(async (intent) => {
    voice.setState('thinking')
    if (intent.type === 'CONFIRM') { resolvePending(true); return }
    if (intent.type === 'CANCEL') { resolvePending(false); return }
    if (intent.type === 'WAKE_ONLY') {
      const r = '我在，请说出你的指令。'; msgs.value.push({ role:'assistant', content: r, time: now() }); voice.setReply(r); voice.setAction('等待指令'); voice.setSuggestion('试试说"打开实时监测"或"我现在安全吗"')
      voice.startContinuous()
      if (autoSpeak.value) speech.speakByBrowser(r)
      voice.setState('idle'); return
    }
    if (pending.value) { return }
    if (intent.sensitive) {
      // ARM/DISARM 没有指定设备 → 先询问设备编号
      if ((intent.type === 'ARM' || intent.type === 'DISARM') && !intent.deviceCode) {
        const r = '请告诉我要对哪台设备进行布防？例如说"对 device_001 进行布防"。'
        msgs.value.push({ role:'assistant', content: r, time: now() })
        voice.setReply(r); voice.setSuggestion('试试说"对 device_001 进行布防"')
        voice.setState('idle'); return
      }
      const devInfo = intent.deviceCode ? `设备 ${intent.deviceCode}` : ''
      const actionName = intent.type === 'ARM' ? '布防' : intent.type === 'DISARM' ? '撤防' : '执行操作'
      pending.value = { prompt: `确认对${devInfo}进行${actionName}？请说「确认执行」继续，或说「取消」。`, type: intent.type, deviceCode: intent.deviceCode }
      voice.setState('confirming'); const r = pending.value.prompt; msgs.value.push({ role:'assistant', content: r, time: now() }); voice.setReply(r); return
    }
    await executeIntent(intent)
  })
  if (!msgs.value.length) msgs.value.push({ role:'assistant', content: '你好！我是小安，你的智慧安防 AI 语音助手。\n\n点击 🎤 或说「小安小安」唤醒我。\n试试说：「我现在安全吗？」', time: now() })
  refreshSys()
})

function now() { return new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}) }
function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
function scrollDown() { nextTick(() => { if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight }) }
function switchTo(m: string) { switchModule(m) }

async function executeIntent(intent: any) {
  voice.heardText.value = intent.originalText || ''
  const t = intent.originalText || ''
  msgs.value.push({ role:'user', content: t, time: now() }); scrollDown()

  if (intent.type === 'SWITCH' && intent.module) {
    switchTo(intent.module); const r = `已为你打开${menuLabel(intent.module)}。`; addReply(r); voice.setAction(`切换到${menuLabel(intent.module)}`)
  } else if (intent.type === 'QUERY_PIR' || intent.type === 'QUERY_STATUS' || intent.type === 'QUERY_DEVICES' || intent.type === 'QUERY_ALARMS' || intent.type === 'QUERY_DEFENSE') {
    await doQuery(t, intent)
  } else {
    await doAiChat(t)
  }
}

async function doQuery(text: string, intent: any) {
  typing.value = true
  try {
    let answer = ''
    if (intent.type === 'QUERY_PIR' || text.includes('今天有没有人经过')) {
      const r = await agentChat(auth.user?.userId||1, `查询今天的PIR人体感应记录和统计数据`).catch(() => null)
      answer = (r?.data?.data as any)?.answer || 'PIR数据查询中，请稍后重试。'
    } else if (intent.type === 'QUERY_STATUS' || text.includes('安全')) {
      await refreshSys()
      answer = `当前共有 ${sys.value.devs} 台设备，未处理告警 ${sys.value.alarms} 条。建议查看首页总控台了解详细态势。`
    } else {
      const r = await agentChat(auth.user?.userId||1, text).catch(() => null)
      answer = (r?.data?.data as any)?.answer || (r?.data as any)?.message || '查询完成，但未能获取详细结果。'
    }
    addReply(answer); voice.setAction('系统查询完成'); voice.setSuggestion('试试问"打开实时监测"或"给我一些安防建议"')
    if (autoSpeak.value) speech.speakByBrowser(answer)
  } catch { addReply('查询失败，请稍后重试。') }
  finally { typing.value = false; voice.setState('idle') }
}

async function doAiChat(text: string) {
  typing.value = true
  try {
    const r = await agentChat(auth.user?.userId||1, text); const d: any = r.data?.data || r.data
    const answer = (d as any)?.answer || (d as any)?.message || '收到，请稍后。'
    addReply(answer); voice.setAction('AI 回复'); voice.setSuggestion('继续提问或说"打开实时监测"')
    if (autoSpeak.value) speech.speakByBrowser(answer)
  } catch { addReply('抱歉，AI 服务暂时不可用。') }
  finally { typing.value = false; voice.setState('idle') }
}

function addReply(content: string) { msgs.value.push({ role:'assistant', content, time: now() }); voice.setReply(content); scrollDown() }

async function sendMsg(msg?: string) {
  const text = (msg || input.value).trim(); if (!text) return; if (!msg) input.value = ''
  voice.heardText.value = text
  if (pending.value) { if (voice.confirmWords.some(w => text.includes(w))) resolvePending(true); else if (voice.cancelWords.some(w => text.includes(w))) resolvePending(false); else { msgs.value.push({ role:'user', content: text, time: now() }); doAiChat(text) } return }
  const intent = voice.parseIntent(text)
  if (intent.sensitive) {
    if ((intent.type === 'ARM' || intent.type === 'DISARM') && !intent.deviceCode) {
      const r = '请告诉我要对哪台设备进行布防？例如说"对 device_001 进行布防"。'
      msgs.value.push({ role:'user', content: text, time: now() }); msgs.value.push({ role:'assistant', content: r, time: now() }); voice.setReply(r); voice.setState('idle'); scrollDown(); return
    }
    const devInfo = intent.deviceCode ? `设备 ${intent.deviceCode}` : ''
    const actionName = intent.type === 'ARM' ? '布防' : intent.type === 'DISARM' ? '撤防' : '执行操作'
    pending.value = { prompt: `确认对${devInfo}进行${actionName}？请说「确认执行」继续，或说「取消」。`, type: intent.type, deviceCode: intent.deviceCode }
    voice.setState('confirming'); const r = pending.value.prompt; msgs.value.push({ role:'user', content: text, time: now() }); msgs.value.push({ role:'assistant', content: r, time: now() }); voice.setReply(r); scrollDown(); return
  }
  await executeIntent({...intent, originalText: text})
}

async function resolvePending(ok: boolean) {
  const p = pending.value; pending.value = null; voice.setState('idle')
  if (ok && p) {
    msgs.value.push({ role:'assistant', content: '操作已确认，正在执行...', time: now() }); voice.setReply('操作已确认执行'); voice.setAction(p.type)
    const targetDevice = p.deviceCode || 'device_001'

    if (p.type === 'ARM') {
      try {
        const res = await armDevice(targetDevice)
        if (res.data?.code === 200 || res.data?.code === 0) {
          addReply(`✅ 已向设备 ${targetDevice} 下发布防指令`)
          ElMessage.success('布防指令已下发')
        } else {
          addReply(`❌ 布防指令下发失败：${res.data?.message || '请稍后重试'}`)
        }
      } catch { addReply('❌ 布防指令下发失败，请检查设备连接') }
    } else if (p.type === 'DISARM') {
      try {
        const res = await disarmDevice(targetDevice)
        if (res.data?.code === 200 || res.data?.code === 0) {
          addReply(`✅ 已向设备 ${targetDevice} 下发撤防指令`)
          ElMessage.success('撤防指令已下发')
        } else {
          addReply(`❌ 撤防指令下发失败：${res.data?.message || '请稍后重试'}`)
        }
      } catch { addReply('❌ 撤防指令下发失败，请检查设备连接') }
    } else if (p.type === 'HELP') {
      addReply('🚨 紧急求助信号已发送，请保持冷静等待响应')
    } else {
      addReply('操作已确认执行。')
    }
  }
  else { msgs.value.push({ role:'assistant', content: '操作已取消。', time: now() }); voice.setReply('操作已取消') }
  voice.setSuggestion(''); scrollDown()
}

function toggleVoice() { if (voice.state.value === 'listening') voice.stopListening(); else voice.startListening() }
function manualTalk() { ElMessageBox.prompt('请输入要说的话','手动说话',{confirmButtonText:'发送'}).then(({value})=>{if(value?.trim())sendMsg(value.trim())}).catch(()=>{}) }
function replayLast() { const last = msgs.value.filter(m=>m.role==='assistant').pop(); if (last) speech.speakByBrowser(last.content) }
function clearChat() { ElMessageBox.confirm('确定清空？','确认',{type:'warning'}).then(()=>{msgs.value=[];msgs.value.push({role:'assistant',content:'对话已清空。',time:now()})}).catch(()=>{}) }
function menuLabel(m: string) { const map: Record<string,string> = { dashboard:'首页总控台',monitor:'实时监测',pir:'PIR监测',alarm:'告警管理',device:'设备管理',sos:'一键报警',ai:'小安AI助手',face:'实时监测' }; return map[m]||m }

async function refreshSys() {
  try { const [dr,ar,kr] = await Promise.all([getOverviewStats().catch(()=>null),getAlarms({page:1,pageSize:999}).catch(()=>null),getKnowledgeList().catch(()=>null)])
    sys.value.devs = (dr?.data?.data as any)?.onlineDeviceCount || 0
    const ad: any = ar?.data?.data; const al = Array.isArray(ad) ? ad : (ad?.records||[])
    sys.value.alarms = al.filter((a:any)=>a.handleStatus===0).length
    const kd: any = kr?.data?.data || kr?.data; sys.value.kb = Array.isArray(kd) ? kd.length : 0
  } catch {/* */}
}

// Knowledge + History (same as before)
const showKb=ref(false); const kbList=ref<any[]>([]); const kbLoading=ref(false); const kbKw=ref('')
const kbDlg=ref(false); const kbEditId=ref<number|null>(null); const kbSaving=ref(false)
const kbForm=ref({title:'',category:'',content:''})
const showHistory=ref(false); const histList=ref<any[]>([]); const histLoading=ref(false); const histKw=ref('')

async function loadKb(){kbLoading.value=true;try{const r=await getKnowledgeList(kbKw.value||undefined);const d:any=r.data?.data||r.data;kbList.value=Array.isArray(d)?d:(d?.records||d||[])}catch{kbList.value=[]}finally{kbLoading.value=false}}
function openKbAdd(){kbEditId.value=null;kbForm.value={title:'',category:'',content:''};kbDlg.value=true}
function openKbEdit(row:any){kbEditId.value=row.id;kbForm.value={title:row.title||'',category:row.category||'',content:row.content||''};kbDlg.value=true}
async function saveKb(){kbSaving.value=true;try{kbEditId.value?await updateKnowledge({id:kbEditId.value,title:kbForm.value.title,category:kbForm.value.category,content:kbForm.value.content}):await addKnowledge(kbForm.value);ElMessage.success('保存成功');kbDlg.value=false;loadKb();refreshSys()}catch{ElMessage.error('保存失败')}finally{kbSaving.value=false}}
async function delKb(id:number){try{await deleteKnowledge(id);ElMessage.success('已删除');loadKb();refreshSys()}catch{ElMessage.error('删除失败')}}
async function loadHistory(){histLoading.value=true;try{const r=await getChatRecords(histKw.value||undefined);const d:any=r.data?.data||r.data;histList.value=Array.isArray(d)?d:(d?.records||d||[])}catch{histList.value=[]}finally{histLoading.value=false}}
function restoreHistory(h:any){showHistory.value=false;msgs.value.push({role:'user',content:h.userMessage||h.question||'',time:fmt(h.createTime)},{role:'assistant',content:h.aiReply||h.answer||'',time:fmt(h.createTime)});scrollDown()}
</script>

<style scoped>
.xiaoan-page { width: 100%; height: calc(100vh - 120px); display: flex; flex-direction: column; }
.xa-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.xa-brand { display: flex; align-items: center; gap: 14px; }
.xa-orb { width: 48px; height: 48px; border-radius: 50%; background: #f1f5f9; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all .2s; position: relative; flex-shrink: 0; border: 2px solid #e2e8f0; }
.xa-orb:hover { border-color: #2563eb; }
.xa-orb.listening { background: #dc2626; border-color: #dc2626; animation: orb-pulse .8s infinite; }
.xa-orb.speaking { background: #2563eb; border-color: #2563eb; }
.xa-orb.disabled { background: #fef2f2; border-color: #fecaca; cursor: default; }
.xa-orb-icon { font-size: 20px; }
.xa-orb-ring { position: absolute; inset: -4px; border-radius: 50%; border: 2px solid #ef4444; animation: ring-expand 1s infinite; }
@keyframes orb-pulse { 0%,100% { transform: scale(1) } 50% { transform: scale(1.08) } }
@keyframes ring-expand { 0%,100% { transform: scale(1); opacity: 1 } 50% { transform: scale(1.2); opacity: .3 } }
.xa-brand h2 { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0; }
.xa-brand p { font-size: 12px; color: #64748b; margin: 2px 0 0; }
.xa-header-acts { display: flex; align-items: center; gap: 8px; }

.xa-voice-panel { background: #f8fafc; border-radius: 12px; padding: 12px 16px; margin-bottom: 12px; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; border: 1px solid #e2e8f0; }
.vp-label { font-size: 11px; color: #94a3b8; }
.vp-status { font-size: 13px; font-weight: 600; }
.vp-status.idle { color: #64748b; }
.vp-status.listening { color: #dc2626; }
.vp-status.thinking { color: #d97706; }
.vp-status.speaking { color: #2563eb; }
.vp-status.confirming { color: #d97706; }
.vp-status.disabled { color: #94a3b8; }
.vp-acts { display: flex; gap: 8px; flex-wrap: wrap; }
.vp-log { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 200px; font-size: 12px; }
.vpl-item { color: #475569; }
.vpl-label { font-weight: 600; margin-right: 6px; color: #0f172a; }
.vp-disabled { font-size: 12px; color: #92400e; background: #fffbeb; padding: 8px 12px; border-radius: 8px; flex: 1; }
.vp-disabled p { margin: 0; }
.vp-wake-hint { font-size: 11px; color: #94a3b8; }
.vp-wake-hint code { background: #e2e8f0; padding: 1px 6px; border-radius: 4px; font-size: 10px; }

.xa-layout { display: grid; grid-template-columns: minmax(0,1fr) 280px; gap: 16px; flex: 1; min-height: 0; }
.xa-chat { background: #fff; border-radius: 14px; box-shadow: 0 2px 12px rgba(0,0,0,.04); display: flex; flex-direction: column; overflow: hidden; }
.xa-chat-body { flex: 1; overflow-y: auto; padding: 14px 18px; display: flex; flex-direction: column; gap: 10px; }
.xa-welcome { text-align: center; padding: 16px; }
.xa-welcome-orb { width: 64px; height: 64px; border-radius: 50%; background: #f1f5f9; display: inline-flex; align-items: center; justify-content: center; font-size: 28px; margin-bottom: 10px; border: 2px solid #e2e8f0; }
.xa-welcome-orb.listening { background: #dc2626; animation: orb-pulse .8s infinite; }
.xa-welcome h3 { font-size: 18px; margin: 0 0 4px; }
.xa-welcome p { font-size: 13px; color: #64748b; margin: 0 0 16px; }
.xa-skills { display: grid; grid-template-columns: repeat(3,1fr); gap: 8px; max-width: 440px; margin: 0 auto; }
.skill-card { background: #f8fafc; border-radius: 10px; padding: 12px 8px; text-align: center; cursor: pointer; transition: all .15s; font-size: 11px; color: #475569; }
.skill-card:hover { background: #eff6ff; color: #2563eb; }
.skill-icon { display: block; font-size: 20px; margin-bottom: 3px; }

.xa-msg { display: flex; flex-direction: column; gap: 3px; }
.xa-msg.user { align-items: flex-end; }
.xa-msg.user .xa-bubble { background: #2563eb; color: #fff; border-radius: 14px 14px 4px 14px; }
.xa-msg.bot .xa-bubble { background: #f1f5f9; color: #334155; border-radius: 14px 14px 14px 4px; }
.xa-bubble { padding: 10px 14px; max-width: 82%; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.xa-bubble.typing { font-style: italic; color: #94a3b8; }
.confirm-bubble { background: #fffbeb !important; border: 1px solid #fde68a; }
.confirm-btns { margin-top: 8px; display: flex; gap: 8px; }
.xa-meta { font-size: 11px; color: #94a3b8; display: flex; gap: 6px; align-items: center; }
.xa-time { color: #cbd5e1; }
.dots { animation: dots 1.5s infinite; }
@keyframes dots { 0%,20%{opacity:0}50%{opacity:1}80%,100%{opacity:0} }

.xa-input-row { display: flex; gap: 8px; padding: 10px 14px; border-top: 1px solid #f1f5f9; }
.xa-input { flex: 1; }

.xa-side { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.xa-panel { background: #fff; border-radius: 14px; padding: 14px; box-shadow: 0 2px 8px rgba(0,0,0,.04); }
.xa-panel h4 { font-size: 13px; font-weight: 600; margin: 0 0 8px; }
.quick-list { display: flex; flex-direction: column; gap: 5px; }
.quick-item { padding: 7px 10px; background: #f8fafc; border-radius: 8px; font-size: 12px; color: #475569; cursor: pointer; }
.quick-item:hover { background: #eff6ff; color: #2563eb; }
.sys-info { display: flex; flex-direction: column; gap: 5px; }
.sys-row { display: flex; justify-content: space-between; padding: 6px 10px; background: #f8fafc; border-radius: 8px; font-size: 12px; cursor: pointer; }
.sys-row:hover { background: #eff6ff; }

.hist-list { display: flex; flex-direction: column; gap: 8px; }
.hist-card { background: #f8fafc; border-radius: 10px; padding: 10px 14px; cursor: pointer; }
.hist-card:hover { background: #eff6ff; }
.hist-top { display: flex; justify-content: space-between; font-size: 12px; color: #475569; margin-bottom: 4px; }
.hist-bot { font-size: 12px; color: #64748b; }
.hist-time { font-size: 10px; color: #94a3b8; }

@media (max-width: 1000px) { .xa-layout { grid-template-columns: 1fr; } .xa-side { max-height: 240px; } }
</style>
