<template>
  <aside class="comment-panel w-[300px] h-full bg-white border-l border-slate-200/80 flex flex-col select-none text-slate-800 shadow-sm z-30">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-slate-100 flex items-center justify-between bg-slate-50/60">
      <div class="flex items-center gap-2">
        <div class="w-5 h-5 rounded-md bg-amber-500/10 text-amber-600 flex items-center justify-center font-bold text-xs">
          💬
        </div>
        <h2 class="text-xs font-bold text-slate-800 tracking-wide">项目评论</h2>
        <span class="text-[10px] text-slate-400 font-mono">Comments</span>
      </div>

      <button
        type="button"
        class="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded-md transition-colors cursor-pointer"
        title="退出评论模式"
        @click="emit('close')"
      >
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- 用户身份/昵称设置条 (方案 A：localStorage 本地设备昵称) -->
    <div class="px-3.5 py-2.5 bg-amber-50/40 border-b border-amber-100/60 flex items-center justify-between text-xs">
      <div class="flex items-center gap-2 min-w-0 flex-1">
        <div
          class="w-6 h-6 rounded-full text-white font-bold text-[10px] flex items-center justify-center shrink-0 shadow-sm"
          :class="getAvatarBg(userName)"
        >
          {{ (userName || '我').slice(0, 1).toUpperCase() }}
        </div>

        <div v-if="!isEditingName" class="flex items-center gap-1.5 min-w-0 truncate">
          <span class="text-[11px] text-slate-500">我的昵称：</span>
          <strong class="text-slate-800 text-xs font-semibold truncate">{{ userName }}</strong>
        </div>

        <div v-else class="flex items-center gap-1 min-w-0 flex-1">
          <input
            ref="nameInputRef"
            v-model="nameInput"
            type="text"
            maxlength="20"
            class="w-full text-xs px-2 py-0.5 bg-white border border-amber-300 rounded outline-none focus:ring-1 focus:ring-amber-500"
            @keydown.enter="saveName"
            @keydown.esc="isEditingName = false"
          />
        </div>
      </div>

      <button
        v-if="!isEditingName"
        type="button"
        class="p-1 text-slate-400 hover:text-amber-600 hover:bg-amber-100/50 rounded transition-colors cursor-pointer shrink-0 ml-1"
        title="修改我的昵称"
        @click="startEditName"
      >
        <Edit2 class="w-3.5 h-3.5" />
      </button>
      <button
        v-else
        type="button"
        class="px-2 py-0.5 bg-amber-500 hover:bg-amber-600 text-white text-[10px] font-semibold rounded transition-colors cursor-pointer shrink-0 ml-1"
        @click="saveName"
      >
        保存
      </button>
    </div>

    <!-- 过滤器 Tabs -->
    <div class="px-3 py-2 border-b border-slate-100 bg-white flex gap-1">
      <button
        type="button"
        class="flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all text-center cursor-pointer"
        :class="
          filterTab === 'unresolved'
            ? 'bg-amber-50 text-amber-700 border border-amber-200/80 font-bold shadow-xs'
            : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 border border-transparent'
        "
        @click="filterTab = 'unresolved'"
      >
        未解决 ({{ unresolvedCount }})
      </button>

      <button
        type="button"
        class="flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all text-center cursor-pointer"
        :class="
          filterTab === 'all'
            ? 'bg-slate-100 text-slate-800 border border-slate-200 font-bold shadow-xs'
            : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 border border-transparent'
        "
        @click="filterTab = 'all'"
      >
        全部 ({{ comments.length }})
      </button>
    </div>

    <!-- 评论列表 -->
    <div class="flex-1 overflow-y-auto p-3 space-y-2.5 custom-scrollbar">
      <div v-if="filteredComments.length === 0" class="py-12 px-4 text-center">
        <div class="w-10 h-10 rounded-full bg-amber-50 text-amber-500 flex items-center justify-center text-lg mx-auto mb-3">
          💬
        </div>
        <p class="text-xs font-semibold text-slate-700 mb-1">
          {{ filterTab === 'unresolved' ? '太棒了，所有评论均已解决！' : '当前暂无任何评论' }}
        </p>
        <p class="text-[11px] text-slate-400 leading-relaxed">
          点击画布或任意画板位置，即可立刻投放带序号的评论图钉进行评审讨论。
        </p>
      </div>

      <div
        v-for="item in filteredComments"
        :key="item.thread.id"
        class="comment-card p-3 rounded-xl border transition-all cursor-pointer relative group"
        :class="[
          selectedThreadId === item.thread.id
            ? 'bg-amber-50/50 border-amber-300 shadow-md ring-1 ring-amber-400/40'
            : 'bg-white hover:bg-slate-50/80 border-slate-200/80 hover:border-slate-300 shadow-xs',
          item.thread.resolved ? 'opacity-65 hover:opacity-100' : 'opacity-100',
        ]"
        @click="emit('selectThread', item.thread.id)"
      >
        <!-- 顶部信息栏 -->
        <div class="flex items-center justify-between gap-1.5 mb-1.5">
          <div class="flex items-center gap-1.5 min-w-0">
            <!-- 序号徽标 -->
            <span
              class="w-4 h-4 rounded-full text-white text-[9px] font-bold flex items-center justify-center shrink-0 font-mono"
              :class="item.thread.resolved ? 'bg-slate-400' : 'bg-amber-500'"
            >
              {{ item.displayIndex }}
            </span>
            <span class="text-[11px] font-semibold text-slate-800 truncate">
              {{ item.thread.pageName || '画板' }}
            </span>
          </div>

          <div class="flex items-center gap-1 shrink-0">
            <span v-if="item.thread.resolved" class="text-[9px] px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded font-medium">
              已解决
            </span>
            <span class="text-[10px] text-slate-400 font-mono">
              {{ formatTime(item.lastReply?.createdAt || item.thread.createdAt) }}
            </span>
          </div>
        </div>

        <!-- 首条评论内容预览 -->
        <div class="text-[11px] text-slate-600 line-clamp-2 leading-relaxed mb-2 break-words">
          <strong class="text-slate-800 font-medium mr-1">{{ item.firstReply?.author }}:</strong>
          {{ item.firstReply?.content || '（暂无文字）' }}
        </div>

        <!-- 底部条：回复计数 & 操作按钮 -->
        <div class="flex items-center justify-between text-[10px] text-slate-400 pt-1.5 border-t border-slate-100">
          <span class="flex items-center gap-1">
            <span v-if="item.thread.replies.length > 1" class="text-amber-600 font-semibold">
              {{ item.thread.replies.length }} 条讨论
            </span>
            <span v-else>1 条评论</span>
          </span>

          <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              type="button"
              class="px-1.5 py-0.5 text-[10px] rounded hover:bg-slate-200/70 text-slate-600 cursor-pointer"
              :title="item.thread.resolved ? '重新开启' : '标记解决'"
              @click.stop="emit('toggleResolve', item.thread.id)"
            >
              {{ item.thread.resolved ? '↺ 重开' : '✓ 解决' }}
            </button>
            <button
              type="button"
              class="p-0.5 text-slate-400 hover:text-red-500 rounded cursor-pointer"
              title="删除整条评论"
              @click.stop="onDeleteThread(item.thread.id)"
            >
              <Trash2 class="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { X, Edit2, Trash2 } from 'lucide-vue-next'
import type { CommentThread } from '../types'

const props = withDefaults(
  defineProps<{
    comments: CommentThread[]
    selectedThreadId?: number | null
    currentUser?: string
  }>(),
  {
    selectedThreadId: null,
    currentUser: '我',
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'selectThread', threadId: number): void
  (e: 'updateCurrentUser', name: string): void
  (e: 'toggleResolve', threadId: number): void
  (e: 'deleteThread', threadId: number): void
}>()

const filterTab = ref<'unresolved' | 'all'>('unresolved')
const isEditingName = ref(false)
const userName = ref(props.currentUser || '我')
const nameInput = ref('')
const nameInputRef = ref<HTMLInputElement | null>(null)

const unresolvedCount = computed(() => {
  return props.comments.filter((c) => !c.resolved).length
})

interface CommentItemWithIndex {
  thread: CommentThread
  displayIndex: number
  firstReply: any
  lastReply: any
}

const allCommentsWithIndex = computed<CommentItemWithIndex[]>(() => {
  return props.comments.map((thread, idx) => ({
    thread,
    displayIndex: idx + 1,
    firstReply: thread.replies && thread.replies.length > 0 ? thread.replies[0] : null,
    lastReply:
      thread.replies && thread.replies.length > 0
        ? thread.replies[thread.replies.length - 1]
        : null,
  }))
})

const filteredComments = computed(() => {
  if (filterTab.value === 'unresolved') {
    return allCommentsWithIndex.value.filter((item) => !item.thread.resolved)
  }
  return allCommentsWithIndex.value
})

function startEditName() {
  nameInput.value = userName.value
  isEditingName.value = true
  nextTick(() => {
    nameInputRef.value?.focus()
  })
}

function saveName() {
  const trimmed = nameInput.value.trim()
  if (trimmed) {
    userName.value = trimmed
    localStorage.setItem('wf_comment_author', trimmed)
    emit('updateCurrentUser', trimmed)
  }
  isEditingName.value = false
}

function onDeleteThread(threadId: number) {
  if (window.confirm('确定要删除整条评论线程吗？')) {
    emit('deleteThread', threadId)
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
