// ==================== 用户 & 角色 ====================

export type Role = 'resident' | 'guard' | 'admin'

export interface UserInfo {
  id: number
  userId?: number
  username: string
  role: Role | string
  realName?: string
  nickname?: string
  avatar?: string
  token: string
}

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  realName: string
  role?: string
}

// ==================== 设备（对应 device 表） ====================

export interface Device {
  id: number
  deviceCode: string
  deviceName: string
  location: string
  onlineStatus: number       // 1 在线 / 0 离线
  humanDetected: number      // 1 有人 / 0 无人
  defenseStatus: number      // 1 布防 / 0 撤防
  lastReportTime: string
  userId?: number
}

export interface AddDeviceForm {
  deviceCode: string
  deviceName: string
  location: string
  userId?: number
}

// ==================== MQTT 上报数据 ====================

export interface MqttReport {
  deviceCode: string
  humanDetected: number
  defenseStatus: number
  timestamp: string
}

export interface MqttStatus {
  deviceCode: string
  onlineStatus: number
  timestamp: string
}

export interface MqttCommand {
  command: 'DEFENSE_ON' | 'DEFENSE_OFF'
  requestId?: string
  timestamp?: string
}

export interface MqttReply {
  deviceCode: string
  requestId: string
  command: string
  success: number
  message?: string
  timestamp: string
}

// ==================== 人体感应记录 ====================

export interface HumanRecord {
  id: number
  deviceCode: string
  deviceName: string
  location?: string
  humanDetected: number
  defenseStatus: number
  reportTime: string
  isAlarm: number
  alarmId?: number
}

// ==================== 告警 ====================

export type AlarmLevel = 'HIGH' | 'MEDIUM' | 'LOW'
export type HandleStatus = 0 | 1 | 2

export interface Alarm {
  id: number
  deviceCode: string
  deviceName: string
  location?: string
  alarmType: string
  alarmLevel: AlarmLevel
  alarmContent: string
  handleStatus: HandleStatus
  alarmTime: string
}

export interface HandleAlarmForm {
  handleStatus: 1 | 2
  handleUser: string
  remark?: string
}

// ==================== 布防/撤防 ====================

export interface DefenseResult {
  deviceCode: string
  defenseStatus: number
  command: string
  sendTime: string
}

// ==================== 统计 ====================

export interface StatisticsOverview {
  deviceTotal: number
  onlineDeviceCount: number
  offlineDeviceCount: number
  todayAlarmCount: number
  defenseDeviceCount: number
}

export interface AlarmTrendItem {
  date: string
  count: number
}

// ==================== AI 对话 ====================

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  // 后端返回的增强字段
  intent?: string
  riskLevel?: string
  evidence?: string[]
  suggestions?: string[]
  actionDraft?: ActionDraft
}

export interface ActionDraft {
  type: string
  title: string
  content: string
}

export interface AiAgentResponse {
  answer: string
  intent: string
  riskLevel: string
  usedTools: string[]
  evidence: string[]
  suggestions: string[]
  actionDraft: ActionDraft | null
  source: string
}

export interface AiAgentRequest {
  userId: number
  message: string
}

// ==================== 总览 ====================

export interface OverviewPoint {
  deviceCode: string
  deviceName: string
  location: string
  online: boolean
  humanDetected: boolean
  defense: boolean
  hasActiveAlarm: boolean
}

// ==================== API 通用响应 ====================

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageData<T> {
  records: T[]       // 官方使用 records
  total: number
  page: number
  pageSize: number
}
