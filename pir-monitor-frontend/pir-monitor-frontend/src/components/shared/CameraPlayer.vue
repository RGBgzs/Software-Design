<template>
  <div class="camera-player">
    <div class="camera-placeholder" v-if="!streamUrl && !snapshotUrl">
      <el-icon :size="48"><VideoCamera /></el-icon>
      <span>暂无画面</span>
    </div>
    <img v-else-if="snapshotUrl" :src="snapshotUrl" alt="摄像头截图" class="camera-snapshot" />
    <video
      v-else-if="streamUrl"
      :src="streamUrl"
      autoplay
      muted
      class="camera-video"
      @error="onError"
    />
    <div class="camera-actions" v-if="streamUrl || snapshotUrl">
      <el-button size="small" @click="refresh" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新画面
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { VideoCamera, Refresh } from '@element-plus/icons-vue'

const props = defineProps<{
  streamUrl?: string
  snapshotUrl?: string
}>()

const emit = defineEmits<{
  refresh: []
}>()

const loading = ref(false)

function refresh() {
  loading.value = true
  emit('refresh')
  setTimeout(() => (loading.value = false), 2000)
}

function onError() {
  console.warn('视频流加载失败')
}
</script>

<style scoped>
.camera-player {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #1a1a1a;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.camera-placeholder {
  color: #666;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.camera-snapshot,
.camera-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.camera-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}
</style>
