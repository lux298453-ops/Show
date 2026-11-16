<template>
  <main class="wf-auth-result wf-auth">
    <div class="wf-result-content">
      <div class="wf-auth-brand">WireForge</div>
      <section class="wf-result-card" aria-live="polite" :aria-busy="loading">
        <div class="wf-result-icon" :class="{ 'is-success': success, 'is-error': !loading && !success }">
          <LoaderCircle v-if="loading" :size="21" class="wf-auth-spinner" aria-hidden="true" />
          <Check v-else-if="success" :size="21" aria-hidden="true" />
          <CircleAlert v-else :size="21" aria-hidden="true" />
        </div>
        <template v-if="loading">
          <h1>正在验证登录</h1>
          <p class="wf-auth-hint">正在确认你的飞书账号</p>
        </template>
        <template v-else-if="success">
          <h1>登录成功</h1>
          <p class="wf-auth-hint">欢迎回来，{{ authedUser?.name }}</p>
          <div class="wf-result-actions">
            <button type="button" class="wf-auth-primary" :disabled="returning" @click="returnToWorkspace">返回项目</button>
          </div>
          <p class="wf-result-return-hint">{{ popupLogin ? '即将关闭此窗口' : '即将返回登录前的页面' }}</p>
        </template>
        <template v-else>
          <h1>未能完成登录</h1>
          <p class="wf-auth-hint">{{ errorHint }}</p>
          <div class="wf-result-actions">
            <button type="button" class="wf-auth-secondary" :disabled="returning" @click="returnToWorkspace">返回项目</button>
            <button type="button" class="wf-auth-primary" :disabled="retrying" @click="retryAuth">
              <LoaderCircle v-if="retrying" :size="15" class="wf-auth-spinner" aria-hidden="true" />
              {{ retrying ? '正在连接…' : '重新登录' }}
            </button>
          </div>
          <p v-if="retryMessage" class="wf-result-error" role="alert">{{ retryMessage }}</p>
          <details v-if="errorMessage" class="wf-result-details">
            <summary>查看详情</summary>
            <p>{{ errorMessage }}</p>
          </details>
        </template>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, CircleAlert, LoaderCircle } from 'lucide-vue-next'
import { completeLogin, type AuthUser } from '@/services/auth'
import { apiUrl } from '@/api/http'

const router = useRouter()
const route = useRoute()
const loading = ref(true)
const success = ref(false)
const errorHint = ref('请重新登录，或返回项目继续查看。')
const errorMessage = ref('')
const authedUser = ref<AuthUser | null>(null)
const retrying = ref(false)
const retryMessage = ref('')
const returning = ref(false)
const popupLogin = ref(false)
const request = new AbortController()
let returnTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false

// 读取一次返回地址，手动返回和自动返回共用，避免重复消费后跳回首页。
const returnTarget = readReturnTarget()
function readReturnTarget(): string {
  try {
    const saved = sessionStorage.getItem('wf_return_url')
    if (saved?.startsWith('/') && !saved.startsWith('//')) {
      const target = new URL(saved, window.location.origin)
      if (target.origin === window.location.origin && target.pathname !== '/auth/callback') {
        return target.pathname + target.search + target.hash
      }
    }
  } catch {}
  return '/'
}

async function returnToWorkspace() {
  if (returning.value || disposed) return
  returning.value = true
  clearTimeout(returnTimer)
  try {
    sessionStorage.removeItem('wf_return_url')
  } catch {}
  try {
    await router.replace(returnTarget)
  } finally {
    returning.value = false
  }
}

async function retryAuth() {
  if (retrying.value) return
  retrying.value = true
  retryMessage.value = ''
  try {
    const callbackUri = window.location.origin + '/auth/callback'
    const res = await fetch(apiUrl(`/auth/feishu/config?redirectUri=${encodeURIComponent(callbackUri)}`), { signal: request.signal })
    const data = await res.json()
    if (disposed) return
    if (res.ok && data?.code === 0 && data.data?.feishuEnabled && data.data.authUrl) {
      window.location.href = data.data.authUrl
      return
    }
    retryMessage.value = '飞书登录暂不可用，请稍后重试或联系管理员。'
  } catch {
    if (disposed) return
    retryMessage.value = '暂时无法连接飞书，请重试。'
  }
  retrying.value = false
}

onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search)
  const queryValue = (key: string) => {
    const value = route.query[key]
    return (typeof value === 'string' ? value : '') || urlParams.get(key) || ''
  }
  const code = queryValue('code')
  const error = queryValue('error')
  if (error || !code.trim()) {
    loading.value = false
    errorHint.value = error ? '授权未完成，你可以重新登录。' : '登录信息已失效，请重新登录。'
    errorMessage.value = error
      ? queryValue('error_description') || `飞书返回授权错误：${error}`
      : '未检测到有效的飞书授权码。'
    return
  }

  try {
    const res = await fetch(apiUrl('/auth/feishu/callback'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: code.trim() }),
      signal: request.signal,
    })
    const data = await res.json()
    if (disposed) return
    if (!res.ok || data.code !== 0) throw new Error(data.message || data.msg || '飞书换取登录信息失败')
    const user = data.data?.token ? await completeLogin(data.data.token, data.data.user) : null
    if (disposed) return
    if (!user) throw new Error('飞书登录凭证无效，请重新授权')
    authedUser.value = user
    loading.value = false
    success.value = true

    if (window.opener && !window.opener.closed) {
      try {
        window.opener.postMessage({ type: 'wf-feishu-login-success' }, window.location.origin)
        popupLogin.value = true
        returnTimer = setTimeout(() => window.close(), 1000)
        return
      } catch {}
    }
    returnTimer = setTimeout(() => { void returnToWorkspace() }, 1200)
  } catch (err: unknown) {
    if (disposed) return
    loading.value = false
    errorMessage.value = err instanceof Error ? err.message : '网络连接超时或飞书服务不可达'
  }
})

onBeforeUnmount(() => {
  disposed = true
  request.abort()
  clearTimeout(returnTimer)
})
</script>

<style scoped>
.wf-auth-result { display: grid; place-items: center; min-height: 100vh; padding: 32px 16px; background: var(--wf-bg); }
.wf-result-content { width: 100%; max-width: 380px; }
.wf-result-content > .wf-auth-brand { margin-bottom: 18px; text-align: center; }
.wf-result-card { padding: 28px 24px; border: 1px solid var(--wf-border); border-radius: 14px; background: var(--wf-surface); text-align: center; }
.wf-result-icon { display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; margin-bottom: 15px; border-radius: 50%; color: var(--wf-auth-blue); background: var(--wf-auth-blue-soft); }
.wf-result-icon.is-success { color: var(--wf-auth-success); background: var(--wf-bg); }
.wf-result-icon.is-error { color: var(--wf-auth-danger); background: var(--wf-bg); }
.wf-result-card h1 { margin: 0 0 7px; font-size: 18px; font-weight: 600; line-height: 26px; }
.wf-result-actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 9px; margin-top: 23px; }
.wf-result-return-hint { margin-top: 12px; color: var(--wf-text-muted); font-size: 11px; line-height: 18px; }
.wf-result-details { margin-top: 17px; color: var(--wf-text-muted); text-align: left; font-size: 11px; line-height: 1.7; }
.wf-result-details summary { width: fit-content; cursor: pointer; }
.wf-result-details p { margin-top: 7px; overflow-wrap: anywhere; white-space: pre-wrap; }
.wf-result-error { margin-top: 12px; color: var(--wf-auth-danger); font-size: 12px; line-height: 1.65; }
@media (max-width: 420px) { .wf-result-card { padding: 24px 16px; } }
</style>
