<template>
  <el-container class="monitor-layout">
    <el-header class="layout-header">
      <div class="header-left">
        <h2>🛡️ 智慧安防 · 综合监控中心</h2>
      </div>
      <div class="header-right">
        <MqttStatus />
        <NotificationBell />
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
          background-color="#001529"
          text-color="#ffffffa6"
          active-text-color="#fff"
        >
          <el-menu-item index="/monitor/home">
            <el-icon><Monitor /></el-icon>
            <span>设备监控</span>
          </el-menu-item>
          <el-menu-item index="/monitor/records">
            <el-icon><Document /></el-icon>
            <span>历史记录</span>
          </el-menu-item>
          <el-menu-item index="/monitor/alarms">
            <el-icon><Bell /></el-icon>
            <span>告警记录</span>
          </el-menu-item>
          <el-menu-item index="/monitor/devices">
            <el-icon><Setting /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/monitor/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
          <el-menu-item index="/monitor/screen" @click="openScreen">
            <el-icon><Monitor /></el-icon>
            <span>监控大屏</span>
          </el-menu-item>
          <el-menu-item class="menu-hs-link" @click="openHumanSecurity">
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
import {
  Monitor,
  Document,
  Bell,
  Setting,
  DataAnalysis,
  ArrowDown,
  Platform,
} from '@element-plus/icons-vue'
import MqttStatus from '@/components/shared/MqttStatus.vue'
import NotificationBell from '@/components/shared/NotificationBell.vue'

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

function openScreen() {
  window.open('/monitor/screen', '_blank')
}

function openHumanSecurity() {
  window.open('http://localhost:8080/', '_blank')
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.monitor-layout {
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
  background: #001529;
  overflow-y: auto;
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
.el-menu {
  border-right: none;
}
</style>
