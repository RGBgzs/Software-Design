import { defineStore } from 'pinia'
import { ref, readonly } from 'vue'

/** 语音动作类型 */
export type VoiceActionType =
  | 'OPEN_MODULE'
  | 'REFRESH_CURRENT'
  | 'QUERY_SECURITY_STATUS'
  | 'QUERY_DEVICE_STATUS'
  | 'QUERY_PIR_TODAY'
  | 'QUERY_ALARMS'
  | 'READ_LATEST_ALARM'
  | 'OPEN_CAMERA'
  | 'CLOSE_CAMERA'
  | 'START_LIVE_MONITOR'
  | 'STOP_LIVE_MONITOR'
  | 'FACE_RECOGNIZE_ONCE'
  | 'OPEN_PERSON_MANAGER'
  | 'QUERY_REGISTERED_PERSONS'
  | 'ARM_DEVICE'
  | 'DISARM_DEVICE'
  | 'SEND_HELP'
  | 'HANDLE_ALARM'
  | 'STOP_SPEAKING'
  | 'CLEAR_CHAT'
  | 'AI_CHAT'
  | 'WAKE_ONLY'
  | 'NONE'

export interface VoiceAction {
  type: VoiceActionType
  module?: string
  payload?: any
  needConfirm?: boolean
  confirmed?: boolean
  /** callback after execution */
  _resolve?: (result: VoiceActionResult) => void
}

export interface VoiceActionResult {
  success: boolean
  reply: string
  action?: string
  data?: any
}

export const useVoiceActionStore = defineStore('voiceAction', () => {
  // === State ===
  const lastAction = ref<VoiceAction | null>(null)
  const lastResult = ref<VoiceActionResult | null>(null)
  const pendingAction = ref<VoiceAction | null>(null)
  const isProcessing = ref(false)

  /** Active listeners: key -> callback */
  const listeners = new Map<string, Set<(action: VoiceAction) => void>>()

  // === Listen / Unlisten ===

  function onAction(key: string, cb: (action: VoiceAction) => void) {
    if (!listeners.has(key)) listeners.set(key, new Set())
    listeners.get(key)!.add(cb)
  }

  function offAction(key: string, cb: (action: VoiceAction) => void) {
    listeners.get(key)?.delete(cb)
  }

  // === Dispatch ===

  async function dispatch(action: VoiceAction): Promise<VoiceActionResult> {
    lastAction.value = action
    isProcessing.value = true

    // If needs confirmation and not yet confirmed
    if (action.needConfirm && !action.confirmed) {
      pendingAction.value = action
      isProcessing.value = false
      return new Promise((resolve) => {
        action._resolve = resolve
      })
    }

    // Broadcast to listeners
    const key = action.type
    const subs = listeners.get(key)
    if (subs) {
      for (const cb of subs) {
        try { cb(action) } catch (e) { console.warn(`[voiceAction] listener error for ${key}:`, e) }
      }
    }

    // Also broadcast to '*' catch-all
    const catchAll = listeners.get('*')
    if (catchAll) {
      for (const cb of catchAll) {
        try { cb(action) } catch (e) { console.warn('[voiceAction] catch-all error:', e) }
      }
    }

    isProcessing.value = false

    const result: VoiceActionResult = {
      success: true,
      reply: '',
      action: action.type,
    }
    lastResult.value = result
    return result
  }

  function confirmPending(ok: boolean): VoiceActionResult {
    const action = pendingAction.value
    pendingAction.value = null

    if (!action) {
      return { success: false, reply: '没有待确认的操作。' }
    }

    if (!ok) {
      const result: VoiceActionResult = { success: false, reply: '已取消操作。', action: action.type }
      action._resolve?.(result)
      action._resolve = undefined
      return result
    }

    action.confirmed = true
    // Re-dispatch with confirmed=true
    const result: VoiceActionResult = { success: true, reply: '操作已确认，正在执行。', action: action.type }
    action._resolve?.(result)
    action._resolve = undefined
    dispatch(action)
    return result
  }

  function clearPending() {
    if (pendingAction.value) {
      pendingAction.value._resolve?.({ success: false, reply: '已取消。', action: pendingAction.value.type })
      pendingAction.value._resolve = undefined
      pendingAction.value = null
    }
  }

  return {
    lastAction: readonly(lastAction),
    lastResult: readonly(lastResult),
    pendingAction: readonly(pendingAction),
    isProcessing: readonly(isProcessing),
    dispatch,
    onAction,
    offAction,
    confirmPending,
    clearPending,
  }
})
