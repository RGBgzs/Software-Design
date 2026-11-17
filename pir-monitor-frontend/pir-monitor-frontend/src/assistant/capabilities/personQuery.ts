/**
 * 已注册人员查询能力
 */
import type { Capability } from '../capabilityRegistry'
import type { AssistantContext, AssistantResult, UserIntent } from '../types'

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export const PersonQueryCapability: Capability = {
  name: 'person-query',
  description: '查询已注册人脸人员',
  examples: ['查看已注册人员', '有哪些注册人员', '已注册人员'],

  match(intent: UserIntent) {
    return intent.type === 'QUERY_DATA' && intent.action === 'QUERY_REGISTERED_PERSONS'
  },

  async execute(_intent: UserIntent, _context: AssistantContext): Promise<AssistantResult> {
    try {
      const json = await getJson('/api/face/users')
      const users = json?.data || []
      if (!users.length) {
        return { success: true, message: '当前没有已注册的人员。', speak: true }
      }
      const names = users.slice(0, 8).map((u: any) => u.name || u.personName || u.personId).join('、')
      const suffix = users.length > 8 ? ` 等共 ${users.length} 人` : `，共 ${users.length} 人`
      return {
        success: true,
        message: `已注册人员：${names}${suffix}。`,
        speak: true,
      }
    } catch {
      return { success: false, message: '暂时无法查询人员信息，人脸服务可能未启动。', speak: true }
    }
  },
}
