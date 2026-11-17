/**
 * DeepSeek LLM 客户端
 * 所有问答统一走 /api/ai/agent-chat
 */
import type { AssistantContext } from './types'

interface AskDeepSeekOptions {
  message: string
  systemContext?: any
  context?: AssistantContext
  instruction?: string
}

export async function askDeepSeek(options: AskDeepSeekOptions): Promise<string> {
  try {
    const res = await fetch('/api/ai/agent-chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 1,
        message: options.message,
        systemContext: options.systemContext || null,
        instruction: options.instruction || null,
      }),
    })

    if (!res.ok) {
      return '我暂时无法连接 AI 服务，但可以继续为你执行本地安防操作。'
    }

    const json = await res.json()
    const data = json?.data || json

    return (
      data?.answer ||
      data?.message ||
      data?.reply ||
      data?.content ||
      '我已经收到你的问题，但暂时没有生成有效回答。'
    )
  } catch {
    return '__AI_UNAVAILABLE__:AI 服务暂不可用，你可以尝试其他本地操作。'
  }
}

/** 判断 DeepSeek 返回的文本是否表示 AI 不可用 */
export function isAiUnavailable(reply: string): boolean {
  if (!reply) return true
  if (reply.startsWith('__AI_UNAVAILABLE__:')) return true
  if (reply.includes('暂时无法') && reply.includes('AI')) return true
  if (reply.includes('暂不可用')) return true
  if (reply.includes('暂时没有生成有效回答')) return true
  return false
}

/** 从 isAiUnavailable 消息中提取可读文本 */
export function extractAiFallbackText(reply: string): string {
  return reply.replace('__AI_UNAVAILABLE__:', '')
}
