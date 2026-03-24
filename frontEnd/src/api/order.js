import axios from 'axios'
import { API_BASE_URL } from './config'
import { useAuthStore } from '../stores/authStore'

const orderClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 8000,
  headers: {
    'Content-Type': 'application/json',
  },
})

orderClient.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers['Authorization'] = `Bearer ${authStore.token}`
  }
  return config
})

export const orderApi = {
  // 发起秒杀下单（异步受理）。
  seckill(productId) {
    return orderClient.post(`/orders/seckill/${productId}`)
  },

  getByOrderId(orderId) {
    return orderClient.get(`/orders/${orderId}`)
  },

  getByUserId(userId) {
    return orderClient.get(`/orders/user/${userId}`)
  },
}
