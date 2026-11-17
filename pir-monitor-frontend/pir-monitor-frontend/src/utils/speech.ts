// ── 浏览器语音播报工具（不依赖后端TTS）──

let _speaking = false
let _speakEnabled = true
let _lastSpeakText = ''
let _lastSpeakPerson = ''
let _lastSpeakAt = 0
let _lastUnknownSpeakAt = 0
const PERSON_COOLDOWN_MS = 10_000   // 同一人 10 秒内不重复播报
const UNKNOWN_COOLDOWN_MS = 15_000  // 未知人员 15 秒内不重复播报

export function isSpeaking() { return _speaking }
export function isSpeechEnabled() { return _speakEnabled }
export function setSpeechEnabled(v: boolean) { _speakEnabled = v; if (!v) cancelSpeech() }
export function getLastSpeakText() { return _lastSpeakText }
export function getLastSpeakPerson() { return _lastSpeakPerson }

// ── 加载中文 voice ──
function loadVoice(): SpeechSynthesisVoice | null {
  const voices = window.speechSynthesis?.getVoices?.() ?? []
  return (
    voices.find(v => v.lang === 'zh-CN') ??
    voices.find(v => v.lang?.toLowerCase().startsWith('zh')) ??
    voices.find(v => v.name?.includes('Chinese')) ??
    voices[0] ??
    null
  )
}

// ── 确保 voices 加载 ──
async function ensureVoices(): Promise<SpeechSynthesisVoice | null> {
  const synth = window.speechSynthesis
  if (!synth) return null
  let v = loadVoice()
  if (v) return v
  return new Promise(resolve => {
    const t = setTimeout(() => resolve(loadVoice()), 2000)
    synth.onvoiceschanged = () => { clearTimeout(t); resolve(loadVoice()) }
  })
}

// ── 取消播报 ──
export function cancelSpeech() {
  try { window.speechSynthesis?.cancel() } catch { /* ignore */ }
  _speaking = false
}

// ── 防重复检查 ──
function shouldSkip(personName: string, isUnknown: boolean): boolean {
  const now = Date.now()
  if (isUnknown) {
    if (now - _lastUnknownSpeakAt < UNKNOWN_COOLDOWN_MS) return true
    _lastUnknownSpeakAt = now
  } else if (personName && personName !== '陌生人') {
    if (personName === _lastSpeakPerson && now - _lastSpeakAt < PERSON_COOLDOWN_MS) return true
    _lastSpeakPerson = personName
  }
  _lastSpeakAt = now
  return false
}

// ── 主播报 ──
export async function speak(text: string, personName?: string): Promise<void> {
  if (!_speakEnabled) return
  if (!text?.trim()) return

  const isUnknown = text.includes('未知') || text.includes('陌生')
  if (shouldSkip(personName ?? '', isUnknown)) {
    console.log('[语音] 冷却期跳过:', text.slice(0, 40))
    return
  }

  if (!window.speechSynthesis) {
    console.warn('[语音] 浏览器不支持 speechSynthesis')
    return
  }

  cancelSpeech()
  _lastSpeakText = text
  _speaking = true

  const voice = await ensureVoices()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = 1.0
  utterance.pitch = 1.0
  utterance.volume = 1.0
  if (voice) utterance.voice = voice

  utterance.onend = () => { _speaking = false }
  utterance.onerror = () => { _speaking = false }

  // 防 Chrome 不发声
  setTimeout(() => {
    try {
      if (window.speechSynthesis.paused) window.speechSynthesis.resume()
    } catch { /* */ }
    window.speechSynthesis.speak(utterance)
  }, 80)
}

// ── 快捷方法 ──
export function speakSuccess(name: string) {
  speak(`识别成功，欢迎 ${name || '用户'}。`, name)
}
export function speakUnknown() {
  speak('检测到未知人员，请注意核验身份。', '')
}
export function speakNoFace() {
  speak('未检测到人脸，请正对摄像头。', '')
}
export function speakMultiFace() {
  speak('检测到多张人脸，请只保留一人进入画面。', '')
}
export function speakLivenessFail() {
  speak('活体检测未通过，请人工核验。', '')
}
export function speakServiceError() {
  speak('人脸识别服务暂不可用。', '')
}
