<template>
  <div
    v-if="visible"
    ref="floatingCardRef"
    class="figma-floating-preview fixed z-[80] select-none flex flex-col items-center drop-shadow-2xl transition-[opacity,transform] duration-200"
    :style="{
      left: `${pos.x}px`,
      top: `${pos.y}px`,
      transform: `scale(${scaleRatio})`,
      transformOrigin: 'top center',
    }"
  >
    <!-- ===== 顶部浮动控制胶囊 (同时作为拖动手柄) ===== -->
    <div
      class="drag-handle mb-2.5 flex items-center justify-between gap-3 px-3 py-1.5 rounded-full bg-slate-900/90 backdrop-blur-xl border border-white/20 text-white shadow-2xl cursor-grab active:cursor-grabbing transition-transform hover:scale-[1.02]"
      @mousedown.stop="startDrag"
    >
      <!-- 左侧操作组：返回与刷新 -->
      <div class="flex items-center gap-1.5">
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

      <!-- 中间指示点与缩放切换 -->
      <div class="flex items-center gap-2">
        <div class="flex items-center gap-1.5 pointer-events-none">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
          <span class="text-[10px] font-bold text-slate-300 truncate max-w-[80px]">
            {{ currentPage?.name || 'Preview' }}
          </span>
        </div>
        <!-- 比例缩放切换按钮 -->
        <button
          class="px-1.5 py-0.5 rounded text-[10px] font-mono bg-white/10 hover:bg-white/20 text-slate-300 transition-colors cursor-pointer"
          :title="`当前缩放 ${Math.round(scaleRatio * 100)}%（点击切换缩放）`"
          @click.stop="toggleScale"
        >
          {{ Math.round(scaleRatio * 100) }}%
        </button>
      </div>

      <!-- 右侧操作组：全屏 Present 与关闭 -->
      <div class="flex items-center gap-1.5">
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
      class="phone-chassis relative bg-[#1a1f2c] p-[8px] rounded-[44px] shadow-[0_0_0_1px_rgba(255,255,255,0.18),0_0_0_3px_#272e3f,0_0_0_4px_rgba(255,255,255,0.06),0_20px_60px_-10px_rgba(0,0,0,0.85),0_0_40px_rgba(6,182,212,0.12)] select-none border border-slate-700/60"
    >
      <!-- 左侧硬件按键 (操作键/音量键) -->
      <div class="absolute -left-[3.5px] top-[80px] w-[3.5px] h-[20px] bg-slate-600 rounded-l-xs"></div>
      <div class="absolute -left-[3.5px] top-[110px] w-[3.5px] h-[36px] bg-slate-600 rounded-l-xs"></div>
      <div class="absolute -left-[3.5px] top-[155px] w-[3.5px] h-[36px] bg-slate-600 rounded-l-xs"></div>
      <!-- 右侧电源键 -->
      <div class="absolute -right-[3.5px] top-[120px] w-[3.5px] h-[48px] bg-slate-600 rounded-r-xs"></div>

      <!-- 手机屏幕视口 (紧凑黄金比例 296 x 580，完美适应屏幕高度) -->
      <div
        class="phone-screen relative w-[296px] h-[580px] bg-white rounded-[36px] overflow-hidden flex flex-col shadow-inner"
      >
        <!-- iOS 顶部状态栏与灵动岛 -->
        <div class="ios-status-bar absolute top-0 left-0 right-0 h-9 z-40 flex items-center justify-between px-5 text-slate-800 pointer-events-none bg-gradient-to-b from-white/90 via-white/50 to-transparent">
          <span class="text-[11px] font-semibold tracking-tight tabular-nums pl-0.5">
            {{ currentTime }}
          </span>
          <!-- 灵动岛 (Dynamic Island) -->
          <div class="w-[82px] h-[24px] rounded-full bg-black flex items-center justify-between px-2 shadow-sm pointer-events-auto">
            <div class="w-2 h-2 rounded-full bg-slate-900 border border-slate-700 flex items-center justify-center">
              <div class="w-1 h-1 rounded-full bg-blue-900"></div>
            </div>
            <div class="w-1 h-1 rounded-full bg-slate-800"></div>
          </div>
          <!-- 右侧信号电量 -->
          <div class="flex items-center gap-1 text-slate-800 pr-0.5">
            <div class="flex items-end gap-[1.5px] h-2">
              <span class="w-[2px] h-[3px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[4.5px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[6px] bg-slate-800 rounded-xs"></span>
              <span class="w-[2px] h-[7.5px] bg-slate-800 rounded-xs"></span>
            </div>
            <div class="w-3.5 h-2 rounded-[2px] border border-slate-800 p-[1px] flex items-center">
              <div class="w-full h-full bg-slate-800 rounded-[1px]"></div>
            </div>
          </div>
        </div>

        <!-- 页面 HTML 内容承载区 (iframe 自适应缩放到 296px 宽，且支持上下丝滑滚动长页面) -->
        <div class="screen-content flex-1 overflow-y-auto overflow-x-hidden relative pt-9 pb-4 custom-phone-scrollbar">
          <iframe
            v-if="currentPage?.html_content"
            ref="iframeRef"
            :srcdoc="runtimeHtml"
            class="w-[375px] min-h-[680px] border-none origin-top-left"
            style="transform: scale(0.7893); width: 375px; height: 735px;"
            sandbox="allow-scripts allow-same-origin"
            @load="onIframeLoad"
          />
          <div v-else class="w-full h-full flex flex-col items-center justify-center text-slate-400 p-4 text-center">
            <Smartphone class="w-8 h-8 text-slate-300 mb-2" />
            <span class="text-xs font-semibold text-slate-500">当前页面暂无交互原型</span>
            <span class="text-[10px] text-slate-400 mt-1">请在画布使用 AI 分析或生成 HTML</span>
          </div>
        </div>

        <!-- 底部手势指示条 (绝对浮于屏幕最下方，100% 不会被遮挡) -->
        <div class="absolute bottom-1.5 left-1/2 -translate-x-1/2 w-24 h-1 bg-slate-500/80 rounded-full z-40 pointer-events-none shadow-xs"></div>
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

// 缩放比例控制 (默认自适应，若屏幕较矮 < 780px 自动采用 0.85 紧凑比例)
const scaleRatio = ref(window.innerHeight < 780 ? 0.85 : 1)

function toggleScale() {
  if (scaleRatio.value === 1) {
    scaleRatio.value = 0.85
  } else if (scaleRatio.value === 0.85) {
    scaleRatio.value = 0.75
  } else {
    scaleRatio.value = 1
  }
}

// 浮层位置 (依据视口高度智能居中并预留底部至少 20px 安全空间)
const pos = ref({ x: Math.max(20, window.innerWidth - 350), y: 30 })
const floatingCardRef = ref<HTMLElement | null>(null)

function resetToSafePosition() {
  const h = window.innerHeight
  const w = window.innerWidth
  // 根据缩放后的实际高度计算
  const estimatedHeight = 635 * scaleRatio.value
  const safeY = Math.max(16, Math.min((h - estimatedHeight) / 2, h - estimatedHeight - 24))
  pos.value = {
    x: Math.max(20, w - 340),
    y: Math.max(16, safeY),
  }
}

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
  const cardH = (floatingCardRef.value?.offsetHeight || 630) * scaleRatio.value
  const cardW = 315 * scaleRatio.value
  const maxX = window.innerWidth - cardW - 10
  const maxY = Math.max(10, window.innerHeight - cardH - 16)
  pos.value = {
    x: Math.max(10, Math.min(maxX, e.clientX - dragOffset.x)),
    y: Math.max(10, Math.min(maxY, e.clientY - dragOffset.y)),
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

function onResize() {
  if (window.innerHeight < 780 && scaleRatio.value === 1) {
    scaleRatio.value = 0.85
  }
  resetToSafePosition()
}

onMounted(() => {
  updateTime()
  resetToSafePosition()
  timer = setInterval(updateTime, 10000)
  window.addEventListener('message', onWindowMessage)
  window.addEventListener('resize', onResize)
  if (activePageId.value && !historyStack.value.length) {
    historyStack.value.push(activePageId.value)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('message', onWindowMessage)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('mousemove', onDragging)
  window.removeEventListener('mouseup', stopDrag)
})
</script>

<style scoped>
.figma-floating-preview {
  touch-action: none;
}
.custom-phone-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-phone-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.4);
  border-radius: 4px;
}
</style>
