// API 配置：使用相对路径，由 nginx 反向代理到后端容器
export const API_BASE_URL = '/api'

// Axios默认配置
export const AXIOS_CONFIG = {
  baseURL: API_BASE_URL,
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json'
  }
}