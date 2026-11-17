import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Alarm, HandleStatus } from '@/types'
import { getAlarmsApi, getAlarmDetailApi, handleAlarmApi } from '@/api/alarm'

export const useAlarmStore = defineStore('alarm', () => {
  const alarms = ref<Alarm[]>([])
  const total = ref(0)
  const currentDetail = ref<Alarm | null>(null)
  const unreadCount = ref(0)

  function addRealtimeAlarm(alarm: Alarm) {
    alarms.value.unshift(alarm)
    unreadCount.value++
  }

  async function fetchAlarms(page = 1, pageSize = 10, handleStatus?: HandleStatus) {
    const res = await getAlarmsApi({ page, pageSize, handleStatus })
    const data = res.data.data
    alarms.value = data.records
    total.value = data.total
  }

  async function fetchAlarmDetail(id: number) {
    const res = await getAlarmDetailApi(id)
    currentDetail.value = res.data.data
  }

  async function handleAlarm(id: number, handleStatus: 1 | 2, handleUser = 'user') {
    await handleAlarmApi(id, { handleStatus, handleUser })
    const idx = alarms.value.findIndex(a => a.id === id)
    if (idx !== -1) alarms.value[idx].handleStatus = handleStatus
    if (currentDetail.value?.id === id) currentDetail.value.handleStatus = handleStatus
  }

  function clearUnread() { unreadCount.value = 0 }

  return { alarms, total, currentDetail, unreadCount, addRealtimeAlarm, fetchAlarms, fetchAlarmDetail, handleAlarm, clearUnread }
})
