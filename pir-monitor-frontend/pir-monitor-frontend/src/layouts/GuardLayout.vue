<template>
  <el-container class="guard-layout">
    <el-header class="layout-header">
      <div class="header-left">
        <h2>🛡️ 安防总控 · 保安端</h2>
      </div>
      <div class="header-right">
        <MqttStatus />
        <el-dropdown @command="handleCommand">
          <span class="user-info">
              <el-tag size="small" :type="roleType" effect="dark" style="margin-right:4px">{{ roleLabel }}</el-tag>
            {{ authStore.user?.username || authStore.user?.realName || '用户' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
        >
          <el-menu-item index="/guard/overview">
            <el-icon><DataAnalysis /></el-icon>
            <span>安防总览</span>
          </el-menu-item>
          <el-menu-item index="/guard/alarms">
            <el-icon><Warning /></el-icon>
            <span>告警日志</span>
          </el-menu-item>
          <el-menu-item index="/guard/faces">
            <el-icon><UserFilled /></el-icon>
            <span>人脸识别记录</span>
          </el-menu-item>
          <el-menu-item index="/monitor" class="menu-monitor-link">
            <el-icon><Monitor /></el-icon>
            <span>综合监控</span>
          </el-menu-item>
          <el-menu-item index="/human-security" class="menu-hs-link" @click="openHumanSecurity">
            <el-icon><Platform /></el-icon>
            <span>智慧安防控制台</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { DataAnalysis, Warning, UserFilled, ArrowDown, Monitor, Platform } from '@element-plus/icons-vue'
import MqttStatus from '@/components/shared/MqttStatus.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const roleLabel = computed(() => {
  const map: Record<string, string> = { admin: '管理员', guard: '保安', resident: '居民' }
  return map[authStore.role || ''] || '用户'
})
const roleType = computed(() => {
  const map: Record<string, string> = { admin: 'success', guard: 'danger', resident: 'warning' }
  return map[authStore.role || ''] || 'info'
})

const activeMenu = computed(() => route.path)

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}

function openHumanSecurity() {
  window.open('http://localhost:8080/', '_blank')
}
</script>

<style scoped>
.guard-layout {
  height: 100vh;
}
.layout-header {
  background: #1f2d3d;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left h2 {
  font-size: 18px;
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-info {
  color: #bfcbd9;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
.layout-aside {
  background: #304156;
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
