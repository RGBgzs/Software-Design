import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, Role, LoginForm } from '@/types'
import { loginApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

export const useAuthStore = defineStore('auth', () => {
  const saved = localStorage.getItem('user_info')
  const user = ref<UserInfo | null>(saved ? JSON.parse(saved) : null)
  const token = ref<string>(localStorage.getItem('token') || '')

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || null)

  function saveUser(u: UserInfo) {
    user.value = u
    localStorage.setItem('user_info', JSON.stringify(u))
  }

  function setLogin(data: { token: string; userId: number; username: string; role: string; realName?: string }) {
    token.value = data.token
    saveUser({
      id: data.userId,
      userId: data.userId,
      username: data.username,
      role: data.role as Role,
      realName: data.realName || data.username,
      token: data.token,
    } as UserInfo)
    localStorage.setItem('token', data.token)
  }

  async function login(form: LoginForm) {
    try {
      const res = await loginApi(form)
      const data: any = res.data.data
      const userData = data.user || data
      token.value = data.token
      saveUser({
        ...userData,
        token: data.token,
        role: data.role || userData.role || 'resident',
      } as UserInfo)
      localStorage.setItem('token', data.token)
      ElMessage.success(data.msg || data.message || '登录成功')
    } catch (err: any) {
      if (err.message && err.message !== '请求失败' && err.message !== 'Request failed') {
        ElMessage.error(err.message)
        throw err
      }
      console.warn('[Auth] 后端不可用，使用模拟登录')
      const mockUser: UserInfo = {
        id: 1, userId: 1, username: form.username, role: 'resident',
        realName: '用户', token: 'mock_token',
      }
      token.value = mockUser.token
      saveUser(mockUser)
      localStorage.setItem('token', mockUser.token)
      ElMessage.success('登录成功（离线模式）')
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user_info')
  }

  function hasRole(...roles: Role[]) {
    return role.value !== null && roles.includes(role.value as Role)
  }

  return { user, token, isLoggedIn, role, login, logout, setLogin, hasRole }
})
