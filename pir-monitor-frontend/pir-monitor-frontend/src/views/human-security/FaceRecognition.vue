<template>
  <div class="face-monitor">
    <div class="fm-header">
      <div><h2 class="fm-title">人脸识别监控</h2><p class="fm-subtitle">实时识别 | 陌生人检测 | 语音播报 | 安防联动</p></div>
      <div class="fm-header-acts">
        <el-switch v-model="autoMode" active-text="自动识别" size="small" style="margin-right:8px" @change="onAutoToggle" />
        <el-switch v-model="voiceOn" active-text="语音播报" size="small" style="margin-right:12px" @change="onVoiceToggle" />
        <el-button :icon="UserFilled" size="small" @click="showUsers=true;loadUsers()">人员管理</el-button>
        <el-button :icon="RefreshRight" size="small" @click="checkService" :loading="svcLoading">检测服务</el-button>
      </div>
    </div>
    <div :class="['fm-bar', faceOnline?'bar-on':'bar-off']"><span class="bar-dot" /><span>{{ faceOnline ? '服务在线' : '服务未连接' }}</span><span v-if="faceOnline"> | 已注册 <strong>{{ faceStats.registered }}</strong> 人</span><span v-if="currentDevice"> | 设备 <strong>{{ currentDeviceName }}</strong></span><span> | 今日识别 <strong>{{ todayTotal }}</strong> 次</span>
      <span style="margin-left:auto;display:flex;align-items:center;gap:6px">
        <el-select v-model="currentDevice" placeholder="选择摄像头" size="small" style="width:200px" @change="onDeviceChange" v-if="cameraDevices.length > 0">
          <el-option v-for="d in cameraDevices" :key="d.deviceCode" :label="`${d.deviceName||d.deviceCode} (${d.deviceCode})`" :value="d.deviceCode"/>
        </el-select>
        <el-button size="small" link type="primary" @click="goToDevice" v-if="currentDevice">查看设备</el-button>
      </span>
    </div>
    <div class="fm-main">
      <div class="fm-cam-col"><div class="cam-card">
        <div class="cam-viewport" :class="{'cam-off':!cameraActive}">
          <video ref="videoRef" autoplay playsinline muted class="cam-video" :class="{hidden:!cameraActive}" />
          <canvas ref="canvasRef" style="display:none" />
          <div v-if="!cameraActive" class="cam-empty"><el-icon :size="48" color="#94a3b8"><VideoCamera /></el-icon><p>摄像头未开启</p><span>点击下方按钮开启摄像头</span></div>
          <div v-if="cameraActive" :class="['cam-badge',monitoring?'live':'ready']"><span class="badge-dot" /> {{ monitoring?'监测中':(snapLock?'识别中...':'就绪') }}</div>
          <div v-if="snapLock" class="cam-scan-overlay"><div class="scan-line" /><span>正在识别...</span></div>
        </div>
        <div class="cam-toolbar">
          <el-button type="primary" :icon="VideoCamera" size="small" @click="startCamera" :disabled="cameraActive||!faceOnline">打开</el-button>
          <el-button type="danger" :icon="VideoCamera" size="small" @click="stopCamera" :disabled="!cameraActive">关闭</el-button>
          <el-button type="success" :icon="Camera" size="small" @click="captureOnce" :disabled="!cameraActive" :loading="snapLock">单次识别</el-button>
          <el-button :type="monitoring?'warning':'primary'" size="small" @click="monitoring?stopMonitor():startMonitor()" :disabled="!cameraActive"><el-icon><component :is="monitoring?VideoPause:VideoPlay" /></el-icon>{{ monitoring?'停止':'实时监测' }}</el-button>
        </div>
        <div class="cam-info"><span v-if="monitoring">自动识别中 | {{ detectCount }} 次 | 失败 {{ failStreak }} 次</span><span v-else-if="cameraActive">就绪</span><span v-else>请开启摄像头</span><span v-if="monitoring" style="margin-left:auto">下次: {{ nextScanIn }}s</span></div>
      </div></div>
      <div class="fm-res-col">
        <div class="res-card" :class="resCardClass">
          <template v-if="resState==='SUCCESS'&&lastResult"><div class="rc-icon"><el-icon :size="32"><CircleCheckFilled /></el-icon></div><div class="rc-title">识别成功</div><div class="rc-grid"><div><span>姓名</span><strong>{{ lastResult.displayName||lastResult.name||'-' }}</strong></div><div><span>相似度</span><strong class="c-good">{{ fmtPct(lastResult.similarity) }}</strong></div><div><span>风险</span><StatusTag :status="lastResult.riskLevel||'LOW'" /></div><div><span>耗时</span><strong>{{ lastResult.durationMs||0 }}ms</strong></div></div><div class="rc-sugg good">人员已登记，可通行</div><div class="rc-acts"><el-button size="small" type="primary" @click="replayLast"><el-icon><Microphone /></el-icon> 播报</el-button></div></template>
          <template v-else-if="resState==='UNKNOWN'&&lastResult"><div class="rc-icon"><el-icon :size="32"><WarningFilled /></el-icon></div><div class="rc-title">未知人员</div><div class="rc-grid"><div><span>相似度</span><strong class="c-warn">{{ fmtPct(lastResult.similarity) }}</strong></div><div><span>风险</span><StatusTag status="HIGH" /></div><div><span>耗时</span><strong>{{ lastResult.durationMs||0 }}ms</strong></div></div><div class="rc-sugg warn">请人工核验身份</div><div class="rc-acts"><el-button size="small" type="danger" @click="replayLast"><el-icon><Microphone /></el-icon> 播报警示</el-button><el-button size="small" type="warning" @click="showUsers=true">登记人员</el-button></div></template>
          <template v-else-if="resState==='NO_FACE'"><div class="rc-icon"><el-icon :size="32" color="#68778D"><PictureFilled /></el-icon></div><div class="rc-title">未检测到人脸</div><p class="rc-hint">请正对摄像头，确保光线充足</p></template>
          <template v-else-if="resState==='MULTI_FACE'"><div class="rc-icon"><el-icon :size="32" color="#E9971A"><WarningFilled /></el-icon></div><div class="rc-title">检测到多张人脸</div><p class="rc-hint">请只保留一人进入画面</p></template>
          <template v-else-if="resState==='LIVENESS_FAIL'"><div class="rc-icon"><el-icon :size="32" color="#E84C5B"><CircleCloseFilled /></el-icon></div><div class="rc-title">活体检测未通过</div><p class="rc-hint">疑似照片或非真实人脸</p></template>
          <template v-else-if="resState==='ERROR'"><div class="rc-icon"><el-icon :size="32" color="#E84C5B"><CircleCloseFilled /></el-icon></div><div class="rc-title">服务暂不可用</div><p class="rc-hint">{{ lastError||'请检查人脸服务' }}</p><div class="rc-acts"><el-button size="small" type="primary" @click="checkService">重试</el-button></div></template>
          <template v-else><div class="rc-empty"><el-icon :size="36" color="#cbd5e1"><PictureFilled /></el-icon><p>{{ cameraActive?'点击单次识别':'请先开启摄像头' }}</p></div></template>
        </div>
        <div class="hist-card"><div class="hist-head"><h4>最近识别</h4><span class="hist-count">{{ recentList.length }}/10</span></div>
        <div v-if="!recentList.length" class="hist-empty">暂无记录</div>
        <div v-for="r in recentList" :key="r.id" :class="['hist-item',r.cssCls]"><span class="hi-icon">{{ r.icon }}</span><span class="hi-name">{{ r.name }}</span><span class="hi-sim">{{ r.sim }}</span><span class="hi-time">{{ r.time }}</span></div>
        </div>
        <div class="stats-row"><div class="stat-chip"><span>今日</span><strong>{{ todayTotal }}</strong></div><div class="stat-chip ok"><span>已知</span><strong>{{ todayKnown }}</strong></div><div class="stat-chip warn"><span>未知</span><strong>{{ todayUnknown }}</strong></div><div class="stat-chip"><span>最高相似度</span><strong>{{ fmtPct(todayMaxSim) }}</strong></div></div>
      </div>
    </div>
    <el-drawer v-model="showUsers" title="人员管理" size="680px" @close="onDrawerClose">
      <div class="user-drawer">
        <div class="ud-panel"><h4>注册人员</h4><el-input v-model="rname" placeholder="姓名（必填）" size="small" class="ud-inp" /><el-select v-model="rrelation" placeholder="与用户的关系" size="small" class="ud-inp"><el-option label="家人" value="家人" /><el-option label="兄弟" value="兄弟" /><el-option label="朋友" value="朋友" /><el-option label="邻居" value="邻居" /><el-option label="保安" value="保安" /><el-option label="同事" value="同事" /><el-option label="其他" value="其他" /></el-select><el-radio-group v-model="rmode" size="small" class="ud-inp"><el-radio-button value="upload">上传照片</el-radio-button><el-radio-button value="camera">摄像头拍照</el-radio-button></el-radio-group><template v-if="rmode==='upload'"><el-upload :auto-upload="false" :show-file-list="false" :on-change="onRegFile" accept="image/*" drag class="ud-upload"><div v-if="!rpreview"><el-icon :size="28" color="#cbd5e1"><UploadFilled /></el-icon><p>上传照片</p></div><img v-else :src="rpreview" class="ud-preview" /></el-upload></template><template v-else>
  <template v-if="!rphotoTaken">
    <div class="ud-cam-box"><video ref="regVidRef" autoplay playsinline muted class="cam-video" /></div>
    <el-button size="small" @click="regCapture" :disabled="!regCamOn" style="width:100%">拍照</el-button>
    <el-button size="small" @click="openRegCam" v-if="!regCamOn" style="width:100%">打开摄像头</el-button>
  </template>
  <template v-else>
    <img v-if="rpreview" :src="rpreview" class="ud-preview" />
    <el-button size="small" @click="regRecapture" style="width:100%">重新拍照</el-button>
  </template>
  <canvas ref="regCvsRef" style="display:none" /></template><el-button type="primary" @click="doRegister" :loading="rloading" :disabled="!canReg" style="width:100%;margin-top:8px">确认注册</el-button><div v-if="rresult" class="ud-msg" :class="rresult==='OK'?'ok':'err'">{{ rresult==='OK'?'注册成功':'注册失败' }}</div></div>
        <div class="ud-panel"><div class="ud-head"><h4>已注册 ({{ users.length }})</h4><el-button size="small" :icon="RefreshRight" @click="loadUsers" :loading="uloading">刷新</el-button></div><div v-if="!uloading&&!users.length" class="hist-empty">暂无</div><div v-for="u in users" :key="u.id" class="ud-row"><div class="ud-avatar">{{ (u.name||u.personId||'?')[0] }}</div><div class="ud-info"><div class="ud-name">{{ u.name||u.personId }}</div><div class="ud-meta">{{ u.personType || rrelation || '家人' }} | {{ fmt(u.createdAt) }}</div></div><el-popconfirm title="确定删除？" @confirm="doDel(u)"><template #reference><el-button size="small" type="danger" link>删除</el-button></template></el-popconfirm></div></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, inject } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  RefreshRight, UploadFilled, VideoCamera, UserFilled, Camera, VideoPause, VideoPlay,
  WarningFilled, CircleCheckFilled, CircleCloseFilled, Microphone, PictureFilled
} from '@element-plus/icons-vue'
import StatusTag from '@/views/unified/components/StatusTag.vue'
import { getFaceStatusApi, getFaceUsersApi, registerFaceApi, deleteFaceUserApi, identifyFaceApi } from '@/api/face'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import { cancelSpeech, setSpeechEnabled, speakSuccess, speakUnknown, speakNoFace, speakMultiFace, speakLivenessFail } from '@/utils/speech'

const authStore = useAuthStore()
const voiceStore = useVoiceActionStore()
const currentUserId = computed(() => authStore.user?.userId ?? null)

const switchModule = inject<(key: string) => void>('switchModule', () => {})

const deviceStore = useDeviceStore()
const deviceList = computed(() => deviceStore.devices || [])
// 从 device 表筛选摄像头类型设备（如无摄像头设备则回退到全部设备）
const cameraDevices = computed(() => {
  const filtered = deviceList.value.filter(d =>
    (d.deviceCode && d.deviceCode.toUpperCase().includes('CAMERA')) ||
    (d.deviceName && (d.deviceName.includes('摄像头') || d.deviceName.includes('相机') || d.deviceName.includes('人脸')))
  )
  return filtered.length > 0 ? filtered : deviceList.value
})
const svcLoading = ref(false)
const faceOnline = ref(false)
const faceStats = reactive({ registered: 0 })
const currentDevice = ref('')
const lastRecogTime = ref('')
const videoRef = ref<HTMLVideoElement>()
const canvasRef = ref<HTMLCanvasElement>()
const cameraActive = ref(false)
let stream: MediaStream | null = null
const autoMode = ref(false)
const monitoring = ref(false)
const snapLock = ref(false)
const detectCount = ref(0)
const failStreak = ref(0)
const autoInterval = 2800
const nextScanIn = ref(0)
let detectTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null
const voiceOn = ref(true)
type ResState = 'IDLE' | 'SUCCESS' | 'UNKNOWN' | 'NO_FACE' | 'MULTI_FACE' | 'LIVENESS_FAIL' | 'ERROR'
const resState = ref<ResState>('IDLE')
const lastResult = ref<any>(null)
const lastError = ref('')
const recentList = ref<any[]>([])
let _recIdSeq = 0
const todayTotal = ref(0)
const todayKnown = ref(0)
const todayUnknown = ref(0)
const todayMaxSim = ref(0)
const showUsers = ref(false)
const rname = ref(''); const rrelation = ref('家人'); const rpersonType = ref('RESIDENT'); const rphotoTaken = ref(false)
const rmode = ref<'upload'|'camera'>('upload')
const rfile = ref<File|null>(null); const rpreview = ref(''); const rloading = ref(false); const rresult = ref('')
const regVidRef = ref<HTMLVideoElement>(); const regCvsRef = ref<HTMLCanvasElement>(); const regCamOn = ref(false)
let regStream: MediaStream|null = null
const users = ref<any[]>([]); const uloading = ref(false)
const canReg = computed(() => !!rname.value && (!!rfile.value || !!rpreview.value))
const currentDeviceName = computed(() => {
  const found = deviceList.value.find(d => d.deviceCode === currentDevice.value)
  return found ? (found.deviceName || found.deviceCode) : (currentDevice.value || '未选择')
})
function onDeviceChange(code: string) { currentDevice.value = code; localStorage.setItem('face_selected_device', code) }
function goToDevice() { if (currentDevice.value) switchModule('device') }
function fmtPct(v?: number) { return v != null ? (v).toFixed(1) + "%" : "-" }
function fmt(t?: string) { return t ? new Date(t).toLocaleString("zh-CN") : "-" }
function fmtNow() { return new Date().toLocaleTimeString("zh-CN") }
function roleLabel(t?: string) {
  const m: Record<string,string> = { RESIDENT:'住户', ADMIN:'管理员', VISITOR:'访客', GUARD:'保安', FAMILY:'家庭成员', STAFF:'工作人员' }
  return m[t||''] || t || '未知'
}
const resCardClass = computed(() => {
  const m: Record<string,string> = { SUCCESS:'rc-ok', UNKNOWN:'rc-warn', NO_FACE:'rc-gray', MULTI_FACE:'rc-warn', LIVENESS_FAIL:'rc-err', ERROR:'rc-err', NO_REGISTERED_FACE:'rc-info', IDLE:'' }
  return m[resState.value] || ''
})
async function checkService() {
  svcLoading.value = true
  try {
    const r = await getFaceStatusApi()
    const d: any = r.data?.data || r.data || {}
    faceOnline.value = d?.available === true || r.data?.code === 200
    faceStats.registered = d?.registered_users || d?.registered_faces || (Array.isArray(d?.user_names) ? d.user_names.length : 0)
    lastRecogTime.value = new Date().toLocaleTimeString("zh-CN")
    if (faceOnline.value) { loadUsers() }
    // 初始化设备：从 localStorage 恢复或选第一个摄像头
    if (!currentDevice.value || currentDevice.value === 'CAMERA_001') {
      const saved = localStorage.getItem('face_selected_device')
      if (saved && cameraDevices.value.some(d => d.deviceCode === saved)) {
        currentDevice.value = saved
      } else if (cameraDevices.value.length > 0) {
        currentDevice.value = cameraDevices.value[0].deviceCode
      } else if (deviceList.value.length > 0) {
        currentDevice.value = deviceList.value[0].deviceCode
      } else {
        currentDevice.value = 'CAMERA_001'
      }
    }
  } catch { faceOnline.value = false }
  finally { svcLoading.value = false }
}
async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: false })
    if (videoRef.value) { videoRef.value.srcObject = stream; await videoRef.value.play() }
    cameraActive.value = true; resState.value = 'IDLE'
  } catch { ElMessage.warning("摄像头权限被拒绝，请在浏览器设置中允许") }
}
function stopCamera() {
  stopMonitor()
  stream?.getTracks().forEach(t => t.stop()); stream = null
  if (videoRef.value) videoRef.value.srcObject = null
  cameraActive.value = false; resState.value = 'IDLE'
}
function startMonitor() {
  if (!cameraActive.value || !faceOnline.value) return
  monitoring.value = true; detectCount.value = 0; failStreak.value = 0
  autoMode.value = true
  doRecognize()
  detectTimer = setInterval(() => { if (!snapLock.value) doRecognize() }, autoInterval)
  countdownTimer = setInterval(() => { nextScanIn.value = Math.max(0, nextScanIn.value - 1) }, 1000)
  nextScanIn.value = Math.round(autoInterval / 1000)
}
function stopMonitor() {
  monitoring.value = false; autoMode.value = false
  if (detectTimer) { clearInterval(detectTimer); detectTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}
function onAutoToggle(v: boolean) { if (v && cameraActive.value && faceOnline.value) startMonitor(); else stopMonitor() }
function onVoiceToggle(v: boolean) { setSpeechEnabled(v) }
async function captureFrame(): Promise<Blob|null> {
  const video = videoRef.value; const canvas = canvasRef.value
  if (!video || !canvas || video.readyState < 2 || !video.videoWidth || !video.videoHeight) return null
  canvas.width = video.videoWidth; canvas.height = video.videoHeight
  canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height)
  return new Promise(resolve => canvas.toBlob(b => resolve((b?.size ? b : null)), 'image/jpeg', 0.92))
}
async function captureOnce() { if (cameraActive.value && !snapLock.value) await doRecognize() }
async function doRecognize() {
  if (snapLock.value) return
  snapLock.value = true; nextScanIn.value = Math.round(autoInterval / 1000)
  const startMs = performance.now()
  try {
    const blob = await captureFrame()
    if (!blob || blob.size === 0) { resState.value = 'NO_FACE'; addRecent('NO_FACE', 'no face', 0, false); return }
    // 连拍第二帧做双帧对比（200ms间隔）
    let frame2: File | undefined
    try {
      await new Promise(r => setTimeout(r, 200))
      const blob2 = await captureFrame()
      if (blob2 && blob2.size > 0) frame2 = new File([blob2], 'frame2.jpg', { type: 'image/jpeg' })
    } catch { /* ignore frame2 errors */ }
    const file = new File([blob], 'camera-frame.jpg', { type: 'image/jpeg' })
    const r = await identifyFaceApi(file, currentDevice.value, '入口', currentUserId.value ?? undefined, monitoring.value ? 'stranger' : 'all', frame2)
    const d: any = r.data?.data || r.data || {}
    const dur = Math.round(performance.now() - startMs)
    const matched = d?.matched === true
    const sim = d?.similarity ?? 0
    const rawName = d?.name || d?.personName || '陌生人'
    const displayName = (matched && rawName !== 'FAMILY' && rawName !== 'UNKNOWN') ? rawName : '陌生人'
    lastResult.value = {
      ...d, name: rawName, displayName, similarity: sim,
      deviceCode: d?.deviceCode || currentDevice.value,
      recognizedAt: fmtNow(), durationMs: dur, liveness: d?.liveness ?? null
    }
    if (d?.personType === 'SPOOF' || d?.spoof) {
      resState.value = 'LIVENESS_FAIL'; addRecent('LIVENESS_FAIL', 'spoof', sim, true)
    } else if ((d?.faces || d?.faceCount || 0) > 1) {
      resState.value = 'MULTI_FACE'; addRecent('MULTI_FACE', 'multi', sim, true)
    } else if (matched) {
      resState.value = 'SUCCESS'; addRecent('SUCCESS', displayName, sim, true); todayKnown.value++
    } else {
      resState.value = 'UNKNOWN'; addRecent('UNKNOWN', 'stranger', sim, true); todayUnknown.value++
    }
    todayTotal.value++
    if (sim > todayMaxSim.value) todayMaxSim.value = sim
    lastRecogTime.value = fmtNow()
    failStreak.value = 0
    if (voiceOn.value) {
      try {
        if (d?.personType === 'SPOOF' || d?.spoof) speakLivenessFail()
        else if ((d?.faces || d?.faceCount || 0) > 1) speakMultiFace()
        else if (matched) speakSuccess(displayName)
        else speakUnknown()
      } catch { /* ignore */ }
    }
  } catch (e: any) {
    failStreak.value++
    resState.value = 'ERROR'; lastError.value = e?.message || 'request failed'
    addRecent('ERROR', 'error', 0, false)
    if (failStreak.value >= 3) {
      stopMonitor()
      ElNotification({ title: '服务异常', message: '连续3次失败，已暂停', type: 'error', duration: 6000 })
    }
  } finally { snapLock.value = false }
}
function addRecent(type: string, name: string, sim: number, spoken: boolean) {
  const cssMap: Record<string,string> = { SUCCESS:'hi-ok', UNKNOWN:'hi-warn', NO_FACE:'hi-gray', MULTI_FACE:'hi-warn', LIVENESS_FAIL:'hi-err', ERROR:'hi-err' }
  const iconMap: Record<string,string> = { SUCCESS:'OK', UNKNOWN:'??', NO_FACE:'--', MULTI_FACE:'++', LIVENESS_FAIL:'!!', ERROR:'XX' }
  recentList.value.unshift({
    id: 'r'+ (++_recIdSeq) +'_'+ Date.now(), type, name, sim: sim ? fmtPct(sim) : '-',
    time: new Date().toLocaleTimeString("zh-CN"), spoken, icon: iconMap[type]||"?", cssCls: cssMap[type]||""
  })
  if (recentList.value.length > 10) recentList.value.length = 10
}
function replayLast() {
  const lr = lastResult.value
  if (!lr) return
  if (resState.value === 'SUCCESS') speakSuccess(lr.displayName || lr.name)
  else if (resState.value === 'UNKNOWN') speakUnknown()
  else if (resState.value === 'LIVENESS_FAIL') speakLivenessFail()
  else speakNoFace()
}
function onRegFile(f: any) { rfile.value = f.raw; const rd = new FileReader(); rd.onload = e => rpreview.value = e.target?.result as string; rd.readAsDataURL(f.raw) }
async function openRegCam() { try { regStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false }); if (regVidRef.value) regVidRef.value.srcObject = regStream; regCamOn.value = true } catch { ElMessage.warning("camera fail") } }
function regCapture() { if (!regVidRef.value||!regCvsRef.value) return; const c = regCvsRef.value; c.width = 320; c.height = 240; c.getContext("2d")?.drawImage(regVidRef.value, 0, 0, 320, 240); rpreview.value = c.toDataURL("image/jpeg", 0.85); c.toBlob(b => { if (b) rfile.value = new File([b], "c.jpg", { type: "image/jpeg" }) }, "image/jpeg"); rphotoTaken.value = true; stopRegCam() }
function regRecapture() { rphotoTaken.value = false; rpreview.value = ''; rfile.value = null; openRegCam() }
function stopRegCam() { if (regStream) { regStream.getTracks().forEach(t => t.stop()); regStream = null; regCamOn.value = false } }
async function doRegister() {
  if (!canReg.value) return; rloading.value = true
  try {
    await registerFaceApi(rname.value, rfile.value!, rname.value, currentUserId.value ?? undefined, rrelation.value)
    rresult.value = "OK"; ElMessage.success("OK: " + rname.value)
    rname.value = ''; rfile.value = null; rpreview.value = ''; rphotoTaken.value = false; loadUsers(); checkService()
  } catch { rresult.value = "ERR"; ElMessage.error("fail") }
  finally { rloading.value = false }
}
// reserved for future use
async function loadUsers() {
  uloading.value = true
  try { const r = await getFaceUsersApi(currentUserId.value ?? undefined); users.value = (Array.isArray(r.data?.data)?r.data.data:(Array.isArray(r.data)?r.data:[])) }
  catch { users.value = [] }
  finally { uloading.value = false }
}
async function doDel(row: any) { const n = row?.personId||row?.name; if (!n) return; try { await deleteFaceUserApi(n, currentUserId.value ?? undefined); ElMessage.success('deleted'); loadUsers(); checkService() } catch { ElMessage.error('fail') } }
function onDrawerClose() { regStream?.getTracks().forEach(t => t.stop()); regStream = null; regCamOn.value = false }
let _visHandler: (()=>void)|null = null
onMounted(async () => {
  await deviceStore.fetchDevices()
  checkService()
  _visHandler = () => { if (document.hidden) stopMonitor(); else if (autoMode.value && cameraActive.value && faceOnline.value) startMonitor() }
  document.addEventListener('visibilitychange', _visHandler)
  voiceStore.onAction('START_LIVE_MONITOR', onVoiceMonitorStart)
  voiceStore.onAction('STOP_LIVE_MONITOR', onVoiceMonitorStop)
})
onUnmounted(() => {
  stopCamera(); regStream?.getTracks().forEach(t => t.stop()); cancelSpeech()
  if (_visHandler) document.removeEventListener('visibilitychange', _visHandler)
  voiceStore.offAction('START_LIVE_MONITOR', onVoiceMonitorStart)
  voiceStore.offAction('STOP_LIVE_MONITOR', onVoiceMonitorStop)
})

function onVoiceMonitorStart() {
  if (!cameraActive.value) startCamera()
  // 等摄像头打开后启动监测
  const check = setInterval(() => {
    if (cameraActive.value) {
      clearInterval(check)
      startMonitor()
    }
  }, 500)
  setTimeout(() => clearInterval(check), 5000) // 5s超时
}
function onVoiceMonitorStop() { stopMonitor() }
</script>

<style scoped>
.face-monitor { width: 100%; }
.fm-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; flex-wrap: wrap; gap: 8px; }
.fm-title { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0; }
.fm-subtitle { font-size: 12px; color: #64748b; margin: 2px 0 0; }
.fm-header-acts { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fm-bar { display: flex; align-items: center; gap: 10px; padding: 8px 14px; border-radius: 8px; font-size: 12px; margin-bottom: 12px; }
.bar-on { background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; }
.bar-off { background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; }
.bar-dot { width: 8px; height: 8px; border-radius: 50%; }
.bar-on .bar-dot { background: #22c55e; }
.bar-off .bar-dot { background: #ef4444; }
.fm-main { display: grid; grid-template-columns: 1fr 420px; gap: 16px; }
@media (max-width: 1100px) { .fm-main { grid-template-columns: 1fr; } }
.cam-card { background: #fff; border-radius: 12px; border: 1px solid #E3EAF3; overflow: hidden; }
.cam-viewport { position: relative; aspect-ratio: 16/9; max-height: 480px; background: #0f172a; display: flex; align-items: center; justify-content: center; }
.cam-viewport.cam-off { background: #f1f5f9; }
.cam-video { width: 100%; height: 100%; object-fit: contain; }
.cam-video.hidden { display: none; }
.cam-empty { text-align: center; color: #94a3b8; padding: 40px; }
.cam-empty p { font-size: 15px; font-weight: 600; margin: 8px 0 4px; color: #64748b; }
.cam-empty span { font-size: 12px; }
.cam-badge { position: absolute; top: 10px; left: 10px; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.cam-badge.ready { background: rgba(34,169,107,.9); color: #fff; }
.cam-badge.live { background: rgba(220,38,38,.9); color: #fff; animation: pulse 1.2s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.6} }
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #fff; }
.cam-scan-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.3); }
.scan-line { position: absolute; left: 0; right: 0; height: 2px; background: linear-gradient(90deg,transparent,#22c55e,transparent); animation: scan 2s ease-in-out infinite; }
@keyframes scan { 0%{top:0} 50%{top:90%} 100%{top:0} }
.cam-scan-overlay span { color: #fff; font-size: 13px; font-weight: 600; text-shadow: 0 1px 4px rgba(0,0,0,.6); z-index: 1; }
.cam-toolbar { display: flex; gap: 6px; padding: 10px 12px; flex-wrap: wrap; background: #fafbfc; border-top: 1px solid #f1f5f9; }
.cam-info { font-size: 11px; color: #68778D; padding: 6px 12px 10px; display: flex; justify-content: space-between; }
.res-card { background: #fff; border-radius: 12px; border: 1px solid #E3EAF3; padding: 20px; min-height: 160px; margin-bottom: 12px; }
.res-card.rc-ok { border-color: #bbf7d0; background: #f9fdfb; }
.res-card.rc-warn { border-color: #fde68a; background: #fffdf7; }
.res-card.rc-err { border-color: #fecaca; background: #fef9f9; }
.res-card.rc-gray { border-color: #e2e8f0; background: #f8fafc; }
.rc-icon { margin-bottom: 4px; }
.rc-ok .rc-icon { color: #22A96B; }
.rc-warn .rc-icon { color: #E9971A; }
.rc-err .rc-icon { color: #E84C5B; }
.rc-gray .rc-icon { color: #68778D; }
.rc-title { font-size: 17px; font-weight: 700; margin-bottom: 10px; color: #0f172a; }
.rc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 16px; font-size: 12px; }
.rc-grid div span { color: #68778D; display: block; font-size: 10px; margin-bottom: 1px; }
.rc-grid div strong { color: #0f172a; font-size: 13px; }
.c-good { color: #22A96B !important; }
.c-warn { color: #E9971A !important; }
.rc-sugg { font-size: 12px; margin-top: 8px; padding: 6px 10px; border-radius: 6px; }
.rc-sugg.good { background: #f0fdf4; color: #166534; }
.rc-sugg.warn { background: #fff7ed; color: #9a3412; }
.rc-hint { font-size: 12px; color: #68778D; margin-top: 8px; line-height: 1.6; }
.rc-acts { margin-top: 10px; display: flex; gap: 6px; flex-wrap: wrap; }
.rc-empty { text-align: center; color: #94a3b8; padding: 30px; }
.rc-empty p { margin-top: 8px; font-size: 13px; }
.hist-card { background: #fff; border-radius: 12px; border: 1px solid #E3EAF3; padding: 14px; margin-bottom: 12px; }
.hist-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.hist-head h4 { font-size: 13px; font-weight: 600; margin: 0; color: #0f172a; }
.hist-count { font-size: 11px; color: #94a3b8; }
.hist-empty { text-align: center; color: #94a3b8; font-size: 12px; padding: 20px; }
.hist-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; font-size: 12px; margin-bottom: 3px; }
.hi-ok { background: #f0fdf4; }
.hi-warn { background: #fff7ed; }
.hi-err { background: #fef2f2; }
.hi-gray { background: #f8fafc; }
.hi-icon { width: 24px; text-align: center; font-weight: 700; }
.hi-name { flex: 1; font-weight: 500; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hi-sim { color: #68778D; min-width: 42px; text-align: right; }
.hi-time { color: #94a3b8; min-width: 56px; text-align: right; font-size: 11px; }
.stats-row { display: flex; gap: 8px; flex-wrap: wrap; }
.stat-chip { display: flex; gap: 4px; padding: 6px 10px; border-radius: 8px; background: #f1f5f9; font-size: 11px; align-items: center; }
.stat-chip span { color: #68778D; }
.stat-chip strong { color: #0f172a; font-size: 14px; }
.stat-chip.ok { background: #f0fdf4; }
.stat-chip.warn { background: #fff7ed; }
.user-drawer { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 0 8px; }
@media (max-width: 600px) { .user-drawer { grid-template-columns: 1fr; } }
.ud-panel { border: 1px solid #f1f5f9; border-radius: 12px; padding: 14px; }
.ud-panel h4 { margin: 0 0 10px; font-size: 14px; font-weight: 600; }
.ud-inp { width: 100%; margin-bottom: 8px; }
.ud-upload { margin-bottom: 6px; }
.ud-preview { max-width: 100%; max-height: 130px; border-radius: 8px; object-fit: contain; }
.ud-cam-box { border-radius: 8px; overflow: hidden; background: #0f172a; min-height: 100px; margin-bottom: 4px; }
.ud-msg { margin-top: 6px; padding: 6px 10px; border-radius: 6px; font-size: 12px; }
.ud-msg.ok { background: #f0fdf4; color: #166534; }
.ud-msg.err { background: #fef2f2; color: #991b1b; }
.ud-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.ud-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
.ud-avatar { width: 36px; height: 36px; border-radius: 50%; background: #3478F6; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; flex-shrink: 0; }
.ud-info { flex: 1; min-width: 0; }
.ud-name { font-weight: 500; font-size: 13px; color: #0f172a; }
.ud-meta { font-size: 11px; color: #94a3b8; }
</style>
