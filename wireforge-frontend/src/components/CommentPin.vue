<template>
  <div
    class="comment-pin-container absolute z-[150] select-none"
    :style="{ left: `${thread.x}px`, top: `${thread.y}px` }"
    @click.stop
    @mousedown.stop
  >
    <!-- Figma 风格圆形/水滴图钉徽标 -->
    <div
      class="comment-pin-badge relative -translate-x-1/2 -translate-y-full cursor-pointer transition-all duration-150 group"
      :class="[
        thread.resolved ? 'opacity-65 hover:opacity-100' : 'opacity-100',
        isSelected ? 'scale-110' : 'hover:scale-105',
      ]"
      @click="toggleExpand"
    >
      <!-- 图钉气泡本体 -->
      <div
        class="w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs shadow-lg transition-colors border-2"
        :class="[
          thread.resolved
            ? 'bg-slate-500 border-slate-300 text-white shadow-slate-900/15'
            : 'bg-amber-500 hover:bg-amber-600 border-white text-white shadow-amber-950/25',
          isSelected ? 'ring-4 ring-[#0D99FF]/50 border-white' : '',
        ]"
      >
        <span class="font-mono text-[11px] leading-none">{{ index }}</span>
      </div>

      <!-- 下方尖角小三角 -->
      <div
        class="w-0 h-0 border-l-[4px] border-l-transparent border-r-[4px] border-r-transparent border-t-[5px] mx-auto -mt-[1px]"
        :class="thread.resolved ? 'border-t-slate-500' : 'border-t-amber-500'"
      ></div>

      <!-- 悬停轻量摘要卡片（仅在未展开 Popover 时出现） -->
      <div
        v-if="!isSelected && firstReply"
        class="absolute bottom-[calc(100%+6px)] left-1/2 -translate-x-1/2 hidden group-hover:flex flex-col bg-slate-900/90 backdrop-blur-md text-white px-2.5 py-1.5 rounded-lg shadow-xl text-[11px] max-w-[200px] pointer-events-none whitespace-normal z-50 animate-in fade-in zoom-in-95 duration-100"
      >
        <div class="flex items-center gap-1.5 font-medium text-amber-300 mb-0.5">
          <span class="w-1.5 h-1.5 rounded-full bg-amber-400"></span>
          <span>{{ firstReply.author }}</span>
          <span v-if="thread.resolved" class="text-[9px] text-slate-400 font-normal ml-auto">(已解决)</span>
        </div>
        <div class="line-clamp-2 text-slate-200 text-[10px] leading-tight break-all">
          {{ firstReply.content }}
        </div>
      </div>
    </div>

    <!-- 展开的评论线程弹窗 (Popover) -->
    <div
      v-if="isSelected"
      class="comment-popover absolute top-1 left-2 w-[310px] bg-white/98 backdrop-blur-xl border border-slate-200/90 rounded-2xl shadow-2xl shadow-slate-900/25 flex flex-col z-[160] overflow-hidden animate-in fade-in zoom-in-95 duration-150"
      @click.stop
      @mousedown.stop
    >
      <!-- Popover 头部 -->
      <div class="px-3.5 py-2.5 bg-slate-50/80 border-b border-slate-100 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span
            class="w-5 h-5 rounded-full text-white text-[10px] font-bold flex items-center justify-center shrink-0 font-mono"
            :class="thread.resolved ? 'bg-slate-500' : 'bg-amber-500'"
          >
            {{ index }}
          </span>
          <span class="text-xs font-semibold text-slate-800 truncate max-w-[130px]">
            {{ thread.pageName || '画板评论' }}
          </span>
        </div>

        <div class="flex items-center gap-1">
          <!-- 解决/重开按钮 -->
          <button
            type="button"
            class="px-2 py-0.5 text-[11px] rounded-md font-medium transition-colors cursor-pointer flex items-center gap-1"
            :class="
              thread.resolved
                ? 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                : 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100 border border-emerald-200/60'
            "
            :title="thread.resolved ? '重新开启此评论' : '标记为已解决'"
            @click="onToggleResolve"
          >
            <RotateCcw v-if="thread.resolved" class="w-3 h-3" />
            <Check v-else class="w-3 h-3" />
            <span>{{ thread.resolved ? '重开' : '解决' }}</span>
          </button>

          <!-- 删除按钮 -->
          <button
            type="button"
            class="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-md transition-colors cursor-pointer"
            title="删除整条评论"
            @click="onDelete"
          >
            <Trash2 class="w-3.5 h-3.5" />
          </button>

          <!-- 关闭弹窗 -->
          <button
            type="button"
            class="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded-md transition-colors cursor-pointer"
            title="关闭弹窗"
            @click="emit('close')"
          >
            <X class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- 评论回复列表 -->
      <div class="p-3 space-y-3 max-h-[220px] overflow-y-auto custom-scrollbar">
        <div
          v-for="(reply, rIdx) in thread.replies"
          :key="reply.id || rIdx"
          class="flex gap-2.5 text-xs"
        >
          <!-- 头像 -->
          <div
            class="w-6 h-6 rounded-full text-white font-bold text-[10px] flex items-center justify-center shrink-0 shadow-sm"
            :class="getAvatarBg(reply.author)"
          >
            {{ (reply.author || '我').slice(0, 1).toUpperCase() }}
          </div>

          <!-- 内容主体 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-baseline justify-between gap-1 mb-0.5">
              <span class="font-semibold text-slate-800 text-[11px] truncate">
                {{ reply.author }}
              </span>
              <span class="text-[10px] text-slate-400 font-mono shrink-0">
                {{ formatTime(reply.createdAt) }}
              </span>
            </div>
            <div class="text-slate-700 text-[11px] leading-relaxed break-words whitespace-pre-wrap bg-slate-50/70 p-2 rounded-xl border border-slate-100">
              {{ reply.content }}
            </div>
          </div>
        </div>
      </div>

      <!-- 底部回复输入框 -->
      <div class="p-2.5 border-t border-slate-100 bg-white">
        <div class="relative flex flex-col gap-1.5 bg-slate-50 rounded-xl border border-slate-200/80 p-1.5 focus-within:border-[#0D99FF] focus-within:bg-white focus-within:ring-2 focus-within:ring-[#0D99FF]/20 transition-all">
          <textarea
            ref="replyInputRef"
            v-model="replyText"
            placeholder="回复评论... (Enter 发送, Shift+Enter 换行)"
            rows="2"
            class="w-full text-xs bg-transparent border-none outline-none resize-none text-slate-800 placeholder:text-slate-400 custom-scrollbar"
            @keydown.enter.exact.prevent="onSubmitReply"
            @keydown.esc="emit('close')"
          ></textarea>

          <div class="flex items-center justify-between pt-1 border-t border-slate-100">
            <span class="text-[10px] text-slate-400">
              以 <strong class="text-slate-600 font-medium">{{ currentUser }}</strong> 的身份
            </span>
            <button
              type="button"
              class="px-3 py-1 bg-[#0D99FF] hover:bg-[#0c88e3] active:scale-95 text-white text-[11px] font-semibold rounded-lg shadow-sm transition-all disabled:opacity-40 disabled:pointer-events-none cursor-pointer"
              :disabled="!replyText.trim()"
              @click="onSubmitReply"
            >
              回复
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { X, Trash2, RotateCcw, Check } from 'lucide-vue-next'
import type { CommentThread } from '../types'

const props = withDefaults(
  defineProps<{
    thread: CommentThread
    index: number
    isSelected?: boolean
    currentUser?: string
  }>(),
  {
    isSelected: false,
    currentUser: '我',
  }
)

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'close'): void
  (e: 'reply', content: string): void
  (e: 'resolve', resolved: boolean): void
  (e: 'delete'): void
}>()

const replyText = ref('')
const replyInputRef = ref<HTMLTextAreaElement | null>(null)

const firstReply = computed(() => {
  return props.thread.replies && props.thread.replies.length > 0
    ? props.thread.replies[0]
    : null
})

function toggleExpand() {
  if (props.isSelected) {
    emit('close')
  } else {
    emit('select')
  }
}

watch(
  () => props.isSelected,
  (val) => {
    if (val) {
      nextTick(() => {
        replyInputRef.value?.focus()
      })
    }
  }
)

function onSubmitReply() {
  const text = replyText.value.trim()
  if (!text) return
  emit('reply', text)
  replyText.value = ''
}

function onToggleResolve() {
  emit('resolve', !props.thread.resolved)
}

function onDelete() {
  if (window.confirm('确定要删除整条评论线程吗？')) {
    emit('delete')
  }
}

function formatTime(isoStr?: string) {
  if (!isoStr) return '刚刚'
  try {
    const d = new Date(isoStr)
    const now = new Date()
    const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin}分钟前`
    const diffH = Math.floor(diffMin / 60)
    if (diffH < 24) return `${diffH}小时前`
    return `${d.getMonth() + 1}月${d.getDate()}日`
  } catch {
    return isoStr
  }
}

const colorPalette = [
  'bg-blue-500',
  'bg-emerald-500',
  'bg-indigo-500',
  'bg-purple-500',
  'bg-rose-500',
  'bg-amber-500',
]

function getAvatarBg(name?: string) {
  if (!name) return colorPalette[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = (hash + name.charCodeAt(i)) % colorPalette.length
  }
  return colorPalette[hash]
}
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}
</style>
