/**
 * 动作执行器 —— 完整语音指令执行
 * 识别到操作 → 解析意图 → 二次确认危险操作 → 调用真实函数/接口 → 刷新状态 → 语音反馈
 */

import type { AssistantContext, AssistantResult, UserIntent } from './types'
import { getMatchedCapability } from './capabilityRegistry'
import { askDeepSeek } from './llmClient'
import { createPendingPrompt } from './safetyGuard'

export async function executeIntent(
  intent: UserIntent,
  context: AssistantContext
): Promise<AssistantResult> {
  // STOP_SPEAKING / CONFIRM_ACTION / CANCEL_ACTION — handled at voice layer
  if (intent.type === 'STOP_SPEAKING' || intent.type === 'CONFIRM_ACTION' || intent.type === 'CANCEL_ACTION') {
    return { success: true, message: '', speak: false }
  }

  // ── 模糊匹配低置信度 → 追问确认 ──
  if (intent.needConfirm && !intent.sensitivity) {
    const aliasHint = intent.matchedAlias || intent.rawText
    return new Promise((resolve) => {
      const action = {
        id: crypto.randomUUID(),
        type: 'CONFIRM_INTENT',
        label: `确认意图`,
        payload: intent,
        resolve: (ok: boolean) => {
          if (!ok) {
            resolve({ success: true, message: '好的，请你再说一遍指令。', speak: true })
            return
          }
          const confirmedIntent = { ...intent, needConfirm: false }
          executeIntent(confirmedIntent, context).then(resolve)
        },
      }
      resolve({
        success: false,
        needConfirm: true,
        message: `我理解你是想${aliasHint}，对吗？`,
        speak: true,
        action,
      })
    })
  }

  // ── OPEN_MODULE — 页面跳转 ──
  if (intent.type === 'OPEN_MODULE' && intent.module) {
    window.dispatchEvent(
      new CustomEvent('assistant:open-module', { detail: { module: intent.module } })
    )
    try {
      const { useVoiceActionStore } = await import('@/stores/voiceActionStore')
      const store = useVoiceActionStore()
      store.dispatch({ type: 'OPEN_MODULE', module: intent.module })
    } catch { /* store not available */ }

    const labels: Record<string, string> = {
      dashboard: '首页总控台', monitor: '实时监测', pir: 'PIR人体感应',
      device: '设备管理', alarm: '告警管理', sos: '一键报警',
    }
    console.log('[小安执行] OPEN_MODULE:', intent.module, '→ 跳转页面')
    return {
      success: true,
      message: `已为你打开${labels[intent.module] || intent.module}。`,
      speak: true,
    }
  }

  // ── SENSITIVE_ACTION — 危险操作二次确认 ──
  if (intent.type === 'SENSITIVE_ACTION') {
    return new Promise((resolve) => {
      const action = {
        id: crypto.randomUUID(),
        type: intent.action || 'SENSITIVE_ACTION',
        label: intent.action || '敏感操作',
        payload: intent,
        resolve: (ok: boolean) => {
          if (!ok) {
            resolve({ success: true, message: '已取消操作。', speak: true })
            return
          }
          executeSensitiveAction(intent, context).then(resolve)
        },
      }
      resolve({
        success: false,
        needConfirm: true,
        message: createPendingPrompt(intent.action || 'SENSITIVE_ACTION'),
        speak: true,
        action,
      })
    })
  }

  // ── QUERY_DATA — 调用真实后端接口查询数据 ──
  if (intent.type === 'QUERY_DATA' && intent.action) {
    return executeQueryAction(intent, context)
  }

  // ── CONTROL_ACTION — 控制类操作 ──
  if (intent.type === 'CONTROL_ACTION' && intent.action) {
    return executeControlAction(intent, context)
  }

  // ── Capability 匹配 ──
  const capability = getMatchedCapability(intent, context)
  if (capability) {
    try {
      const result = await capability.execute(intent, context)
      context.lastIntent = intent
      context.lastAnswer = result.message
      context.lastData = result.data
      return result
    } catch (e: any) {
      return {
        success: false,
        message: `执行「${capability.name}」时出错：${e.message || '未知错误'}`,
        speak: true,
      }
    }
  }

  // ── Fallback 关键词匹配 ──
  const raw = intent.rawText || ''

  // 布防
  const armFb = /布防|防护模式|警戒模式|设防/i
  const disarmFb = /撤防|关闭布防|退出布防|关闭防护|退出防护|关闭警戒|退出警戒|解除布防|解除防护/i

  if (armFb.test(raw) && !disarmFb.test(raw)) {
    console.log('[小安 fallback] ARM_DEVICE')
    return new Promise((resolve) => {
      const action = {
        id: crypto.randomUUID(),
        type: 'ARM_DEVICE',
        label: '开启布防',
        payload: { type: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', rawText: raw },
        resolve: (ok: boolean) => {
          if (!ok) { resolve({ success: true, message: '已取消操作。', speak: true }); return }
          executeSensitiveAction({ type: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', rawText: raw, confidence: 0.7 }, context).then(resolve)
        },
      }
      resolve({
        success: false, needConfirm: true,
        message: createPendingPrompt('ARM_DEVICE'),
        speak: true, action,
      })
    })
  }

  if (disarmFb.test(raw)) {
    console.log('[小安 fallback] DISARM_DEVICE')
    return new Promise((resolve) => {
      const action = {
        id: crypto.randomUUID(),
        type: 'DISARM_DEVICE',
        label: '撤销布防',
        payload: { type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', rawText: raw },
        resolve: (ok: boolean) => {
          if (!ok) { resolve({ success: true, message: '已取消操作。', speak: true }); return }
          executeSensitiveAction({ type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', rawText: raw, confidence: 0.7 }, context).then(resolve)
        },
      }
      resolve({
        success: false, needConfirm: true,
        message: createPendingPrompt('DISARM_DEVICE'),
        speak: true, action,
      })
    })
  }

  // 发送求助 — "报警"/"救命"立即执行，其他需确认
  if (/报警|救命/.test(raw)) {
    console.log('[小安 fallback] IMMEDIATE_SOS')
    const result = await executeSensitiveAction(
      { type: 'SENSITIVE_ACTION', action: 'SEND_HELP', rawText: raw, confidence: 0.95 }, context
    )
    result.message = '已报警，保安将立即响应。' + (result.message || '')
    return result
  }

  if (/发送求助|紧急求助|帮我求助|有危险|帮我报警/.test(raw)) {
    return new Promise((resolve) => {
      const action = {
        id: crypto.randomUUID(),
        type: 'SEND_HELP',
        label: '发送紧急求助',
        payload: { type: 'SENSITIVE_ACTION', action: 'SEND_HELP', rawText: raw },
        resolve: (ok: boolean) => {
          if (!ok) { resolve({ success: true, message: '已取消操作。', speak: true }); return }
          executeSensitiveAction({ type: 'SENSITIVE_ACTION', action: 'SEND_HELP', rawText: raw, confidence: 0.7 }, context).then(resolve)
        },
      }
      resolve({
        success: false, needConfirm: true,
        message: '是否确认发送紧急求助？',
        speak: true, action,
      })
    })
  }

  // 安防关键词兜底
  const securityKeywords = /告警|警告|报警|预警|异常|安全|设备|pir|人体|求助|入侵|陌生人|摄像头|监控|多少|几条|今天|本周|最近|未处理/i
  if (securityKeywords.test(raw)) {
    try {
      const fallbackIntent = { type: 'QUERY_DATA' as const, action: 'QUERY_ALARMS', confidence: 0.6, rawText: raw }
      const cap = getMatchedCapability(fallbackIntent, context)
      if (cap) {
        try {
          const result = await cap.execute(fallbackIntent, context)
          context.lastIntent = fallbackIntent
          context.lastAnswer = result.message
          return result
        } catch {}
      }
    } catch {}
  }

  const anyBizKeyword = /告警|警告|报警|布防|撤防|防护|警戒|设备|pir|人体|求助|消息|联系人|实时监测|摄像头|安全/i
  if (anyBizKeyword.test(raw)) {
    return {
      success: false,
      message: '我理解这是安防系统相关指令，但没有完全听清。你可以换一种说法，例如「开启布防」「查看告警」或「发送求助」。',
      speak: true,
    }
  }

  // 最终兜底 → DeepSeek
  try {
    const reply = await askDeepSeek({ message: raw, context })
    context.lastIntent = intent
    context.lastAnswer = reply
    return { success: true, message: reply, speak: true }
  } catch {
    return { success: false, message: '我暂时无法处理这个请求，请稍后重试。', speak: true }
  }
}

// ═══════════════════════════════════════════
// QUERY_DATA 执行 — 调用真实后端接口
// ═══════════════════════════════════════════
async function executeQueryAction(intent: UserIntent, context: AssistantContext): Promise<AssistantResult> {
  const action = intent.action || ''
  console.log('[小安执行] QUERY_DATA:', action, '→ 调用真实接口')

  try {
    switch (action) {
      case 'QUERY_DEVICE_STATUS': {
        const res = await fetch('/api/devices/stats')
        const json = await res.json()
        const data = json?.data || json
        const total = data?.total || 0
        const online = data?.online || 0
        const armed = data?.armed || 0
        return {
          success: true,
          message: `当前共有 ${total} 台设备，其中 ${online} 台在线，${armed} 台已布防。`,
          speak: true,
          data,
        }
      }

      case 'QUERY_PIR_TODAY': {
        const res = await fetch('/api/pir/today-count')
        const json = await res.json()
        const count = json?.data || 0
        const latestRes = await fetch('/api/pir/latest')
        const latestJson = await latestRes.json()
        const latest = latestJson?.data
        const latestTime = latest?.reportTime || '暂无'
        return {
          success: true,
          message: `今天共检测到 ${count} 次人体活动。最近一次：${latestTime}。`,
          speak: true,
          data: { count, latestTime },
        }
      }

      case 'QUERY_ALARM_TODAY': {
        const res = await fetch('/api/statistics/overview')
        const json = await res.json()
        const data = json?.data || json
        const todayAlarms = data?.todayAlarmCount || data?.todayAlarms || 0
        return {
          success: true,
          message: `今天共有 ${todayAlarms} 条告警。`,
          speak: true,
          data: { todayAlarms },
        }
      }

      case 'QUERY_ALARM_COUNT':
      case 'QUERY_UNHANDLED_ALARMS': {
        const res = await fetch('/api/statistics/overview')
        const json = await res.json()
        const data = json?.data || json
        const unhandled = data?.unhandledAlarmCount || data?.unhandledAlarms || 0
        const total = data?.todayAlarmCount || data?.todayAlarms || 0
        return {
          success: true,
          message: `当前有 ${unhandled} 条未处理告警，今天共 ${total} 条告警。`,
          speak: true,
          data: { unhandled, total },
        }
      }

      case 'QUERY_ALARM_WEEK':
      case 'QUERY_ALARMS': {
        const res = await fetch('/api/statistics/alarm-trend?days=7')
        const json = await res.json()
        const trendData = json?.data || []
        const totalWeek = trendData.reduce((sum: number, item: any) => sum + (item.count || 0), 0)
        return {
          success: true,
          message: `最近7天共有 ${totalWeek} 条告警。`,
          speak: true,
          data: { totalWeek, trend: trendData },
        }
      }

      case 'READ_LATEST_ALARM': {
        const res = await fetch('/api/alarm/latest')
        const json = await res.json()
        const alarm = json?.data
        if (alarm) {
          const content = alarm.alarmContent || alarm.content || '未知告警'
          const time = alarm.alarmTime || alarm.createTime || ''
          return {
            success: true,
            message: `最新告警：${content}，时间：${time}。`,
            speak: true,
            data: alarm,
          }
        }
        return { success: true, message: '当前没有告警记录。', speak: true }
      }

      case 'QUERY_SECURITY_STATUS': {
        const res = await fetch('/api/statistics/overview')
        const json = await res.json()
        const data = json?.data || json
        const online = data?.deviceOnline || data?.onlineDevices || 0
        const total = data?.deviceTotal || data?.totalDevices || 0
        const todayAlarms = data?.todayAlarmCount || data?.todayAlarms || 0
        const unhandled = data?.unhandledAlarmCount || data?.unhandledAlarms || 0
        let status = '正常'
        if (unhandled > 0) status = '有未处理告警'
        if (online < total) status += '，部分设备离线'
        return {
          success: true,
          message: `安防状态：${status}。${online}/${total} 台设备在线，今天 ${todayAlarms} 条告警，${unhandled} 条未处理。`,
          speak: true,
          data,
        }
      }

      default:
        // Try capability registry
        const cap = getMatchedCapability(intent, context)
        if (cap) {
          const result = await cap.execute(intent, context)
          context.lastIntent = intent
          context.lastAnswer = result.message
          return result
        }
        return { success: false, message: `查询「${action}」暂不支持，请换个说法。`, speak: true }
    }
  } catch (e: any) {
    console.error('[小安执行] QUERY_DATA 失败:', action, e)
    return { success: false, message: `查询失败：${e.message || '网络错误'}，请稍后重试。`, speak: true }
  }
}

// ═══════════════════════════════════════════
// CONTROL_ACTION 执行
// ═══════════════════════════════════════════
async function executeControlAction(intent: UserIntent, _context: AssistantContext): Promise<AssistantResult> {
  const action = intent.action || ''
  console.log('[小安执行] CONTROL_ACTION:', action)

  // 摄像头监测需要二次确认
  if (action === 'START_LIVE_MONITOR' || action === 'STOP_LIVE_MONITOR') {
    const label = action === 'START_LIVE_MONITOR' ? '开启实时监测' : '关闭实时监测'
    if (!intent.confirmed) {
      return new Promise((resolve) => {
        const act = {
          id: crypto.randomUUID(),
          type: action,
          label,
          payload: intent,
          resolve: (ok: boolean) => {
            if (!ok) { resolve({ success: true, message: '已取消操作。', speak: true }); return }
            executeControlAction({ ...intent, confirmed: true } as UserIntent, _context).then(resolve)
          },
        }
        resolve({
          success: false, needConfirm: true,
          message: `是否确认${label}？`,
          speak: true, action: act,
        })
      })
    }
  }

  const actionResults: Record<string, string> = {
    'OPEN_CAMERA': '已为你打开摄像头。',
    'CLOSE_CAMERA': '已为你关闭摄像头。',
    'START_LIVE_MONITOR': '已开始实时监测，摄像头将持续识别。',
    'STOP_LIVE_MONITOR': '已停止实时监测。',
    'FACE_RECOGNIZE_ONCE': '已执行人脸识别。',
    'OPEN_PERSON_MANAGER': '已为你打开人员管理。',
    'REFRESH_CURRENT': '已刷新当前页面。',
  }

  try {
    // 摄像头操作需要先切换到监测页面
    if (action === 'START_LIVE_MONITOR' || action === 'STOP_LIVE_MONITOR' || action === 'OPEN_CAMERA' || action === 'CLOSE_CAMERA') {
      window.dispatchEvent(
        new CustomEvent('assistant:open-module', { detail: { module: 'monitor' } })
      )
      // 等待页面切换完成再执行
      await new Promise(r => setTimeout(r, 500))
    }
    const { useVoiceActionStore } = await import('@/stores/voiceActionStore')
    const store = useVoiceActionStore()
    store.dispatch({ type: action as any, module: intent.module })
  } catch { /* store not available */ }

  return {
    success: true,
    message: actionResults[action] || `已执行${action}。`,
    speak: true,
  }
}

// ═══════════════════════════════════════════
// SENSITIVE_ACTION 执行 — 真实调用后端接口
// ═══════════════════════════════════════════
function getCurrentUserId(): number {
  try {
    const stored = localStorage.getItem('user_info') || localStorage.getItem('user') || localStorage.getItem('auth')
    if (stored) {
      const user = JSON.parse(stored)
      return user?.id || user?.userId || 1
    }
  } catch {}
  return 1
}

async function getDefaultDeviceCode(): Promise<string> {
  try {
    const uid = getCurrentUserId()
    const res = await fetch(`/api/devices?userId=${uid}&role=user`)
    const json = await res.json()
    const list = json?.data?.records || json?.data || []
    const devices = Array.isArray(list) ? list : []
    if (devices.length > 0) {
      const online = devices.find((d: any) => d.onlineStatus === 1 || d.online_status === 1)
      const d = online || devices[0]
      return d.deviceCode || d.device_code || 'device_001'
    }
  } catch {}
  return 'device_001'
}

async function executeSensitiveAction(
  intent: UserIntent,
  _context: AssistantContext
): Promise<AssistantResult> {
  const action = intent.action || ''
  console.log('[小安执行] SENSITIVE_ACTION:', action, '→ 调用真实接口')

  if (action === 'ARM_DEVICE') {
    try {
      const { useVoiceActionStore } = await import('@/stores/voiceActionStore')
      useVoiceActionStore().dispatch({ type: 'ARM_DEVICE' })
      return { success: true, message: '布防指令已下达，设备进入警戒状态。', speak: true }
    } catch {
      return { success: false, message: '布防指令下发失败。', speak: true }
    }
  }

  if (action === 'DISARM_DEVICE') {
    try {
      const { useVoiceActionStore } = await import('@/stores/voiceActionStore')
      useVoiceActionStore().dispatch({ type: 'DISARM_DEVICE' })
      return { success: true, message: '撤防指令已下达。', speak: true }
    } catch {
      return { success: false, message: '撤防指令下发失败。', speak: true }
    }
  }

  if (action === 'SEND_HELP') {
    try {
      const userId = getCurrentUserId()
      // 获取设备和最近告警信息
      let deviceCode = ''
      let description = '最高等级，需立即处理'
      try {
        const deviceRes = await fetch(`/api/devices?userId=${userId}&role=user`)
        const deviceJson = await deviceRes.json()
        const list = deviceJson?.data?.records || deviceJson?.data || []
        const devices = Array.isArray(list) ? list : []
        if (devices.length > 0) {
          const online = devices.find((d: any) => d.onlineStatus === 1 || d.online_status === 1)
          deviceCode = (online || devices[0]).deviceCode || (online || devices[0]).device_code || ''
        }
        // 获取最近告警
        const alarmRes = await fetch(`/api/alarms?limit=1&userId=${userId}&role=user`)
        const alarmJson = await alarmRes.json()
        const alarmList = alarmJson?.data?.records || alarmJson?.data || []
        const alarms = Array.isArray(alarmList) ? alarmList : []
        if (alarms.length > 0) {
          const a = alarms[0]
          description = '关联告警：' + (a.alarmContent || '').substring(0, 60) + '。最高等级，需立即处理'
          if (!deviceCode) deviceCode = a.deviceCode || ''
        }
      } catch { /* fallback */ }
      const res = await fetch('/api/messages/help', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, deviceCode, description, helpType: 'SECURITY' }),
      })
      const result = await res.json()
      if (res.ok && (result.code === 200)) {
        const count = result.data?.count || result.count || 0
        if (count === 0) {
          return { success: false, message: '你还没有添加紧急联系人，请先到紧急求助页面添加联系人。', speak: true }
        }
        return { success: true, message: `求助信息已发送给 ${count} 位紧急联系人，请保持冷静。`, speak: true }
      }
      return { success: false, message: result.message || '求助发送失败，请尝试其他方式求助。', speak: true }
    } catch {
      return { success: false, message: '求助发送失败，请直接拨打紧急电话。', speak: true }
    }
  }

  if (action === 'SEND_MESSAGE') {
    try {
      const userId = getCurrentUserId()
      const res = await fetch('/api/messages/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderId: userId, receiverId: 1,
          title: '语音消息', content: intent.rawText || '用户通过语音发送消息', messageType: 'NORMAL',
        }),
      })
      const result = await res.json()
      if (res.ok && result.code === 200) return { success: true, message: '消息已发送。', speak: true }
      return { success: false, message: result.message || '消息发送失败，请稍后重试。', speak: true }
    } catch {
      return { success: false, message: '消息发送失败，请稍后重试。', speak: true }
    }
  }

  if (action === 'CLEAR_CHAT') {
    return { success: true, message: '对话已清空。', speak: true }
  }

  return { success: false, message: `操作「${action}」暂未接入执行器。`, speak: true }
}
