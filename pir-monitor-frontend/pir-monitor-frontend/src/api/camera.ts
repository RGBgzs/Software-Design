import http from './index'
import type { ApiResponse } from '@/types'

/** 获取摄像头实时流地址 */
export function getCameraStreamUrlApi(deviceCode: string) {
  return http.get<ApiResponse<{ streamUrl: string }>>(`/camera/${deviceCode}/stream`)
}

/** 手动截图 */
export function captureSnapshotApi(deviceCode: string) {
  return http.post<ApiResponse<{ snapshotUrl: string }>>(`/camera/${deviceCode}/snapshot`)
}
