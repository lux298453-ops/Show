<template>
  <aside
    class="fixed top-0 right-0 h-full w-[420px] bg-slate-900/95 backdrop-blur-2xl border-l border-white/10 shadow-[-20px_0_50px_rgba(0,0,0,0.7)] flex flex-col z-50 transition-transform duration-300 ease-in-out select-none"
    :class="isOpen ? 'translate-x-0' : 'translate-x-full pointer-events-none'"
    @click.stop
  >
    <!-- Drawer Header -->
    <div class="px-5 py-4 border-b border-white/10 flex items-center justify-between bg-slate-900/60">
      <div class="flex items-center gap-2.5">
        <div class="w-7 h-7 rounded-lg bg-[#0D99FF]/20 border border-[#0D99FF]/40 flex items-center justify-center text-[#38bdf8]">
          <Sliders class="w-4 h-4" />
        </div>
        <div>
          <div class="font-bold text-xs text-white tracking-wide flex items-center gap-1.5">
            <span>AI 调整</span>
            <span
              v-if="selectedElement"
              class="px-1.5 py-0.2 rounded bg-[#0D99FF]/30 text-[#38bdf8] text-[10px] font-mono font-medium border border-[#0D99FF]/40"
            >
              单元素微调模式
            </span>
          </div>
          <div class="text-[10px] text-slate-200 mt-0.5 truncate max-w-[260px]">
            当前画板：<span class="text-[#38bdf8] font-medium">{{ currentPage?.name || '未选择' }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <!-- 一键恢复进入时原版画板（防多轮污染，随时可回到最初进入时的版本） -->
        <button
          v-if="canRestoreSessionInitial"
          type="button"
          :disabled="saving"
          class="px-2.5 py-1 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 hover:text-rose-200 border border-rose-500/30 text-[11px] cursor-pointer flex items-center gap-1 transition-all disabled:opacity-50"
          title="一键撤销本次会话的所有调整与应用，完全恢复到进入时的初始画板"
          @click="handleRestoreSessionInitial"
        >
          <RotateCcw class="w-3 h-3" />
          <span>恢复进入时原版</span>
        </button>

        <!-- Close Drawer Button -->
        <button
          type="button"
          class="w-7 h-7 rounded-lg bg-white/10 hover:bg-white/20 active:bg-white/25 text-slate-200 hover:text-white flex items-center justify-center transition-all cursor-pointer border border-white/20 hover:border-white/30 shadow-xs"
          title="收起对话框"
          aria-label="关闭对话框"
          @click="$emit('close')"
        >
          <X class="w-4 h-4 stroke-[2.2]" />
        </button>
      </div>
    </div>

    <!-- Dialogue Messages History Stream -->
    <div ref="chatStreamRef" class="flex-1 overflow-y-auto custom-scrollbar p-5 space-y-4">
      
      <!-- Multi-turn History Snapshots (可回退至历史步骤) -->
      <div
        v-for="(snap, sIdx) in historySnapshots"
        :key="snap.id"
        class="p-3 rounded-xl bg-white/5 border border-white/10 text-slate-200 text-xs space-y-1.5"
      >
        <div class="flex items-center justify-between">
          <span class="font-medium text-slate-200 text-[11px]">历史快照 #{{ sIdx + 1 }}</span>
          <button
            type="button"
            class="px-2 py-0.5 rounded bg-white/10 hover:bg-white/15 border border-white/15 text-slate-200 hover:text-white text-[11px] cursor-pointer transition-colors"
            title="回退到此历史版本"
            @click="handleRollbackToSnapshot(snap)"
          >
            回退至此步骤
          </button>
        </div>
        <div class="text-[11px] text-slate-300">{{ snap.summary }}</div>
      </div>

      <!-- Initial Welcome Message if empty -->
      <div v-if="messages.length === 0 && !loading" class="p-4 rounded-xl bg-white/5 border border-white/10 text-slate-200 text-xs leading-relaxed space-y-2">
        <div class="font-semibold text-white flex items-center justify-between text-xs">
          <span class="flex items-center gap-1.5">
            <Sparkles class="w-3.5 h-3.5 text-[#38bdf8]" />
            <span>AI 原型微调与重构</span>
          </span>
          <span class="text-[10px] text-slate-400 font-mono">Figma AI / Cursor 级</span>
        </div>
        <div class="text-slate-200 text-xs leading-relaxed">
          直接描述想对当前画板【{{ currentPage?.name }}】进行的修改。也可以在左侧画板中<span class="text-[#38bdf8] font-semibold">点击任意元素</span>进行单点精准微调，消耗低、速度快、绝对不影响其他元素。
        </div>
      </div>

      <!-- Dialogue Turn Loop -->
      <template v-for="(msg, mIdx) in messages" :key="mIdx">
        <!-- User Message Bubble -->
        <div v-if="msg.role === 'user'" class="flex flex-col items-end gap-1">
          <div class="max-w-[88%] px-3.5 py-2.5 rounded-2xl rounded-tr-xs bg-[#0D99FF] text-white text-xs leading-relaxed shadow-md">
            {{ msg.content }}
          </div>
          <span class="text-[10px] text-slate-400 pr-1">{{ msg.time }}</span>
        </div>

        <!-- AI Response Card -->
        <div v-else-if="msg.role === 'assistant'" class="flex flex-col items-start gap-2 max-w-[96%]">
          <div class="flex items-center gap-1.5 text-slate-300 text-xs pl-1 font-medium">
            <div
              class="w-4 h-4 rounded-full flex items-center justify-center"
              :class="msg.isReverted ? 'bg-amber-500/20 text-amber-400' : (msg.isCommitted ? 'bg-emerald-500/20 text-emerald-400' : 'bg-cyan-500/20 text-cyan-400')"
            >
              <RotateCcw v-if="msg.isReverted" class="w-2.5 h-2.5" />
              <Check v-else class="w-2.5 h-2.5" />
            </div>
            <span>
              {{ msg.isReverted 
                  ? '修改已回退至修改前状态' 
                  : (msg.isCommitted 
                      ? '调整已保存到画板 · 支持随时回退' 
                      : (msg.isElementMode 
                          ? '元素微调已生效 · 已在屏幕上实时预览' 
                          : '调整方案已就绪 · 已在屏幕上实时预览')) }}
            </span>
          </div>

          <!-- Diff Card -->
          <div class="w-full rounded-2xl bg-slate-800/80 border border-white/10 p-3.5 space-y-3 shadow-lg">
            <div class="text-xs text-slate-200 leading-normal flex items-center justify-between">
              <span class="font-medium text-white">{{ msg.summary || '已完成调整' }}</span>
              <span v-if="msg.snapshotId" class="text-[10px] text-slate-300 font-mono">{{ msg.snapshotId }}</span>
              <span v-else-if="msg.isElementMode" class="text-[10px] text-cyan-300 font-mono px-1.5 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/20">单元素微调</span>
            </div>

            <!-- Diff list items with semantic badges -->
            <div v-if="msg.diffList && msg.diffList.length > 0" class="space-y-1.5 bg-black/30 rounded-xl p-2.5 border border-white/5 text-xs">
              <div
                v-for="(diff, dIdx) in msg.diffList"
                :key="dIdx"
                class="flex items-start gap-2 text-[11px]"
              >
                <span
                  v-if="diff.includes('[删除]') || diff.includes('删除') || diff.includes('移除')"
                  class="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-400 font-bold font-mono text-[10px] shrink-0"
                >- 删除</span>
                <span
                  v-else-if="diff.includes('[新增]') || diff.includes('新增') || diff.includes('添加')"
                  class="px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold font-mono text-[10px] shrink-0"
                >+ 新增</span>
                <span
                  v-else-if="diff.includes('[修改]') || diff.includes('调整') || diff.includes('样式') || diff.includes('修改')"
                  class="px-1.5 py-0.5 rounded bg-sky-500/20 text-sky-400 font-bold font-mono text-[10px] shrink-0"
                >~ 修改</span>
                <span v-else class="text-[#38bdf8] font-mono shrink-0">•</span>

                <span class="text-slate-200 leading-tight">
                  {{ diff.replace(/^\[(删除|修改|新增)\]\s*/, '') }}
                </span>
              </div>
            </div>

            <!-- 状态 1：草稿/预览状态 (尚未提交也未撤销) -->
            <div v-if="!msg.isCommitted && !msg.isReverted" class="space-y-2 pt-1">
              <div class="flex items-center gap-2">
                <!-- 保留并应用修改 -->
                <button
                  type="button"
                  :disabled="saving"
                  class="flex-1 py-1.5 px-3 rounded-xl bg-[#0D99FF] hover:bg-[#0284c7] text-white font-semibold text-xs shadow-md transition-all flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50"
                  @click="handleCommit(msg)"
                >
                  <Check class="w-3.5 h-3.5" />
                  <span>{{ saving ? '保存中...' : '保留并应用修改' }}</span>
                </button>

                <!-- 还原草稿 -->
                <button
                  type="button"
                  class="py-1.5 px-3 rounded-xl bg-white/5 hover:bg-rose-500/20 text-slate-300 hover:text-rose-300 border border-white/10 hover:border-rose-500/30 text-xs font-medium transition-all flex items-center gap-1 cursor-pointer"
                  title="放弃本次修改并还原到上一步 (Ctrl+Z)"
                  @click="handleRevert(msg)"
                >
                  <RotateCcw class="w-3 h-3" />
                  <span>还原</span>
                </button>
              </div>

              <!-- 按住对比原版 (整页模式有效) -->
              <button
                v-if="!msg.isElementMode"
                type="button"
                class="w-full py-1 rounded-lg bg-black/20 hover:bg-black/40 border border-white/5 text-slate-300 hover:text-white text-[11px] transition-all flex items-center justify-center gap-1 cursor-pointer"
                title="按住不放查看修改前，松开恢复修改后"
                @mousedown="$emit('start-compare')"
                @mouseup="$emit('end-compare')"
                @mouseleave="$emit('end-compare')"
              >
                <Split class="w-3 h-3 text-slate-300" />
                <span>按住查看修改前对比 (松开恢复)</span>
              </button>
            </div>

            <!-- 状态 2：已提交/已应用状态 (仍支持随时回退与撤回应用) -->
            <div v-else-if="msg.isCommitted && !msg.isReverted" class="flex items-center justify-between pt-2 border-t border-white/10">
              <div class="flex items-center gap-1.5 text-emerald-400 text-xs font-medium">
                <Check class="w-3.5 h-3.5" />
                <span>已应用至画板</span>
              </div>
              <button
                type="button"
                :disabled="msg.isReverting"
                class="px-3 py-1.5 rounded-xl bg-white/10 hover:bg-rose-500/20 text-slate-200 hover:text-rose-200 border border-white/15 hover:border-rose-500/40 text-xs font-medium flex items-center gap-1.5 cursor-pointer transition-all disabled:opacity-50"
                title="撤销本次应用，恢复到应用前的画板状态 (支持快捷键 Ctrl+Z)"
                @click="handleRevertCommitted(msg)"
              >
                <RotateCcw class="w-3 h-3" />
                <span>{{ msg.isReverting ? '回退中...' : '回退此修改' }}</span>
              </button>
            </div>

            <!-- 状态 3：已被回退状态 (支持反悔并重新应用) -->
            <div v-else-if="msg.isReverted" class="flex items-center justify-between pt-2 border-t border-white/10">
              <div class="flex items-center gap-1.5 text-slate-400 text-xs">
                <RotateCcw class="w-3 h-3" />
                <span>此修改已回退</span>
              </div>
              <button
                type="button"
                :disabled="msg.isReverting"
                class="px-2.5 py-1 rounded-lg bg-sky-500/10 hover:bg-sky-500/20 text-sky-400 hover:text-sky-300 border border-sky-500/30 text-xs font-medium flex items-center gap-1 cursor-pointer transition-all disabled:opacity-50"
                title="重新应用本次修改"
                @click="handleReapply(msg)"
              >
                <span>{{ msg.isReverting ? '处理中...' : '重新应用' }}</span>
              </button>
            </div>
          </div>

          <span class="text-[10px] text-slate-400 pl-1">{{ msg.time }}</span>
        </div>
      </template>

      <!-- Loading State (AI Thinking / Analyzing) -->
      <div v-if="loading" class="flex flex-col items-start gap-2 max-w-[96%]">
        <div class="flex items-center gap-2 text-slate-300 text-xs pl-1 font-medium">
          <div class="w-4 h-4 text-[#38bdf8] animate-spin-fast flex items-center justify-center">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-dasharray="32" stroke-dashoffset="12" d="M12 2a10 10 0 0 1 10 10 10 10 0 0 1-10 10 10 10 0 0 1-10-10"/>
            </svg>
          </div>
          <span>{{ selectedElement ? '正在对选中元素进行极致局部微调...' : '正在分析当前画板结构并生成调整方案...' }}</span>
        </div>

        <div class="w-full rounded-2xl bg-slate-800/80 border border-white/10 p-3.5 space-y-3 shadow-lg">
          <div class="space-y-2 text-xs">
            <div class="flex items-center gap-2 text-slate-300">
              <div class="w-4 h-4 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0">
                <Check class="w-2.5 h-2.5" />
              </div>
              <span class="text-slate-200">
                {{ selectedElement ? `已捕获目标 <${selectedElement.tag}> 元素节点代码` : `已检索画板【${currentPage?.name}】结构` }}
              </span>
            </div>

            <div class="flex items-center gap-2 text-slate-200 font-medium">
              <div class="w-4 h-4 rounded-full bg-[#0D99FF]/20 text-[#38bdf8] flex items-center justify-center shrink-0 animate-pulse">
                <div class="w-1.5 h-1.5 rounded-full bg-[#38bdf8]"></div>
              </div>
              <span class="text-[#38bdf8]">
                {{ selectedElement ? '大模型单点重构中 · 物理隔离零副作用...' : '正在重新计算页面样式与布局变动...' }}
              </span>
            </div>

            <div class="flex items-center gap-2 text-slate-400">
              <div class="w-4 h-4 rounded-full bg-white/5 border border-white/10 flex items-center justify-center shrink-0">
                <div class="w-1.5 h-1.5 rounded-full bg-slate-400"></div>
              </div>
              <span class="text-slate-300">即将完成局部替换并实时挂载预览</span>
            </div>
          </div>

          <div class="space-y-2 pt-1">
            <div class="h-3 rounded bg-white/5 shimmer-bar w-full"></div>
            <div class="h-3 rounded bg-white/5 shimmer-bar w-4/5"></div>
            <div class="h-3 rounded bg-white/5 shimmer-bar w-2/3"></div>
          </div>
        </div>
      </div>

      <!-- Error State Banner -->
      <div v-if="errorMessage" class="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center justify-between">
        <span>{{ errorMessage }}</span>
        <button
          type="button"
          class="px-2 py-0.5 rounded bg-rose-500/20 hover:bg-rose-500/30 text-white font-medium cursor-pointer"
          @click="errorMessage = ''"
        >
          关闭
        </button>
      </div>
    </div>

    <!-- Bottom Prompt & Target Scope Area -->
    <div class="p-4 bg-slate-900/95 border-t border-white/10 flex flex-col gap-2.5">
      
      <!-- ===== Figma AI / Cursor Style Target Scope Card ===== -->
      <!-- Case 1: An element is selected -->
      <div
        v-if="selectedElement"
        class="p-3 rounded-2xl bg-gradient-to-r from-[#0D99FF]/15 via-slate-800/80 to-slate-800/80 border border-[#0D99FF]/40 shadow-lg space-y-2 select-none"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="w-6 h-6 rounded-lg bg-[#0D99FF]/20 border border-[#0D99FF]/50 flex items-center justify-center text-[#38bdf8]">
              <Crosshair class="w-3.5 h-3.5" />
            </div>
            <div class="flex items-center gap-1.5">
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-[#0D99FF]/30 text-[#38bdf8] font-mono font-bold">
                &lt;{{ selectedElement.tag }}&gt;
              </span>
              <span class="text-xs font-semibold text-white max-w-[170px] truncate" :title="selectedElement.label">
                {{ selectedElement.label }}
              </span>
            </div>
          </div>

          <button
            type="button"
            class="px-2 py-0.5 rounded-lg bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white text-[11px] flex items-center gap-1 cursor-pointer transition-colors"
            title="取消选中，切换回整页画板调整"
            @click="$emit('clear-selected-element')"
          >
            <X class="w-3 h-3" />
            <span>切回整页</span>
          </button>
        </div>

        <!-- Token & Isolation Metrics Badge -->
        <div class="flex items-center justify-between text-[10px] text-slate-300 pt-0.5 border-t border-white/5">
          <span class="text-emerald-400 font-medium">物理级单点隔离 · 绝不误改其他元素</span>
          <span class="text-cyan-300 font-mono">Token 省 95% (~300) · 耗时 2~3s</span>
        </div>

        <!-- Quick Suggestion Chips (Cursor / v0.dev style) -->
        <div class="flex flex-wrap gap-1.5 pt-1">
          <button
            v-for="chip in quickChips"
            :key="chip"
            type="button"
            class="px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/15 active:bg-[#0D99FF]/30 border border-white/10 hover:border-white/20 text-[11px] text-slate-200 hover:text-white transition-all cursor-pointer select-none"
            @click="handleApplyQuickChip(chip)"
          >
            {{ chip }}
          </button>
        </div>
      </div>

      <!-- Case 2: No element selected (Prompt to click any element) -->
      <div
        v-else
        class="px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-[11px] text-slate-300 flex items-center justify-between"
      >
        <span class="flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
          <span>在画板上点击任意元素，即可针对该元素单独微调</span>
        </span>
        <span class="text-[10px] font-mono text-[#38bdf8]">整页调整模式</span>
      </div>

      <!-- Input Box -->
      <div class="relative bg-black/40 border border-white/12 focus-within:border-[#0D99FF]/60 rounded-2xl p-2.5 transition-colors">
        <textarea
          ref="textareaRef"
          v-model="inputPrompt"
          rows="3"
          :placeholder="inputPlaceholder"
          class="w-full bg-transparent text-xs text-white placeholder-slate-400 outline-none resize-none leading-relaxed custom-scrollbar"
          @keydown="onTextareaKeydown"
        ></textarea>

        <div class="flex items-center justify-between pt-1">
          <span
            class="text-[10px] px-2.5 py-1 rounded-lg border font-medium transition-colors"
            :class="selectedElement 
              ? 'bg-[#0D99FF]/20 text-[#38bdf8] border-[#0D99FF]/40' 
              : 'bg-white/10 text-slate-200 border-white/15'"
          >
            {{ selectedElement ? `仅作用于选中 <${selectedElement.tag}>` : '作用于当前整页画板' }}
          </span>

          <!-- Send Button (纯图标，无文字，高质感) -->
          <button
            type="button"
            :disabled="!inputPrompt.trim() || loading"
            class="w-7 h-7 rounded-lg bg-[#0D99FF] hover:bg-[#0284c7] text-white flex items-center justify-center shadow-md transition-all cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed"
            title="发送微调指令 (Enter)"
            aria-label="发送"
            @click="handleSend"
          >
            <ArrowUp class="w-4 h-4" />
          </button>
        </div>
      </div>

      <div class="flex items-center justify-between text-[11px] text-slate-300 px-1 font-normal">
        <span>Enter 发送 / Shift + Enter 换行 / 支持随时撤回</span>
        <span class="text-slate-400">WireForge 原型系统</span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { Sliders, X, Check, RotateCcw, ArrowUp, Split, Sparkles, Crosshair } from 'lucide-vue-next'
import { projectApi, type AiAdjustResponse } from '../api/project'
import type { Page } from '../types'

interface HistorySnapshot {
  id: string
  pageId: number
  summary: string
  originalHtml: string
  modifiedHtml: string
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content?: string
  time: string
  summary?: string
  diffList?: string[]
  snapshotId?: string
  originalHtml?: string
  modifiedHtml?: string
  // 单元素微调模式字段
  isElementMode?: boolean
  elementId?: string
  elementTag?: string
  elementLabel?: string
  action?: string
  originalSnippet?: string
  modifiedSnippet?: string
  isCommitted?: boolean
  isReverted?: boolean
  isReverting?: boolean
}

const props = withDefaults(
  defineProps<{
    projectId: number
    currentPage: Page | null
    isOpen: boolean
    currentDraftHtml?: string | null
    selectedElement?: {
      id: string
      tag: string
      label: string
      html: string
    } | null
  }>(),
  {
    currentDraftHtml: null,
    selectedElement: null,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'preview-draft', pageId: number, draftHtml: string): void
  (e: 'revert-draft', pageId: number): void
  (e: 'start-compare'): void
  (e: 'end-compare'): void
  (e: 'commit-success', pageId: number, newHtml: string): void
  (e: 'clear-selected-element'): void
  (e: 'patch-element-success', payload: {
    action: 'REPLACE' | 'DELETE'
    elementId: string
    summary: string
    originalSnippet: string
    modifiedSnippet: string
    onApplied?: (newCleanHtml: string) => void
  }): void
  (e: 'toast', msg: string): void
}>()

const inputPrompt = ref('')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const messages = ref<ChatMessage[]>([])
const historySnapshots = ref<HistorySnapshot[]>([])
const chatStreamRef = ref<HTMLDivElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// 针对当前画板进入 AI 调整时的初始原版 HTML（会话级终极回退锚点，永不被单次修改覆盖）
const sessionInitialHtml = ref<string>('')
// 针对当前画板最近一次基准 HTML
const artboardBaseHtml = ref<string>('')

const canRestoreSessionInitial = computed(() => {
  if (!props.currentPage) return false
  if (props.currentDraftHtml && props.currentDraftHtml !== sessionInitialHtml.value) return true
  if (props.currentPage.html_content && props.currentPage.html_content !== sessionInitialHtml.value) return true
  return messages.value.some(
    (m) => m.role === 'assistant' && (m.isCommitted || (!m.isReverted && props.currentDraftHtml))
  )
})

// 动态 Placeholder
const inputPlaceholder = computed(() => {
  if (props.selectedElement) {
    return `对选中的 <${props.selectedElement.tag}> 进行微调（例如：换成渐变暖色、加粗文字、改为圆角胶囊）...`
  }
  return `描述您想对【${props.currentPage?.name || '当前画板'}】整页调整的内容...`
})

// 基于选中元素类型的智能快捷 Prompt 建议卡片 (Cursor / v0.dev 风格)
const quickChips = computed(() => {
  if (!props.selectedElement) return []
  const tag = (props.selectedElement.tag || '').toLowerCase()
  if (tag === 'button' || tag === 'a') {
    return ['换成暖橙色渐变', '加大字号并加粗', '改成圆角胶囊样式', '增加发光立体投影', '删除此按钮']
  }
  if (tag === 'img' || tag === 'svg') {
    return ['替换为圆角头像样式', '增加微光边框', '尺寸等比放大 1.2 倍', '删除此图标']
  }
  if (tag === 'input' || tag === 'textarea' || tag === 'select') {
    return ['改为悬浮胶囊搜索框', '边框加粗并高亮', '内边距加大更圆润', '删除此输入框']
  }
  if (tag === 'div' || tag === 'section' || tag === 'header' || tag === 'footer') {
    return ['背景换为深色磨砂玻璃', '增加 16px 圆角与柔和投影', '居中对齐内容', '删除此卡片容器']
  }
  return ['换成暖橙色渐变', '加大字号并加粗', '增加高雅微光投影', '改成圆角胶囊样式', '删除此元素']
})

function handleApplyQuickChip(chip: string) {
  inputPrompt.value = chip
  nextTick(() => {
    textareaRef.value?.focus()
  })
}

// 监听画板切换，确保严格隔离目标画板
watch(
  () => props.currentPage?.id,
  (newPageId) => {
    if (!newPageId) return
    const cur = props.currentPage?.html_content || ''
    sessionInitialHtml.value = cur
    artboardBaseHtml.value = cur
    errorMessage.value = ''
    historySnapshots.value = []
    messages.value = []
  },
  { immediate: true }
)

watch(
  () => props.isOpen,
  (open) => {
    if (open) {
      nextTick(() => {
        textareaRef.value?.focus()
        scrollToBottom()
      })
    }
  }
)

function scrollToBottom() {
  nextTick(() => {
    if (chatStreamRef.value) {
      chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight
    }
  })
}

function getCurrentTime() {
  const now = new Date()
  const h = String(now.getHours()).padStart(2, '0')
  const m = String(now.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function onTextareaKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  const val = inputPrompt.value.trim()
  if (!val || loading.value || !props.currentPage) return

  const targetPageId = props.currentPage.id
  const targetPageName = props.currentPage.name
  errorMessage.value = ''

  // ===== 分支 A：单元素微调模式 (Figma AI / Cursor 极速单点模式) =====
  if (props.selectedElement) {
    const elTag = props.selectedElement.tag
    const elLabel = props.selectedElement.label
    const elId = props.selectedElement.id
    const elHtml = props.selectedElement.html
    const beforePageHtml = props.currentDraftHtml || props.currentPage.html_content || ''

    messages.value.push({
      role: 'user',
      content: `【微调 <${elTag}> ${elLabel}】: ${val}`,
      time: getCurrentTime(),
    })
    inputPrompt.value = ''
    scrollToBottom()

    loading.value = true
    try {
      const res = await projectApi.aiAdjustElement(props.projectId, targetPageId, {
        elementId: elId,
        elementTag: elTag,
        elementLabel: elLabel,
        elementHtml: elHtml,
        instruction: val,
      })

      const assistantMsg: ChatMessage = {
        role: 'assistant',
        time: getCurrentTime(),
        summary: res.summary || '已完成微调',
        diffList: [
          res.action === 'DELETE'
            ? `[删除] 已彻底移除 <${elTag}> ${elLabel}`
            : `[修改] 已针对 <${elTag}> ${elLabel} 更新样式与结构`,
        ],
        action: res.action,
        originalSnippet: elHtml,
        modifiedSnippet: res.modifiedSnippet,
        originalHtml: beforePageHtml,
        modifiedHtml: beforePageHtml,
        elementId: elId,
        elementTag: elTag,
        elementLabel: elLabel,
        isElementMode: true,
        isCommitted: false,
        isReverted: false,
      }

      messages.value.push(assistantMsg)
      emit('patch-element-success', {
        action: res.action as 'REPLACE' | 'DELETE',
        elementId: elId,
        summary: res.summary,
        originalSnippet: elHtml,
        modifiedSnippet: res.modifiedSnippet,
        onApplied: (newCleanHtml: string) => {
          assistantMsg.modifiedHtml = newCleanHtml
        },
      })
      emit('toast', `单元素微调成功 (耗时 ~2s, Token 消耗 ~300)`)
    } catch (err: any) {
      errorMessage.value = '微调失败: ' + (err.message || '网络异常，请重试')
    } finally {
      loading.value = false
      scrollToBottom()
    }
    return
  }

  // ===== 分支 B：整页画板调整模式 =====
  messages.value.push({
    role: 'user',
    content: val,
    time: getCurrentTime(),
  })
  inputPrompt.value = ''
  scrollToBottom()

  const contextHtml = props.currentDraftHtml || props.currentPage.html_content || ''

  loading.value = true
  try {
    const res: AiAdjustResponse = await projectApi.aiAdjust(props.projectId, targetPageId, {
      instruction: val,
      contextHtml,
    })

    if (!res || !res.modifiedHtml) {
      throw new Error('AI 未能生成有效的画板修改方案')
    }

    const snapId = `快照 #${historySnapshots.value.length + 1}`

    const snapshotItem: HistorySnapshot = {
      id: snapId,
      pageId: targetPageId,
      summary: res.summary || `修改 ${targetPageName}`,
      originalHtml: contextHtml,
      modifiedHtml: res.modifiedHtml,
    }

    messages.value.push({
      role: 'assistant',
      time: getCurrentTime(),
      summary: res.summary || '已完成原型调整',
      diffList: res.diffList || [],
      snapshotId: snapId,
      originalHtml: contextHtml,
      modifiedHtml: res.modifiedHtml,
      isElementMode: false,
      isCommitted: false,
      isReverted: false,
    })

    emit('preview-draft', targetPageId, res.modifiedHtml)
    emit('toast', '已在屏幕上实时预览 AI 调整效果')
  } catch (err: any) {
    errorMessage.value = err.message || 'AI 调整请求失败，请稍后重试'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 核心还原操作（支持单元素与整页）
function handleRevert(msg: ChatMessage) {
  if (!props.currentPage) return
  msg.isReverted = true

  // 单元素还原
  if (msg.isElementMode && msg.elementId && msg.originalSnippet) {
    emit('patch-element-success', {
      action: 'REPLACE',
      elementId: msg.elementId,
      summary: '已还原元素到修改前状态',
      originalSnippet: msg.modifiedSnippet || '',
      modifiedSnippet: msg.originalSnippet,
    })
    emit('toast', '已还原该元素修改')
    return
  }

  // 整页还原
  if (msg.originalHtml && msg.originalHtml !== artboardBaseHtml.value) {
    emit('preview-draft', props.currentPage.id, msg.originalHtml)
    emit('toast', '已撤回本次修改，恢复到上一步状态')
  } else {
    emit('revert-draft', props.currentPage.id)
    emit('toast', '已撤回修改，完全恢复至原版画板')
  }
}

// 一键完全恢复到进入 AI 调整时的初始画板（会话级终极回滚，物理级还原）
async function handleRestoreSessionInitial() {
  if (!props.currentPage || !sessionInitialHtml.value) return
  saving.value = true
  try {
    const cleanInitial = sessionInitialHtml.value.replace(/<style\s+id=["']wf-inspector-style["'].*?<\/style>/gis, '')
    await projectApi.saveHtml(props.projectId, props.currentPage.id, cleanInitial)

    for (const m of messages.value) {
      if (m.role === 'assistant') {
        m.isCommitted = false
        m.isReverted = true
      }
    }

    artboardBaseHtml.value = cleanInitial
    emit('commit-success', props.currentPage.id, cleanInitial)
    emit('clear-selected-element')
    emit('toast', '已完全恢复到进入 AI 调整时的初始画板！')
  } catch (err: any) {
    errorMessage.value = `恢复初始失败: ${err.message || '未知异常'}`
    emit('toast', `恢复初始失败: ${err.message || '网络异常'}`)
  } finally {
    saving.value = false
  }
}

// 保留并应用修改（真正持久化写入后端）
async function handleCommit(msg: ChatMessage) {
  if (!props.currentPage) return
  saving.value = true
  try {
    let finalHtml = props.currentDraftHtml || msg.modifiedHtml || props.currentPage.html_content || ''
    finalHtml = finalHtml.replace(/<style\s+id=["']wf-inspector-style["'].*?<\/style>/gis, '')
    await projectApi.saveHtml(props.projectId, props.currentPage.id, finalHtml)
    msg.isCommitted = true
    msg.isReverted = false

    const snapId = msg.snapshotId || `快照 #${historySnapshots.value.length + 1}`
    msg.snapshotId = snapId

    historySnapshots.value.push({
      id: snapId,
      pageId: props.currentPage.id,
      summary: msg.summary || '修改已保存',
      originalHtml: msg.originalHtml || artboardBaseHtml.value,
      modifiedHtml: finalHtml,
    })
    artboardBaseHtml.value = finalHtml

    emit('commit-success', props.currentPage.id, finalHtml)
    emit('toast', '修改已成功保存至当前画板！后续仍支持随时回退')
  } catch (err: any) {
    errorMessage.value = `保存失败: ${err.message || '网络异常'}`
    emit('toast', `保存失败: ${err.message || '网络异常'}`)
  } finally {
    saving.value = false
  }
}

// 回退已应用/已保存的修改（让用户在应用后觉得有问题依然可以随时回退）
async function handleRevertCommitted(msg: ChatMessage) {
  if (!props.currentPage) return
  msg.isReverting = true
  try {
    let revertToHtml = msg.originalHtml || sessionInitialHtml.value || ''
    if (!revertToHtml) {
      throw new Error('未找到修改前的历史版本代码')
    }
    revertToHtml = revertToHtml.replace(/<style\s+id=["']wf-inspector-style["'].*?<\/style>/gis, '')

    // 1. 持久化写回后端数据库
    await projectApi.saveHtml(props.projectId, props.currentPage.id, revertToHtml)

    // 2. 状态切换
    msg.isCommitted = false
    msg.isReverted = true
    artboardBaseHtml.value = revertToHtml

    // 3. 通知画板立即更新
    emit('commit-success', props.currentPage.id, revertToHtml)
    emit('clear-selected-element')
    emit('toast', '已成功回退此次修改，画板已恢复到修改前状态！')
  } catch (err: any) {
    errorMessage.value = `回退失败: ${err.message || '网络异常'}`
    emit('toast', `回退失败: ${err.message || '未知错误'}`)
  } finally {
    msg.isReverting = false
  }
}

// 重新应用已被回退的修改
async function handleReapply(msg: ChatMessage) {
  if (!props.currentPage) return
  msg.isReverting = true
  try {
    let targetHtml = msg.modifiedHtml || ''
    if (!targetHtml) {
      throw new Error('未找到修改后的版本代码')
    }
    targetHtml = targetHtml.replace(/<style\s+id=["']wf-inspector-style["'].*?<\/style>/gis, '')

    await projectApi.saveHtml(props.projectId, props.currentPage.id, targetHtml)

    msg.isCommitted = true
    msg.isReverted = false
    artboardBaseHtml.value = targetHtml

    emit('commit-success', props.currentPage.id, targetHtml)
    emit('toast', '已重新应用该修改并保存！')
  } catch (err: any) {
    errorMessage.value = `重新应用失败: ${err.message || '网络异常'}`
    emit('toast', `重新应用失败: ${err.message || '网络异常'}`)
  } finally {
    msg.isReverting = false
  }
}

// 回退至指定的历史步骤快照并直接持久化
async function handleRollbackToSnapshot(snap: HistorySnapshot) {
  if (!props.currentPage) return
  saving.value = true
  try {
    let targetHtml = snap.modifiedHtml.replace(/<style\s+id=["']wf-inspector-style["'].*?<\/style>/gis, '')
    await projectApi.saveHtml(props.projectId, props.currentPage.id, targetHtml)
    artboardBaseHtml.value = targetHtml
    emit('commit-success', props.currentPage.id, targetHtml)
    emit('toast', `已成功回退至【${snap.id}】状态并保存！`)
  } catch (err: any) {
    errorMessage.value = `回退快照失败: ${err.message || '网络异常'}`
    emit('toast', `回退快照失败: ${err.message || '网络异常'}`)
  } finally {
    saving.value = false
  }
}

// 键盘快捷键监听：全屏演示下按 Ctrl+Z 触发撤回（已保存的与未保存的均支持逐步撤回）
function onGlobalKeyDown(e: KeyboardEvent) {
  if (!props.isOpen) return
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && !m.isReverted) {
        e.preventDefault()
        if (m.isCommitted) {
          handleRevertCommitted(m)
        } else {
          handleRevert(m)
        }
        return
      }
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeyDown)
})
</script>

<style scoped>
@keyframes spinLinear {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.animate-spin-fast {
  animation: spinLinear 0.85s linear infinite;
}
@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}
.shimmer-bar {
  position: relative;
  overflow: hidden;
}
.shimmer-bar::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.25), transparent);
  animation: shimmer 1.6s infinite;
}
.custom-scrollbar::-webkit-scrollbar {
  width: 5px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.16);
  border-radius: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
