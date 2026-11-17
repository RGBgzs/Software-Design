<template>
  <div class="login-page">
    <canvas ref="canvasRef" class="particle-canvas"></canvas>
    <el-card class="login-card">
      <h1 class="login-title">人体感应智慧安防系统</h1>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading">登 录</el-button>
        </el-form-item>
        <el-form-item>
          <span style="font-size:14px;color:#909399">没有账号？</span>
          <el-link type="primary" @click="goRegister" style="font-size:14px">立即注册</el-link>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { LoginForm } from '@/types'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<LoginForm>({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// ─── 粒子背景 ───
const canvasRef = ref<HTMLCanvasElement>()
let animId = 0
interface Particle { x: number; y: number; vx: number; vy: number; r: number; alpha: number }

function initParticles(canvas: HTMLCanvasElement) {
  const ctx = canvas.getContext('2d')!
  let w = canvas.width = window.innerWidth, h = canvas.height = window.innerHeight
  const particles: Particle[] = Array.from({ length: 80 }, () => ({
    x: Math.random() * w, y: Math.random() * h,
    vx: (Math.random() - .5) * .6, vy: (Math.random() - .5) * .6,
    r: Math.random() * 2.5 + 1, alpha: Math.random() * .5 + .2,
  }))
  const mouse = { x: -999, y: -999 }

  const onResize = () => { w = canvas.width = window.innerWidth; h = canvas.height = window.innerHeight }
  const onMove = (e: MouseEvent) => { mouse.x = e.clientX; mouse.y = e.clientY }
  const onLeave = () => { mouse.x = -999; mouse.y = -999 }
  window.addEventListener('resize', onResize)
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseleave', onLeave)

  function draw() {
    ctx.clearRect(0, 0, w, h)
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0) p.x = w; if (p.x > w) p.x = 0
      if (p.y < 0) p.y = h; if (p.y > h) p.y = 0
      const dx = p.x - mouse.x, dy = p.y - mouse.y, dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < 120) { p.vx += dx / dist * .02; p.vy += dy / dist * .02 }
      p.vx *= .98; p.vy *= .98
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255,255,255,${p.alpha})`; ctx.fill()
    }
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j], dx = a.x - b.x, dy = a.y - b.y, dist = dx * dx + dy * dy
        if (dist < 15000) {
          ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(255,255,255,${.15 * (1 - dist / 15000)})`; ctx.stroke()
        }
      }
    }
    animId = requestAnimationFrame(draw)
  }
  draw()

  onUnmounted(() => {
    cancelAnimationFrame(animId)
    window.removeEventListener('resize', onResize)
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseleave', onLeave)
  })
}

onMounted(() => { if (canvasRef.value) initParticles(canvasRef.value) })

function goRegister() { router.push('/register') }

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.login({ ...form })
    const role = authStore.role
    if (role === 'admin') router.push('/admin/devices')
    else if (role === 'guard') router.push('/guard/overview')
    else router.push('/resident/dashboard')
  } catch { /* 错误已在 authStore 中提示 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page { height: 100vh; display: flex; align-items: center; justify-content: center; position: relative; background: url('@/assets/pexels-steve-12843338.jpg') center/cover no-repeat; }
.login-page::before { content: ''; position: absolute; inset: 0; background: rgba(0,0,0,.45); z-index: 0; }
.particle-canvas { position: absolute; inset: 0; z-index: 1; pointer-events: none; }
.login-card { width: 420px; padding: 30px; background: rgba(255,255,255,.1); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,.15); border-radius: 12px; position: relative; z-index: 2; }
.login-title { text-align: center; font-size: 22px; margin-bottom: 32px; color: #fff; }
.login-card :deep(.el-input__wrapper) { background: rgba(255,255,255,.12); box-shadow: none; }
.login-card :deep(.el-input__inner) { color: #fff; }
.login-card :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.5); }
.login-card :deep(.el-input__prefix) { color: rgba(255,255,255,.6); }
.login-card :deep(.el-button--primary) { width: 100%; }
</style>
