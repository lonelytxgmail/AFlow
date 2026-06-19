import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from './types'

/**
 * Type-safe axios instance for AFlow API.
 * All responses are automatically unwrapped from the ApiResponse<T> envelope.
 */
const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Response interceptor: unwrap ApiResponse envelope
instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse<unknown>
    if (!res.success) {
      const msg = res.message || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return res.data as any
  },
  (error) => {
    const msg =
      error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

// --- Typed HTTP helper methods ---

export function get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, { params, ...config }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>
}

export { instance as axiosInstance }
export default instance
