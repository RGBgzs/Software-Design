import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import mqtt, { type MqttClient } from 'mqtt'
import type { MqttReport, MqttStatus } from '@/types'
import { useDeviceStore } from './device'

export type HumanDetectedCallback = (deviceCode: string, report: MqttReport) => void
const _humanCallbacks: HumanDetectedCallback[] = []

export function onHumanDetected(cb: HumanDetectedCallback) {
  _humanCallbacks.push(cb)
}



/**
 * MQTT 协议（官方规范 05_MQTT协议规范.md）：
 *   订阅: human/device/+/data    (人体感应数据)
 *         human/device/+/status  (心跳)
 *         human/device/+/reply   (指令反馈)
 *   发布: human/device/{code}/command (布防撤防指令)
 *
 * Broker 默认: localhost:1883 (本地 EMQX/Mosquitto)
 */
const _raw = import.meta.env.VITE_MQTT_URL || 'ws://47.108.58.107:8083/mqtt'
const BROKER_URL = location.protocol === 'https:' ? _raw.replace('ws://', 'wss://').replace(':8083', ':8084') : _raw

const TOPIC_DATA   = 'human/device/+/data'
const TOPIC_STATUS = 'human/device/+/status'
const TOPIC_REPLY  = 'human/device/+/reply'
const TOPIC_PIR_DATA   = 'pir/device/+/data'
const TOPIC_PIR_STATUS = 'pir/device/+/status'
const TOPIC_PIR_ALARM  = 'pir/device/+/alarm'

const ALL_TOPICS = [TOPIC_DATA, TOPIC_STATUS, TOPIC_REPLY, TOPIC_PIR_DATA, TOPIC_PIR_STATUS, TOPIC_PIR_ALARM]

export const useMqttStore = defineStore('mqtt', () => {
  const client = ref<MqttClient | null>(null)
  const connected = ref(false)
  const connectionStatus = computed(() => connected.value ? 'connected' : 'disconnected')

  const connectError = ref(false)

  function connect() {
    if (client.value) return
    client.value = mqtt.connect(BROKER_URL, {
      keepalive: 30,
      clientId: `web_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`,
      connectTimeout: 5000,
      reconnectPeriod: 5000,  // 5 秒后自动重连
    })

    client.value.on('connect', () => {
      connected.value = true
      console.log('[MQTT] 已连接', BROKER_URL)
      client.value!.subscribe(ALL_TOPICS, (err) => {
        if (err) console.error('[MQTT] 订阅失败:', err)
        else console.log('[MQTT] 已订阅:', ALL_TOPICS.join(', '))
      })
    })

    client.value.on('message', (topic, payload) => {
      const text = payload.toString()
      try {
        if (topic.includes('/data')) {
          const data: MqttReport = JSON.parse(text)
          useDeviceStore().applyMqttReport(data)
          if (data.humanDetected === 1) {
            _humanCallbacks.forEach(cb => cb(data.deviceCode, data))
          }
        } else if (topic.includes('/status')) {
          const data: MqttStatus = JSON.parse(text)
          useDeviceStore().applyMqttStatus(data)
        } else if (topic.includes('/alarm')) {
          // pir/device/+/alarm — 设备告警消息
          try {
            const alarmData = JSON.parse(text)
            console.log('[MQTT] PIR 告警:', alarmData)
            // 通知设备 store 更新
            if (alarmData.deviceCode || alarmData.device_code) {
              useDeviceStore().applyMqttReport(alarmData as any)
            }
          } catch { /* parse error */ }
        } else if (topic.includes('/reply')) {
          console.log('[MQTT] 设备回复:', text)
        }
      } catch { /* parse error */ }
    })

    client.value.on('error', () => { connected.value = false; connectError.value = true })
    client.value.on('close', () => { connected.value = false })
  }

  /** 下发指令到指定设备（通过 topic b，与后端 MQTT 规范一致） */
  function sendCommand(_deviceCode: string, action: 'on' | 'off') {
    if (!client.value || !connected.value) { console.warn('[MQTT] 未连接，请通过 REST API 下发指令'); return }
    // 主通道：topic b（设备实际监听），纯文本 1/0
    const topic = 'b'
    const payload = action === 'on' ? '1' : '0'
    client.value.publish(topic, payload, { qos: 2 }, (err) => {
      if (err) console.error('[MQTT] 指令下发失败:', err)
      else console.log('[MQTT] 指令已下发:', topic, payload)
    })
  }

  function disconnect() { client.value?.end(); client.value = null; connected.value = false }

  return { client, connected, connectError, connectionStatus, connect, sendCommand, disconnect }
})
