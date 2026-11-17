<template>
  <div class="camera-view">
    <el-page-header @back="$router.back()" title="返回"><template #content><span>摄像头 — {{ deviceCode }}</span></template></el-page-header>
    <el-row :gutter="20" style="margin-top:16px">
      <el-col :span="16">
        <el-card shadow="never"><template #header><span>实时画面</span><el-button size="small" style="float:right" @click="capture" :loading="capturing"><el-icon><Camera /></el-icon>截图</el-button></template><CameraPlayer :snapshot-url="snapshotUrl" @refresh="loadStream" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><template #header>设备信息</template><el-descriptions :column="2" size="small"><el-descriptions-item label="编号">{{ deviceCode }}</el-descriptions-item></el-descriptions></el-card>
        <el-card shadow="never" style="margin-top:16px"><template #header>截图历史</template><div v-if="snapshots.length" class="snap-list"><img v-for="(u,i) in snapshots" :key="i" :src="u" class="snap-thumb" @click="snapshotUrl=u" /></div><el-empty v-else description="暂无截图" :image-size="60" /></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { getCameraStreamUrlApi, captureSnapshotApi } from '@/api/camera'
import CameraPlayer from '@/components/shared/CameraPlayer.vue'

const props = defineProps<{ deviceId: string }>()
const deviceCode = ref(props.deviceId)
const snapshotUrl = ref('')
const snapshots = ref<string[]>([])
const capturing = ref(false)

onMounted(() => loadStream())

async function loadStream() { try { await getCameraStreamUrlApi(deviceCode.value) } catch { /* */ } }
async function capture() {
  capturing.value = true
  try { const r = await captureSnapshotApi(deviceCode.value); const u = r.data.data.snapshotUrl; snapshotUrl.value = u; snapshots.value.unshift(u); if (snapshots.value.length > 10) snapshots.value.pop(); ElMessage.success('截图保存') }
  catch { ElMessage.error('截图失败') }
  finally { capturing.value = false }
}
</script>

<style scoped>
.camera-view { padding: 16px; }
.snap-list { display: flex; flex-wrap: wrap; gap: 8px; }
.snap-thumb { width: 80px; height: 60px; object-fit: cover; border-radius: 4px; cursor: pointer; border: 2px solid transparent; }
.snap-thumb:hover { border-color: #409eff; }
</style>
