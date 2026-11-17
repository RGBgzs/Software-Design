import http from './index'
import type { ApiResponse, Device, PageData, AddDeviceForm } from '@/types'

/** 设备列表（分页） */
export function getDevicesApi(params?: { keyword?: string; onlineStatus?: number; page?: number; pageSize?: number; userId?: number; role?: string }) {
  return http.get<ApiResponse<PageData<Device>>>('/devices', { params })
}

/** 设备实时状态 */
export function getDeviceStatusApi(id: number) {
  return http.get<ApiResponse<Device>>(`/devices/${id}/status`)
}

/** 添加设备 */
export function addDeviceApi(data: AddDeviceForm) {
  return http.post<ApiResponse<{ id: number; deviceCode: string }>>('/devices', data)
}

/** 删除设备 */
export function deleteDeviceApi(id: number) {
  return http.delete<ApiResponse<boolean>>(`/devices/${id}`)
}

/** 开启布防（后端路径参数为 Long id） */
export function defenseOnApi(id: number, operator: string) {
  return http.post(`/devices/${id}/defense/on`, { operator })
}

/** 关闭布防 */
export function defenseOffApi(id: number, operator: string) {
  return http.post(`/devices/${id}/defense/off`, { operator })
}
