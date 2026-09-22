<template>
  <div
    v-if="visible"
    ref="floatingCardRef"
    class="figma-floating-preview fixed z-[80] select-none flex flex-col items-center drop-shadow-2xl"
    :style="{
      left: `${pos.x}px`,
      top: `${pos.y}px`,
    }"
  >
    <!-- ===== 顶部浮动控制胶囊 (同时作为拖动手柄) ===== -->
    <div
      class="drag-handle mb-3 flex items-center justify-between gap-6 px-3.5 py-1.5 rounded-full bg-slate-900/90 backdrop-blur-xl border border-white/20 text-white shadow-2xl cursor-grab active:cursor-grabbing transition-transform hover:scale-105"
      @mousedown.stop="startDrag"
    >
      <!-- 左侧操作组：返回与刷新 -->
      <div class="flex items-center gap-2">
        <button
          class="p-1 hover:bg-white/20 rounded-full transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="返回上一页"
          :disabled="historyStack.length <= 1"
          @click.stop="goBack"
        >
          <ChevronLeft class="w-3.5 h-3.5" />
        </button>
        <button
          class="p-1 hover:bg-white/20 rounded-full transition-colors cursor-pointer"
          title="重置到第一页"
          @click.stop="resetPreview"
        >
          <RotateCcw class="w-3 h-3" />
        </button>
      </div>

      <!-- 中间指示点 -->
      <div class="flex items-center gap-1.5 pointer-events-none">
        <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
        <span class="text-[10px] font-bold text-slate-300 truncate max-w-[100px]">
          {{ currentPage?.name || 'Preview' }}
        </span>
      </div>

      <!-- 右侧操作组：全屏 Present 与关闭 -->
      <div class="flex items-center gap-2">
        <button
          class="p-1 hover:bg-white/20 rounded-full transition-colors cursor-pointer"
          title="进入全屏纯原型演示模式 (Present)"
          @click.stop="emit('open-present')"
        >
          <Maximize2 class="w-3.5 h-3.5 text-cyan-300" />
        </button>
        <button
          class="p-1 hover:bg-rose-500/80 rounded-full transition-colors cursor-pointer"
          title="关闭手机浮层"
          @click.stop="emit('close')"
        >
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- ===== 高精度 iPhone 16 Pro 手机机身 ===== -->
    <div
      class="phone-chassis relative bg-[#1a1f2c] p-[10px] rounded-[48px] shadow-[0_0_0_1px_rgba(255,255,255,0.18),0_0_0_3px_#272e3f,0_0_0_4px_rgba(255,255,255,0.06),0_25px_80px_-15px_rgba(0,0,0,0.9),0_0_50px_rgba(6,182,212,0.15)] select-none border border-slate-700/60"
    >
      <!-- 左侧硬件按键 (操作键/音量键) -->
      <div class="absolute -left-[4px] top-[90px] w-[4px] h-[22px] bg-slate-600 rounded-l-xs"></div>
      <div class="absolute -left-[4px] top-[125px] w-[4px] h-[40px] bg-slate-600 rounded-l-xs"></div>
      <div class="absolute -left-[4px] top-[175px] w-[4px] h-[40px] bg-slate-600 rounded-l-xs"></div>
      <!-- 右侧电源键 -->
      <div class="absolute -right-[4px] top-[135px] w-[4px] h-[55px] bg-slate-600 rounded-r-xs"></div>

      <!-- 手机屏幕视口 -->
      <div
        class="phone-screen relative w-[320px] h-[640px] bg-white rounded-[38px] overflow-hidden flex flex-col shadow-inner"
      >
        <!-- iOS 顶部状态栏与灵动岛 -->
        <div class="ios-status-bar absolute top-0 left-0 right-0 h-10 z-40 flex items-center justify-between px-6 text-slate-800 pointer-events-none">
          <span class="text-[12px] font-semibold tracking-tight tabular-nums pl-0.5">
            {{ currentTime }}
          </span>
          <!-- 灵动岛 (Dynamic Island) -->
          <div class="w-[90px] h-[26px] rounded-full bg-black flex items-center justify-between px-2.5 shadow-sm pointer-events-auto">
            <div class="w-2.5 h-2.5 rounded-full bg-slate-900 border border-slate-700 flex items-center justify-center">
              <div class="w-1 h-1 rounded-full bg-blue-900"></div>
            </div>
            <div class="w-1.5 h-1.5 rounded-full bg-slate-800"></div>
          </div>
          <!-- 右侧信号电量 -->
          <div class="flex items-center gap-1.5 text-slate-800 pr-0.5">
            <div class="flex items-end gap-[1.5px] h-2.5">
              <span class="w-[2px] h-[3px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[5px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[7px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[9px] bg-slate-800 rounded-xs"></span>
            </div>
            <div class="w-4 h-2 rounded-[2px] border border-slate-800 p-[1px] flex items-center">
              <div class="w-full h-full bg-slate-800 rounded-[1px]"></div>
            </div>
          </div>
        </div>

        <!-- 页面 HTML 内容承载区 (自适应缩放以适配 320px 宽度) -->
        <div class="screen-content flex-1 overflow-hidden relative pt-10">
          <iframe
            v-if="currentPage?.html_content"
            ref="iframeRef"
            :srcdoc="runtimeHtml"
            class="w-[375px] h-[750px] border-none origin-top-left"
            style="transform: scale(0.8533);"
            sandbox="allow-scripts allow-same-origin"
            @load="onIframeLoad"
          />
          <div v-else class="w-full h-full flex flex-col items-center justify-center text-slate-400 p-4 text-center">
            <Smartphone class="w-8 h-8 text-slate-300 mb-2" />
            <span class="text-xs font-semibold text-slate-500">当前页面暂无交互原型</span>
            <span class="text-[10px] text-slate-400 mt-1">请在画布使用 AI 分析或生成 HTML</span>
          </div>
        </div>

        <!-- 底部手势指示条 -->
        <div class="absolute bottom-1 left-1/2 -translate-x-1/2 w-28 h-1 bg-slate-400/80 rounded-full z-40 pointer-events-none"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  ChevronLeft,
  RotateCcw,
  Maximize2,
  X,
  Smartphone,
} from 'lucide-vue-next'
import type { Page } from '../types'

const props = defineProps<{
  visible: boolean
  pages: Page[]
  initialPageId?: number | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'open-present'): void
  (e: 'navigate-page', pageId: number): void
}>()

// 浮层位置 (默认定位于屏幕右侧、偏上方)
const pos = ref({ x: window.innerWidth - 380, y: 70 })
const floatingCardRef = ref<HTMLElement | null>(null)

// 拖拽控制逻辑
let isDragging = false
let dragOffset = { x: 0, y: 0 }

function startDrag(e: MouseEvent) {
  isDragging = true
  dragOffset = {
    x: e.clientX - pos.value.x,
    y: e.clientY - pos.value.y,
  }
  window.addEventListener('mousemove', onDragging)
  window.addEventListener('mouseup', stopDrag)
}

function onDragging(e: MouseEvent) {
  if (!isDragging) return
  const maxX = window.innerWidth - 340
  const maxY = window.innerHeight - 300
  pos.value = {
    x: Math.max(20, Math.min(maxX, e.clientX - dragOffset.x)),
    y: Math.max(50, Math.min(maxY, e.clientY - dragOffset.y)),
  }
}

function stopDrag() {
  isDragging = false
  window.removeEventListener('mousemove', onDragging)
  window.removeEventListener('mouseup', stopDrag)
}

// 页面路由与跳转栈
const activePageId = ref<number | null>(props.initialPageId || props.pages[0]?.id || null)
const historyStack = ref<number[]>([])

watch(
  () => props.initialPageId,
  (id) => {
    if (id && id !== activePageId.value) {
      activePageId.value = id
      if (!historyStack.value.includes(id)) {
        historyStack.value.push(id)
      }
    }
  },
  { immediate: true }
)

const currentPage = computed(() => {
  return props.pages.find((p) => p.id === activePageId.value) || props.pages[0] || null
})

// 运行时 HTML (注入真机交互监听器)
const runtimeHtml = computed(() => {
  const html = currentPage.value?.html_content || ''
  if (!html) return ''
  const inject = `
    <script>
      document.addEventListener('click', function(e){
        var el = e.target.closest('[data-nav]');
        if(el){
          e.preventDefault(); e.stopPropagation();
          var nav = el.getAttribute('data-nav');
          window.parent.postMessage({ type: 'wf-floating-nav', page: nav }, '*');
        }
      }, true);
    <\/script>
  `
  if (/<\/body>/i.test(html)) {
    return html.replace(/<\/body>/i, `${inject}</body>`)
  }
  return html + inject
})

function onIframeLoad() {
  // frame 加载完毕
}

function goBack() {
  if (historyStack.value.length > 1) {
    historyStack.value.pop()
    const prev = historyStack.value[historyStack.value.length - 1]
    activePageId.value = prev
  }
}

function resetPreview() {
  if (props.pages.length) {
    const firstId = props.pages[0].id
    activePageId.value = firstId
    historyStack.value = [firstId]
  }
}

// 跨页导航消息监听
function onWindowMessage(e: MessageEvent) {
  if (!e.data || e.data.type !== 'wf-floating-nav') return
  const targetName = e.data.page
  const targetP = props.pages.find(
    (p) => p.name === targetName || (p.name && targetName && (p.name.includes(targetName) || targetName.includes(p.name)))
  )
  if (targetP) {
    activePageId.value = targetP.id
    historyStack.value.push(targetP.id)
    emit('navigate-page', targetP.id)
  }
}

// 实时时间
const currentTime = ref('09:41')
let timer: ReturnType<typeof setInterval> | null = null

function updateTime() {
  const now = new Date()
  const h = String(now.getHours()).padStart(2, '0')
  const m = String(now.getMinutes()).padStart(2, '0')
  currentTime.value = `${h}:${m}`
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 10000)
  window.addEventListener('message', onWindowMessage)
  if (activePageId.value && !historyStack.value.length) {
    historyStack.value.push(activePageId.value)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('message', onWindowMessage)
  window.removeEventListener('mousemove', onDragging)
  window.removeEventListener('mouseup', stopDrag)
})
</script>

<style scoped>
.figma-floating-preview {
  touch-action: none;
}
</style>
