<template>
  <div class="chat-history-page">
    <PageHeader title="聊天记录" desc="AI 问答与语音助手历史">
      <el-button :icon="RefreshRight" @click="loadRecords" :loading="loading">刷新</el-button>
    </PageHeader>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索聊天内容..." clearable @clear="loadRecords" @keyup.enter="loadRecords" style="width: 280px" :prefix-icon="Search" />
    </div>

    <div v-loading="loading" class="records-list">
      <div v-if="records.length" class="record-cards">
        <div v-for="r in records" :key="r.id" class="record-card">
          <div class="record-header">
            <span class="record-source">{{ r.source || 'AI助手' }}</span>
            <span class="record-time">{{ fmt(r.createTime || r.createdAt) }}</span>
          </div>
          <div class="record-body">
            <div class="record-q"><strong>Q:</strong> {{ r.userMessage || r.question || '-' }}</div>
            <div class="record-a"><strong>A:</strong> {{ truncate(r.aiReply || r.answer || r.response || '-', 120) }}</div>
          </div>
        </div>
      </div>
      <EmptyState v-else text="暂无聊天记录" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import PageHeader from '@/views/unified/components/PageHeader.vue'
import EmptyState from '@/views/unified/components/EmptyState.vue'
import { getChatRecords } from '@/api/humanSecurity'

const loading = ref(false)
const keyword = ref('')
const records = ref<any[]>([])

onMounted(() => loadRecords())

async function loadRecords() {
  loading.value = true
  try {
    const res = await getChatRecords(keyword.value || undefined)
    const data = res.data?.data || res.data
    records.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function truncate(text: string, max: number) {
  if (!text) return '-'
  return text.length > max ? text.slice(0, max) + '...' : text
}

function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
</script>

<style scoped>
.chat-history-page { width: 100%; }
.toolbar { margin-bottom: 16px; }
.records-list { min-height: 200px; }
.record-cards { display: flex; flex-direction: column; gap: 12px; }
.record-card { background: #fff; border-radius: 12px; padding: 14px 18px; box-shadow: 0 2px 8px rgba(0,0,0,.04); }
.record-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.record-source { font-size: 12px; color: #2563eb; font-weight: 600; }
.record-time { font-size: 11px; color: #94a3b8; }
.record-q { font-size: 13px; color: #334155; margin-bottom: 4px; }
.record-a { font-size: 13px; color: #64748b; }
</style>
