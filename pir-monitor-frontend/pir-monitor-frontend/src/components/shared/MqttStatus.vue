<template>
  <el-tag :type="mqttStore.connected ? 'success' : 'warning'" size="small">
    {{ mqttStore.connected ? 'MQTT 实时' : '轮询模式' }}
  </el-tag>
</template>

<script setup lang="ts">
import { useMqttStore } from '@/stores/mqtt'
const mqttStore = useMqttStore()
</script>
