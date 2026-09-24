<template>
  <div
    class="vector-sub-toolbar absolute bottom-20 left-1/2 -translate-x-1/2 z-50 bg-white/95 backdrop-blur-xl border border-slate-200/80 shadow-2xl rounded-2xl px-2 py-1.5 flex items-center gap-1 select-none pointer-events-auto"
    @mousedown.stop
    @pointerdown.stop
    @click.stop
  >
    <!-- 0) Pen (钢笔画点 - 快捷键 P) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer"
      :class="activeSubTool === 'pen'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="钢笔画点 (P)"
      @click="setSubTool('pen')"
    >
      <PenTool class="w-4 h-4" />
    </button>

    <!-- 1) Move (移动锚点 - 快捷键 V) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer"
      :class="activeSubTool === 'move'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="移动锚点 (V)"
      @click="setSubTool('move')"
    >
      <svg
        class="wf-ui-icon w-4 h-4 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path
          d="m3 3 7.07 16.97 2.51-7.39 7.39-2.51L3 3z"
          :fill="activeSubTool === 'move' ? 'currentColor' : 'none'"
        />
        <circle cx="13" cy="13" r="1.5" fill="currentColor" />
      </svg>
    </button>

    <!-- 2) Lasso (套索 - 快捷键 L) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer"
      :class="activeSubTool === 'lasso'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="套索圈选锚点 (L)"
      @click="setSubTool('lasso')"
    >
      <Lasso class="w-4 h-4" />
    </button>

    <!-- 【分割线】 -->
    <div class="h-4 w-[1px] bg-slate-200 mx-1"></div>

    <!-- 3) Paint (填色桶 - 快捷键 B) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer relative"
      :class="activeSubTool === 'paint'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      :title="isClosed ? '填色桶 (B)：点击切换闭合路径填充色' : '填色桶 (B)：点击闭合路径并填充颜色'"
      @click="onPaintClick"
    >
      <PaintBucket class="w-4 h-4" />
      <span
        v-if="isClosed"
        class="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-emerald-500"
        title="当前路径已闭合"
      ></span>
    </button>

    <!-- 4) Bend (弯曲工具 - 无全局快捷键) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center gap-1.5 transition-all cursor-pointer font-medium text-xs"
      :class="activeSubTool === 'bend'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30 font-semibold'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="弯曲工具 (Bend)：按住线段拉弯"
      @click="setSubTool('bend')"
    >
      <svg
        class="wf-ui-icon w-4 h-4 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M4 17 C 8 7, 16 7, 20 17" />
        <circle cx="4" cy="17" r="1.8" fill="currentColor" />
        <circle cx="20" cy="17" r="1.8" fill="currentColor" />
        <circle cx="12" cy="9.5" r="1.8" fill="currentColor" />
        <line x1="7.5" y1="9.5" x2="16.5" y2="9.5" stroke="currentColor" stroke-width="1.2" stroke-dasharray="2 2" opacity="0.75" />
      </svg>
      <span>Bend</span>
    </button>

    <!-- 5) Cut (剪刀 - 快捷键 X) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer"
      :class="activeSubTool === 'cut'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="剪刀工具 (X)：拆开线段"
      @click="setSubTool('cut')"
    >
      <Scissors class="w-4 h-4" />
    </button>

    <!-- 6) Erase (橡皮擦 - 快捷键 E) -->
    <button
      type="button"
      class="h-8 px-2.5 rounded-xl flex items-center justify-center transition-all cursor-pointer"
      :class="activeSubTool === 'erase'
        ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
        : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
      title="橡皮擦 (E)：删除点或线"
      @click="setSubTool('erase')"
    >
      <Eraser class="w-4 h-4" />
    </button>

    <!-- 【分割线】 -->
    <div class="h-4 w-[1px] bg-slate-200 mx-1"></div>

    <!-- 7) 完成。铅笔连续绘制时这一笔松手已保存，点这里不退出。 -->
    <button
      type="button"
      class="h-8 px-3 rounded-xl flex items-center gap-1.5 text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 active:scale-95 transition-all shadow-xs cursor-pointer ml-0.5"
      :title="continuous ? '这一笔松手已保存，点这里不会退出。按 Esc 或右侧叉才退出' : '完成矢量编辑 (Enter 或 Esc)'"
      @click="emit('done')"
    >
      <Check class="w-3.5 h-3.5 stroke-[2.5]" />
      <span>完成</span>
    </button>

    <button
      type="button"
      class="h-8 w-8 rounded-xl flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-100/90 active:scale-95 transition-all cursor-pointer"
      title="取消并退出 (Esc)"
      @click="emit('cancel')"
    >
      <X class="w-4 h-4" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import {
  PenTool,
  Lasso,
  PaintBucket,
  Scissors,
  Eraser,
  Check,
  X,
} from 'lucide-vue-next'

export type VectorSubToolType = 'pen' | 'move' | 'lasso' | 'paint' | 'bend' | 'cut' | 'erase'

const props = withDefaults(
  defineProps<{
    activeSubTool?: VectorSubToolType | string
    isClosed?: boolean
    continuous?: boolean
  }>(),
  {
    activeSubTool: 'pen',
    isClosed: false,
    continuous: false,
  },
)

const emit = defineEmits<{
  (e: 'update:activeSubTool', tool: VectorSubToolType): void
  (e: 'done'): void
  (e: 'cancel'): void
  (e: 'toggle-fill'): void
}>()

function setSubTool(tool: VectorSubToolType) {
  emit('update:activeSubTool', tool)
}

function onPaintClick() {
  setSubTool('paint')
  emit('toggle-fill')
}

function handleKeyDown(e: KeyboardEvent) {
  const activeTag = (document.activeElement?.tagName || '').toLowerCase()
  if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
    return
  }
  if (e.ctrlKey || e.metaKey || e.altKey) return

  const key = e.key.toUpperCase()
  if (key === 'P' && !e.shiftKey) {
    e.preventDefault()
    setSubTool('pen')
  } else if (key === 'V') {
    e.preventDefault()
    setSubTool('move')
  } else if (key === 'L') {
    e.preventDefault()
    setSubTool('lasso')
  } else if (key === 'B') {
    e.preventDefault()
    onPaintClick()
  } else if (key === 'X') {
    e.preventDefault()
    setSubTool('cut')
  } else if (key === 'E') {
    e.preventDefault()
    setSubTool('erase')
  } else if (e.key === 'Enter') {
    e.preventDefault()
    e.stopPropagation()
    if (props.continuous) return
    if (props.activeSubTool === 'pen') emit('done')
  } else if (e.key === 'Escape') {
    e.preventDefault()
    emit('cancel')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
.vector-sub-toolbar {
  animation: slideUpSubToolbar 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUpSubToolbar {
  from {
    opacity: 0;
    transform: translate(-50%, 8px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0) scale(1);
  }
}
</style>
