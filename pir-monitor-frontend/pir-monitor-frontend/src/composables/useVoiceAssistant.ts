import { ref } from 'vue'

export type VoiceState = 'idle' | 'listening' | 'thinking' | 'speaking' | 'confirming' | 'disabled'
export type VoiceIntent = {
  type: string; module?: string; sensitive?: boolean
  originalText?: string; queryTarget?: string; deviceCode?: string
}

export function useVoiceAssistant() {
  const state = ref<VoiceState>('idle')
  const heardText = ref('')
  const lastReply = ref('')
  const lastAction = ref('')
  const nextSuggestion = ref('')
  const voiceOk = ref(false)
  const continuousUntil = ref(0)
  let recognition: any = null
  let taskId = 0
  let contTimer: any = null

  const wakeWords = ['小安小安', '小安', '安防助手', '智能管家']
  const confirmWords = ['确认', '确认执行', '确定', '执行吧']
  const cancelWords = ['取消', '算了', '不用了']

  function isSecure() {
    const p = typeof location !== 'undefined' ? location.protocol : 'http:'
    const h = typeof location !== 'undefined' ? location.hostname : ''
    return p === 'https:' || h === 'localhost' || h === '127.0.0.1' || h === '[::1]'
  }

  function init() {
    if (!isSecure()) { state.value = 'disabled'; voiceOk.value = false; return }
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SR) { state.value = 'disabled'; voiceOk.value = false; return }
    voiceOk.value = true
    recognition = new SR(); recognition.lang = 'zh-CN'; recognition.interimResults = false; recognition.continuous = false; recognition.maxAlternatives = 1
    recognition.onstart = () => { state.value = 'listening' }
    recognition.onresult = (e: any) => {
      let t = ''; for (let i = e.resultIndex; i < e.results.length; i++) t += e.results[i][0].transcript
      t = t.trim()
      if (t) {
        heardText.value = t
        // Strip wake word
        let clean = t
        for (const w of wakeWords) {
          if (clean.startsWith(w)) { clean = clean.slice(w.length).trim(); break }
        }
        // Start or extend continuous mode
        if (continuousUntil.value > 0) { extendContinuous() }
        // If it was just wake word, handle separately
        if (t === '小安' || t === '小安小安' || (clean.length === 0 && wakeWords.some(w => t.includes(w)))) {
          handleResult({ type: 'WAKE_ONLY', originalText: t, queryTarget: 'WAKE' })
        } else {
          handleResult(parseIntent(clean || t))
        }
      }
    }
    recognition.onerror = (e: any) => {
      if (e.error === 'not-allowed') state.value = 'disabled'
      else if (e.error !== 'aborted') state.value = 'idle'
    }
    recognition.onend = () => { if (state.value === 'listening') state.value = 'idle' }
  }

  let intentHandler: ((intent: VoiceIntent) => void) | null = null
  function onIntent(handler: (intent: VoiceIntent) => void) { intentHandler = handler }

  function handleResult(intent: VoiceIntent) {
    state.value = 'thinking'
    if (intentHandler) intentHandler(intent)
  }

  function parseIntent(text: string): VoiceIntent {
    const c = text.replace(/[，,。！!？?\s]/g, '')
    // === PAGE SWITCH (only "打开/进入/切换/帮我打开/带我去") ===
    if (/打开|进入|切换|帮我打开|带我去/.test(c)) {
      if (/实时监测|监控/.test(c)) return { type: 'SWITCH', module: 'monitor', originalText: text }
      if (/PIR|人体感应/.test(c)) return { type: 'SWITCH', module: 'pir', originalText: text }
      if (/告警/.test(c)) return { type: 'SWITCH', module: 'alarm', originalText: text }
      if (/设备管理/.test(c)) return { type: 'SWITCH', module: 'device', originalText: text }
      if (/首页|总控台/.test(c)) return { type: 'SWITCH', module: 'dashboard', originalText: text }
      if (/消息中心|紧急求助|一键报警|报警/.test(c)) return { type: 'SWITCH', module: 'sos', originalText: text }
    }
    // === SENSITIVE ===
    // 支持 "对 device_001 开启布防" 格式
    const armMatch = c.match(/(?:对|给)\s*(\S+?)\s*(?:进行|开启|开始)?\s*布防/)
    if (armMatch) return { type: 'ARM', sensitive: true, originalText: text, deviceCode: armMatch[1] }
    if (c.includes('开启布防')) return { type: 'ARM', sensitive: true, originalText: text }

    const disarmMatch = c.match(/(?:对|给)\s*(\S+?)\s*(?:进行|关闭|撤销|停止)?\s*撤防/)
    if (disarmMatch) return { type: 'DISARM', sensitive: true, originalText: text, deviceCode: disarmMatch[1] }
    if (c.includes('撤销布防') || c.includes('关闭布防')) return { type: 'DISARM', sensitive: true, originalText: text }
    if (c.includes('发送求助') || c.includes('紧急求助')) return { type: 'HELP', sensitive: true, originalText: text }
    if (c.includes('开始实时监测') || c.includes('开始监测')) return { type: 'START_MONITOR', originalText: text }
    if (c.includes('停止实时监测') || c.includes('停止监测')) return { type: 'STOP_MONITOR', originalText: text }
    // === QUERY (answer + speak, NO page switch) ===
    if (c.includes('今天有没有人经过') || c.includes('今天有人')) return { type: 'QUERY_PIR', originalText: text, queryTarget: 'today_pir' }
    if (c.includes('我现在安全吗') || c.includes('安防状态') || c.includes('有没有危险')) return { type: 'QUERY_STATUS', originalText: text, queryTarget: 'security_status' }
    if (c.includes('设备在线') || c.includes('设备状态') || c.includes('哪些设备')) return { type: 'QUERY_DEVICES', originalText: text, queryTarget: 'online_devices' }
    if (c.includes('未处理告警') || c.includes('有没有告警') || c.includes('最近告警')) return { type: 'QUERY_ALARMS', originalText: text, queryTarget: 'unhandled_alarms' }
    if (c.includes('布防状态')) return { type: 'QUERY_DEFENSE', originalText: text, queryTarget: 'defense_status' }
    if (c.includes('人脸识别') || c.includes('人脸')) return { type: 'QUERY_FACE', originalText: text }
    if (confirmWords.some(w => c.includes(w)) && state.value === 'confirming') return { type: 'CONFIRM', originalText: text }
    if (cancelWords.some(w => c.includes(w)) && state.value === 'confirming') return { type: 'CANCEL', originalText: text }
    return { type: 'AI_CHAT', originalText: text }
  }

  function setState(s: VoiceState) { state.value = s }
  function setReply(r: string) { lastReply.value = r }
  function setAction(a: string) { lastAction.value = a }
  function setSuggestion(s: string) { nextSuggestion.value = s }

  function startListening() {
    if (!voiceOk.value || !recognition) return
    stopSpeaking(); taskId++
    try { recognition.start() } catch { state.value = 'idle' }
  }

  function stopListening() {
    if (recognition) { try { recognition.abort() } catch { /* */ } }
    state.value = 'idle'
  }

  function startContinuous() { continuousUntil.value = Date.now() + 10000 }
  function extendContinuous() { continuousUntil.value = Date.now() + 10000; clearTimeout(contTimer); contTimer = setTimeout(() => { continuousUntil.value = 0 }, 10000) }
  function isContinuous() { return Date.now() < continuousUntil.value }

  function stopSpeaking() { if ('speechSynthesis' in window) speechSynthesis.cancel() }
  function startSpeaking() { state.value = 'speaking' }
  function doneSpeaking() { if (state.value === 'speaking') state.value = 'idle' }
  function getTaskId() { return ++taskId }

  function destroy() { stopListening(); stopSpeaking(); clearTimeout(contTimer) }

  return {
    state, heardText, lastReply, lastAction, nextSuggestion, voiceOk,
    init, startListening, stopListening, parseIntent, onIntent,
    setState, setReply, setAction, setSuggestion,
    startContinuous, isContinuous, stopSpeaking, startSpeaking, doneSpeaking,
    getTaskId, isSecure, wakeWords, confirmWords, cancelWords, destroy,
  }
}
