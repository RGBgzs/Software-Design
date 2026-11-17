/**
 * 能力注册表 —— 参考 Leon 的 Skills -> Actions -> Tools -> Functions
 */

import type { AssistantContext, AssistantResult, UserIntent } from './types'

export interface Capability {
  name: string
  description: string
  examples: string[]
  /** 判断此能力是否匹配当前意图 */
  match: (intent: UserIntent, context: AssistantContext) => boolean
  /** 执行能力，返回结果 */
  execute: (intent: UserIntent, context: AssistantContext) => Promise<AssistantResult>
}

const capabilities: Capability[] = []

export function registerCapability(capability: Capability): void {
  // 避免重复注册
  if (capabilities.find(c => c.name === capability.name)) return
  capabilities.push(capability)
}

export function getMatchedCapability(
  intent: UserIntent,
  context: AssistantContext
): Capability | undefined {
  return capabilities.find(c => c.match(intent, context))
}

export function listCapabilities(): Capability[] {
  return [...capabilities]
}
