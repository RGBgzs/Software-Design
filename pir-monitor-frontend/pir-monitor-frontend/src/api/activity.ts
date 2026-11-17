import http from './index'
import type { ApiResponse, HumanRecord, PageData } from '@/types'

export function getRecordsApi(params: {
  deviceCode?: string; humanDetected?: number
  startTime?: string; endTime?: string
  page?: number; pageSize?: number
}) {
  return http.get<ApiResponse<PageData<HumanRecord>>>('/records', { params })
}
