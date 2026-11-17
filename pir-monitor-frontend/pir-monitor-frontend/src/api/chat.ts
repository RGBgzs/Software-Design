import http from './index'
import type { ApiResponse, AiAgentResponse } from '@/types'

export function agentChatApi(userId: number, message: string) {
  return http.post<ApiResponse<AiAgentResponse>>('/ai/agent-chat', { userId, message })
}
