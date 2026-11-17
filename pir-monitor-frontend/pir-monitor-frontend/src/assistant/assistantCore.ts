/**
 * Assistant Core —— 参考 leon-ai/leon 架构
 *
 * 流程：
 *   text → parseIntent → executeIntent → result
 *
 * 模式：
 *   controlled — 本地规则匹配，不调 AI
 *   smart      — 本地规则 + AI 总结
 *   agent      — 完全 AI 决策（未来扩展）
 */

import type { AssistantContext, AssistantResult, UserIntent } from './types'
import { parseIntent } from './intentEngine'
import { executeIntent } from './actionExecutor'
import { handleConfirmCancel } from './safetyGuard'
import { registerAllCapabilities } from './capabilities/index'

export type { AssistantContext, AssistantResult, UserIntent }

export function createAssistant(): AssistantCore {
  registerAllCapabilities()
  return new AssistantCore()
}

export class AssistantCore {
  context: AssistantContext = {
    conversationActive: false,
  }

  /** 处理用户输入文本，返回结果 */
  async handleText(text: string): Promise<AssistantResult> {
    // 1. Handle pending confirmation
    if (this.context.pendingAction) {
      const result = handleConfirmCancel(text, this.context)
      if (result) return result
    }

    // 2. Parse intent locally
    const intent = parseIntent(text, this.context)

    // 3. Execute
    const result = await executeIntent(intent, this.context)

    // 4. If need confirm, store pending action
    if (result.needConfirm && result.action) {
      this.context.pendingAction = result.action
    }

    // 5. Update context
    this.context.lastIntent = intent
    this.context.lastAnswer = result.message

    return result
  }

  /** 开始连续对话 */
  startConversation(): void {
    this.context.conversationActive = true
  }

  /** 结束连续对话 */
  endConversation(): void {
    this.context.conversationActive = false
    this.context.pendingAction = undefined
  }

  /** 重置上下文 */
  reset(): void {
    this.context = { conversationActive: false }
  }
}
