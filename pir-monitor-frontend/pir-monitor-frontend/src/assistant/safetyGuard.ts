/**
 * 安全确认层
 * 敏感操作必须二次确认后才执行
 */

import type { AssistantContext, AssistantResult } from './types'

const CONFIRM_WORDS = ['确认', '确认执行', '确定', '执行吧', '可以']
const CANCEL_WORDS = ['取消', '算了', '不用了', '不要']

export function isConfirmText(text: string): boolean {
  const c = text.replace(/\s+/g, '').replace(/[，,。.!！?？；;：:、]/g, '')
  return CONFIRM_WORDS.some(w => c.includes(w))
}

export function isCancelText(text: string): boolean {
  const c = text.replace(/\s+/g, '').replace(/[，,。.!！?？；;：:、]/g, '')
  return CANCEL_WORDS.some(w => c.includes(w))
}

export function createPendingPrompt(actionType: string): string {
  const labels: Record<string, string> = {
    ARM_DEVICE: '开启布防',
    DISARM_DEVICE: '撤销布防',
    SEND_HELP: '发送紧急求助',
    SEND_MESSAGE: '发送消息',
    HANDLE_ALARM: '处理告警',
    CLEAR_CHAT: '清空对话',
    CONFIRM_INTENT: '确认意图',
  }
  const label = labels[actionType] || actionType
  return `⚠️ 这是敏感操作（${label}），请确认是否继续。你可以说「确认执行」或「取消」。`
}

export function handleConfirmCancel(
  text: string,
  context: AssistantContext
): AssistantResult | null {
  if (!context.pendingAction) return null

  if (isCancelText(text)) {
    const action = context.pendingAction
    context.pendingAction = undefined
    action.resolve?.(false)
    return { success: true, message: '已取消操作。', speak: true }
  }

  if (isConfirmText(text)) {
    const action = context.pendingAction
    context.pendingAction = undefined
    action.resolve?.(true)
    return { success: true, message: '操作已确认，正在执行。', speak: true }
  }

  // Still pending — remind user
  return { success: false, message: '请说「确认执行」或「取消」。', speak: true }
}
