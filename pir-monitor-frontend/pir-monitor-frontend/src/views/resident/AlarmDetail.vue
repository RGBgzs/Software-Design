<template>
  <div class="alarm-detail" v-loading="loading">
    <el-page-header @back="$router.back()" title="返回"><template #content><span>告警详情</span></template></el-page-header>
    <template v-if="alarm">
      <el-card class="info-card" shadow="never">
        <template #header>告警信息</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备">{{ alarm.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="编号">{{ alarm.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ alarm.alarmType }}</el-descriptions-item>
          <el-descriptions-item label="等级"><el-tag :type="lv(alarm.alarmLevel)">{{ alarm.alarmLevel }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="处理"><el-tag :type="st(alarm.handleStatus)">{{ stt(alarm.handleStatus) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="时间">{{ fmt(alarm.alarmTime) }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">{{ alarm.alarmContent }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
      <el-card class="info-card" shadow="never">
        <template #header>摄像头画面</template>
        <CameraPlayer :snapshot-url="snapshotUrl" @refresh="capture" />
      </el-card>
      <el-card class="info-card" shadow="never" v-if="alarm.handleStatus===0">
        <el-space>
          <el-button type="success" @click="handleResolve(1)">标记处理</el-button>
          <el-button type="warning" @click="handleResolve(2)">标记误报</el-button>
        </el-space>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { HandleStatus } from '@/types'
import { useAlarmStore } from '@/stores/alarm'
import { captureSnapshotApi } from '@/api/camera'
import CameraPlayer from '@/components/shared/CameraPlayer.vue'

const props = defineProps<{ id: string }>()
const alarmStore = useAlarmStore()
const loading = ref(false)
const snapshotUrl = ref('')
const alarm = ref(alarmStore.currentDetail)

onMounted(async () => {
  loading.value = true
  try { await alarmStore.fetchAlarmDetail(Number(props.id)); alarm.value = alarmStore.currentDetail }
  catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
})

async function capture() {
  if (!alarm.value) return
  try { const r = await captureSnapshotApi(alarm.value.deviceCode); snapshotUrl.value = r.data.data.snapshotUrl } catch { /* */ }
}

async function handleResolve(s: 1|2) {
  if (!alarm.value) return
  try { await alarmStore.handleAlarm(alarm.value.id, s); alarm.value.handleStatus = s; ElMessage.success('操作成功') }
  catch { ElMessage.error('操作失败') }
}

function lv(l: string) { return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[l] as string }
function st(s: HandleStatus) { return { 0: 'warning', 1: 'success', 2: 'info' }[s] as string }
function stt(s: HandleStatus) { return { 0: '未处理', 1: '已处理', 2: '误报' }[s] }
function fmt(t: string) { return new Date(t).toLocaleString('zh-CN') }
</script>

<style scoped>
.alarm-detail { max-width: 960px; margin: 0 auto; }
.info-card { margin-top: 16px; }
</style>
