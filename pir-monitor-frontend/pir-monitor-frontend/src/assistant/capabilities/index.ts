/**
 * 注册所有能力
 */
import { registerCapability } from '../capabilityRegistry'
import { SecurityStatusCapability } from './securityStatus'
import { PirQueryCapability } from './pirQuery'
import { DeviceQueryCapability } from './deviceQuery'
import { AlarmQueryCapability } from './alarmQuery'
import { ContactQueryCapability } from './contactQuery'
import { PersonQueryCapability } from './personQuery'

let registered = false

export function registerAllCapabilities(): void {
  if (registered) return
  registered = true

  registerCapability(SecurityStatusCapability)
  registerCapability(PirQueryCapability)
  registerCapability(DeviceQueryCapability)
  registerCapability(AlarmQueryCapability)
  registerCapability(ContactQueryCapability)
  registerCapability(PersonQueryCapability)
}
