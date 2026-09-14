import axios from 'axios'

const rawBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')
const baseURL = rawBaseUrl ? `${rawBaseUrl}/api` : '/api'

export function getFileUrl(url?: string | null): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url
  if (rawBaseUrl) {
    const cleanUrl = url.startsWith('/') ? url : `/${url}`
    return `${rawBaseUrl}${cleanUrl}`
  }
  return url
}

const http = axios.create({
  baseURL,
  timeout: 600000,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message || '操作失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    let msg = ''
    if (error.response) {
      const data = error.response.data
      if (data && typeof data === 'object') {
        msg = data.message || data.error || data.msg || ''
      }
      if (!msg) {
        const status = error.response.status
        if (status === 400) {
          msg = '请求参数有误或任务正在进行中，请稍候再试'
        } else if (status === 404) {
          msg = '请求的资源或页面不存在'
        } else if (status === 500) {
          msg = '服务端处理异常，请稍后重试'
        } else {
          msg = `服务响应错误 (HTTP ${status})`
        }
      }
    } else if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      msg = '⏱️ 请求响应超时，大模型生成任务可能较耗时，请稍后刷新查看'
    } else if (error.message === 'Network Error') {
      msg = '🔌 无法连接到后端服务，请确认后端已正常启动'
    } else {
      msg = error.message || '未知错误'
    }
    return Promise.reject(new Error(msg))
  },
)

export default http