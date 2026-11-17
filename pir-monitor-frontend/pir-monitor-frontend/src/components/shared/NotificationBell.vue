<template>
  <el-badge :value="alarmStore.unreadCount" :max="99" :hidden="alarmStore.unreadCount === 0">
    <el-icon :size="22" @click="handleClick" style="cursor: pointer">
      <Bell />
    </el-icon>
  </el-badge>
</template>

<script setup lang="ts">
import { Bell } from '@element-plus/icons-vue'
import { useAlarmStore } from '@/stores/alarm'
import { useRouter } from 'vue-router'

const alarmStore = useAlarmStore()
const router = useRouter()

function handleClick() {
  alarmStore.clearUnread()
  router.push('/resident/alarms')
}
</script>
