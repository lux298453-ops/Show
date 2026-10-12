<template>
  <el-dialog
    v-model="visible"
    title=""
    width="400px"
    :show-close="false"
    append-to-body
    destroy-on-close
    class="feishu-auth-dialog"
  >
    <div class="relative overflow-hidden rounded-2xl bg-white dark:bg-[#222222] text-slate-800 dark:text-slate-100 select-none">
      <!-- Absolute Top-Right Close Button -->
      <button
        type="button"
        class="absolute top-3.5 right-3.5 z-10 p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-[#333] rounded-lg transition-colors cursor-pointer"
        title="关闭窗口"
        @click="close"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Modal Header with Feishu Brand Logo -->
      <div class="px-6 pt-5 pb-4 border-b border-slate-100 dark:border-[#333333] flex items-center pr-12">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-[#00D6B9] to-[#3370FF] p-1.5 flex items-center justify-center shadow-xs shrink-0">
            <svg viewBox="0 0 24 24" fill="none" class="w-5 h-5 text-white">
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
          <div>
            <h2 class="text-sm font-bold tracking-tight text-slate-900 dark:text-white">飞书账号登录</h2>
            <p class="text-xs text-slate-400 mt-0.5">即刻开启团队协同与评审</p>
          </div>
        </div>
      </div>

      <!-- Main Login Body -->
      <div class="p-6 flex flex-col items-center">
        <!-- Direct Jump Button to Official Feishu OAuth -->
        <button
          type="button"
          :disabled="fetching"
          class="w-full py-3 px-5 bg-[#3370FF] hover:bg-[#2860E1] text-white rounded-xl text-sm font-bold shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-all active:scale-[0.99] disabled:opacity-60"
          @click="handleJumpOAuth"
        >
          <span v-if="fetching">正在获取飞书授权地址...</span>
          <span v-else>使用飞书登录</span>
          <ArrowRight v-if="!fetching" class="w-4 h-4" />
        </button>


      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { X, ArrowRight } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import type { AuthUser } from '../services/auth'
import { apiUrl } from '../api/http'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'loginSuccess', user: AuthUser): void
}>()

const visible = ref(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
    if (v) {
      loadFeishuConfig()
    }
  }
)
watch(visible, (v) => {
  emit('update:modelValue', v)
})

const feishuConfig = ref<{ feishuEnabled: boolean; authUrl?: string }>({ feishuEnabled: false })
const fetching = ref(false)

async function loadFeishuConfig(): Promise<string> {
  if (fetching.value) return feishuConfig.value.authUrl || ''
  fetching.value = true
  try {
    const callbackUri = window.location.origin + '/auth/callback'
    const res = await fetch(apiUrl(`/auth/feishu/config?redirectUri=${encodeURIComponent(callbackUri)}`))
    if (res.ok) {
      const data = await res.json()
      if (data && data.data) {
        feishuConfig.value = data.data
        return data.data.authUrl || ''
      }
    }
  } catch (err) {
    console.warn('[FeishuLoginModal] Failed to load config:', err)
  } finally {
    fetching.value = false
  }
  return feishuConfig.value.authUrl || ''
}

onMounted(() => {
  loadFeishuConfig()
})

function close() {
  visible.value = false
}

async function handleJumpOAuth() {
  let authUrl = feishuConfig.value.authUrl
  if (!authUrl) {
    authUrl = await loadFeishuConfig()
  }
  if (authUrl) {
    try {
      sessionStorage.setItem('wf_return_url', window.location.pathname + window.location.search)
    } catch {}
    window.location.href = authUrl
  } else {
    ElMessage.warning('飞书登录未配置或授权地址不可用')
  }
}
</script>

<style>
.feishu-auth-dialog.el-dialog {
  padding: 0 !important;
  border-radius: 1rem !important;
  overflow: hidden !important;
  background: white !important;
  box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1) !important;
}
.dark .feishu-auth-dialog.el-dialog {
  background: #222222 !important;
}
.feishu-auth-dialog .el-dialog__header {
  display: none !important;
  height: 0 !important;
  padding: 0 !important;
  margin: 0 !important;
  border: none !important;
}
.feishu-auth-dialog .el-dialog__body {
  padding: 0 !important;
}
</style>
