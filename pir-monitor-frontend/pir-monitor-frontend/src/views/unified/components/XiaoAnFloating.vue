<template>
  <div v-if="!panelOpen" :class="['xa-orb', vs]" @click="openPanel" title="小安 AI 助手">
    <span class="xa-orb-icon">🎤</span><span :class="['xa-dot', vs]" /><span class="xa-orb-ring" v-if="vs==='listening'||vs==='speaking'" />
  </div>
  <transition name="xa-slide"><div v-if="panelOpen" class="xa-drawer">
    <div class="xa-hd"><div class="xa-hd-left"><div :class="['xa-avatar', vs]"><span>🤖</span></div><div><div class="xa-hd-title">小安 AI 助手</div><div class="xa-hd-state">{{ statusLabel }}</div></div></div>
    <div class="xa-hd-right"><el-switch v-model="sp.autoSpeakEnabled.value" size="small" active-text="播报" /><el-dropdown trigger="click"><el-button size="small" link>⋯</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="showHistory=true">📋 历史记录</el-dropdown-item><el-dropdown-item @click="showKb=true">📚 知识库管理</el-dropdown-item><el-dropdown-item @click="showDebug=!showDebug">🔧 {{showDebug?'隐藏':'显示'}}调试</el-dropdown-item><el-dropdown-item divided @click="clearMsgs">🗑 清空对话</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button size="small" link @click="panelOpen=false">✕</el-button></div></div>
    <div class="xa-strip" v-if="heardText||lastReply"><div class="xa-strip-row" v-if="heardText"><span class="xas-icon">🎧</span><span class="xas-text">{{ heardText }}</span></div><div class="xa-strip-row" v-if="lastReply"><span class="xas-icon">💬</span><span class="xas-text">{{ lastReply }}</span></div><div class="xa-strip-row" v-if="lastAction"><span class="xas-icon">⚡</span><span class="xas-text">{{ lastAction}}</span></div></div>
    <div class="xa-ctrl"><el-button :type="voiceEnabled?'danger':''" size="small" @click="toggleVoice" round>{{ voiceEnabled?'⏹ 关闭':'🎤 开启语音' }}</el-button><el-button size="small" round @click="manualTalk">✍️ 手动</el-button><el-button size="small" round @click="sp.stopSpeaking()" :disabled="!sp.isSpeaking.value">⏹ 停止</el-button><el-button size="small" round @click="testMic" type="primary" plain>🎙 麦克风测试</el-button><el-button size="small" round @click="sp.testSpeak()" type="warning" plain>🔊 测试播报</el-button><span class="xa-ctrl-hint">{{ statusHint }}</span></div>
    <div class="xa-quick"><span v-for="s in quickActs" :key="s" class="xa-quick-tag" @click="handleCmd(s)">{{ s }}</span></div>
    <div class="xa-body" ref="msgBody"><div v-for="(m,i) in msgs" :key="i" :class="['xa-msg', m.role]"><div class="xa-bubble">{{ m.content }}</div></div><div v-if="typing" class="xa-msg bot"><div class="xa-bubble typing"><span class="xa-dots"><i>.</i><i>.</i><i>.</i></span></div></div><div v-if="pending" class="xa-msg bot"><div class="xa-bubble cfm">⚠️ {{ pending.prompt }}<div class="cfm-btns"><el-button size="small" type="primary" @click="resolvePending(true)">确认</el-button><el-button size="small" @click="resolvePending(false)">取消</el-button></div></div></div></div>
    <div class="xa-inp"><el-input v-model="input" placeholder="输入或说「小安小安」..." size="small" @keydown.enter.exact.prevent="handleCmd(input||'')" class="xa-inp-field" /><el-button size="small" type="primary" @click="handleCmd(input||'')" :disabled="!input.trim()" round>发送</el-button></div>
    <div class="xa-debug-toggle" @click="showDebug=!showDebug">🔧 调试</div><div class="xa-debug" v-if="showDebug"><small>
  🔤 原始: {{ lastRaw||'无' }}<br/>
  ✅ 最终: {{ lastFinalText||'无' }}<br/>
  🔄 标准化: {{ pipelineDebug?.normalized||'无' }}<br/>
  🔧 纠错后: {{ pipelineDebug?.corrected||'无' }}<br/>
  🎯 Intent: {{ pipelineDebug?.action||'无' }} ({{ pipelineDebug?.score||0 }}% {{ pipelineDebug?.confidence||'' }})<br/>
  📋 命中别名: {{ pipelineDebug?.matchedAlias||'无' }}<br/>
  🤖 AI兜底: {{ pipelineDebug?.aiFallback?'是':'否' }} | 确认中: {{ pipelineDebug?.needConfirm?'是':'否' }}<br/>
  🔊 播报:{{ sp.isSpeaking.value?'是':'否' }} | 📡 {{ vs }} | SR:{{ isRecognitionRunning?'开':'关' }} | TTS暂停:{{ ttsPlaying?'是':'否' }}</small></div>
  </div></transition>
  <div v-if="panelOpen" class="xa-overlay" @click="panelOpen=false" />
  <el-drawer v-model="showKb" title="📚 知识库" size="560px"><div style="padding:0 18px"><div style="display:flex;gap:10px;margin-bottom:12px"><el-input v-model="kbKw" placeholder="搜索..." size="small" clearable @clear="loadKb" @keyup.enter="loadKb" style="width:180px"/><el-button size="small" type="primary" @click="openKbAdd">新增</el-button><el-button size="small" @click="loadKb" :loading="kbLoading">刷新</el-button></div><el-table :data="kbList" stripe size="small" v-loading="kbLoading" max-height="420"><el-table-column prop="id" label="ID" width="50"/><el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip/><el-table-column prop="category" label="分类" width="70"/><el-table-column label="内容" min-width="160" show-overflow-tooltip><template #default="{r}">{{(r.content||'').slice(0,50)}}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{r}"><el-button size="small" link @click="editKb(r)">编辑</el-button><el-popconfirm title="删除？" @confirm="delKb(r.id!)"><template #reference><el-button size="small" type="danger" link>删除</el-button></template></el-popconfirm></template></el-table-column></el-table></div></el-drawer>
  <el-dialog v-model="kbDlg" :title="kbEditId?'编辑':'新增'" width="440px"><el-form :model="kbForm" label-width="50px"><el-form-item label="标题"><el-input v-model="kbForm.title" size="small"/></el-form-item><el-form-item label="分类"><el-input v-model="kbForm.category" size="small"/></el-form-item><el-form-item label="内容"><el-input v-model="kbForm.content" type="textarea" :rows="4" size="small"/></el-form-item></el-form><template #footer><el-button size="small" @click="kbDlg=false">取消</el-button><el-button size="small" type="primary" :loading="kbSaving" @click="saveKb">保存</el-button></template></el-dialog>
  <el-drawer v-model="showHistory" title="📋 聊天记录" size="460px"><div style="padding:0 18px"><el-input v-model="histKw" placeholder="搜索..." size="small" clearable @clear="loadHistory" @keyup.enter="loadHistory" style="margin-bottom:10px"/><div v-if="histList.length" class="hist-list"><div v-for="h in histList" :key="h.id" class="hist-card" @click="restoreHistory(h)"><div class="hist-top"><span>{{h.userMessage||h.question||'-'}}</span><span class="hist-time">{{fmt(h.createTime)}}</span></div><div class="hist-bot">{{(h.aiReply||h.answer||'').slice(0,80)}}</div></div></div><div v-else style="text-align:center;padding:30px;color:#94a3b8">暂无聊天记录</div></div></el-drawer>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeList, addKnowledge, updateKnowledge, deleteKnowledge, getChatRecords } from '@/api/humanSecurity'
import { useSecuritySpeech } from '@/composables/useSecuritySpeech'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import { createAssistant } from '@/assistant/assistantCore'
import { getLastPipelineDebug } from '@/assistant/intentEngine'
import { runSpeechPipeline } from '@/assistant/speechFuzzy'
import type { AssistantResult } from '@/assistant/types'

const sp = useSecuritySpeech()
const voiceStore = useVoiceActionStore()
const assistant = createAssistant()

const panelOpen = ref(false); const msgs = ref<{role:string;content:string}[]>([]); const input = ref('')
const typing = ref(false); const autoSpeak = sp.autoSpeakEnabled; const pending = ref<{prompt:string;type:string}|null>(null)
const showDebug = ref(false); const welcomeShown = ref(false)

// Speech debug
const ssSupported = ref(false)
const ssVoiceCount = ref(0)
const ssZhVoice = ref('')
const lastSpeakError = ref('')
const pipelineDebug = ref<any>(null)

const vs = ref('idle'); const voiceEnabled = ref(false); const convActive = ref(false)
const heardText = ref(''); const lastReply = ref(''); const lastAction = ref(''); const lastRaw = ref('')
const currentInterimText = ref(''); const lastFinalText = ref('')
let recognition: any = null; let convTimer: any = null; let restartTimer: any = null
const isRecognitionRunning = ref(false); let manualStop = false
let ttsPlaying = false  // TTS 播报中，阻止 recognition.onend 自动重启
const micTestMode = ref(false) // true=测试模式(只显示不执行), false=正式语音助手
const debugVoice = ref(false)  // true=打印 recognition onstart/onend 详细日志

const wakeWords = ['小安小安','小安','安防助手','智能管家','小安助手','小安同学','安防小安']
const confirmWords = ['确认','确认执行','确定','执行吧']; const cancelWords = ['取消','算了','不用了']
const quickActs = ['我现在安全吗？','打开实时监测','打开PIR','查看告警','设备状态','发送求助']

const statusLabel = computed(()=>({idle:'未启动',waiting:'等待唤醒',listening:'正在聆听',thinking:'思考中',speaking:'播报中',confirming:'等待确认',limited:'环境受限'}[vs.value]||'就绪'))
const statusHint = computed(()=>{if(micTestMode.value)return'🎙 测试模式：仅显示识别文字，不执行命令';if(!voiceEnabled.value)return'点击「🎤开启语音」';if(vs.value==='waiting')return'说「小安小安」唤醒（或直接说命令）';if(vs.value==='listening')return'我在听，请说出指令';if(vs.value==='speaking')return'🔊 播报中，可说「停止」打断';return''})

function norm(t: string) { return (t||'').replace(/\s+/g,'').replace(/[，,。.!！?？；;：:、]/g,'').replace(/^[额嗯啊呃]+/,'').trim() }

// === DEDUP: prevent Chrome firing onresult twice for same utterance ===
let lastFinalNormalized = ''; let lastFinalAt = 0
function isDupFinal(t: string): boolean {
  const n = norm(t); if (!n) return true
  const now = Date.now()
  if (n === lastFinalNormalized && now - lastFinalAt < 2500) return true
  lastFinalNormalized = n; lastFinalAt = now
  return false
}

// === WAKE-ONLY COOLDOWN: prevent double "我在" in 3s ===
let lastWakeOnlyAt = 0
function isWakeCooling(): boolean {
  const now = Date.now()
  if (now - lastWakeOnlyAt < 3000) return true
  lastWakeOnlyAt = now
  return false
}

// === SELF-SPEECH SHIELD: ignore TTS being re-recognized ===
let ignoreSelfUntil = 0; let lastSpokenSnapshot = ''
const SELF_SHIELD_MS = 12000  // 延长到 12s，覆盖大多数 TTS 播报时长
function markSelfSpeechStart(spokenText: string) {
  ignoreSelfUntil = Date.now() + SELF_SHIELD_MS
  lastSpokenSnapshot = norm(spokenText).slice(0, 80)
}
function isSelfSpeech(text: string): boolean {
  if (Date.now() > ignoreSelfUntil) return false
  const n = norm(text); if (!n || n.length < 2) return n.length < 2 // 极短片段在 shield 期间也忽略
  // 精确包含匹配
  if (lastSpokenSnapshot.includes(n)) return true
  // 反向：识别的文本包含播报的关键词（如播报"已为你打开实时监测"，识别到"打开实时监测"）
  if (n.length >= 3) {
    // 提取播报文本中最长的有意义的词
    const keywords = lastSpokenSnapshot.replace(/已为你|已|好的|，|。/g, ' ').split(/\s+/).filter(k => k.length >= 3)
    for (const kw of keywords) {
      if (n.includes(kw)) return true
    }
  }
  if (n.includes('我在') && n.includes('请说出')) return true
  return false
}

// === BOT MSG DEDUP: same content within 3s not re-added ===
let lastBotMsg = ''; let lastBotMsgAt = 0
function addBotOnce(content: string) {
  const c = (content||'').trim(); if (!c) return
  const now = Date.now()
  if (c === lastBotMsg && now - lastBotMsgAt < 3000) return
  lastBotMsg = c; lastBotMsgAt = now
  msgs.value.push({ role:'bot', content: c }); lastReply.value = c
}
function addUserMsg(text: string) {
  const t = (text||'').trim(); if (!t) return
  msgs.value.push({ role:'user', content: t })
}
function hasWake(t: string) { const n = norm(t); return wakeWords.find(w => n.includes(w)) }
function rmWake(t: string) { let r = norm(t); wakeWords.forEach(w => r = r.replaceAll(w,'')); return r.trim() }

function openPanel() { if (!welcomeShown.value) { addBotOnce('你好！我是小安。点击🎤开启语音，或直接输入指令。'); welcomeShown.value = true } panelOpen.value = true }
function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
function scroll() { nextTick(()=>{const e=document.querySelector('.xa-body');if(e)e.scrollTop=e.scrollHeight}) }
function isSecure() { const p=location.protocol;const h=location.hostname;return p==='https:'||h==='localhost'||h==='127.0.0.1'||h==='[::1]' }

// === VOICE ENGINE ===
function initRec() {
  const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  ssSupported.value = !!SR
  if (!SR) { vs.value = 'limited'; console.warn('[小安语音] SpeechRecognition 不支持'); return }
  recognition = new SR(); recognition.lang = 'zh-CN'; recognition.continuous = true; recognition.interimResults = true; recognition.maxAlternatives = 1

  recognition.onstart = () => {
    isRecognitionRunning.value = true
    if (debugVoice.value) console.debug('[小安语音] ✅ onstart')
  }

  recognition.onresult = (e: any) => {
    if (debugVoice.value) console.debug('[小安语音] onresult, resultIndex=' + e.resultIndex + ', results.length=' + e.results.length)
    let finalText = ''; let interimText = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript || ''
      if (e.results[i].isFinal) finalText += t; else interimText += t
    }
    if (interimText) { currentInterimText.value = interimText; heardText.value = interimText; if (debugVoice.value) console.debug('[小安语音] interim:', interimText) }
    if (!finalText) return
    heardText.value = finalText; lastFinalText.value = finalText; lastRaw.value = finalText
    console.log('[小安语音] ✅ final:', finalText)
    handleVoiceFinal(finalText.trim())
  }

  recognition.onerror = (e: any) => {
    lastSpeakError.value = e.error || 'unknown'
    console.error('[小安语音] ❌ onerror:', e.error, e.message)
    if (e.error === 'no-speech') return
    if (e.error === 'not-allowed') { vs.value = 'idle'; voiceEnabled.value = false; ElMessage.warning('麦克风权限被拒绝，请在浏览器地址栏中允许麦克风权限。') }
    else if (e.error === 'audio-capture') { vs.value = 'error'; ElMessage.warning('未检测到麦克风，请检查设备连接。') }
    else if (e.error === 'network') { console.warn('[小安语音] 网络异常，将尝试恢复') }
    else { console.warn('[小安语音识别异常]', e.error) }
  }

  recognition.onend = () => {
    isRecognitionRunning.value = false
    if (debugVoice.value) console.debug('[小安语音] onend — voiceEnabled=' + voiceEnabled.value + ' ttsPlaying=' + ttsPlaying)
    if (voiceEnabled.value && !manualStop && !ttsPlaying) {
      if (restartTimer) clearTimeout(restartTimer)
      restartTimer = window.setTimeout(() => { safeStartRecognition() }, 500)
    }
  }
}

function safeStartRecognition() {
  if (!recognition) { console.warn('[小安语音] recognition 不存在'); return }
  if (isRecognitionRunning.value) { console.log('[小安语音] recognition 已在运行，跳过'); return }
  try {
    recognition.start()
    console.log('[小安语音] ✅ recognition.start() 已调用')
  } catch (e: any) {
    console.warn('[小安语音] start 失败:', e.name, e.message)
    if (e.name === 'InvalidStateError') { isRecognitionRunning.value = true; console.log('[小安语音] 已在上次运行中，标记为运行中') }
  }
}

// === TTS 播报期间暂停/恢复识别，防止听到自己声音误触发 ===
function pauseRecognitionForTts() {
  ttsPlaying = true
  if (recognition && isRecognitionRunning.value) {
    try { recognition.stop() } catch { /* already stopped */ }
    isRecognitionRunning.value = false
    if (debugVoice.value) console.debug('[小安语音] 🔇 识别已暂停（TTS 播报中）')
  }
}
function resumeRecognitionAfterTts() {
  ttsPlaying = false
  if (voiceEnabled.value && !manualStop) {
    setTimeout(() => {
      safeStartRecognition()
      if (debugVoice.value) console.debug('[小安语音] 🎤 识别已恢复（TTS 播报结束）')
    }, 400)
  }
}

// === SINGLE ENTRY for all final voice results (v3: robust) ===
function handleVoiceFinal(text: string) {
  console.log('[小安语音] ===== handleVoiceFinal =====')
  console.log('[小安语音] raw:', text)
  console.log('[小安语音] vs:', vs.value)
  console.log('[小安语音] convActive:', convActive.value)
  console.log('[小安语音] voiceEnabled:', voiceEnabled.value)
  console.log('[小安语音] micTestMode:', micTestMode.value)

  if (!text || isDupFinal(text)) { console.log('[小安语音] ❌ drop: empty/dup'); return }
  lastRaw.value = text
  if (isSelfSpeech(text)) { console.log('[小安语音] ❌ drop: self-speech'); return }

  // ── 打断播报 ──
  if (sp.isSpeaking.value) {
    sp.stopSpeaking()
    console.log('[小安语音] 🔇 interrupted TTS')
    if (text.length < 2) { vs.value = voiceEnabled.value ? (convActive.value ? 'listening' : 'waiting') : 'idle'; return }
  }

  // ── 麦克风测试模式：只显示，不执行 ──
  if (micTestMode.value) {
    console.log('[小安语音] 🎙 TEST MODE — display only, no exec')
    addBotOnce('🎙 [测试] 识别到：' + text)
    return
  }

  // ── 待确认操作 ──
  if (pending.value) {
    const c = norm(text)
    if (confirmWords.some(w => c.includes(w))) { console.log('[小安语音] ✅ confirmed'); resolvePending(true); return }
    if (cancelWords.some(w => c.includes(w))) { console.log('[小安语音] ✅ cancelled'); resolvePending(false); return }
  }

  // ── 退出对话 ──
  if (/退出|结束对话|不用了/.test(text)) {
    addBotOnce('好的，已回到等待唤醒。'); endConv(); return
  }

  // ── 唤醒词检测 ──
  const wakeWord = hasWake(text)
  const hasWakeWord = Boolean(wakeWord)
  const cmd = hasWakeWord ? rmWake(text) : text

  console.log('[小安语音] wakeWord:', wakeWord || null, 'hasWake:', hasWakeWord, 'cmd:', cmd || '(空)')

  // 纯唤醒词（无命令）
  if (hasWakeWord && !cmd) {
    if (isWakeCooling()) { console.log('[小安语音] ❌ wake cooldown'); return }
    console.log('[小安语音] ✅ WAKE-ONLY')
    heardText.value = '小安'
    addBotOnce('我在，请说出你的指令。')
    markSelfSpeechStart('我在，请说出你的指令。')
    vs.value = 'listening'; startConv()
    if (autoSpeak.value) sp.speakText('我在，请说出你的指令。')
    return
  }

  // ── 有命令文本（可能带或不带唤醒词） ──
  if (cmd) {
    // waiting 状态 + 无唤醒词 → 用 fuzzy 匹配判断是否为已知命令
    if (vs.value === 'waiting' && !hasWakeWord) {
      try {
        const fuzzy = runSpeechPipeline(norm(cmd))
        console.log('[小安语音] fuzzy result:', fuzzy.fuzzy.action, 'score:', fuzzy.fuzzy.score, 'confidence:', fuzzy.confidence)
        if (fuzzy.confidence === 'HIGH' && fuzzy.fuzzy.intent !== 'AI_CHAT') {
          console.log('[小安语音] ✅ HIGH confidence cmd without wake → EXECUTING')
          addBotOnce('好的。')
          startConv()
          handleCmd(cmd)
          return
        }
      } catch (e) {
        console.error('[小安语音] fuzzy match error:', e)
      }
      console.log('[小安语音] ❌ waiting + no wake + low confidence → drop')
      return
    }

    // listening / convActive / wake+cmd → 直接执行
    console.log('[小安语音] ✅ EXECUTING:', cmd)
    if (vs.value === 'waiting') startConv()
    else resetConv()
    handleCmd(cmd)
    return
  }

  console.log('[小安语音] ❌ drop: no cmd extracted, vs=' + vs.value)
}

// === MIC PERMISSION ===
async function requestMicPermission(): Promise<boolean> {
  if (!navigator.mediaDevices?.getUserMedia) { ElMessage.warning('当前浏览器不支持麦克风访问'); return false }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(t => t.stop())
    console.log('[小安语音] ✅ 麦克风权限已获取')
    return true
  } catch (e: any) {
    console.error('[小安语音] ❌ 麦克风权限失败:', e.name, e.message)
    ElMessage.warning('无法访问麦克风，请在浏览器地址栏允许麦克风权限。')
    return false
  }
}

async function testMic() {
  ElMessage.info('正在请求麦克风权限...')
  const ok = await requestMicPermission()
  if (!ok) return
  if (!recognition) initRec()
  if (!recognition) { ElMessage.warning('SpeechRecognition 不可用'); return }
  micTestMode.value = true; manualStop = false; voiceEnabled.value = true
  ElMessage.success('🎙 麦克风测试模式 — 仅显示识别文字，不执行命令。说"测试测试"。')
  safeStartRecognition()
}

function toggleVoice() {
  if (voiceEnabled.value) { stopVoice(); return }
  startVoice()
}
async function startVoice() {
  if (!isSecure()) { vs.value = 'limited'; ElMessage.warning('当前环境不支持语音识别（需要 HTTPS 或 localhost）'); return }
  const micOk = await requestMicPermission()
  if (!micOk) return
  if (!recognition) initRec()
  if (!recognition) { vs.value = 'limited'; ElMessage.warning('SpeechRecognition 不可用'); return }
  micTestMode.value = false; manualStop = false; voiceEnabled.value = true
  sp.unlockSpeechSynthesis()
  safeStartRecognition()
  vs.value = 'waiting'
  ElMessage.success('🎤 语音助手已开启 — 说"小安"唤醒，或直接说出命令。')
}
function stopVoice() {
  manualStop = true; voiceEnabled.value = false; micTestMode.value = false; endConv()
  clearTimeout(restartTimer)
  if (recognition) try { recognition.stop() } catch {/* */}
  isRecognitionRunning.value = false; vs.value = 'idle'; sp.stopSpeaking()
}
function startConv() { convActive.value = true; resetConv() }
function resetConv() { clearTimeout(convTimer); convTimer = setTimeout(endConv, 30000) }
function endConv() { convActive.value = false; clearTimeout(convTimer); if (voiceEnabled.value && vs.value!=='confirming') vs.value = 'waiting' }

// === COMMANDS (powered by Assistant Core) ===
function handleCmd(text: string) { if (!text.trim()) return; input.value = ''; processWithAssistant(text.trim()) }

async function processWithAssistant(text: string) {
  if (!text?.trim()) return
  // 注意：不要在这里调用 isDupFinal，因为 handleVoiceFinal 已经调过了，
  // 再调用会导致同一次语音输入被 isDupFinal 误判为重复而跳过执行！
  console.log('[小安链路] ========== processWithAssistant ==========')
  console.log('[小安链路] text:', text)
  vs.value = 'thinking'; heardText.value = text
  addUserMsg(text); scroll()

  typing.value = true
  pipelineDebug.value = getLastPipelineDebug()
  console.log('[小安链路] pipeline:', JSON.stringify(pipelineDebug.value))

  try {
    console.log('[小安链路] 调用 assistant.handleText...')
    const result: AssistantResult = await assistant.handleText(text)
    console.log('[小安链路] result:', JSON.stringify({ success: result.success, message: result.message?.slice(0, 100), needConfirm: result.needConfirm, speak: result.speak }))

    if (result.needConfirm && result.action) {
      pending.value = { prompt: result.message, type: result.action.type, _ref: result.action }
      vs.value = 'confirming'
      addBotOnce(result.message)
      return
    }

    if (result.data?.action) {
      voiceStore.dispatch(result.data.action)
    }

    const reply = result.message || ''
    addBotOnce(reply)
    lastAction.value = reply ? '已完成' : ''
    scroll()

    vs.value = voiceEnabled.value ? (convActive.value ? 'listening' : 'waiting') : 'idle'
    if (autoSpeak.value && reply && result.speak !== false) {
      markSelfSpeechStart(reply)
      // 播报前暂停识别，防止麦克风听到 TTS 声音后误触发
      pauseRecognitionForTts()
      sp.speakText(reply).finally(() => {
        resumeRecognitionAfterTts()
      })
    }
  } catch (e: any) {
    const fallback = '抱歉，我暂时无法处理这个请求。'
    addBotOnce(fallback)
    vs.value = voiceEnabled.value ? (convActive.value ? 'listening' : 'waiting') : 'idle'
  } finally {
    typing.value = false
  }
}

function resolvePending(ok: boolean) {
  const p = pending.value
  addBotOnce(ok ? '操作已确认。' : '已取消。')
  pending.value = null
  assistant.context.pendingAction = undefined
  if (ok && p && (p as any)._ref) {
    (p as any)._ref.resolve(true)
  }
  vs.value = voiceEnabled.value ? (convActive.value ? 'listening' : 'waiting') : 'idle'
}
function manualTalk() { ElMessageBox.prompt('输入要说的话', '手动说话', { confirmButtonText: '发送' }).then(({ value }) => { if (value?.trim()) handleCmd(value.trim()) }).catch(() => { }) }
function clearMsgs() { ElMessageBox.confirm('确定清空？', '确认', { type: 'warning' }).then(() => { msgs.value = [] }).catch(() => { }) }

// KB/History (same)
const showKb=ref(false);const kbList=ref<any[]>([]);const kbLoading=ref(false);const kbKw=ref('')
const kbDlg=ref(false);const kbEditId=ref<number|null>(null);const kbSaving=ref(false);const kbForm=ref({title:'',category:'',content:''})
async function loadKb(){kbLoading.value=true;try{const r=await getKnowledgeList(kbKw.value||undefined);const d:any=r.data?.data||r.data;kbList.value=Array.isArray(d)?d:(d?.records||d||[])}catch{kbList.value=[]}finally{kbLoading.value=false}}
function openKbAdd(){kbEditId.value=null;kbForm.value={title:'',category:'',content:''};kbDlg.value=true}
function editKb(row:any){kbEditId.value=row.id;kbForm.value={title:row.title||'',category:row.category||'',content:row.content||''};kbDlg.value=true}
async function saveKb(){kbSaving.value=true;try{kbEditId.value?await updateKnowledge({id:kbEditId.value,title:kbForm.value.title,category:kbForm.value.category,content:kbForm.value.content}):await addKnowledge(kbForm.value);ElMessage.success('已保存');kbDlg.value=false;loadKb()}catch{ElMessage.error('失败')}finally{kbSaving.value=false}}
async function delKb(id:number){try{await deleteKnowledge(id);ElMessage.success('已删除');loadKb()}catch{ElMessage.error('失败')}}
const showHistory=ref(false);const histList=ref<any[]>([]);const histLoading=ref(false);const histKw=ref('')
async function loadHistory(){histLoading.value=true;try{const r=await getChatRecords(histKw.value||undefined);const d:any=r.data?.data||r.data;histList.value=Array.isArray(d)?d:(d?.records||d||[])}catch{histList.value=[]}finally{histLoading.value=false}}
function restoreHistory(h:any){showHistory.value=false;msgs.value.push({role:'user',content:h.userMessage||h.question||''},{role:'bot',content:h.aiReply||h.answer||''});scroll()}

onMounted(() => {
  if (isSecure()) initRec()
  // 初始化语音调试信息
  ssSupported.value = !!window.speechSynthesis
  if (window.speechSynthesis) {
    const voices = window.speechSynthesis.getVoices()
    ssVoiceCount.value = voices.length
    const zh = voices.find((v: any) => v.lang === 'zh-CN') || voices.find((v: any) => v.lang?.startsWith('zh'))
    ssZhVoice.value = zh ? (zh.name + ' (' + zh.lang + ')') : ''
    window.speechSynthesis.onvoiceschanged = () => {
      const v2 = window.speechSynthesis.getVoices()
      ssVoiceCount.value = v2.length
      const zh2 = v2.find((v: any) => v.lang === 'zh-CN') || v2.find((v: any) => v.lang?.startsWith('zh'))
      ssZhVoice.value = zh2 ? (zh2.name + ' (' + zh2.lang + ')') : ''
    }
  }
  window.addEventListener('error', (e) => {
    if (e.message?.includes('speech') || e.message?.includes('audio')) {
      lastSpeakError.value = e.message.slice(0, 60)
    }
  })
})
onUnmounted(() => { stopVoice(); clearTimeout(restartTimer); clearTimeout(convTimer) })
</script>

<style scoped>
.xa-orb{position:fixed;left:24px;bottom:24px;width:52px;height:52px;border-radius:50%;background:linear-gradient(135deg,#2563eb,#1d4ed8);color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;z-index:2000;box-shadow:0 4px 24px rgba(37,99,235,.4);transition:transform .2s;animation:xa-breathe 3s infinite}
.xa-orb:hover{transform:scale(1.12)}
.xa-orb-icon{font-size:22px}
.xa-dot{position:absolute;top:6px;right:6px;width:10px;height:10px;border-radius:50%;border:2px solid #fff;background:#22c55e}
.xa-dot.waiting{background:#3b82f6}.xa-dot.listening{background:#fbbf24}.xa-dot.speaking{background:#a78bfa}.xa-dot.limited{background:#ef4444}
.xa-orb-ring{position:absolute;inset:-4px;border-radius:50%;border:2px solid rgba(255,255,255,.35);animation:xa-ring 1s infinite}
@keyframes xa-breathe{0%,100%{box-shadow:0 4px 24px rgba(37,99,235,.4)}50%{box-shadow:0 4px 36px rgba(37,99,235,.55)}}
@keyframes xa-ring{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.25);opacity:.3}}
.xa-overlay{position:fixed;inset:0;z-index:1999}
.xa-drawer{position:fixed;right:24px;top:76px;bottom:24px;width:420px;max-width:calc(100vw - 48px);background:rgba(255,255,255,.97);backdrop-filter:blur(16px);border:1px solid rgba(226,232,240,.85);border-radius:20px;box-shadow:0 24px 80px rgba(15,23,42,.15);z-index:2000;display:flex;flex-direction:column;overflow:hidden}
.xa-slide-enter-active,.xa-slide-leave-active{transition:all .25s ease}
.xa-slide-enter-from,.xa-slide-leave-to{opacity:0;transform:translateX(30px)}
.xa-hd{display:flex;justify-content:space-between;align-items:center;padding:14px 18px;border-bottom:1px solid #f1f5f9;flex-shrink:0}
.xa-hd-left{display:flex;align-items:center;gap:12px}.xa-avatar{width:38px;height:38px;border-radius:50%;background:linear-gradient(135deg,#2563eb,#1d4ed8);display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}
.xa-avatar.waiting{animation:xa-breathe 2s infinite}.xa-avatar.listening{animation:xa-breathe .8s infinite}
.xa-hd-title{font-size:15px;font-weight:700;color:#0f172a}.xa-hd-state{font-size:11px;color:#64748b}.xa-hd-right{display:flex;align-items:center;gap:4px}
.xa-strip{padding:8px 18px;background:linear-gradient(135deg,#f8fafc,#f1f5f9);border-bottom:1px solid #e2e8f0;flex-shrink:0}
.xa-strip-row{display:flex;gap:6px;font-size:11px;color:#475569;line-height:1.4;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.xas-icon{flex-shrink:0;font-weight:600}.xas-text{overflow:hidden;text-overflow:ellipsis}
.xa-ctrl{display:flex;align-items:center;gap:6px;padding:8px 18px;flex-shrink:0;flex-wrap:wrap}
.xa-ctrl-hint{font-size:11px;color:#94a3b8;margin-left:auto}
.xa-quick{display:flex;gap:6px;padding:0 18px 8px;flex-shrink:0;flex-wrap:wrap}
.xa-quick-tag{font-size:11px;padding:4px 12px;border-radius:14px;background:#f1f5f9;color:#475569;cursor:pointer;white-space:nowrap;transition:all .12s}
.xa-quick-tag:hover{background:#dbeafe;color:#2563eb}
.xa-body{flex:1;overflow-y:auto;padding:12px 18px;display:flex;flex-direction:column;gap:10px}
.xa-msg{display:flex}.xa-msg.user{justify-content:flex-end}
.xa-msg.user .xa-bubble{background:linear-gradient(135deg,#2563eb,#1d4ed8);color:#fff;border-radius:16px 16px 4px 16px}
.xa-msg.bot .xa-bubble{background:#f8fafc;color:#334155;border-radius:16px 16px 16px 4px;border:1px solid #f1f5f9}
.xa-bubble{padding:10px 14px;max-width:84%;font-size:13px;line-height:1.55;word-break:break-word;white-space:pre-wrap}
.xa-bubble.typing{color:#94a3b8}.xa-bubble.cfm{background:#fffbeb;border-color:#fde68a}
.cfm-btns{margin-top:8px;display:flex;gap:6px}
.xa-dots i{animation:xa-blink 1.4s infinite;font-style:normal}.xa-dots i:nth-child(2){animation-delay:.2s}.xa-dots i:nth-child(3){animation-delay:.4s}
@keyframes xa-blink{0%,80%,100%{opacity:.2}40%{opacity:1}}
.xa-inp{display:flex;gap:8px;padding:10px 18px;border-top:1px solid #f1f5f9;flex-shrink:0}
.xa-inp-field{flex:1}
.xa-debug-toggle{position:absolute;bottom:60px;right:20px;font-size:12px;cursor:pointer;opacity:.3;z-index:1}
.xa-debug-toggle:hover{opacity:.7}
.xa-debug{padding:4px 18px;font-size:10px;color:#94a3b8;border-top:1px solid #f1f5f9;flex-shrink:0}
.hist-list{display:flex;flex-direction:column;gap:6px}.hist-card{background:#f8fafc;border-radius:8px;padding:8px 12px;cursor:pointer}.hist-card:hover{background:#eff6ff}.hist-top{display:flex;justify-content:space-between;font-size:11px;color:#475569;margin-bottom:2px}.hist-bot{font-size:11px;color:#64748b}.hist-time{color:#94a3b8}
@media(max-width:768px){.xa-drawer{left:12px;right:12px;top:72px;bottom:12px;width:auto}}
</style>
