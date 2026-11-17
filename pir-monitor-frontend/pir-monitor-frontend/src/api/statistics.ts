import http from './index'
import type { ApiResponse, StatisticsOverview, AlarmTrendItem } from '@/types'

/** 首页统计总览 */
export function getOverviewStatsApi() {
  return http.get<ApiResponse<StatisticsOverview>>('/statistics/overview')
}

/** 近7天告警趋势 */
export function getAlarmTrendApi() {
  return http.get<ApiResponse<AlarmTrendItem[]>>('/statistics/alarm-trend')
}
