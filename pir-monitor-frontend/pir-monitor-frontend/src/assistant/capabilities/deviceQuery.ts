/**
 * 设备状态查询能力
 */
import type { Capability } from '../capabilityRegistry'
import { askDeepSeek, isAiUnavailable } from '../llmClient'

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) return null
  const json = await res.json()
  return json?.data || json
}

function buildLocalDeviceSummary(devices: any): string {
  const records = devices?.records || devices || []
  const list = Array.isArray(records) ? records : []

  if (list.length === 0) {
    return '暂未获取到设备数据，请检查设备连接。'
  }

  const online = list.filter((d: any) =>
    d.onlineStatus === 1 || d.status === 'ONLINE' || d.online === true
  )
  const offline = list.filter((d: any) =>
    d.onlineStatus === 0 || d.status === 'OFFLINE' || d.online === false
  )
  const lowBattery = list.filter((d: any) => {
    const b = d.battery || d.batteryLevel || 100
    return Number(b) <= 20
  })

  let msg = `当前共有 ${list.length} 台设备`
  if (online.length > 0) msg += `，其中 ${online.length} 台在线`
  if (offline.length > 0) msg += `，${offline.length} 台离线，请检查`
  if (lowBattery.length > 0) msg += `，${lowBattery.length} 台设备电量低`
  msg += '。'

  if (offline.length > 0) {
    const names = offline.map((d: any) => d.deviceName || d.name || d.deviceCode || '未知').join('、')
    msg += `离线设备：${names}。`
  }

  return msg
}

export const DeviceQueryCapability: Capability = {
  name: 'DeviceQueryCapability',
  description: '查询设备在线状态、电量、布防等',
  examples: ['有哪些设备在线', '设备状态怎么样', '有没有离线设备', '设备状态'],

  match(intent) {
    return intent.type === 'QUERY_DATA' && intent.action === 'QUERY_DEVICE_STATUS'
  },

  async execute(intent, context) {
    const devices = await getJson('/api/devices')
    const systemData = { devices }

    context.lastDevices = devices
    context.lastData = systemData

    let reply = ''
    try {
      reply = await askDeepSeek({
        message: intent.rawText,
        systemContext: systemData,
        instruction: `你是小安。基于设备数据回答。口语化、简洁、适合朗读。
列出在线/离线数量，有离线或低电量要提醒。`,
      })
    } catch {
      reply = ''
    }

    if (isAiUnavailable(reply)) {
      reply = buildLocalDeviceSummary(devices)
    }

    return { success: true, message: reply, data: systemData, speak: true }
  },
}
