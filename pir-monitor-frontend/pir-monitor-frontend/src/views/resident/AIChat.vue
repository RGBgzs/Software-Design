<template>
  <div class="ai-chat">
    <div class="chat-main">
      <div class="chat-header">
        <h3>安防 AI 问答</h3>
        <el-button size="small" @click="chatStore.clearMessages()">清空对话</el-button>
      </div>

      <div class="chat-messages" ref="msgContainer">
        <div v-if="chatStore.messages.length === 0" class="quick-questions">
          <p class="quick-title">选择常见问题，或在下方向 AI 提问：</p>
          <el-button v-for="q in quickQuestions" :key="q" size="small" @click="handleQuick(q)" class="quick-btn" :disabled="chatStore.sending">
            {{ q }}
          </el-button>
        </div>

        <div v-for="msg in chatStore.messages" :key="msg.id" class="message" :class="msg.role">
          <div class="message-avatar">
            <el-avatar :size="36" :icon="msg.role === 'user' ? UserFilled : Service" />
          </div>
          <div class="message-body">
            <div class="message-bubble">
              <div class="message-content" v-html="renderMarkdown(msg.content)" />
            </div>
            <div v-if="msg.role === 'assistant' && msg.suggestions && msg.suggestions.length" class="message-suggestions">
              <el-button v-for="(s, i) in msg.suggestions" :key="i" size="small" plain @click="handleQuick(s)" :disabled="chatStore.sending">
                {{ s }}
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputText"
          placeholder="输入安防相关问题，按 Enter 发送"
          @keydown.enter.prevent="handleSend"
          :disabled="chatStore.sending"
          clearable
        >
          <template #append>
            <el-button @click="handleSend" :loading="chatStore.sending" type="primary">
              {{ chatStore.sending ? '发送中...' : '发送' }}
            </el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { UserFilled, Service } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'

const chatStore = useChatStore()
const authStore = useAuthStore()
const inputText = ref('')
const msgContainer = ref<HTMLElement>()

const quickQuestions = [
  '当前安防态势如何？',
  '如何设置布防时间？',
  '收到入侵告警后应该怎么做？',
  '摄像头无法连接怎么办？',
  '如何添加新的感应设备？',
]

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.sending) return

  const userId = authStore.user?.userId || authStore.user?.id || 1
  inputText.value = ''
  await chatStore.sendMessage(userId, text)
  await nextTick()
  scrollToBottom()
}

async function handleQuick(q: string) {
  inputText.value = q
  await handleSend()
}

function scrollToBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}

function renderMarkdown(text: string) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br/>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
}

</script>

<style scoped>
.ai-chat {
  display: flex;
  height: calc(100vh - 140px);
  gap: 16px;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
}
.chat-header h3 {
  margin: 0;
  font-size: 16px;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.message.user {
  flex-direction: row-reverse;
}
.message-body {
  max-width: 70%;
}
.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  background: #f0f2f5;
}
.message.user .message-bubble {
  background: #409eff;
  color: #fff;
}
.message-content code {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
}
.message-suggestions {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.quick-questions {
  text-align: center;
  margin-top: 60px;
}
.quick-title {
  color: #909399;
  margin-bottom: 12px;
  font-size: 14px;
}
.quick-btn {
  margin: 4px;
}
.chat-input {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
}
</style>
