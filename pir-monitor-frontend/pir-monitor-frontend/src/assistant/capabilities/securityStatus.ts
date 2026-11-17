/**
 * 安防状态查询能力
 * 读取实时设备/告警/PIR/人脸数据 → DeepSeek 总结
 */
import type { Capability } from '../capabilityRegistry'
import { askDeepSeek, isAiUnavailable } from '../llmClient'

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) return null
  const json = await res.json()
  return json?.data || json
}

function buildLocalSecuritySummary(systemData: any): string {
  const parts: string[] = []

  // 设备状态
  const devices = systemData.devices
  if (devices) {
    const records = devices?.records || devices || []
    const list = Array.isArray(records) ? records : []
    const online = list.filter((d: any) =>
      d.onlineStatus === 1 || d.status === 'ONLINE' || d.online === true
    )
    const offline = list.filter((d: any) =>
      d.onlineStatus === 0 || d.status === 'OFFLINE' || d.online === false
    )
    if (list.length > 0) {
      parts.push(`${list.length} 台设备中 ${online.length} 台在线`)
      if (offline.length > 0) parts.push(`${offline.length} 台离线`)
    }
  }

  // 告警
  const alarms = systemData.alarms
  if (alarms) {
    const records = alarms?.records || alarms || []
    const list = Array.isArray(records) ? records : []
    const highCount = list.filter((a: any) =>
      a.alarmLevel === 'HIGH' || a.alarmLevelName === '高危'
    ).length
    if (list.length > 0) {
      parts.push(`有 ${list.length} 条未处理告警`)
      if (highCount > 0) parts.push(`其中 ${highCount} 条高危`)
    } else {
      parts.push('没有未处理告警')
    }
  }

  // PIR
  const pirStats = systemData.pirStats
  if (pirStats) {
    const todayCount = pirStats?.todayCount ?? pirStats?.today ?? pirStats?.count ?? 0
    if (todayCount > 0) {
      parts.push(`今天检测到 ${todayCount} 次人体活动`)
    } else {
      parts.push('今天暂未检测到人体活动')
    }
  }

  if (parts.length === 0) {
    return '暂未获取到安防数据，系统可能正在初始化。'
  }

  const hasWarning = parts.some(p => p.includes('离线') || p.includes('高危') || p.includes('未处理'))
  const prefix = hasWarning
    ? '当前安防状态存在异常，请注意。'
    : '当前安防状态一切正常。'

  return prefix + parts.join('。') + '。'
}

export const SecurityStatusCapability: Capability = {
  name: 'SecurityStatusCapability',
  description: '查询当前安防综合状态，包含设备、告警、PIR、人脸',
  examples: ['我现在安全吗', '当前安防状态怎么样', '家里有没有异常'],

  match(intent) {
    return intent.type === 'QUERY_DATA' && intent.action === 'QUERY_SECURITY_STATUS'
  },

  async execute(intent, context) {
    const [devices, alarms, pirStats, faceStatus] = await Promise.allSettled([
      getJson('/api/devices'),
      getJson('/api/alarms'),
      getJson('/api/pir/stats'),
      getJson('/api/face/status'),
    ])

    const systemData: any = {}
    if (devices.status === 'fulfilled') systemData.devices = devices.value
    if (alarms.status === 'fulfilled') systemData.alarms = alarms.value
    if (pirStats.status === 'fulfilled') systemData.pirStats = pirStats.value
    if (faceStatus.status === 'fulfilled') systemData.faceStatus = faceStatus.value

    context.lastDevices = systemData.devices
    context.lastAlarms = systemData.alarms
    context.lastPirData = systemData.pirStats
    context.lastData = systemData

    let reply = ''
    try {
      reply = await askDeepSeek({
        message: intent.rawText,
        systemContext: systemData,
        instruction: `你是小安，面向视障人群的智慧安防语音助手。
先给结论，再给关键原因，最后给建议。
口语化、适合朗读，每句话不超过30字。
不要说"请看页面"或"点击屏幕"。`,
      })
    } catch {
      reply = ''
    }

    if (isAiUnavailable(reply)) {
      reply = buildLocalSecuritySummary(systemData)
    }

    return { success: true, message: reply, data: systemData, speak: true }
  },
}
