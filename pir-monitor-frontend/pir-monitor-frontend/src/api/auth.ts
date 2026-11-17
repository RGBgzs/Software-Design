import http from './index'
import type { ApiResponse, UserInfo, LoginForm, RegisterForm } from '@/types'

export function loginApi(data: LoginForm) {
  return http.post<ApiResponse<UserInfo>>('/login', data)
}

export function registerApi(data: RegisterForm) {
  return http.post<ApiResponse<null>>('/register', data)
}
