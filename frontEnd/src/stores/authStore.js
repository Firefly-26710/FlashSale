import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '', // 从 localStorage 初始化 token
    userId: localStorage.getItem('userId') || null, // 从 localStorage 初始化 userId
  }),
  actions: {
    setToken(newToken) {
      this.token = newToken
      localStorage.setItem('token', newToken)
    },
    setUserId(newUserId) {
      this.userId = newUserId
      localStorage.setItem('userId', newUserId)
    },
    clearToken() {
      this.token = ''
      this.userId = null
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
    },
  },
  getters: {
    isAuthenticated: (state) => !!state.token, // 判断用户是否已登录
  },
})