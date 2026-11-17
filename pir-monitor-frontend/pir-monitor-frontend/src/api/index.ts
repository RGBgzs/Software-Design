import axios from 'axios'
import type { ApiResponse } from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：自动附加 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一处理
http.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    const errMsg = (res as any).message || (res as any).msg
    if (res.code !== 200 && res.code !== 0) {
      console.error(`[API Error] ${errMsg}`)
      return Promise.reject(new Error(errMsg || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default http
