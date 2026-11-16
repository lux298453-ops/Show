<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="400px"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    class="feishu-auth-dialog wf-auth"
  >
    <div class="wf-login-body" :aria-busy="fetching || redirecting">
      <button type="button" class="wf-login-close" aria-label="关闭登录窗口" @click="visible = false">
        <X :size="16" aria-hidden="true" />
      </button>
      <div class="wf-auth-brand">WireForge</div>
      <h2>{{ title }}</h2>
      <p class="wf-auth-hint">{{ currentUser ? `当前账号：${currentUser.name}` : '登录后按项目权限编辑与评论' }}</p>
      <button type="button" class="wf-auth-primary wf-login-main" :disabled="fetching || redirecting" @click="handleLogin">
        <LoaderCircle v-if="fetching || redirecting" :size="16" class="wf-auth-spinner" aria-hidden="true" />
        <span>{{ buttonLabel }}</span>
        <ArrowUpRight v-if="!fetching && !redirecting && authUrl" :size="16" aria-hidden="true" />
      </button>
      <p v-if="configMessage" class="wf-login-message" :role="configError ? 'alert' : 'status'">{{ configMessage }}</p>
      <button type="button" class="wf-auth-link wf-login-browse" @click="visible = false">
        {{ currentUser ? '取消，保留当前账号' : '暂不登录，继续查看' }}
      </button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowUpRight, LoaderCircle, X } from 'lucide-vue-next'
import { currentUser, type AuthUser } from '../services/auth'
import { apiUrl } from '../api/http'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'loginSuccess', user: AuthUser): void
}>()

const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const title = computed(() => currentUser.value ? '切换飞书账号' : '飞书登录')
const authUrl = ref('')
const fetching = ref(false)
const redirecting = ref(false)
const configMessage = ref('')
const configError = ref(false)
const buttonLabel = computed(() => {
  if (fetching.value) return '正在连接飞书…'
  if (redirecting.value) return '正在前往飞书…'
  if (configMessage.value) return '重试连接'
  return currentUser.value ? '继续前往飞书' : '使用飞书登录'
})
let configRequest: AbortController | null = null

async function loadFeishuConfig() {
  configRequest?.abort()
  const request = new AbortController()
  configRequest = request
  fetching.value = true
  authUrl.value = ''
  configMessage.value = ''
  configError.value = false
  try {
    const callbackUri = window.location.origin + '/auth/callback'
    const res = await fetch(apiUrl(`/auth/feishu/config?redirectUri=${encodeURIComponent(callbackUri)}`), { signal: request.signal })
    const data = await res.json()
    if (!res.ok || data?.code !== 0 || !data.data) throw new Error('Unavailable login configuration')
    if (request.signal.aborted) return
    if (data.data.feishuEnabled && data.data.authUrl) {
      authUrl.value = data.data.authUrl
    } else {
      configMessage.value = '飞书登录暂未启用，请联系管理员。'
    }
  } catch {
    if (request.signal.aborted) return
    configError.value = true
    configMessage.value = '暂时无法连接飞书，请重试。'
  } finally {
    if (configRequest === request) {
      configRequest = null
      fetching.value = false
    }
  }
}

function handleLogin() {
  if (fetching.value || redirecting.value) return
  if (!authUrl.value) {
    void loadFeishuConfig()
    return
  }
  redirecting.value = true
  try {
    sessionStorage.setItem('wf_return_url', window.location.pathname + window.location.search + window.location.hash)
  } catch {}
  window.location.href = authUrl.value
}

watch(() => props.modelValue, open => {
  if (open) {
    redirecting.value = false
    void loadFeishuConfig()
  } else {
    configRequest?.abort()
  }
}, { immediate: true })
onBeforeUnmount(() => configRequest?.abort())
</script>

<style>
.feishu-auth-dialog.el-dialog {
  max-width: calc(100vw - 32px);
  padding: 0 !important;
  border: 1px solid var(--wf-border);
  border-radius: 14px !important;
  background: var(--wf-surface) !important;
  box-shadow: 0 12px 44px rgb(15 23 42 / 10%) !important;
}
.feishu-auth-dialog .el-dialog__header { display: none; }
.feishu-auth-dialog .el-dialog__body { padding: 0 !important; }
.feishu-auth-dialog .wf-login-body { position: relative; padding: 34px 28px 24px; text-align: center; }
.feishu-auth-dialog .wf-login-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  padding: 6px;
  border-radius: 6px;
  background: transparent;
  color: var(--wf-text-muted);
  cursor: pointer;
}
.feishu-auth-dialog .wf-login-close:hover { background: var(--wf-bg); color: var(--wf-text-main); }
.feishu-auth-dialog h2 { margin: 19px 0 7px; font-size: 20px; font-weight: 600; line-height: 28px; letter-spacing: -0.3px; }
.feishu-auth-dialog .wf-login-main { width: 100%; margin-top: 24px; min-height: 40px; }
.feishu-auth-dialog .wf-login-browse { margin-top: 13px; }
.feishu-auth-dialog .wf-login-message { margin-top: 12px; color: var(--wf-text-muted); font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; }
.feishu-auth-dialog .wf-login-message[role="alert"] { color: var(--wf-auth-danger); }
@media (max-width: 420px) {
  .feishu-auth-dialog .wf-login-body { padding: 32px 20px 21px; }
}
</style>
