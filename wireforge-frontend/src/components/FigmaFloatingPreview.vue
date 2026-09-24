<template>
  <div
    v-if="visible"
    ref="floatingCardRef"
    class="figma-floating-preview fixed z-[80] select-none flex flex-col items-center drop-shadow-2xl transition-[opacity,transform] duration-200"
    :style="{
      left: `${pos.x}px`,
      top: `${pos.y}px`,
      transform: `scale(${fitScale})`,
      transformOrigin: 'top left',
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
          :disabled="historyStack.length === 0"
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
          class="px-1.5 py-0.5 rounded text-[10px] font-mono bg-white/10 text-slate-300 cursor-default"
          title="与全屏演示相同的缩放比例"
        >
          {{ Math.round(fitScale * 100) }}%
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

    <!-- ===== 与全屏演示相同的 iPhone 机身：375 宽，按页面高度，再整体缩放到同一视觉大小 ===== -->
    <div
      class="phone-chassis relative bg-[#1a1f2c] p-[14px] rounded-[56px] shadow-[0_0_0_1px_rgba(255,255,255,0.18),0_0_0_3px_#272e3f,0_0_0_4px_rgba(255,255,255,0.06),0_25px_80px_-15px_rgba(0,0,0,0.9)] select-none"
    >
      <div class="absolute -left-[5px] top-[108px] w-[5px] h-[28px] bg-slate-700/80 rounded-l-[3px]"></div>
      <div class="absolute -left-[5px] top-[152px] w-[5px] h-[52px] bg-slate-700/80 rounded-l-[3px]"></div>
      <div class="absolute -left-[5px] top-[214px] w-[5px] h-[52px] bg-slate-700/80 rounded-l-[3px]"></div>
      <div class="absolute -right-[5px] top-[168px] w-[5px] h-[68px] bg-slate-700/80 rounded-r-[3px]"></div>

      <div
        class="phone-screen relative w-[375px] bg-black overflow-hidden rounded-[44px]"
        :style="{ height: `${screenHeight}px` }"
      >
        <div class="ios-status-bar absolute top-0 left-0 right-0 h-12 z-40 flex items-center justify-between px-7 text-white pointer-events-none select-none">
          <span class="text-[14px] font-semibold tracking-tight tabular-nums pl-1 drop-shadow-sm">
            {{ currentTime }}
          </span>
          <div class="absolute top-2.5 left-1/2 -translate-x-1/2 w-[122px] h-[34px] rounded-full bg-black flex items-center justify-between px-3 shadow-md">
            <div class="w-3 h-3 rounded-full bg-slate-950 border border-slate-800/80"></div>
            <div class="w-2 h-2 rounded-full bg-slate-950/80 border border-slate-900"></div>
          </div>
          <div class="flex items-center gap-1.5 pr-1 drop-shadow-sm text-white text-[11px] font-semibold">
            <span>5G</span>
          </div>
        </div>

        <div class="absolute inset-0 overflow-hidden bg-white">
          <PageCanvas
            v-if="currentPage"
            :key="currentPage.id"
            :page="currentPage"
            :all-pages="pages"
            frame-type="prototype"
            :show-wireframe="true"
            :show-design="false"
            :show-annotations="false"
            :edit-mode="false"
            :interactive="true"
            :box-w="0"
            :gap="0"
            @navigate="onCanvasNavigate"
            @back="goBack"
            @miss-click="onHotspotClick"
            @element-click="onElementClick"
          />
        </div>

        <div class="absolute bottom-2 left-1/2 -translate-x-1/2 w-28 h-1 bg-slate-500/80 rounded-full z-40 pointer-events-none"></div>

        <button
          v-if="historyStack.length > 0"
          class="wf-tap absolute top-14 left-3.5 z-40 w-8 h-8 rounded-full bg-black/50 hover:bg-black/75 backdrop-blur-md text-white flex items-center justify-center transition-all cursor-pointer shadow-lg border border-white/20"
          title="返回上一页"
          @click.stop="goBack"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  ArrowLeft,
  ChevronLeft,
  RotateCcw,
  Maximize2,
  X,
} from 'lucide-vue-next'
import type { Element, Page } from '../types'
import { findInteractionByDomUids, resolveNavigateElement } from '../utils/interactionHit'
import PageCanvas from './PageCanvas.vue'

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

const viewport = ref({ w: window.innerWidth, h: window.innerHeight })
const pos = ref({ x: 24, y: 24 })
const floatingCardRef = ref<HTMLElement | null>(null)

const currentPage = computed(() => {
  return props.pages.find((p) => p.id === activePageId.value) || props.pages[0] || null
})

const screenHeight = computed(() => currentPage.value?.canvas_height || 812)

/** 与全屏演示同一套缩放：375 宽的手机按窗口高宽放进画面 */
const fitScale = computed(() => {
  const availW = Math.max(100, viewport.value.w - 40)
  const availH = Math.max(100, viewport.value.h - 100)
  const frameW = 403
  const frameH = screenHeight.value + 28
  return Math.min(1.05, Math.max(0.4, Math.min(availW / frameW, availH / frameH)))
})

function resetToSafePosition() {
  const frameW = 403 * fitScale.value
  const frameH = (screenHeight.value + 28 + 52) * fitScale.value
  pos.value = {
    x: Math.max(12, viewport.value.w - frameW - 20),
    y: Math.max(12, (viewport.value.h - frameH) / 2),
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
  const cardW = 403 * fitScale.value
  const cardH = (screenHeight.value + 28 + 52) * fitScale.value
  const maxX = window.innerWidth - cardW - 8
  const maxY = Math.max(8, window.innerHeight - cardH - 8)
  pos.value = {
    x: Math.max(8, Math.min(maxX, e.clientX - dragOffset.x)),
    y: Math.max(8, Math.min(maxY, e.clientY - dragOffset.y)),
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
    if (id && id !== activePageId.value) activePageId.value = id
  },
)

function navigateTo(pageId: number) {
  if (!pageId || pageId === activePageId.value) return
  if (activePageId.value != null) historyStack.value.push(activePageId.value)
  activePageId.value = pageId
  emit('navigate-page', pageId)
}

function onCanvasNavigate(pageName: string, uids?: string[]) {
  const byUid = findInteractionByDomUids(currentPage.value, uids)
  const uidTarget = byUid?.interaction?.target_page_id
  if (uidTarget) {
    navigateTo(uidTarget)
    return
  }
  const clean = pageName.trim().toLowerCase()
  const target = props.pages.find(
    (p) => p.name.trim().toLowerCase() === clean || String(p.id) === clean,
  ) || props.pages.find(
    (p) => p.name.toLowerCase().includes(clean) || clean.includes(p.name.toLowerCase()),
  )
  if (target) navigateTo(target.id)
}

function onHotspotClick(pos?: { x: number; y: number; uids?: string[] }) {
  if (!pos) return
  const targetId = resolveNavigateElement(currentPage.value, pos.x, pos.y, pos.uids)?.interaction?.target_page_id
  if (targetId) navigateTo(targetId)
}

function onElementClick(el: Element) {
  const targetId = el.interaction?.target_page_id
  const action = el.interaction?.action
  if (targetId && (!action || action === 'navigate')) navigateTo(targetId)
}

function goBack() {
  if (!historyStack.value.length) return
  activePageId.value = historyStack.value.pop()!
}

function resetPreview() {
  historyStack.value = []
  if (props.pages.length) activePageId.value = props.pages[0].id
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
  viewport.value = { w: window.innerWidth, h: window.innerHeight }
  resetToSafePosition()
}

onMounted(() => {
  updateTime()
  resetToSafePosition()
  timer = setInterval(updateTime, 10000)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
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
