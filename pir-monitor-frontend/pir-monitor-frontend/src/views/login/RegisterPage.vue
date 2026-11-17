<template>
  <div class="register-page">
    <canvas ref="canvasRef" class="particle-canvas"></canvas>
    <el-card class="register-card">
      <h1 class="register-title">注册账号</h1>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请确认密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="role">
          <el-select v-model="form.role" placeholder="选择角色" style="width:100%">
            <el-option label="居民" value="resident" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" @click="handleRegister" :loading="loading">注 册</el-button>
        </el-form-item>
        <el-form-item>
          <span style="font-size:14px;color:rgba(255,255,255,.6)">已有账号？</span>
          <el-link type="primary" @click="goLogin" style="font-size:14px">立即登录</el-link>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { registerApi } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

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

const form = reactive({
  username: '',
  realName: '',
  password: '',
  confirmPassword: '',
  role: 'resident',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3~20 个字符', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6~20 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== form.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await registerApi({ username: form.username, password: form.password, realName: form.realName, role: form.role })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    ElMessage.error('注册失败')
  } finally {
    loading.value = false
  }
}

function goLogin() { router.push('/login') }
</script>

<style scoped>
.register-page { height: 100vh; display: flex; align-items: center; justify-content: center; position: relative; background: url('@/assets/pexels-steve-12843338.jpg') center/cover no-repeat; }
.register-page::before { content: ''; position: absolute; inset: 0; background: rgba(0,0,0,.45); z-index: 0; }
.particle-canvas { position: absolute; inset: 0; z-index: 1; pointer-events: none; }
.register-card { width: 420px; padding: 30px; background: rgba(255,255,255,.1); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,.15); border-radius: 12px; position: relative; z-index: 2; }
.register-title { text-align: center; font-size: 22px; margin-bottom: 32px; color: #fff; }
.register-card :deep(.el-input__wrapper) { background: rgba(255,255,255,.12); box-shadow: none; }
.register-card :deep(.el-input__inner) { color: #fff; }
.register-card :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.5); }
.register-card :deep(.el-input__prefix) { color: rgba(255,255,255,.6); }
.register-card :deep(.el-select .el-input__wrapper) { background: rgba(255,255,255,.12); }
</style>
