import { ref, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { identifyFaceApi } from '@/api/face'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'
import { useSecuritySpeech } from './useSecuritySpeech'

export interface CaptureResult {
  deviceCode: string
  snapshot: string
  loading: boolean
  result: {
    matched: boolean
    name: string
    personType: string
    similarity: number
    riskLevel: string
    suggestion: string
  } | null
  error: string | null
  timestamp: string
}

const captures = ref<CaptureResult[]>([])
const _objectUrls = new Set<string>()
let stream: MediaStream | null = null
let isCapturing = false

function _set(idx: number, key: keyof CaptureResult, value: any) {
  if (captures.value[idx]) (captures.value[idx] as any)[key] = value
}

function stopCamera() {
  if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null }
}

async function captureFrame(): Promise<string | null> {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    const video = document.createElement('video')
    video.srcObject = stream
    video.autoplay = true
    video.playsInline = true
    await new Promise(resolve => { video.onloadedmetadata = resolve })
    video.play()
    await new Promise(resolve => setTimeout(resolve, 300))

    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 480
    canvas.getContext('2d')!.drawImage(video, 0, 0)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.8)
    stopCamera()
    return dataUrl
  } catch {
    stopCamera()
    return null
  }
}

function dataUrlToBlob(dataUrl: string): Blob {
  const [header, data] = dataUrl.split(',')
  const mime = header.match(/:(.*?);/)![1]
  const bstr = atob(data)
  const n = bstr.length
  const u8arr = new Uint8Array(n)
  for (let i = 0; i < n; i++) u8arr[i] = bstr.charCodeAt(i)
  return new Blob([u8arr], { type: mime })
}

export function useFaceCapture() {
  const deviceStore = useDeviceStore()
  const authStore = useAuthStore()
  const speech = useSecuritySpeech()
  const prevStates = ref<Record<string, number>>({})
  const lastCaptureTime = ref<Record<string, number>>({})  // 30秒内不重复抓拍
  const userArmed = ref(false)

  // 监听布防状态：布防开启时允许触发，布防关闭时不允许
  watch(
    () => deviceStore.devices.map(d => ({
      code: d.deviceCode,
      defenseStatus: deviceStore.getRealtimeStatus(d.deviceCode).defenseStatus,
    })),
    (list) => {
      userArmed.value = list.some(d => d.defenseStatus === 1)
    },
    { deep: true, immediate: true }
  )

  const stopWatch = watch(
    () => deviceStore.devices.map(d => ({
      code: d.deviceCode,
      humanDetected: deviceStore.getRealtimeStatus(d.deviceCode).humanDetected,
      defenseStatus: deviceStore.getRealtimeStatus(d.deviceCode).defenseStatus,
    })),
    (list) => {
      for (const dev of list) {
        const prev = prevStates.value[dev.code]
        // 只在布防开启 + 人体从0→1时触发
        if (dev.defenseStatus !== 1) continue
        if (prev === 0 && dev.humanDetected === 1) {
          // 30秒冷却，避免频繁抓拍
          const now = Date.now()
          const last = lastCaptureTime.value[dev.code] || 0
          if (now - last < 30000) continue
          lastCaptureTime.value[dev.code] = now
          triggerCapture(dev.code)
        }
        prevStates.value[dev.code] = dev.humanDetected
      }
    },
    { deep: true, immediate: true }
  )

  async function triggerCapture(deviceCode: string) {
    if (isCapturing) return
    isCapturing = true

    const entry: CaptureResult = {
      deviceCode,
      snapshot: '',
      loading: true,
      result: null,
      error: null,
      timestamp: new Date().toISOString(),
    }
    captures.value.unshift(entry)
    if (captures.value.length > 20) {
      const removed = captures.value.pop()
      if (removed?.snapshot) { _objectUrls.delete(removed.snapshot); URL.revokeObjectURL(removed.snapshot) }
    }

    const dataUrl = await captureFrame()
    if (!dataUrl) {
      _set(0, 'error', '无法打开摄像头')
      isCapturing = false
      return
    }
    const blob = dataUrlToBlob(dataUrl)
    const url = URL.createObjectURL(blob)
    _objectUrls.add(url)
    _set(0, 'snapshot', url)

    try {
      const file = new File([blob], 'capture.jpg', { type: 'image/jpeg' })
      // 连拍第二帧
      let frame2: File | undefined
      try {
        await new Promise(r => setTimeout(r, 200))
        const dataUrl2 = await captureFrame()
        if (dataUrl2) {
          const blob2 = dataUrlToBlob(dataUrl2)
          frame2 = new File([blob2], 'frame2.jpg', { type: 'image/jpeg' })
        }
      } catch { /* ignore */ }
      const uid = authStore.user?.userId || authStore.user?.id
      const res = await identifyFaceApi(file, deviceCode, '入口', uid, 'all', frame2)
      const body = res.data as { code: number; data: { matched: boolean; name: string; personType: string; similarity: number; riskLevel: string; suggestion: string } }
      if (body.code === 200 && body.data) {
        _set(0, 'result', body.data)
        const d = body.data
        if (d.matched) {
          // 已登记 → 语音欢迎 + 低危
          const welcome = '欢迎' + d.name
          speech.speakByBrowser(welcome)
          _set(0, 'result', { ...d, riskLevel: 'LOW', suggestion: welcome })
        } else {
          // 陌生人 → 语音警告 + 高危
          speech.speakByBrowser('门口有陌生人经过，请注意')
          _set(0, 'result', { ...d, riskLevel: 'HIGH', suggestion: '门口有陌生人经过，请注意' })
        }
      } else {
        _set(0, 'error', '未检测到人脸')
      }
    } catch {
      _set(0, 'error', '识别请求失败')
    } finally {
      _set(0, 'loading', false)
      isCapturing = false
    }
  }

  onUnmounted(() => { stopWatch(); stopCamera(); _objectUrls.forEach(u => URL.revokeObjectURL(u)); _objectUrls.clear() })

  return { captures }
}
