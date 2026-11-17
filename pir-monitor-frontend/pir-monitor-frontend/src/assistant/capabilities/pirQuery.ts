/**
 * PIR 人体感应查询能力
 */
import type { Capability } from '../capabilityRegistry'
import { askDeepSeek, isAiUnavailable } from '../llmClient'

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) return null
  const json = await res.json()
  return json?.data || json
}

function buildLocalPirSummary(systemData: any): string {
  const stats = systemData.stats || {}
  const records = systemData.recentRecords || {}

  const recordList = records?.records || records || []
  const list = Array.isArray(recordList) ? recordList : []
  const todayCount = stats?.todayCount ?? stats?.today ?? stats?.count ?? list.length

  if (todayCount === 0 && list.length === 0) {
    return '今天暂时没有检测到人体活动。'
  }

  let msg = `今天共检测到 ${todayCount} 次人体活动`
  if (list.length > 0) {
    const last = list[0]
    const time = last?.triggerTime || last?.createTime || last?.time || ''
    if (time) msg += `，最近一次在 ${new Date(time).toLocaleTimeString('zh-CN')}`
  }
  msg += '。'

  return msg
}

export const PirQueryCapability: Capability = {
  name: 'PirQueryCapability',
  description: '查询 PIR 人体感应记录和统计',
  examples: ['今天有没有人经过', 'PIR 触发了多少次', '最近一次感应是什么时候'],

  match(intent) {
    return intent.type === 'QUERY_DATA' && intent.action === 'QUERY_PIR_TODAY'
  },

  async execute(intent, context) {
    const [stats, latest, records] = await Promise.allSettled([
      getJson('/api/pir/stats'),
      getJson('/api/pir/latest'),
      getJson('/api/pir/records?page=1&pageSize=5'),
    ])

    const systemData: any = {}
    if (stats.status === 'fulfilled') systemData.stats = stats.value
    if (latest.status === 'fulfilled') systemData.latest = latest.value
    if (records.status === 'fulfilled') systemData.recentRecords = records.value

    context.lastPirData = systemData
    context.lastData = systemData

    let reply = ''
    try {
      reply = await askDeepSeek({
        message: intent.rawText,
        systemContext: systemData,
        instruction: `你是小安，面向视障人群的语音助手。
基于 PIR 人体感应数据回答用户。口语化、简洁、适合朗读。`,
      })
    } catch {
      reply = ''
    }

    if (isAiUnavailable(reply)) {
      reply = buildLocalPirSummary(systemData)
    }

    return { success: true, message: reply, data: systemData, speak: true }
  },
}
