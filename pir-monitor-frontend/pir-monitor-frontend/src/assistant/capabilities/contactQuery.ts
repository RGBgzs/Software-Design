/**
 * 联系人查询能力
 */
import type { Capability } from '../capabilityRegistry'
import type { AssistantContext, AssistantResult, UserIntent } from '../types'

async function getJson(url: string): Promise<any> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

function getUserId(): number {
  try {
    const stored = localStorage.getItem('user_info') || localStorage.getItem('user')
    if (stored) {
      const user = JSON.parse(stored)
      return user?.id || user?.userId || 1
    }
  } catch {}
  return 1
}

export const ContactQueryCapability: Capability = {
  name: 'contact-query',
  description: '查询紧急联系人',
  examples: ['查看联系人', '我有几个紧急联系人', '我的紧急联系人'],

  match(intent: UserIntent) {
    return intent.type === 'QUERY_DATA' && intent.action === 'QUERY_CONTACTS'
  },

  async execute(_intent: UserIntent, _context: AssistantContext): Promise<AssistantResult> {
    const userId = getUserId()
    try {
      const json = await getJson(`/api/contacts/list?userId=${userId}`)
      const contacts = json?.data || []
      if (!contacts.length) {
        return { success: true, message: '你还没有添加紧急联系人，请先到消息中心添加。', speak: true }
      }
      const names = contacts.map((c: any) => c.contactNickname || c.contactUsername).join('、')
      return {
        success: true,
        message: `你共有 ${contacts.length} 位紧急联系人：${names}。`,
        speak: true,
      }
    } catch {
      return { success: false, message: '暂时无法查询联系人，请稍后重试。', speak: true }
    }
  },
}
