/**
 * 智慧安防模块 API 聚合适配层
 * 内部复用 pir-monitor-frontend 已有 API 模块，统一对外暴露
 * 所有接口使用 /api 相对路径
 */
import http from './index'
import type { ApiResponse, PageData, Alarm, HumanRecord, Device, StatisticsOverview, AlarmTrendItem, ChatMessage } from '@/types'

// ==================== AI 智能助手 ====================

/** AI Agent 对话 */
export function agentChat(userId: number, message: string) {
  return http.post<ApiResponse<ChatMessage>>('/ai/agent-chat', { userId, message })
}

/** 语音命令 (预留接口) */
export function sendVoiceCommand(data: { userId: number; command: string }) {
  return http.post<ApiResponse<any>>('/ai/voice-command', data)
}

// ==================== PIR 人体感应监测 ====================

/** 获取设备实时状态 */
export function getPirDeviceStatus(deviceCode: string) {
  return http.get<ApiResponse<Device>>(`/devices/${deviceCode}/status`)
}

/** 获取人体感应记录列表 */
export function getPirRecords(params?: { page?: number; pageSize?: number; deviceCode?: string }) {
  return http.get<ApiResponse<PageData<HumanRecord>>>('/records', { params })
}

/** 布防 */
export function armDevice(deviceCode: string, operator?: string) {
  return http.post<ApiResponse<any>>(`/devices/${deviceCode}/defense/on`, { operator })
}

/** 撤防 */
export function disarmDevice(deviceCode: string, operator?: string) {
  return http.post<ApiResponse<any>>(`/devices/${deviceCode}/defense/off`, { operator })
}

// ==================== 设备管理 ====================

/** 获取设备列表 */
export function getDevices(params?: { page?: number; pageSize?: number; deviceCode?: string; deviceName?: string; userId?: number; role?: string }) {
  return http.get<ApiResponse<PageData<Device>>>('/devices', { params })
}

/** 新增设备 */
export function addDevice(data: { deviceCode: string; deviceName: string; location?: string; userId?: number }) {
  return http.post<ApiResponse<any>>('/devices', data)
}

/** 更新设备 */
export function updateDevice(id: number, data: { deviceCode?: string; deviceName?: string; location?: string }) {
  return http.put<ApiResponse<any>>(`/devices/${id}`, data)
}

/** 删除设备 */
export function deleteDevice(id: number) {
  return http.delete<ApiResponse<any>>(`/devices/${id}`)
}

// ==================== 告警管理 ====================

/** 获取告警列表 */
export function getAlarms(params?: { page?: number; pageSize?: number; handleStatus?: number; deviceCode?: string; userId?: number; role?: string }) {
  return http.get<ApiResponse<PageData<Alarm>>>('/alarms', { params })
}

/** 获取告警详情 */
export function getAlarmDetail(id: number) {
  return http.get<ApiResponse<Alarm>>(`/alarms/${id}`)
}

/** 处理告警 */
export function handleAlarm(id: number, data: { handleStatus: number; handleUser?: string; remark?: string }) {
  return http.post<ApiResponse<any>>(`/alarms/${id}/handle`, data)
}

// ==================== 人脸识别 ====================

/** 人脸注册 */
export function registerFace(personName: string, imageFile: File, userId?: number) {
  const formData = new FormData()
  formData.append('personName', personName)
  formData.append('name', personName)
  if (imageFile) formData.append('imageFile', imageFile)
  if (imageFile) formData.append('image', imageFile)
  if (userId) formData.append('userId', String(userId))
  return http.post<ApiResponse<any>>('/face/register', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 人脸识别 */
export function identifyFace(image: Blob, deviceCode?: string, location?: string) {
  const formData = new FormData()
  formData.append('image', image)
  if (deviceCode) formData.append('deviceCode', deviceCode)
  if (location) formData.append('location', location)
  return http.post<ApiResponse<any>>('/face/identify', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 获取已注册人脸列表 */
export function getFaceUsers() {
  return http.get<ApiResponse<any[]>>('/face/users')
}

/** 删除已注册人脸 */
export function deleteFaceUser(name: string) {
  return http.delete<ApiResponse<any>>(`/face/users/${encodeURIComponent(name)}`)
}

/** 获取人脸识别记录 */
export function getFaceRecords() {
  return http.get<ApiResponse<any[]>>('/face/record/list')
}

// ==================== 统计数据 ====================

/** 获取综合统计概览 */
export function getOverviewStats(userId?: number, role?: string) {
  return http.get<ApiResponse<StatisticsOverview>>('/statistics/overview', { params: { userId, role } })
}

/** 获取告警趋势 */
export function getAlarmTrend(days?: number, userId?: number, role?: string) {
  return http.get<ApiResponse<AlarmTrendItem[]>>('/statistics/alarm-trend', { params: { days, userId, role } })
}

// ==================== 消息中心 ====================

/** 获取消息列表 */
export function getMessages(userId: number | string) {
  return http.get<ApiResponse<PageData<any>>>(`/messages/inbox?userId=${userId}`)
}

/** 标记消息已读 */
export function markMessageRead(id: number) {
  return http.post<ApiResponse<any>>(`/messages/${id}/read`)
}

/** 删除消息 */
export function deleteMessage(id: number, userId: number | string) {
  return http.post<ApiResponse<any>>(`/messages/${id}/delete?userId=${userId}`)
}

/** 未读消息数 */
export function getUnreadCount(userId: number | string) {
  return http.get<ApiResponse<any>>(`/messages/unread-count?userId=${userId}`)
}

// ==================== 紧急联系人 ====================

/** 获取我的紧急联系人列表 */
export function getContacts(userId: number | string) {
  return http.get<ApiResponse<any[]>>(`/contacts/list?userId=${userId}`)
}

/** 通过 username 添加紧急联系人 */
export function addContact(userId: number | string, contactUsername: string) {
  return http.post<ApiResponse<any>>('/contacts/add', { userId, contactUsername })
}

/** 删除紧急联系人 */
export function deleteContact(id: number, userId: number | string) {
  return http.delete<ApiResponse<any>>(`/contacts/${id}?userId=${userId}`)
}

// ==================== 聊天消息 ====================

/** 获取会话对话记录 */
export function getConversation(userId: number | string, targetUserId: number | string) {
  return http.get<ApiResponse<any[]>>(`/messages/conversation?userId=${userId}&targetUserId=${targetUserId}`)
}

/** 获取会话列表 */
export function getConversationList(userId: number | string) {
  return http.get<ApiResponse<any[]>>(`/messages/conversations?userId=${userId}`)
}

/** 发送聊天消息 */
export function sendChatMessage(senderId: number | string, receiverId: number | string, content: string) {
  return http.post<ApiResponse<any>>('/messages/send', { senderId, receiverId, content, messageType: 'TEXT' })
}

/** 发送紧急求助 */
export function sendEmergencyHelpApi(userId: number | string) {
  return http.post<ApiResponse<any>>('/messages/help', { userId })
}

/** 搜索用户 */
export function searchUsers(username: string) {
  return http.get<ApiResponse<any[]>>(`/users/search?username=${encodeURIComponent(username)}`)
}

// ==================== 紧急求助 ====================

/** 发送紧急求助 (POST /api/messages/help) */
export function sendEmergencyHelp(data: { userId?: number; helpType?: string; deviceCode?: string; location?: string; description?: string; message?: string; notifySecurity?: boolean; relatePirAlert?: boolean }) {
  return http.post<ApiResponse<any>>('/messages/help', data)
}

/** 获取紧急求助记录 (GET /api/messages/sos/list) */
export function getEmergencyRecords(params?: { page?: number; pageSize?: number; userId?: number }) {
  return http.get<ApiResponse<any>>('/messages/sos/list', { params })
}

// ==================== 聊天记录 ====================

/** 获取聊天记录 */
export function getChatRecords(keyword?: string) {
  return http.get<ApiResponse<any>>('/chat-record/list', { params: keyword ? { keyword } : {} })
}

// ==================== 知识库 ====================

/** 获取知识库列表 */
export function getKnowledgeList(keyword?: string) {
  return http.get<ApiResponse<any>>('/kb/list', { params: keyword ? { keyword } : {} })
}

/** 新增知识 */
export function addKnowledge(data: { title: string; category: string; content: string }) {
  return http.post<ApiResponse<any>>('/kb/add', data)
}

/** 更新知识 */
export function updateKnowledge(data: { id: number; title?: string; category?: string; content?: string; enabled?: boolean }) {
  return http.put<ApiResponse<any>>('/kb/update', data)
}

/** 删除知识 */
export function deleteKnowledge(id: number) {
  return http.delete<ApiResponse<any>>(`/kb/delete/${id}`)
}
