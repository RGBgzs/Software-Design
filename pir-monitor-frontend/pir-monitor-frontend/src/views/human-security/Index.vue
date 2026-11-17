<template>
  <div class="hs-index">
    <!-- 头部 -->
    <div class="hs-header">
      <h1 class="hs-title">🛡️ 智慧安防 AI 智能体控制台</h1>
      <p class="hs-subtitle">融合 AI 问答、语音助手、PIR 监测、人脸识别、告警管理与紧急求助</p>
    </div>

    <!-- 功能卡片网格 -->
    <div class="hs-cards">
      <el-card
        v-for="card in cards"
        :key="card.key"
        shadow="hover"
        class="hs-card"
        :class="`hs-card--${card.color}`"
        @click="goTo(card.route)"
      >
        <div class="hs-card__icon">{{ card.icon }}</div>
        <div class="hs-card__title">{{ card.title }}</div>
        <div class="hs-card__desc">{{ card.desc }}</div>
        <div class="hs-card__arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
        <el-tag
          v-if="card.badge"
          size="small"
          :type="card.badgeType || 'danger'"
          effect="dark"
          class="hs-card__badge"
        >
          {{ card.badge }}
        </el-tag>
      </el-card>
    </div>

    <!-- 底部状态栏 -->
    <div class="hs-footer">
      <div class="hs-footer__item">
        <span class="hs-footer__dot online"></span>
        <span>安防系统运行中</span>
      </div>
      <div class="hs-footer__item">
        <MqttStatus />
      </div>
      <div class="hs-footer__item">
        <span>{{ currentTime }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import MqttStatus from '@/components/shared/MqttStatus.vue'

interface FeatureCard {
  key: string
  title: string
  desc: string
  icon: string
  route: string
  color: string
  badge?: string
  badgeType?: 'danger' | 'warning' | 'success' | 'info'
}

const router = useRouter()
const currentTime = ref('')

const cards: FeatureCard[] = [
  {
    key: 'dashboard',
    title: '首页总控台',
    desc: '实时安防数据统计、设备状态一览、告警趋势图表',
    icon: '📊',
    route: '/human-security/dashboard',
    color: 'blue',
    badge: '核心',
    badgeType: 'danger',
  },
  {
    key: 'ai',
    title: 'AI 智能助手 · 小安',
    desc: 'AI 安防知识问答、智能分析建议、语音交互',
    icon: '🤖',
    route: '/human-security/ai',
    color: 'purple',
    badge: 'AI',
    badgeType: 'success',
  },
  {
    key: 'device',
    title: '设备管理',
    desc: '设备注册与配置、在线状态监控、远程控制',
    icon: '📡',
    route: '/human-security/device',
    color: 'cyan',
  },
  {
    key: 'pir',
    title: 'PIR 人体感应监测',
    desc: '实时人体检测、布防/撤防控制、感应记录查询',
    icon: '🔍',
    route: '/human-security/pir',
    color: 'orange',
    badge: '实时',
    badgeType: 'warning',
  },
  {
    key: 'face',
    title: '人脸识别',
    desc: '人脸注册与识别、陌生人告警、识别记录查询',
    icon: '👤',
    route: '/human-security/face',
    color: 'green',
  },
  {
    key: 'alarm',
    title: '告警管理',
    desc: '告警记录查看、告警确认处理、告警统计报表',
    icon: '🚨',
    route: '/human-security/alarm',
    color: 'red',
    badge: '重要',
    badgeType: 'danger',
  },
  {
    key: 'message',
    title: '消息中心',
    desc: '系统通知、设备消息、安防事件推送汇总',
    icon: '💬',
    route: '/human-security/message',
    color: 'teal',
  },
  {
    key: 'help',
    title: '紧急求助',
    desc: '一键紧急求助、位置上报、快速响应调度',
    icon: '🆘',
    route: '/human-security/help',
    color: 'crimson',
    badge: 'SOS',
    badgeType: 'danger',
  },
]

function goTo(route: string) {
  router.push(route)
}

function updateClock() {
  currentTime.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    weekday: 'short',
  })
}

let clockTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.hs-index {
  padding: 0;
  width: 100%;
  margin: 0 auto;
}

/* 头部 */
.hs-header {
  text-align: center;
  margin-bottom: 32px;
  padding: 32px 20px 28px;
  background: linear-gradient(135deg, #0a1a2e 0%, #1a3a5e 50%, #0d2137 100%);
  border-radius: 12px;
  color: #fff;
  border: 1px solid rgba(0, 212, 255, 0.2);
}
.hs-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px;
  background: linear-gradient(90deg, #00d4ff, #409eff, #00d4ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.hs-subtitle {
  font-size: 14px;
  color: #aed8f2;
  margin: 0;
  letter-spacing: 1px;
}

/* 卡片网格 */
.hs-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.hs-card {
  cursor: pointer;
  border-radius: 12px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 8px;
}
.hs-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}
.hs-card--blue:hover { border-color: #409eff; }
.hs-card--purple:hover { border-color: #a855f7; }
.hs-card--cyan:hover { border-color: #06b6d4; }
.hs-card--orange:hover { border-color: #f59e0b; }
.hs-card--green:hover { border-color: #10b981; }
.hs-card--red:hover { border-color: #ef4444; }
.hs-card--teal:hover { border-color: #14b8a6; }
.hs-card--crimson:hover { border-color: #dc143c; }

.hs-card__icon {
  font-size: 44px;
  margin-bottom: 12px;
}
.hs-card__title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.hs-card__desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
  margin-bottom: 16px;
  padding: 0 8px;
}
.hs-card__arrow {
  position: absolute;
  bottom: 12px;
  right: 16px;
  color: #c0c4cc;
  transition: all 0.3s;
}
.hs-card:hover .hs-card__arrow {
  color: #409eff;
  transform: translateX(4px);
}
.hs-card__badge {
  position: absolute;
  top: 12px;
  right: 12px;
}

/* 底部状态栏 */
.hs-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  color: #606266;
  font-size: 13px;
}
.hs-footer__item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hs-footer__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.hs-footer__dot.online {
  background: #10b981;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
