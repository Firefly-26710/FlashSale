// API配置
export const API_BASE_URL = 'http://localhost:6005/api'

// Axios默认配置
export const AXIOS_CONFIG = {
  baseURL: API_BASE_URL,
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json'
  }
}