<template>
  <div class="face-capture-panel">
    <div class="panel-header">
      <h3><el-icon><Camera /></el-icon> 人脸抓拍识别</h3>
      <el-button size="small" @click="showPanel = !showPanel">
        {{ showPanel ? '收起' : '展开' }}
      </el-button>
    </div>

    <div v-if="showPanel">
      <div v-if="captures.length === 0" class="empty">等待人体感应触发自动抓拍...</div>

      <div class="capture-list" v-else>
        <div
          v-for="(item, idx) in captures"
          :key="idx"
          class="capture-item"
          :class="{ danger: item.result && !item.result.matched, success: item.result && item.result.matched }"
        >
          <div class="capture-time">{{ fmt(item.timestamp) }}</div>
          <div class="capture-device">设备：{{ item.deviceCode }}</div>

          <div v-if="item.loading" class="capture-loading">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>正在抓拍识别...</span>
          </div>

          <div v-else-if="item.error" class="capture-error">
            <el-icon><WarningFilled /></el-icon> {{ item.error }}
          </div>

          <div v-else-if="item.result" class="capture-result">
            <img :src="item.snapshot" class="snapshot-img" />
            <div class="result-info">
              <div class="result-name" :class="{ stranger: !item.result.matched }">
                <el-icon v-if="item.result.matched"><SuccessFilled /></el-icon>
                <el-icon v-else><WarningFilled /></el-icon>
                {{ item.result.matched ? item.result.name : `陌生人（${item.result.name}）` }}
              </div>
              <div class="result-detail">
                <el-tag :type="riskType(item.result.riskLevel)" size="small">
                  {{ item.result.riskLevel }}
                </el-tag>
                <span>匹配度：{{ (item.result.similarity).toFixed(1) }}%</span>
              </div>
              <div class="result-suggestion">{{ item.result.suggestion }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Camera, Loading, WarningFilled, SuccessFilled } from '@element-plus/icons-vue'
import { useFaceCapture } from '@/composables/useFaceCapture'

const { captures } = useFaceCapture()
const showPanel = ref(true)

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function riskType(level: string) {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  return 'info'
}
</script>

<style scoped>
.face-capture-panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-top: 16px;
  background: #fff;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.offline-notice {
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  color: #e6a23c;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
}
.empty {
  color: #909399;
  text-align: center;
  padding: 32px 0;
}
.capture-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 600px;
  overflow-y: auto;
}
.capture-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  transition: border-color .3s;
}
.capture-item.success {
  border-color: #67c23a;
  background: #f0f9eb;
}
.capture-item.danger {
  border-color: #f56c6c;
  background: #fef0f0;
}
.capture-time {
  font-size: 12px;
  color: #909399;
}
.capture-device {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.capture-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  color: #409eff;
}
.capture-error {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #f56c6c;
  padding: 8px 0;
}
.capture-result {
  display: flex;
  gap: 12px;
}
.snapshot-img {
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  flex-shrink: 0;
}
.result-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.result-name {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}
.result-name.stranger { color: #f56c6c; }
.result-detail {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.result-suggestion {
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
}
</style>
