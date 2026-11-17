import http from './index'
import type { ApiResponse, Alarm, PageData, HandleAlarmForm } from '@/types'

/** 告警列表 */
export function getAlarmsApi(params: {
  deviceCode?: string; handleStatus?: number
  startTime?: string; endTime?: string
  page?: number; pageSize?: number
}) {
  return http.get<ApiResponse<PageData<Alarm>>>('/alarms', { params })
}

/** 告警详情 */
export function getAlarmDetailApi(id: number) {
  return http.get<ApiResponse<Alarm>>(`/alarms/${id}`)
}

/** 处理告警 */
export function handleAlarmApi(id: number, data: HandleAlarmForm) {
  return http.put<ApiResponse<boolean>>(`/alarms/${id}/handle`, data)
}
