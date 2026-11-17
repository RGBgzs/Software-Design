import { onMounted } from 'vue'
import { useMqttStore } from '@/stores/mqtt'

/**
 * 组合式函数：页面挂载时连接 MQTT
 * 全局共享同一连接，只需在其中一个页面调用 connect 即可
 */
export function useMqtt() {
  const mqttStore = useMqttStore()

  onMounted(() => {
    if (!mqttStore.connected) {
      mqttStore.connect()
    }
  })

  return { mqttStore }
}
