import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Device, MqttReport, MqttStatus, OverviewPoint } from '@/types'
import { getDevicesApi, addDeviceApi, deleteDeviceApi, defenseOnApi, defenseOffApi } from '@/api/device'
import { useMqttStore } from './mqtt'
import { useAuthStore } from './auth'

export const useDeviceStore = defineStore('device', () => {
  const devices = ref<Device[]>([])
  const total = ref(0)
  const statusMap = ref<Record<string, { humanDetected: number; online: boolean; defenseStatus: number }>>({})

  function getRealtimeStatus(deviceCode: string) {
    return statusMap.value[deviceCode] || { humanDetected: 0, online: false, defenseStatus: 0 }
  }

  // ─── MQTT 实时更新 ───

  function applyMqttReport(report: MqttReport) {
    const prev = statusMap.value[report.deviceCode]
    statusMap.value[report.deviceCode] = {
      humanDetected: report.humanDetected,
      defenseStatus: prev?.defenseStatus ?? report.defenseStatus,
      online: true,
    }
    const idx = devices.value.findIndex(d => d.deviceCode === report.deviceCode)
    if (idx !== -1) {
      devices.value[idx].humanDetected = report.humanDetected
      devices.value[idx].onlineStatus = 1
      devices.value[idx].lastReportTime = report.timestamp
    }
  }

  function applyMqttStatus(status: MqttStatus) {
    if (statusMap.value[status.deviceCode]) {
      statusMap.value[status.deviceCode].online = status.onlineStatus === 1
    }
    const idx = devices.value.findIndex(d => d.deviceCode === status.deviceCode)
    if (idx !== -1) {
      devices.value[idx].onlineStatus = status.onlineStatus
    }
  }

  // ─── API ───

  async function fetchDevices() {
    const auth = useAuthStore()
    const res = await getDevicesApi({
      pageSize: 100,
      role: auth.user?.role || 'admin',
      userId: auth.user?.userId || auth.user?.id,
    })
    const data = res.data.data
    devices.value = Array.isArray(data) ? data : (data?.records || [])
    total.value = Array.isArray(data) ? data.length : (data?.total || 0)
    devices.value.forEach(d => {
      const prev = statusMap.value[d.deviceCode]
      statusMap.value[d.deviceCode] = {
        humanDetected: d.humanDetected,
        defenseStatus: prev?.defenseStatus ?? d.defenseStatus,
        online: d.onlineStatus === 1,
      }
    })
  }

  async function fetchMyDevices(userId: number) {
    const auth = useAuthStore()
    const res = await getDevicesApi({
      pageSize: 100,
      role: auth.user?.role || 'user',
      userId,
    })
    const data = res.data.data
    const list = Array.isArray(data) ? data : (data?.records || [])
    devices.value = list.filter((d: Device) => d.userId === userId)
    total.value = devices.value.length
    devices.value.forEach(d => {
      const prev = statusMap.value[d.deviceCode]
      statusMap.value[d.deviceCode] = {
        humanDetected: d.humanDetected,
        defenseStatus: prev?.defenseStatus ?? d.defenseStatus,
        online: d.onlineStatus === 1,
      }
    })
  }

  async function bindDevice(data: { deviceCode: string; deviceName: string; location: string; userId: number }) {
    const res = await addDeviceApi(data)
    return res.data
  }

  async function unbindDevice(id: number) {
    await deleteDeviceApi(id)
    devices.value = devices.value.filter(d => d.id !== id)
  }

  // ─── 布防/撤防 ───

  async function toggleDefense(id: number, deviceCode: string, action: 'on' | 'off') {
    const newStatus = action === 'on' ? 1 : 0
    if (statusMap.value[deviceCode]) statusMap.value[deviceCode].defenseStatus = newStatus
    const idx = devices.value.findIndex(d => d.deviceCode === deviceCode)
    if (idx !== -1) devices.value[idx].defenseStatus = newStatus

    try {
      if (action === 'on') {
        await defenseOnApi(id, 'user')
      } else {
        await defenseOffApi(id, 'user')
      }
    } catch {
      console.warn('[布防] API 异常（后端 MQTT 下发失败）')
    }
    try {
      const mqttStore = useMqttStore()
      mqttStore.sendCommand(deviceCode, action)
    } catch {}
  }

  const overviewPoints = computed<OverviewPoint[]>(() =>
    devices.value.map(d => {
      const rt = statusMap.value[d.deviceCode]
      return {
        deviceCode: d.deviceCode,
        deviceName: d.deviceName,
        location: d.location,
        online: rt?.online ?? d.onlineStatus === 1,
        humanDetected: (rt?.humanDetected ?? d.humanDetected) === 1,
        defense: (rt?.defenseStatus ?? d.defenseStatus) === 1,
        hasActiveAlarm: false,
      }
    })
  )

  return { devices, total, statusMap, overviewPoints, getRealtimeStatus, applyMqttReport, applyMqttStatus, fetchDevices, fetchMyDevices, bindDevice, unbindDevice, toggleDefense }
})
