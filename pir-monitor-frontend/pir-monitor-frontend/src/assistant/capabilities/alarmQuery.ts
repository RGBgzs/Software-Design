/**
 * 告警查询能力 — 支持按时间维度统计
 */
import type { Capability } from '../capabilityRegistry'
import { askDeepSeek, isAiUnavailable } from '../llmClient'

// ═══════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════

function getAlarmTime(alarm: any): number {
  const t = alarm.alarmTime || alarm.createTime || alarm.createdAt || alarm.time || alarm.eventTime
  return t ? new Date(t).getTime() : 0
}

function isToday(ts: number): boolean {
  const d = new Date(ts)
  const now = new Date()
  return d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate()
}

function isInLast7Days(ts: number): boolean {
  const now = Date.now()
  return ts > 0 && (now - ts) <= 7 * 24 * 60 * 60 * 1000
}

function isUnhandled(alarm: any): boolean {
  const s = alarm.handleStatus ?? alarm.status ?? alarm.alarmStatus
  return s === 0 || s === '0' || s === 'UNHANDLED' || s === '未处理'
}

function isHigh(alarm: any): boolean {
  const level = alarm.alarmLevel || alarm.level || alarm.riskLevel
  return level === 'HIGH' || level === '高' || level === '高危'
}

function fmtTime(t?: string | number): string {
  if (!t) return '未知时间'
  try {
    return new Date(t).toLocaleString('zh-CN', {
      month: 'numeric', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  } catch { return '未知时间' }
}

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) return null
  const json = await res.json()
  return json?.data || json
}

// ═══════════════════════════════════════════
// 本地统计构建
// ═══════════════════════════════════════════

function buildTodaySummary(list: any[]): string {
  const todayList = list.filter(a => isToday(getAlarmTime(a)))
  if (todayList.length === 0) {
    return '今天暂时没有新的告警记录，当前告警情况相对平稳。'
  }
  const unhandled = todayList.filter(isUnhandled).length
  const high = todayList.filter(isHigh).length
  const latest = todayList.reduce((a, b) =>
    getAlarmTime(a) > getAlarmTime(b) ? a : b, todayList[0])
  const latestContent = latest?.alarmContent || latest?.content || '未知内容'
  const latestTime = fmtTime(getAlarmTime(latest))

  let msg = `今天共有 ${todayList.length} 条告警`
  if (unhandled > 0) msg += `，其中 ${unhandled} 条未处理`
  if (high > 0) msg += `，${high} 条为高危`
  msg += `。最近一条告警发生在 ${latestTime}，内容是${latestContent}。`
  if (unhandled > 0 && high > 0) msg += '建议你优先核查未处理的高危告警。'
  else if (unhandled > 0) msg += '建议你及时处理未处理告警。'
  return msg
}

function buildWeekSummary(list: any[]): string {
  const weekList = list.filter(a => isInLast7Days(getAlarmTime(a)))
  if (weekList.length === 0) {
    return '最近 7 天没有告警记录，系统整体较稳定。'
  }
  const unhandled = weekList.filter(isUnhandled).length
  const high = weekList.filter(isHigh).length

  // 按天统计
  const dayMap = new Map<string, number>()
  weekList.forEach(a => {
    const ts = getAlarmTime(a)
    if (ts > 0) {
      const key = new Date(ts).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
      dayMap.set(key, (dayMap.get(key) || 0) + 1)
    }
  })
  let maxDay = ''; let maxCount = 0
  dayMap.forEach((v, k) => { if (v > maxCount) { maxCount = v; maxDay = k } })

  let msg = `最近 7 天共有 ${weekList.length} 条告警`
  if (unhandled > 0) msg += `，其中 ${unhandled} 条未处理`
  if (high > 0) msg += `，${high} 条为高危`
  if (maxDay) msg += `。告警最多的一天是 ${maxDay}，共 ${maxCount} 条`
  msg += '。'
  if (unhandled > 0) msg += '建议你优先处理未处理的高危告警，并检查对应设备是否频繁误报。'
  return msg
}

function buildUnhandledSummary(list: any[]): string {
  const unhandledList = list.filter(isUnhandled)
  if (unhandledList.length === 0) {
    return '当前没有未处理告警，所有告警均已处理完毕。'
  }
  const high = unhandledList.filter(isHigh).length
  const latest = unhandledList.reduce((a, b) =>
    getAlarmTime(a) > getAlarmTime(b) ? a : b, unhandledList[0])

  let msg = `当前有 ${unhandledList.length} 条未处理告警`
  if (high > 0) msg += `，其中 ${high} 条为高危`
  msg += `。最近一条未处理告警是${latest?.alarmContent || '未知'}，发生在 ${fmtTime(getAlarmTime(latest))}。`
  return msg
}

function buildLatestSummary(list: any[]): string {
  if (list.length === 0) {
    return '当前没有任何告警记录。'
  }
  const sorted = [...list].sort((a, b) => getAlarmTime(b) - getAlarmTime(a))
  const latest = sorted[0]
  const level = latest?.alarmLevel || latest?.level || '未知'
  const content = latest?.alarmContent || latest?.content || '未知'
  const status = isUnhandled(latest) ? '未处理' : '已处理'
  return `最近一条告警是${content}，等级为${level === 'HIGH' ? '高危' : level === 'MEDIUM' ? '中危' : '低危'}，发生时间是 ${fmtTime(getAlarmTime(latest))}，目前状态为${status}。`
}

function buildCountSummary(list: any[]): string {
  if (list.length === 0) {
    return '当前没有任何告警记录。'
  }
  const unhandled = list.filter(isUnhandled).length
  const high = list.filter(isHigh).length
  let msg = `系统共有 ${list.length} 条告警记录`
  if (unhandled > 0) msg += `，其中 ${unhandled} 条未处理`
  if (high > 0) msg += `，${high} 条为高危`
  msg += '。'
  return msg
}

// ═══════════════════════════════════════════
// 告警查询能力
// ═══════════════════════════════════════════

export const AlarmQueryCapability: Capability = {
  name: 'AlarmQueryCapability',
  description: '查询告警状态，支持今日/本周/未处理/最新/总数统计',
  examples: [
    '今天有多少告警', '本周告警', '有没有未处理告警',
    '最近一条告警是什么', '告警有多少条', '今天警告有多少条',
  ],

  match(intent) {
    return intent.type === 'QUERY_DATA' && [
      'QUERY_ALARMS', 'QUERY_ALARM_TODAY', 'QUERY_ALARM_WEEK',
      'QUERY_ALARM_COUNT', 'QUERY_UNHANDLED_ALARMS', 'READ_LATEST_ALARM',
    ].includes(intent.action || '')
  },

  async execute(intent, context) {
    // 拉取所有告警（大 pageSize 覆盖足够多数据）
    const result = await getJson('/api/alarms?page=1&pageSize=1000')
    const records = result?.records || result || []
    const list: any[] = Array.isArray(records) ? records : []

    // 按意图类型选择统计方法
    let localReply: string
    switch (intent.action) {
      case 'QUERY_ALARM_TODAY':
        localReply = buildTodaySummary(list)
        break
      case 'QUERY_ALARM_WEEK':
        localReply = buildWeekSummary(list)
        break
      case 'QUERY_UNHANDLED_ALARMS':
        localReply = buildUnhandledSummary(list)
        break
      case 'READ_LATEST_ALARM':
        localReply = buildLatestSummary(list)
        break
      case 'QUERY_ALARM_COUNT':
        localReply = buildCountSummary(list)
        break
      default: // QUERY_ALARMS
        localReply = buildUnhandledSummary(list)
        break
    }

    context.lastAlarms = list
    context.lastData = { alarms: list }

    // 尝试 DeepSeek 润色
    let reply = ''
    try {
      reply = await askDeepSeek({
        message: intent.rawText,
        systemContext: { alarms: list, localSummary: localReply },
        instruction: `你是小安，面向视障人群的语音助手。
以下是告警数据的本地统计结果：
${localReply}

请基于以上结果，用口语化、适合语音朗读的方式回答用户。
不要改变统计数字。如果数据为0就说平稳。保持简洁。`,
      })
    } catch { reply = '' }

    if (isAiUnavailable(reply)) {
      reply = localReply
    }

    return { success: true, message: reply, data: { alarms: list }, speak: true }
  },
}
