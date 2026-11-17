/**
 * 核心类型定义 —— 参考 leon-ai/leon 的 Skills -> Actions -> Tools -> Functions 架构
 */

export type AssistantMode = 'controlled' | 'smart' | 'agent'

export type IntentType =
  | 'QUERY_DATA'
  | 'CONTROL_ACTION'
  | 'OPEN_MODULE'
  | 'SENSITIVE_ACTION'
  | 'AI_CHAT'
  | 'STOP_SPEAKING'
  | 'CONFIRM_ACTION'
  | 'CANCEL_ACTION'
  | 'UNKNOWN'

export interface UserIntent {
  type: IntentType
  action?: string
  module?: string
  target?: string
  params?: Record<string, any>
  sensitivity?: boolean
  confidence: number
  rawText: string
  /** 是否需要二次确认（语音容错层标记） */
  needConfirm?: boolean
  /** 模糊匹配命中的别名 */
  matchedAlias?: string
}

export interface AssistantContext {
  currentModule?: string
  lastIntent?: UserIntent
  lastAnswer?: string
  lastData?: any
  conversationActive: boolean
  pendingAction?: PendingAction
  /** 最近一次查询的设备列表 */
  lastDevices?: any[]
  /** 最近一次查询的告警列表 */
  lastAlarms?: any[]
  /** 最近一次 PIR 数据 */
  lastPirData?: any
  /** 风险等级 */
  lastRiskLevel?: string
}

export interface PendingAction {
  id: string
  type: string
  label: string
  payload?: any
  resolve?: (ok: boolean) => void
}

export interface AssistantResult {
  success: boolean
  message: string
  data?: any
  speak?: boolean
  needConfirm?: boolean
  action?: PendingAction
}
