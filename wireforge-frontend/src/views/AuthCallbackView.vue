<template>
  <div class="min-h-screen bg-slate-50 dark:bg-[#181818] flex items-center justify-center p-4 select-none">
    <div class="w-full max-w-md bg-white dark:bg-[#222] rounded-2xl border border-slate-200 dark:border-[#333] shadow-xl p-8 flex flex-col items-center text-center">
      <!-- Feishu Logo Capsule -->
      <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-[#00D6B9] to-[#3370FF] p-3 flex items-center justify-center shadow-md mb-5">
        <svg viewBox="0 0 24 24" fill="none" class="w-8 h-8 text-white">
          <path
            d="M3.5 14.5L12 3L20.5 14.5L12 21L3.5 14.5Z"
            fill="currentColor"
            fill-opacity="0.9"
          />
          <path
            d="M12 3L20.5 14.5L12 11L3.5 14.5L12 3Z"
            fill="white"
            fill-opacity="0.95"
          />
        </svg>
      </div>

      <!-- Loading State -->
      <template v-if="loading">
        <h2 class="text-base font-bold text-slate-800 dark:text-slate-100">正在验证飞书授权...</h2>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1.5">
          正在换取飞书账号信息与企业工作区权限，请稍候
        </p>
        <div class="mt-6 flex items-center justify-center">
          <div class="w-7 h-7 border-3 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
        </div>
      </template>

      <!-- Success State -->
      <template v-else-if="success">
        <div class="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-2">
          <Check class="w-5 h-5 stroke-[2.5]" />
        </div>
        <h2 class="text-base font-bold text-slate-800 dark:text-slate-100">飞书登录成功</h2>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
          欢迎，<span class="font-bold text-blue-600 dark:text-blue-400">{{ authedUser?.name }}</span><template v-if="isSystemAdmin">（系统管理员）</template>
        </p>
        <p class="text-[11px] text-slate-400 mt-4">正在自动跳转回原型工作区...</p>
      </template>

      <!-- Error State -->
      <template v-else>
        <div class="w-10 h-10 rounded-full bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 flex items-center justify-center mb-2">
          <AlertCircle class="w-5 h-5" />
        </div>
        <h2 class="text-base font-bold text-slate-800 dark:text-slate-100">飞书授权失败</h2>
        <p class="text-xs text-rose-500 mt-1 px-4 break-all">{{ errorMessage }}</p>
        
        <div class="mt-6 w-full flex gap-3">
          <button
            type="button"
            class="flex-1 py-2 px-3 text-xs font-medium rounded-xl border border-slate-200 dark:border-[#383838] text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-[#282828] transition-colors cursor-pointer"
            @click="returnToWorkspace"
          >
            返回工作区
          </button>
          <button
            type="button"
            class="flex-1 py-2 px-3 text-xs font-semibold rounded-xl bg-[#3370FF] hover:bg-[#2860E1] text-white transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-wait"
            :disabled="retrying"
            @click="retryAuth"
          >
            {{ retrying ? '正在打开飞书…' : '重新授权' }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Check, AlertCircle } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { completeLogin, isSystemAdmin, type AuthUser } from '@/services/auth'
import { apiUrl } from '@/api/http'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const success = ref(false)
const errorMessage = ref('')
const authedUser = ref<AuthUser | null>(null)

function getTargetUrl(): string {
  try {
    const saved = sessionStorage.getItem('wf_return_url')
    if (saved && saved.startsWith('/')) {
      sessionStorage.removeItem('wf_return_url')
      return saved
    }
  } catch {}
  return '/'
}

function returnToWorkspace() {
  const target = getTargetUrl()
  router.push(target)
}

const retrying = ref(false)

/** 重新打开飞书授权页，原来所在的页面地址留在 sessionStorage 里，授权回来还能跳回去 */
async function retryAuth() {
  if (retrying.value) return
  retrying.value = true
  try {
    const callbackUri = window.location.origin + '/auth/callback'
    const res = await fetch(apiUrl(`/auth/feishu/config?redirectUri=${encodeURIComponent(callbackUri)}`))
    const authUrl = res.ok ? (await res.json())?.data?.authUrl : ''
    if (authUrl) {
      window.location.href = authUrl
      return
    }
  } catch {}
  retrying.value = false
  ElMessage.warning('飞书登录未配置或授权地址不可用')
}

onMounted(async () => {
  // 从 query 或 hash 或 location 解析 code
  const urlParams = new URLSearchParams(window.location.search)
  const code = (route.query.code as string) || urlParams.get('code') || ''
  const error = (route.query.error as string) || urlParams.get('error') || ''
  const errorDesc = (route.query.error_description as string) || urlParams.get('error_description') || ''

  if (error) {
    loading.value = false
    success.value = false
    errorMessage.value = errorDesc || `飞书返回授权错误: ${error}`
    return
  }

  if (!code) {
    loading.value = false
    success.value = false
    errorMessage.value = '未检测到飞书授权码 (code)，请重新发起登录'
    return
  }

  try {
    const res = await fetch(apiUrl('/auth/feishu/callback'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: code.trim() }),
    })

    const data = await res.json()
    if (!res.ok || data.code !== 0) {
      throw new Error(data.message || data.msg || '飞书换取登录信息失败')
    }

    const user = data.data.token ? await completeLogin(data.data.token, data.data.user) : null
    if (!user) {
      throw new Error('飞书登录凭证无效，请重新授权')
    }
    authedUser.value = user
    loading.value = false
    success.value = true

    ElMessage.success(`飞书登录成功，欢迎 ${user.name}`)

    // 若是在弹窗子窗口中打开，尝试通知母窗口并自闭合
    if (window.opener && !window.opener.closed) {
      try {
        window.opener.postMessage({ type: 'wf-feishu-login-success' }, window.location.origin)
        setTimeout(() => {
          window.close()
        }, 1000)
        return
      } catch {}
    }

    // 默认跳转回原型工作区
    setTimeout(() => {
      returnToWorkspace()
    }, 1200)
  } catch (err: any) {
    loading.value = false
    success.value = false
    errorMessage.value = err.message || '网络连接超时或飞书服务不可达'
  }
})
</script>
