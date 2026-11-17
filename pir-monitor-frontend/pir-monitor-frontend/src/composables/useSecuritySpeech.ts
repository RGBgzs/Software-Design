import { ref } from 'vue'

// ── 模块级响应式状态 ──
export const isSpeaking = ref(false)
export const autoSpeakEnabled = ref(true)
export const lastSpokenText = ref('')
export const lastSpeakStartAt = ref(0)

// ── 内部变量 ──
let currentAudio: HTMLAudioElement | null = null
let currentSpeakTaskId = 0
let voicesReadyPromise: Promise<SpeechSynthesisVoice[]> | null = null

// ── 辅助函数 ──
function normalizeSpeakText(text: string): string {
  return (text || '')
    .replace(/\s+/g, ' ')
    .replace(/AI_CHAT|OPEN_DEVICE|QUERY_SECURITY_STATUS|RULE/g, '')
    .trim()
}

// ═══════════════════════════════════════════════
//  stopSpeaking — 先递增 taskId，再停旧播报
// ═══════════════════════════════════════════════
export function stopSpeaking(_reason = 'manual') {
  console.log('[小安播报] 停止播报，原因:', _reason)
  // 关键：先让当前 taskId 失效，再停止
  currentSpeakTaskId++

  try {
    window.speechSynthesis?.cancel()
  } catch (e) {
    console.warn('[小安播报] speechSynthesis.cancel 失败', e)
  }

  try {
    if (currentAudio) {
      currentAudio.pause()
      currentAudio.currentTime = 0
      currentAudio = null
    }
  } catch (e) {
    console.warn('[小安播报] audio 停止失败', e)
  }

  isSpeaking.value = false
}

// ═══════════════════════════════════════════════
//  unlockSpeechSynthesis — 用户手势后初始化
// ═══════════════════════════════════════════════
export function unlockSpeechSynthesis() {
  try {
    const u = new SpeechSynthesisUtterance(' ')
    u.volume = 0
    window.speechSynthesis.speak(u)
    window.speechSynthesis.cancel()
    console.log('[小安播报] speechSynthesis 已解锁')
  } catch (e) {
    console.warn('[小安播报] unlock speech failed', e)
  }
}

// ═══════════════════════════════════════════════
//  ensureVoicesLoaded — 等待浏览器 voices 就绪
// ═══════════════════════════════════════════════
async function ensureVoicesLoaded(): Promise<SpeechSynthesisVoice[]> {
  if (voicesReadyPromise) return voicesReadyPromise

  voicesReadyPromise = new Promise((resolve) => {
    const synth = window.speechSynthesis
    if (!synth) {
      resolve([])
      return
    }

    let voices = synth.getVoices()
    if (voices && voices.length > 0) {
      resolve(voices)
      return
    }

    const timer = window.setTimeout(() => {
      resolve(synth.getVoices())
    }, 1500)

    synth.onvoiceschanged = () => {
      voices = synth.getVoices()
      clearTimeout(timer)
      resolve(voices)
    }
  })

  return voicesReadyPromise
}

// ═══════════════════════════════════════════════
//  playAudioUrl — 播放音频 URL
// ═══════════════════════════════════════════════
function playAudioUrl(
  url: string,
  taskId: number,
  revokeAfterPlay: boolean,
): Promise<boolean> {
  return new Promise((resolve) => {
    if (taskId !== currentSpeakTaskId) {
      resolve(false)
      return
    }

    const audio = new Audio(url)
    currentAudio = audio

    audio.volume = 1.0
    audio.playbackRate = 1.0

    audio.onended = () => {
      if (taskId === currentSpeakTaskId) {
        isSpeaking.value = false
      }
      if (revokeAfterPlay) {
        URL.revokeObjectURL(url)
      }
      resolve(true)
    }

    audio.onerror = (err) => {
      console.warn('[小安播报] audio 播放失败', err)
      if (taskId === currentSpeakTaskId) {
        isSpeaking.value = false
      }
      if (revokeAfterPlay) {
        URL.revokeObjectURL(url)
      }
      resolve(false)
    }

    audio.play().then(() => {
      console.log('[小安播报] audio 播放开始')
    }).catch((e) => {
      console.warn('[小安播报] audio.play 被拦截或失败', e)
      if (taskId === currentSpeakTaskId) {
        isSpeaking.value = false
      }
      resolve(false)
    })
  })
}

// ═══════════════════════════════════════════════
//  speakByTts — 后端 TTS，失败返回 false
// ═══════════════════════════════════════════════
async function speakByTts(text: string, taskId: number): Promise<boolean> {
  try {
    const res = await fetch('/api/face/tts/synthesize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    })

    if (!res.ok) {
      console.warn('[小安播报] 后端 TTS HTTP 失败：', res.status)
      return false
    }

    const contentType = res.headers.get('content-type') || ''

    // 情况 1：后端直接返回音频 blob
    if (contentType.includes('audio')) {
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      return await playAudioUrl(url, taskId, true)
    }

    // 情况 2：后端返回 JSON，里面有 audio / audioUrl / url
    const data = await res.json()
    const audioBase64 =
      data?.data?.audio ||
      data?.audio ||
      data?.data?.audioUrl ||
      data?.audioUrl ||
      data?.url ||
      null

    if (audioBase64) {
      // base64 直接播放
      const prefix = audioBase64.startsWith('data:') ? '' : 'data:audio/wav;base64,'
      return await playAudioUrl(prefix + audioBase64, taskId, false)
    }

    const audioUrl = data?.data?.audioUrl || data?.audioUrl || data?.url || null
    if (audioUrl && typeof audioUrl === 'string' && audioUrl.startsWith('http')) {
      return await playAudioUrl(audioUrl, taskId, false)
    }

    console.warn('[小安播报] 后端 TTS 没有返回可用的音频数据，降级浏览器播报')
    return false
  } catch (e) {
    console.warn('[小安播报] speakByTts 异常，降级浏览器播报', e)
    return false
  }
}

// ═══════════════════════════════════════════════
//  speakByBrowser — 浏览器 speechSynthesis 播报
// ═══════════════════════════════════════════════
async function speakByBrowserInternal(text: string, taskId: number): Promise<boolean> {
  return new Promise(async (resolve) => {
    if (!window.speechSynthesis) {
      console.warn('[小安播报] 当前浏览器不支持 speechSynthesis')
      isSpeaking.value = false
      resolve(false)
      return
    }

    if (taskId !== currentSpeakTaskId) {
      resolve(false)
      return
    }

    try {
      const voices = await ensureVoicesLoaded()

      if (taskId !== currentSpeakTaskId) {
        resolve(false)
        return
      }

      // 先 cancel 掉可能残留的 utterance
      window.speechSynthesis.cancel()

      const utterance = new SpeechSynthesisUtterance(text)
      // GC 安全：utterance 被 onend/onerror 闭包持有，不会被回收

      utterance.lang = 'zh-CN'
      utterance.volume = 1
      utterance.rate = 1
      utterance.pitch = 1

      // 选中文 voice
      const zhVoice =
        voices.find((v) => v.lang === 'zh-CN') ||
        voices.find((v) => v.lang?.toLowerCase().startsWith('zh')) ||
        voices.find((v) => v.name?.includes('Chinese')) ||
        null

      if (zhVoice) {
        utterance.voice = zhVoice
        console.log('[小安播报] 使用语音：', zhVoice.name, zhVoice.lang)
      }

      utterance.onstart = () => {
        console.log('[小安播报] 浏览器播报开始:', text.slice(0, 50))
        if (taskId === currentSpeakTaskId) {
          isSpeaking.value = true
        }
      }

      utterance.onend = () => {
        console.log('[小安播报] 浏览器播报结束')
        if (taskId === currentSpeakTaskId) {
          isSpeaking.value = false
        }
        resolve(true)
      }

      utterance.onerror = (event) => {
        console.warn('[小安播报] 浏览器播报错误', event)
        if (taskId === currentSpeakTaskId) {
          isSpeaking.value = false
        }
        resolve(false)
      }

      // 延迟 50ms 防止 Chrome 某些情况下不出声
      setTimeout(() => {
        if (taskId === currentSpeakTaskId) {
          window.speechSynthesis.speak(utterance!)
        }
      }, 50)

      // 兜底：如果 1.5 秒后还没 speaking，尝试 resume
      setTimeout(() => {
        try {
          if (
            taskId === currentSpeakTaskId &&
            window.speechSynthesis.paused
          ) {
            window.speechSynthesis.resume()
          }
        } catch {
          /* ignore */
        }
      }, 1500)
    } catch (e) {
      console.warn('[小安播报] speakByBrowserInternal 异常', e)
      isSpeaking.value = false
      resolve(false)
    }
  })
}

// ═══════════════════════════════════════════════
//  speakText — 主入口
//  ⚠️ 关键顺序：先 stopSpeaking()，再 ++currentSpeakTaskId
// ═══════════════════════════════════════════════
export async function speakText(text: string) {
  const cleanText = normalizeSpeakText(text)

  if (!cleanText) return
  if (!autoSpeakEnabled.value) {
    console.log('[小安播报] autoSpeak 关闭，跳过播报')
    return
  }

  // ── 关键：先停止旧播报，再创建新 taskId ──
  stopSpeaking('new-speak')

  const taskId = ++currentSpeakTaskId

  lastSpokenText.value = cleanText
  lastSpeakStartAt.value = Date.now()
  isSpeaking.value = true

  console.log('[小安播报] 准备播报 taskId=' + taskId + ':', cleanText.slice(0, 60))

  // 先尝试后端 TTS
  let ttsOk = false
  try {
    ttsOk = await speakByTts(cleanText, taskId)
  } catch (e) {
    console.warn('[小安播报] 后端 TTS 异常，降级浏览器播报', e)
  }

  if (ttsOk) {
    console.log('[小安播报] 后端 TTS 播放成功')
    return
  }

  // 检查 taskId 是否仍然有效
  if (taskId !== currentSpeakTaskId) {
    console.log('[小安播报] taskId 已失效，跳过浏览器播报')
    return
  }

  // 降级到浏览器播报
  console.log('[小安播报] 后端 TTS 不可用，降级浏览器播报')
  await speakByBrowserInternal(cleanText, taskId)
}

// ═══════════════════════════════════════════════
//  testSpeak — 测试播报按钮
// ═══════════════════════════════════════════════
export async function testSpeak() {
  console.log('[小安播报] === 测试播报开始 ===')
  console.log('[小安播报] speechSynthesis 支持:', !!window.speechSynthesis)

  const voices = await ensureVoicesLoaded()
  console.log('[小安播报] voices 数量:', voices.length)
  if (voices.length > 0) {
    const zh = voices.filter(
      (v) => v.lang?.toLowerCase().startsWith('zh') || v.name?.includes('Chinese'),
    )
    console.log('[小安播报] 中文 voices:', zh.map((v) => v.name + ' (' + v.lang + ')'))
  }

  console.log('[小安播报] autoSpeakEnabled:', autoSpeakEnabled.value)
  console.log('[小安播报] isSpeaking:', isSpeaking.value)

  await speakText('小安语音播报测试成功。')
  console.log('[小安播报] === 测试播报结束 ===')
}

// ═══════════════════════════════════════════════
//  useSecuritySpeech — 兼容旧调用方式
// ═══════════════════════════════════════════════
export function useSecuritySpeech() {
  // 保留旧的 broadcast builders
  function buildFaceBroadcastText(r: any): string {
    const name = r?.personName || r?.name || (r?.matched ? '用户' : '陌生人')
    if (r?.spoof) return '高危警告，检测到疑似照片或视频人脸攻击，请立即人工核查。'
    if (r?.matched) return `识别成功，欢迎你，${name}。`
    return '警告，检测到陌生人进入，请注意安全。'
  }

  function buildPirBroadcastText(r: any): string {
    if (r?.defenseStatus && r?.humanDetected) return '警告，布防状态下检测到人体活动。'
    if (r?.humanDetected) return '检测到人体活动。'
    return '当前无异常活动。'
  }

  function buildAlarmBroadcastText(r: any): string {
    if (r?.handleStatus === 1) return '告警已处理完成。'
    return `新的告警：${r?.alarmContent || '请查看详情'}`
  }

  return {
    // 响应式状态（模块级共享）
    isSpeaking,
    autoSpeakEnabled,
    lastSpokenText,
    lastSpeakStartAt,

    // 公开的 speakByBrowser
    speakByBrowser: (text: string) => {
      const taskId = ++currentSpeakTaskId
      return speakByBrowserInternal(text, taskId)
    },

    // 核心方法（模块级函数）
    speakText,
    stopSpeaking,
    testSpeak,
    unlockSpeechSynthesis,

    // 兼容旧 API
    buildFaceBroadcastText,
    buildPirBroadcastText,
    buildAlarmBroadcastText,
  }
}
