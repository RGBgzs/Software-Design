/**
 * 消息中心 API
 * 所有请求路径不带 /api 前缀（axios baseURL 已设 /api）
 */
import http from './index'

export interface AddContactParams {
  userId?: number
  currentUserId?: number
  contactUsername: string
  username?: string
  phone?: string
  relation?: string
}

export interface SendMessageParams {
  senderId: number
  currentUserId?: number
  receiverUsername: string
  content: string
}

function normalizeUserId(userId?: number, currentUserId?: number): number {
  return userId || currentUserId || 1
}

/**
 * 获取紧急联系人列表
 * GET /api/contacts/list
 */
export function getContactList(userId?: number) {
  return http.get('/contacts/list', {
    params: { userId: userId || 1 }
  })
}

/**
 * 添加紧急联系人
 * POST /api/contacts/add
 */
export function addEmergencyContact(data: AddContactParams) {
  const finalUserId = normalizeUserId(data.userId, data.currentUserId)

  return http.post('/contacts/add', {
    userId: finalUserId,
    currentUserId: finalUserId,
    contactUsername: data.contactUsername,
    username: data.contactUsername,
    targetUsername: data.contactUsername,
    phone: data.phone || '',
    relation: data.relation || ''
  })
}

/**
 * 删除紧急联系人
 * DELETE /api/contacts/delete
 */
export function deleteEmergencyContact(id: number, userId?: number) {
  return http.delete('/contacts/delete', {
    params: { id, userId: userId || 1 }
  })
}

/**
 * 获取和某个联系人的聊天记录
 * GET /api/messages/conversation
 */
export function getConversation(userId: number, targetUserId: number) {
  return http.get('/messages/conversation', {
    params: { userId, targetUserId }
  })
}

/**
 * 获取会话列表
 * GET /api/messages/conversations
 */
export function getConversationList(userId: number) {
  return http.get('/messages/conversations', {
    params: { userId }
  })
}

/**
 * 发送普通消息
 * POST /api/messages/send
 */
export function sendNormalMessage(data: SendMessageParams) {
  return http.post('/messages/send', {
    senderId: data.senderId,
    currentUserId: data.senderId,
    receiverUsername: data.receiverUsername,
    content: data.content,
    messageType: 'TEXT'
  })
}

/**
 * 一键求助
 * POST /api/messages/help
 */
export function sendEmergencyHelp(userId?: number) {
  return http.post('/messages/help', {
    userId: userId || 1,
    currentUserId: userId || 1
  })
}

/**
 * 搜索用户
 * GET /api/users/search
 */
export function searchUser(username: string) {
  return http.get('/users/search', {
    params: { username }
  })
}

/**
 * 获取未读消息数
 * GET /api/messages/unread-count
 */
export function getUnreadCount(userId: number) {
  return http.get('/messages/unread-count', {
    params: { userId }
  })
}

/**
 * 标记消息已读
 * PUT /api/messages/read
 */
export function markMessageRead(id: number) {
  return http.put('/messages/read', { id })
}
