import http from './index'

/** 人脸服务状态 */
export function getFaceStatusApi() {
  return http.get('/face/status')
}

/** 人脸注册 */
export function registerFaceApi(personId: string, imageFile: File, personName?: string, userId?: number, personType?: string) {
  const form = new FormData()
  form.append('personId', personId)
  form.append('image', imageFile)
  if (personName) form.append('name', personName)
  if (userId) form.append('userId', String(userId))
  if (personType) form.append('personType', personType)
  return http.post('/face/register', form)
}

/** 获取已注册用户 */
export function getFaceUsersApi(userId?: number) {
  return http.get('/face/users', { params: userId ? { userId } : {} })
}

/** 删除已注册用户 */
export function deleteFaceUserApi(name: string, userId?: number) {
  return http.delete(`/face/users/${encodeURIComponent(name)}`, { params: userId ? { userId } : {} })
}

/** 获取识别记录 */
export function getFaceRecordsApi(userId?: number) {
  return http.get('/face/record/list', { params: userId ? { userId } : {} })
}

/** 上传识别 */
export function identifyFaceApi(image: File, deviceCode: string, location: string, userId?: number, alarmMode?: string, frame2?: File) {
  const form = new FormData()
  form.append('image', image)
  form.append('deviceCode', deviceCode)
  form.append('location', location)
  if (userId) form.append('userId', String(userId))
  if (alarmMode) form.append('alarmMode', alarmMode)
  if (frame2) form.append('frame2', frame2)
  return http.post('/face/identify', form)
}

/** 活体检测 (发送 blob) */
export function liveDetectApi(blob: Blob) {
  return http.post('/face/live/detect', blob, {
    headers: { 'Content-Type': 'image/jpeg' },
  })
}

/** TTS 合成 */
export function ttsSynthesizeApi(text: string, speed?: number) {
  return http.post('/face/tts/synthesize', { text, speed: speed ?? 1, sid: 0 })
}
