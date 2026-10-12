<template>
  <aside class="comment-panel w-[300px] h-full bg-white dark:bg-[#202020] border-l border-slate-200/80 dark:border-[#383838] flex flex-col select-none text-slate-800 dark:text-slate-100 shadow-sm z-30">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-slate-100 dark:border-[#333] flex items-center justify-between bg-slate-50/60 dark:bg-[#252525]">
      <div class="flex items-center gap-2">
        <div class="w-5 h-5 rounded-md bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center font-bold text-xs">
          <MessageSquare class="w-3.5 h-3.5" />
        </div>
        <h2 class="text-xs font-bold text-slate-800 dark:text-white tracking-wide">项目评论</h2>
        <span class="text-[10px] text-slate-400 font-mono">Comments</span>
      </div>

      <button
        type="button"
        class="p-1 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-[#383838] rounded-md transition-colors cursor-pointer"
        title="退出评论模式"
        @click="emit('close')"
      >
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- 用户飞书实名身份条 -->
    <div class="px-3.5 py-2.5 bg-slate-50 dark:bg-[#252525] border-b border-slate-100 dark:border-[#333] flex items-center justify-between text-xs">
      <div v-if="authUser" class="flex items-center gap-2 min-w-0 flex-1">
        <div class="relative shrink-0">
          <img
            v-if="authUser.avatar"
            :src="authUser.avatar"
            :alt="userName"
            class="w-6 h-6 rounded-full object-cover border border-slate-200 dark:border-slate-700 shadow-2xs"
            @error="onAvatarError($event)"
          />
          <div
            v-else
            class="w-6 h-6 rounded-full text-white font-bold text-[10px] flex items-center justify-center shrink-0 shadow-sm"
            :class="getAvatarBg(userName)"
          >
            {{ userName.slice(0, 1).toUpperCase() }}
          </div>
        </div>

        <div class="flex items-center gap-1.5 min-w-0 truncate">
          <strong class="text-slate-800 dark:text-slate-100 text-xs font-semibold truncate">{{ userName }}</strong>
          <span
            v-if="projectRole"
            class="text-[9px] font-bold px-1.5 py-0.2 rounded-full shrink-0"
            :class="getRoleBadgeClass(projectRole)"
          >
            {{ roleLabel(projectRole) }}
          </span>
        </div>
      </div>

      <div v-else class="flex items-center gap-1.5 min-w-0 flex-1 text-slate-500 dark:text-slate-400">
        <User class="w-3.5 h-3.5 text-slate-400 shrink-0" />
        <span class="text-xs">未登录（只读预览）</span>
      </div>

      <button
        type="button"
        class="text-[11px] text-[#3370FF] hover:text-[#2860E1] dark:text-blue-400 font-semibold px-2 py-0.5 rounded hover:bg-blue-50 dark:hover:bg-blue-950/50 transition-colors cursor-pointer shrink-0 ml-1"
        :title="authUser ? '切换飞书账号' : '立即登录飞书'"
        @click="emit('openAuthModal')"
      >
        {{ authUser ? '切换' : '登录' }}
      </button>
    </div>

    <!-- 过滤器 Tabs -->
    <div class="px-3 py-2 border-b border-slate-100 dark:border-[#333] bg-white dark:bg-[#202020] flex gap-1">
      <button
        type="button"
        class="flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all text-center cursor-pointer"
        :class="
          filterTab === 'unresolved'
            ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-400 border border-amber-200/80 dark:border-amber-800/60 font-bold shadow-xs'
            : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-[#282828] border border-transparent'
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
            ? 'bg-slate-100 text-slate-800 dark:bg-[#333] dark:text-white border border-slate-200 dark:border-[#444] font-bold shadow-xs'
            : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-[#282828] border border-transparent'
        "
        @click="filterTab = 'all'"
      >
        全部 ({{ comments.length }})
      </button>
    </div>

    <!-- 评论列表 -->
    <div class="flex-1 overflow-y-auto p-3 space-y-2.5 custom-scrollbar">
      <div v-if="filteredComments.length === 0" class="py-12 px-4 text-center">
        <div class="w-10 h-10 rounded-full bg-amber-50 dark:bg-amber-950/30 text-amber-500 flex items-center justify-center mx-auto mb-3">
          <MessageSquare class="w-5 h-5" />
        </div>
        <p class="text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
          {{ filterTab === 'unresolved' ? '所有评论均已解决' : '当前暂无任何评论' }}
        </p>
        <p class="text-[11px] text-slate-400 leading-relaxed">
          点击画布或任意画板位置，即可投放带序号的评论图钉进行评审讨论。
        </p>
      </div>

      <div
        v-for="item in filteredComments"
        :key="item.thread.id"
        class="comment-card p-3 rounded-xl border transition-all cursor-pointer relative group"
        :class="[
          selectedThreadId === item.thread.id
            ? 'bg-amber-50/50 dark:bg-amber-950/20 border-amber-300 dark:border-amber-600 shadow-md ring-1 ring-amber-400/40'
            : 'bg-white dark:bg-[#262626] hover:bg-slate-50/80 dark:hover:bg-[#2e2e2e] border-slate-200/80 dark:border-[#383838] hover:border-slate-300 shadow-xs',
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
            <span class="text-[11px] font-semibold text-slate-800 dark:text-slate-100 truncate">
              {{ item.thread.pageName || '画板' }}
            </span>
          </div>

          <div class="flex items-center gap-1 shrink-0">
            <span v-if="item.thread.resolved" class="text-[9px] px-1.5 py-0.5 bg-slate-100 dark:bg-[#333] text-slate-500 dark:text-slate-300 rounded font-medium">
              已解决
            </span>
            <span class="text-[10px] text-slate-400 font-mono">
              {{ formatTime(item.lastReply?.createdAt || item.thread.createdAt) }}
            </span>
          </div>
        </div>

        <!-- 首条评论内容预览 (实名展示) -->
        <div class="flex items-center gap-1.5 mb-1">
          <img
            v-if="item.firstReply?.authorAvatar || item.thread.authorAvatar"
            :src="item.firstReply?.authorAvatar || item.thread.authorAvatar"
            class="w-4 h-4 rounded-full object-cover shrink-0 border border-slate-200 dark:border-[#444]"
            alt=""
            @error="onAvatarError($event)"
          />
          <span
            v-else
            class="w-4 h-4 rounded-full text-white text-[9px] font-bold flex items-center justify-center shrink-0"
            :class="getAvatarBg(item.firstReply?.author || item.thread.author)"
          >
            {{ (item.firstReply?.author || item.thread.author || '我').slice(0, 1).toUpperCase() }}
          </span>
          <strong class="text-slate-800 dark:text-slate-200 text-xs font-semibold">{{ item.firstReply?.author || item.thread.author }}</strong>
        </div>
        <div class="text-[11px] text-slate-600 dark:text-slate-300 line-clamp-2 leading-relaxed mb-2 break-words">
          {{ item.firstReply?.content || '（暂无文字）' }}
        </div>

        <!-- 底部条：回复计数 & 操作按钮 -->
        <div class="flex items-center justify-between text-[10px] text-slate-400 pt-1.5 border-t border-slate-100 dark:border-[#333]">
          <span class="flex items-center gap-1">
            <span v-if="item.thread.replies.length > 1" class="text-amber-600 dark:text-amber-400 font-semibold">
              {{ item.thread.replies.length }} 条讨论
            </span>
            <span v-else>1 条评论</span>
          </span>

          <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              type="button"
              class="px-2 py-0.5 text-[10px] rounded hover:bg-slate-200/70 dark:hover:bg-[#383838] text-slate-600 dark:text-slate-300 cursor-pointer font-medium"
              :title="item.thread.resolved ? '重新开启' : '标记解决'"
              @click.stop="emit('toggleResolve', item.thread.id)"
            >
              {{ item.thread.resolved ? '重开' : '解决' }}
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
import { ref, computed } from 'vue'
import { X, Trash2, MessageSquare, User } from 'lucide-vue-next'
import type { CommentThread } from '../types'
import { currentUser as authUser, projectRole, roleLabel, type UserRole } from '../services/auth'

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
  (e: 'openAuthModal'): void
}>()

const filterTab = ref<'unresolved' | 'all'>('unresolved')
const userName = computed(() => authUser.value?.name || props.currentUser || '我')

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

function getRoleBadgeClass(role?: UserRole) {
  switch (role) {
    case 'admin':
      return 'bg-amber-100 text-amber-700 dark:bg-amber-950/60 dark:text-amber-400'
    case 'editor':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-950/60 dark:text-blue-400'
    case 'viewer':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
    default:
      return 'bg-slate-100 text-slate-600'
  }
}

function onAvatarError(e: Event) {
  const target = e.target as HTMLImageElement
  if (target) {
    target.style.display = 'none'
  }
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
