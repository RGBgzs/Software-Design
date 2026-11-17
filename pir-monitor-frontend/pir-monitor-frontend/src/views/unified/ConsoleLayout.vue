<template>
  <div class="console-layout">
    <AppHeader />
    <div class="console-body">
      <AppSidebar :active="sidebarActive" @change="switchModule" />
      <main class="console-content">
        <keep-alive>
          <component :is="currentComponent" :key="activeModule" />
        </keep-alive>
      </main>
    </div>
    <XiaoAnFloating />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, defineAsyncComponent, provide, onMounted, onUnmounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import XiaoAnFloating from './components/XiaoAnFloating.vue'
import { useVoiceActionStore } from '@/stores/voiceActionStore'
import { useFaceCapture } from '@/composables/useFaceCapture'

// PIR + 摄像头联动：布防状态下检测到人体 → 自动抓拍识别
useFaceCapture()

const DashboardModule   = defineAsyncComponent(() => import('@/views/human-security/Dashboard.vue'))
const DeviceModule      = defineAsyncComponent(() => import('@/views/human-security/DeviceManage.vue'))
const PirModule         = defineAsyncComponent(() => import('@/views/human-security/PirMonitor.vue'))
const MonitorModule     = defineAsyncComponent(() => import('@/views/human-security/FaceRecognition.vue'))
const AlarmModule       = defineAsyncComponent(() => import('@/views/human-security/AlarmManage.vue'))
const SosModule         = defineAsyncComponent(() => import('@/views/human-security/SosAlert.vue'))
const UsersManageModule = defineAsyncComponent(() => import('@/views/guard/UsersManage.vue'))

const activeModule = ref('dashboard')
const sidebarActive = ref('dashboard')

const moduleMap: Record<string, any> = {
  dashboard: DashboardModule,
  device: DeviceModule,
  pir: PirModule,
  monitor: MonitorModule,
  alarm: AlarmModule,
  sos: SosModule,
  users: UsersManageModule,
}

const currentComponent = computed(() => moduleMap[activeModule.value] || DashboardModule)

function switchModule(key: string) {
  if (moduleMap[key]) {
    activeModule.value = key
    sidebarActive.value = key
  }
}

provide('switchModule', switchModule)

const voiceStore = useVoiceActionStore()
function handleVoiceAction(action: any) {
  if (action.type === 'OPEN_MODULE' && action.module) switchModule(action.module)
}
function handleAssistantOpenModule(e: Event) {
  const detail = (e as CustomEvent).detail
  if (detail?.module) switchModule(detail.module)
}

onMounted(() => {
  voiceStore.onAction('OPEN_MODULE', handleVoiceAction)
  window.addEventListener('assistant:open-module', handleAssistantOpenModule)
})
onUnmounted(() => {
  voiceStore.offAction('OPEN_MODULE', handleVoiceAction)
  window.removeEventListener('assistant:open-module', handleAssistantOpenModule)
})
</script>

<style scoped>
.console-layout {
  display: flex; flex-direction: column; height: 100vh; overflow: hidden;
}
.console-body {
  display: flex; flex: 1; overflow: hidden;
}
.console-content {
  flex: 1; overflow-y: auto; overflow-x: hidden;
  background: var(--color-page-bg); padding: var(--space-5);
  min-height: 0; min-width: 0;
}
</style>
