<template>
  <div class="auth-page">
    <!-- 背景装饰 -->
    <div class="auth-bg">
      <div class="bg-grid"></div>
      <div class="bg-glow bg-glow-1"></div>
      <div class="bg-glow bg-glow-2"></div>
    </div>

    <div class="auth-container">
      <!-- 左侧品牌区 -->
      <div class="auth-brand">
        <div class="brand-content">
          <div class="brand-icon">
            <el-icon :size="48"><Monitor /></el-icon>
          </div>
          <h1 class="brand-title">智慧安防 AI 智能体控制台</h1>
          <p class="brand-subtitle">
            融合 AI 问答、PIR 监测、人脸识别、告警管理与紧急求助
          </p>
          <div class="brand-features">
            <div class="feature-item" v-for="f in features" :key="f.label">
              <el-icon :size="20"><component :is="f.icon" /></el-icon>
              <span>{{ f.label }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧登录/注册卡片 -->
      <div class="auth-card-wrap">
        <!-- 登录 -->
        <transition name="fade-slide" mode="out-in">
          <div v-if="mode === 'login'" key="login" class="auth-card">
            <h2 class="card-title">欢迎回来</h2>
            <p class="card-desc">登录您的账户以继续</p>
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              @keyup.enter="handleLogin"
            >
              <el-form-item prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="用户名"
                  size="large"
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loginLoading"
                @click="handleLogin"
                class="submit-btn"
              >
                登 录
              </el-button>
            </el-form>
            <p class="switch-text">
              还没有账户？<span class="switch-link" @click="switchMode('register')">立即注册</span>
            </p>
          </div>

          <!-- 注册 -->
          <div v-else key="register" class="auth-card">
            <h2 class="card-title">创建账户</h2>
            <p class="card-desc">注册后即可使用全部功能</p>
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              @keyup.enter="handleRegister"
            >
              <el-form-item prop="username">
                <el-input
                  v-model="registerForm.username"
                  placeholder="用户名"
                  size="large"
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="确认密码"
                  size="large"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="realName">
                <el-input
                  v-model="registerForm.realName"
                  placeholder="真实姓名"
                  size="large"
                  :prefix-icon="UserFilled"
                />
              </el-form-item>
              <el-form-item prop="role">
                <el-select v-model="registerForm.role" placeholder="选择角色" size="large" style="width: 100%">
                  <el-option label="居民" value="resident" />
                  <el-option label="保安" value="guard" />
                  <el-option label="管理员" value="admin" />
                </el-select>
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="registerLoading"
                @click="handleRegister"
                class="submit-btn"
              >
                注 册
              </el-button>
            </el-form>
            <p class="switch-text">
              已有账户？<span class="switch-link" @click="switchMode('login')">返回登录</span>
            </p>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, UserFilled, Monitor, Cpu, Camera, Warning, ChatDotRound } from '@element-plus/icons-vue'
import { loginApi, registerApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const emit = defineEmits<{ (e: 'login-success'): void }>()

const authStore = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const loginLoading = ref(false)
const registerLoading = ref(false)

const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  role: 'resident',
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const validateConfirmPass = (_rule: any, value: string, cb: any) => {
  if (value !== registerForm.password) cb(new Error('两次密码不一致'))
  else cb()
}

const registerRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPass, trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const features = [
  { icon: Cpu, label: 'AI 智能问答' },
  { icon: Camera, label: '人脸识别' },
  { icon: Warning, label: '智能告警' },
  { icon: ChatDotRound, label: '语音助手' },
]

function switchMode(m: 'login' | 'register') {
  mode.value = m
  loginFormRef.value?.resetFields()
  registerFormRef.value?.resetFields()
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loginLoading.value = true
  try {
    const res = await loginApi(loginForm)
    const data = res.data?.data || res.data
    if (data?.token) {
      localStorage.setItem('token', data.token)
      const user = (data as any).user || {}
      authStore.setLogin({
        token: data.token,
        userId: user.id || 1,
        username: user.username || loginForm.username,
        role: user.role || 'admin',
        realName: user.realName || loginForm.username,
      })
      ElMessage.success('登录成功')
      emit('login-success')
    } else {
      ElMessage.error((res.data as any)?.msg || (res.data as any)?.message || '登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败，请检查网络')
  } finally {
    loginLoading.value = false
  }
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  registerLoading.value = true
  try {
    const res = await registerApi({
      username: registerForm.username,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      realName: registerForm.realName,
      role: registerForm.role,
    })
    const code = res.data?.code ?? (res.data as any)?.code
    if (code === 200 || code === 0) {
      ElMessage.success('注册成功，请登录')
      switchMode('login')
      registerForm.username = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      registerForm.realName = ''
    } else {
      ElMessage.error((res.data as any)?.msg || (res.data as any)?.message || '注册失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '注册失败，请检查网络')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #0a1628 0%, #132244 30%, #1a3366 60%, #0d1f3c 100%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: .15;
}
.bg-glow-1 { width: 500px; height: 500px; background: #4a90d9; top: -150px; right: -100px; }
.bg-glow-2 { width: 400px; height: 400px; background: #2563eb; bottom: -100px; left: -80px; }

.auth-container {
  position: relative;
  z-index: 1;
  display: flex;
  width: 960px;
  max-width: 95vw;
  min-height: 560px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 25px 80px rgba(0,0,0,.4);
}

/* 左侧品牌 */
.auth-brand {
  width: 420px;
  background: linear-gradient(160deg, rgba(16,40,80,.95), rgba(12,28,60,.95));
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  border-right: 1px solid rgba(255,255,255,.06);
}
.brand-content { text-align: center; color: #fff; }
.brand-icon { color: #60a5fa; margin-bottom: 20px; }
.brand-title { font-size: 22px; font-weight: 700; letter-spacing: 2px; margin: 0 0 12px; }
.brand-subtitle { font-size: 13px; color: rgba(255,255,255,.6); line-height: 1.6; margin: 0 0 32px; }
.brand-features { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.feature-item {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 20px;
  background: rgba(255,255,255,.08); color: rgba(255,255,255,.75);
  font-size: 12px;
}

/* 右侧卡片 */
.auth-card-wrap {
  flex: 1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
.auth-card { width: 100%; max-width: 340px; }
.card-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 6px; }
.card-desc { font-size: 13px; color: #94a3b8; margin: 0 0 28px; }
.submit-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 4px; }
.switch-text { text-align: center; font-size: 13px; color: #94a3b8; margin-top: 20px; }
.switch-link { color: #3b82f6; cursor: pointer; font-weight: 600; }
.switch-link:hover { text-decoration: underline; }

.fade-slide-enter-active, .fade-slide-leave-active { transition: all .25s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateX(20px); }
.fade-slide-leave-to { opacity: 0; transform: translateX(-20px); }

@media (max-width: 800px) {
  .auth-brand { display: none; }
  .auth-container { width: 400px; }
}
</style>
