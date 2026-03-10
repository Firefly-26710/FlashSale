import axios from 'axios';
import { API_BASE_URL } from './config';
import { useAuthStore } from '../stores/authStore'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器 - 添加 token 到请求头
apiClient.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers['Authorization'] = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理常见错误
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response) {
      const authStore = useAuthStore()
      if (error.response.status === 401) {
        authStore.clearToken() // 清除 Pinia 中的 token
        window.location.href = '/login' // 重定向到登录页面
      } else {
        console.error('请求失败', error.response.data || error.message)
      }
    }
    return Promise.reject(error)
  }
)


// 认证相关 API
export const authApi = {
  // 用户登录
  login(username, password) {
    return apiClient.post('/auth/login', {
      username,
      password,
    }).then((response) => {
      const token = response.data.token
      const userId = response.data.id
      if (token && userId) {
        const authStore = useAuthStore()
        authStore.setToken(token)
        authStore.setUserId(userId)
      }
      return response
    })
  },

  // 用户登出
  logout() {
    const authStore = useAuthStore()
    authStore.clearToken()
    window.location.href = '/login' // 重定向到登录页面
  },

  // 用户注册
  register(username, password, role) {
    return apiClient.post('/auth/register', {
      username,
      password,
      role
    })
  },

    // 获取用户信息
  getUserInfo() {
    return apiClient.get('/auth/user-info')
  },

  // 修改密码
  changePassword(userId, oldPassword, newPassword) {
    return apiClient.post('/auth/change-password', {
      userId,
      oldPassword,
      newPassword
    })
  },

}
