import axios from 'axios'
import { API_BASE_URL } from './config'
import { useAuthStore } from '../stores/authStore'

const productClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json',
  },
})

productClient.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers['Authorization'] = `Bearer ${authStore.token}`
  }
  return config
})

export const productApi = {
  list() {
    return productClient.get('/products')
  },

  detail(id) {
    return productClient.get(`/products/${id}`)
  },
}
