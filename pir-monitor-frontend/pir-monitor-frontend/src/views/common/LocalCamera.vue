<template>
  <div class="local-camera-page">
    <el-page-header @back="$router.back()" title="返回"><template #content><span>本地摄像头</span></template></el-page-header>
    <el-card shadow="never" style="margin-top:16px;text-align:center">
      <video ref="videoRef" autoplay playsinline class="local-video" v-show="streaming" />
      <div v-show="!streaming" class="no-camera">
        <el-icon :size="64"><VideoCamera /></el-icon>
        <p>点击下方按钮打开电脑摄像头</p>
      </div>
      <div style="margin-top:16px">
        <el-button :type="streaming?'danger':'primary'" @click="toggleCamera"><el-icon><VideoCamera /></el-icon>{{ streaming ? '关闭摄像头' : '打开摄像头' }}</el-button>
        <el-button @click="capture" :disabled="!streaming"><el-icon><Camera /></el-icon>截图</el-button>
      </div>
      <div v-if="snapshot" style="margin-top:16px">
        <h4>截图预览</h4>
        <img :src="snapshot" class="snapshot-img" @click="downloadSnapshot" style="cursor:pointer" title="点击下载" />
        <p style="color:#909399;font-size:12px;margin-top:4px">点击图片下载</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoCamera, Camera } from '@element-plus/icons-vue'

const videoRef = ref<HTMLVideoElement>()
const streaming = ref(false)
const snapshot = ref('')
let stream: MediaStream | null = null

function toggleCamera() {
  if (streaming.value) stopCamera()
  else startCamera()
}

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    if (videoRef.value) videoRef.value.srcObject = stream
    streaming.value = true
  } catch {
    ElMessage.error('无法访问摄像头')
  }
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
  streaming.value = false
}

function capture() {
  const video = videoRef.value
  if (!video) return
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d')!.drawImage(video, 0, 0)
  snapshot.value = canvas.toDataURL('image/png')
  ElMessage.success('截图已保存')
}

function downloadSnapshot() {
  if (!snapshot.value) return
  const a = document.createElement('a')
  a.href = snapshot.value
  a.download = `截图_${new Date().toLocaleString('zh-CN').replace(/[/:, ]/g, '_')}.png`
  a.click()
  ElMessage.success('截图已下载')
}

onUnmounted(() => stopCamera())
</script>

<style scoped>
.local-camera-page { padding: 16px; }
.local-video { width: 100%; max-width: 720px; border-radius: 8px; background: #000; }
.no-camera { padding: 60px 0; color: #909399; }
.no-camera p { margin-top: 12px; }
.snapshot-img { max-width: 100%; max-height: 400px; border-radius: 8px; border: 1px solid #e4e7ed; }
</style>
