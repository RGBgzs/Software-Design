<template>
  <div class="face-register">
    <h3>人脸注册</h3>

    <el-row :gutter="20">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>拍照 / 上传照片</template>
          <div class="camera-area">
            <video ref="videoRef" autoplay playsinline class="live-video" v-show="cameraOn" />
            <div v-show="!cameraOn && !previewUrl" class="placeholder">
              <el-icon :size="48"><Camera /></el-icon>
              <p>打开摄像头或上传照片</p>
            </div>
            <img v-if="previewUrl" :src="previewUrl" class="preview-img" />
          </div>
          <div class="camera-actions">
            <el-button :type="cameraOn ? 'danger' : 'primary'" @click="toggleCamera">
              <el-icon><VideoCamera /></el-icon>{{ cameraOn ? '关闭摄像头' : '打开摄像头' }}
            </el-button>
            <el-button @click="capture" :disabled="!cameraOn">
              <el-icon><Camera /></el-icon>拍照
            </el-button>
            <el-upload :show-file-list="false" :before-upload="handleUpload" accept="image/*">
              <el-button><el-icon><Picture /></el-icon>选择照片</el-button>
            </el-upload>
            <el-button v-if="previewUrl" type="warning" @click="clearPreview">
              <el-icon><Delete /></el-icon>清除
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <template #header>确认注册</template>
          <el-form>
            <el-form-item label="姓名">
              <el-input v-model="personName" placeholder="输入姓名" :disabled="uploading" />
            </el-form-item>
            <el-form-item label="照片">
              <el-tag v-if="previewUrl" type="success">已选择</el-tag>
              <el-tag v-else type="info">未选择</el-tag>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRegister" :loading="uploading" :disabled="!previewUrl || !personName.trim()">
                {{ uploading ? '注册中...' : '提交注册' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top:16px">
          <template #header>已注册人脸</template>
          <div v-loading="loading">
            <div v-for="u in users" :key="u.name" class="user-item">
              <el-avatar :size="32">{{ u.name?.charAt(0) }}</el-avatar>
              <span>{{ u.name }}</span>
              <el-tag size="small">{{ u.registeredAt ? new Date(u.registeredAt).toLocaleDateString('zh-CN') : '' }}</el-tag>
              <el-button size="small" type="danger" link @click="handleDelete(u.name)">删除</el-button>
            </div>
            <el-empty v-if="!loading && users.length === 0" description="暂无注册" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera, Camera, Picture, Delete } from '@element-plus/icons-vue'
import { registerFaceApi, getFaceUsersApi, deleteFaceUserApi } from '@/api/face'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const videoRef = ref<HTMLVideoElement>()
const cameraOn = ref(false)
const previewUrl = ref('')
const personName = ref('')
const uploading = ref(false)
const loading = ref(false)
const users = ref<{ name: string; registeredAt?: string }[]>([])
let stream: MediaStream | null = null
let capturedFile: File | null = null

onMounted(async () => {
  personName.value = authStore.user?.realName || authStore.user?.username || ''
  await fetchUsers()
})

onUnmounted(() => stopCamera())

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getFaceUsersApi()
    const body = res.data as { code: number; data: { name: string; registeredAt?: string }[] }
    users.value = body.data || []
  } catch { /* */ }
  finally { loading.value = false }
}

function toggleCamera() {
  if (cameraOn.value) stopCamera()
  else { clearPreview(); startCamera() }
}

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    if (videoRef.value) videoRef.value.srcObject = stream
    cameraOn.value = true
  } catch {
    ElMessage.error('无法访问摄像头')
  }
}

function stopCamera() {
  if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null }
  cameraOn.value = false
}

function capture() {
  const video = videoRef.value
  if (!video) return
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d')!.drawImage(video, 0, 0)
  previewUrl.value = canvas.toDataURL('image/jpeg', 0.9)
  capturedFile = dataUrlToFile(previewUrl.value, 'capture.jpg')
  stopCamera()
  ElMessage.success('拍照成功')
}

function dataUrlToFile(dataUrl: string, filename: string) {
  const [header, data] = dataUrl.split(',')
  const mime = header.match(/:(.*?);/)![1]
  const bstr = atob(data)
  const n = bstr.length
  const u8arr = new Uint8Array(n)
  for (let i = 0; i < n; i++) u8arr[i] = bstr.charCodeAt(i)
  return new File([u8arr], filename, { type: mime })
}

function handleUpload(file: File) {
  previewUrl.value = URL.createObjectURL(file)
  capturedFile = file
  return false
}

function clearPreview() {
  previewUrl.value = ''
  capturedFile = null
}

async function handleDelete(name: string) {
  try {
    await ElMessageBox.confirm(`确认删除「${name}」的人脸信息？`, '提示', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    await deleteFaceUserApi(name)
    ElMessage.success('已删除')
    await fetchUsers()
  } catch { /* 取消或失败 */ }
}

async function handleRegister() {
  const name = personName.value.trim()
  if (!name) { ElMessage.warning('请输入姓名'); return }
  if (!capturedFile && !previewUrl) { ElMessage.warning('请拍照或选择照片'); return }

  if (!capturedFile && previewUrl.value.startsWith('data:')) {
    capturedFile = dataUrlToFile(previewUrl.value, 'capture.jpg')
  }
  if (!capturedFile) { ElMessage.warning('照片数据异常'); return }

  uploading.value = true
  try {
    const uid = authStore.user?.userId || authStore.user?.id || 0
    await registerFaceApi(name, capturedFile, name, uid || undefined)
    ElMessage.success('人脸注册成功')
    clearPreview()
    await fetchUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '注册失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.face-register h3 { margin-bottom: 16px; }
.camera-area {
  background: #1a1a1a;
  border-radius: 6px;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.live-video, .preview-img {
  width: 100%;
  max-height: 400px;
  object-fit: contain;
}
.placeholder {
  color: #666;
  text-align: center;
}
.placeholder p { margin-top: 8px; }
.camera-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.user-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}
.user-item:last-child { border-bottom: none; }
</style>
