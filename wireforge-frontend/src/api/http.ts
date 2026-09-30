import axios, { type InternalAxiosRequestConfig } from 'axios'
import { beginSave, finishSave } from '../utils/saveState'

type SaveConfig = InternalAxiosRequestConfig & { wfSaveTicket?: ReturnType<typeof beginSave>; wfReleaseSave?: () => void }
const saveQueues = new Map<string, Promise<void>>()

function finishRequest(config: SaveConfig | undefined, error?: string) {
  finishSave(config?.wfSaveTicket, error)
  config?.wfReleaseSave?.()
}

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

http.interceptors.request.use(async (config: SaveConfig) => {
  config.wfSaveTicket = beginSave(config, savedConfig => http.request(savedConfig))
  if (config.wfSaveTicket?.retryable) {
    // Serialize writes to one resource so an older HTML snapshot cannot land
    // after a newer edit. Different pages/resources remain independent.
    const key = config.wfSaveTicket.resource
    const previous = saveQueues.get(key) || Promise.resolve()
    let release!: () => void
    const current = new Promise<void>(resolve => { release = resolve })
    saveQueues.set(key, current)
    config.wfReleaseSave = () => {
      release()
      if (saveQueues.get(key) === current) saveQueues.delete(key)
    }
    await previous
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        finishRequest(response.config as SaveConfig, body.message || '操作失败')
        return Promise.reject(new Error(body.message || '操作失败'))
      }
      finishRequest(response.config as SaveConfig)
      return body.data
    }
    finishRequest(response.config as SaveConfig)
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
      msg = (error.config as SaveConfig | undefined)?.wfSaveTicket
        ? '保存请求超时，请保持本页打开并重试'
        : '请求响应超时，大模型生成任务可能较耗时，请稍后刷新查看'
    } else if (error.message === 'Network Error') {
      msg = '暂时无法连接服务，请检查连接后重试'
    } else {
      msg = error.message || '未知错误'
    }
    finishRequest(error.config as SaveConfig | undefined, msg)
    return Promise.reject(new Error(msg))
  },
)

export default http
