import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, AiAgentResponse } from '@/types'
import { agentChatApi } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)

  async function sendMessage(userId: number, question: string) {
    const userMsg: ChatMessage = {
      id: `user_${Date.now()}`,
      role: 'user',
      content: question,
      timestamp: new Date().toISOString(),
    }
    messages.value.push(userMsg)

    const aiMsg: ChatMessage = {
      id: `ai_${Date.now()}`,
      role: 'assistant',
      content: '',
      timestamp: new Date().toISOString(),
    }
    messages.value.push(aiMsg)

    sending.value = true

    try {
      const res = await agentChatApi(userId, question)
      const data = res.data as { code: number; message: string; data: AiAgentResponse }
      if (data.code === 200 && data.data) {
        aiMsg.content = data.data.answer
        aiMsg.intent = data.data.intent
        aiMsg.riskLevel = data.data.riskLevel
        aiMsg.evidence = data.data.evidence
        aiMsg.suggestions = data.data.suggestions
        aiMsg.actionDraft = data.data.actionDraft ?? undefined
      } else {
        aiMsg.content = '抱歉，回复生成失败，请重试。'
      }
    } catch {
      aiMsg.content = '抱歉，回复生成失败，请检查网络后重试。'
    } finally {
      sending.value = false
    }
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    messages,
    sending,
    sendMessage,
    clearMessages,
  }
})
