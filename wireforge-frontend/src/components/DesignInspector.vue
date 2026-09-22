<template>
  <div class="design-inspector-panel flex-1 flex flex-col h-full bg-white select-none text-xs overflow-y-auto custom-scrollbar">
    <!-- 1. 顶部当前选中对象标识 & 6大对齐工具 -->
    <div class="border-b border-slate-100 bg-slate-50/60 shrink-0">
      <!-- 对象标识行 -->
      <div class="px-3 py-2 flex items-center justify-between">
        <div class="flex items-center gap-1.5 min-w-0">
          <component
            :is="hasSelection ? Box : Hash"
            class="w-3.5 h-3.5 text-slate-500 shrink-0"
          />
          <span class="font-bold text-slate-800 truncate text-[11px]">
            {{ hasSelection ? (elementInfo?.tagName || selectedElement?.label || selectedElement?.type || '选定元素') : (currentPage?.name || '未选画板') }}
          </span>
        </div>
        <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-200/80 text-slate-600 font-mono">
          {{ hasSelection ? 'Element' : 'Frame' }}
        </span>
      </div>

      <!-- 6 大 Figma 一键对齐工具栏 -->
      <div class="px-2.5 py-1.5 border-t border-slate-200/60 bg-white flex items-center justify-between gap-1">
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="左对齐 (Align Left)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'left')"
        >
          <AlignLeft class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="水平居中 (Align Horizontal Centers)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'center-h')"
        >
          <AlignCenterHorizontal class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="右对齐 (Align Right)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'right')"
        >
          <AlignRight class="w-3.5 h-3.5" />
        </button>
        <div class="w-[1px] h-3.5 bg-slate-200/80 mx-0.5"></div>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="顶对齐 (Align Top)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'top')"
        >
          <AlignStartVertical class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="垂直居中 (Align Vertical Centers)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'center-v')"
        >
          <AlignCenterVertical class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="底对齐 (Align Bottom)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'bottom')"
        >
          <AlignEndVertical class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- 2. 几何尺寸与坐标 (Transform) -->
    <div class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">布局 (Layout)</div>
      <div class="grid grid-cols-2 gap-2">
        <!-- X 坐标 -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">X</span>
          <input
            type="number"
            :value="displayX"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!hasSelection"
            @change="onPosChange('x', $event)"
          />
        </div>
        <!-- Y 坐标 -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">Y</span>
          <input
            type="number"
            :value="displayY"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!hasSelection"
            @change="onPosChange('y', $event)"
          />
        </div>
        <!-- 宽度 W -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">W</span>
          <input
            type="number"
            :value="displayW"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            @change="onDimensionChange('width', $event)"
          />
        </div>
        <!-- 高度 H -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">H</span>
          <input
            type="number"
            :value="displayH"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            @change="onDimensionChange('height', $event)"
          />
        </div>
      </div>
    </div>

    <!-- 3. 圆角调节 (Corner Radius) -->
    <div class="p-3 border-b border-slate-100">
      <div class="flex items-center justify-between mb-2">
        <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">圆角 (Corner Radius)</div>
        <span class="font-mono text-[10px] text-slate-500">{{ currentRadius }}px</span>
      </div>
      <div class="space-y-2">
        <!-- 数值输入与滑块 -->
        <div class="flex items-center gap-2">
          <div class="flex-1 flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white">
            <Minimize2 class="w-3 h-3 text-slate-400 mr-1.5" />
            <input
              type="number"
              min="0"
              max="9999"
              :value="currentRadius"
              class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
              @change="onRadiusChange"
            />
          </div>
          <!-- 常用预设快捷按钮 -->
          <div class="flex items-center gap-1">
            <button
              v-for="r in [0, 4, 8, 12, 16]"
              :key="r"
              class="px-1.5 py-1 rounded text-[10px] font-mono transition-colors cursor-pointer"
              :class="currentRadius === r ? 'bg-blue-600 text-white font-bold' : 'bg-slate-100 hover:bg-slate-200 text-slate-600'"
              @click="applyRadius(r)"
            >
              {{ r }}
            </button>
            <button
              class="px-1.5 py-1 rounded text-[10px] font-mono transition-colors cursor-pointer"
              :class="currentRadius >= 99 ? 'bg-blue-600 text-white font-bold' : 'bg-slate-100 hover:bg-slate-200 text-slate-600'"
              title="胶囊全圆角"
              @click="applyRadius(9999)"
            >
              全
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 描边与边框 (Stroke / Border) -->
    <div class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">描边 (Stroke)</div>
      <div class="flex items-center justify-between gap-2">
        <!-- 粗细选择 -->
        <div class="flex items-center gap-1 bg-slate-50 border border-slate-200/80 rounded-md p-0.5">
          <button
            v-for="w in [0, 1, 2, 4]"
            :key="w"
            class="px-1.5 py-0.5 rounded text-[10px] font-mono transition-colors cursor-pointer"
            :class="strokeWidth === w ? 'bg-white shadow-xs text-blue-600 font-bold' : 'text-slate-500 hover:text-slate-800'"
            @click="setStrokeWidth(w)"
          >
            {{ w === 0 ? '无' : `${w}px` }}
          </button>
        </div>

        <!-- 边框色选择器 -->
        <div class="flex items-center gap-1.5 bg-slate-50 border border-slate-200/80 rounded-md px-1.5 py-1">
          <label
            class="w-4 h-4 rounded border border-slate-300 shadow-2xs cursor-pointer relative overflow-hidden shrink-0"
            :style="{ backgroundColor: strokeColor }"
          >
            <input
              type="color"
              :value="strokeColor"
              class="absolute -top-2 -left-2 w-8 h-8 opacity-0 cursor-pointer"
              @input="onStrokeColorInput"
            />
          </label>
          <span class="font-mono text-[10px] text-slate-600 uppercase">
            {{ strokeColor }}
          </span>
        </div>
      </div>
    </div>

    <!-- 5. 阴影与特效 (Effects / Box Shadow) -->
    <div class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">阴影 (Effects)</div>
      <div class="grid grid-cols-2 gap-1.5">
        <button
          v-for="eff in shadowOptions"
          :key="eff.id"
          class="py-1 px-2 rounded-md border text-[11px] font-medium transition-all text-center cursor-pointer"
          :class="activeShadowId === eff.id
            ? 'bg-blue-50 border-blue-400 text-blue-700 shadow-2xs font-semibold'
            : 'bg-slate-50/70 border-slate-200/80 text-slate-600 hover:bg-slate-100'"
          @click="applyShadow(eff)"
        >
          {{ eff.label }}
        </button>
      </div>
    </div>

    <!-- 6. 填充与色彩 (Fill) -->
    <div class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">填充 (Fill)</div>
      <div class="flex items-center justify-between gap-2 bg-slate-50 border border-slate-200/80 rounded-lg p-1.5">
        <div class="flex items-center gap-2">
          <!-- 色盘输入器 -->
          <label
            class="w-5 h-5 rounded border border-slate-300 shadow-2xs cursor-pointer relative overflow-hidden shrink-0"
            :style="{ backgroundColor: currentFillColor }"
          >
            <input
              type="color"
              :value="currentFillColor"
              class="absolute -top-2 -left-2 w-10 h-10 opacity-0 cursor-pointer"
              @input="onColorInput"
              @change="onColorChange"
            />
          </label>
          <span class="font-mono text-[11px] text-slate-700 font-semibold uppercase">
            {{ currentFillColor }}
          </span>
        </div>
        <!-- 快捷预设色点 -->
        <div class="flex items-center gap-1">
          <button
            v-for="c in ['#0D99FF', '#10b981', '#f97316', '#64748b', '#ffffff']"
            :key="c"
            class="w-3.5 h-3.5 rounded-xs border border-black/10 cursor-pointer hover:scale-125 transition-transform"
            :style="{ backgroundColor: c }"
            @click="applyPresetColor(c)"
          />
        </div>
      </div>
    </div>

    <!-- 7. 文字属性 (Typography - 仅当选中文字元素时呈现) -->
    <div v-if="isTextElement" class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">文字 (Typography)</div>
      <div class="flex items-center justify-between gap-2">
        <span class="text-slate-500 text-[11px]">字号大小</span>
        <div class="flex items-center gap-1 bg-slate-50 border border-slate-200/80 rounded-lg p-1">
          <button
            class="w-5 h-5 rounded hover:bg-slate-200 flex items-center justify-center font-bold text-slate-600 text-xs cursor-pointer"
            @click="stepFontSize(-2)"
          >
            −
          </button>
          <span class="w-8 text-center font-mono text-[11px] font-semibold text-slate-700">
            {{ currentFontSize }}px
          </span>
          <button
            class="w-5 h-5 rounded hover:bg-slate-200 flex items-center justify-center font-bold text-slate-600 text-xs cursor-pointer"
            @click="stepFontSize(2)"
          >
            +
          </button>
        </div>
      </div>
    </div>

    <!-- 8. 快速操作 (Actions) -->
    <div class="p-3">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">常用操作 (Actions)</div>
      <div class="flex flex-col gap-1.5">
        <button
          class="w-full py-1.5 px-2 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-lg text-slate-700 text-[11px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          @click="emit('duplicate-selection')"
        >
          <Copy class="w-3 h-3 text-slate-500" />
          <span>克隆副本 (Ctrl+D)</span>
        </button>
        <button
          v-if="hasSelection"
          class="w-full py-1.5 px-2 bg-rose-50 hover:bg-rose-100 border border-rose-200/80 rounded-lg text-rose-700 text-[11px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          @click="emit('delete-selection')"
        >
          <Trash2 class="w-3 h-3 text-rose-500" />
          <span>删除元素 (Backspace)</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  Hash,
  Box,
  Copy,
  Trash2,
  AlignLeft,
  AlignCenterHorizontal,
  AlignRight,
  AlignStartVertical,
  AlignCenterVertical,
  AlignEndVertical,
  Minimize2,
} from 'lucide-vue-next'
import type { Page, Element } from '../types'

const props = defineProps<{
  currentPage: Page | null
  selectedElement: Element | null
  elementInfo?: {
    tagName?: string
    x?: number
    y?: number
    width?: number
    height?: number
    borderRadius?: number
    borderWidth?: number
    borderColor?: string
    borderStyle?: string
    boxShadow?: string
    backgroundColor?: string
    fontSize?: number
  } | null
}>()

const emit = defineEmits<{
  (e: 'update-dimension', payload: { key: 'width' | 'height'; val: number }): void
  (e: 'update-position', payload: { key: 'x' | 'y'; val: number }): void
  (e: 'update-color', color: string): void
  (e: 'update-font-size', delta: number): void
  (e: 'align-selection', type: 'left' | 'center-h' | 'right' | 'top' | 'center-v' | 'bottom'): void
  (e: 'update-radius', radius: number): void
  (e: 'update-stroke', stroke: { width: number; color: string; style: string }): void
  (e: 'update-shadow', shadow: string): void
  (e: 'duplicate-selection'): void
  (e: 'delete-selection'): void
}>()

const hasSelection = computed(() => !!props.selectedElement || !!props.elementInfo)

const displayX = computed(() => {
  if (props.elementInfo?.x !== undefined) return props.elementInfo.x
  if (props.selectedElement) return Math.round(props.selectedElement.x)
  return Math.round(props.currentPage?.canvas_x || 0)
})

const displayY = computed(() => {
  if (props.elementInfo?.y !== undefined) return props.elementInfo.y
  if (props.selectedElement) return Math.round(props.selectedElement.y)
  return Math.round(props.currentPage?.canvas_y || 0)
})

const displayW = computed(() => {
  if (props.elementInfo?.width !== undefined) return props.elementInfo.width
  if (props.selectedElement) return Math.round(props.selectedElement.width)
  return Math.round(props.currentPage?.canvas_width || 375)
})

const displayH = computed(() => {
  if (props.elementInfo?.height !== undefined) return props.elementInfo.height
  if (props.selectedElement) return Math.round(props.selectedElement.height)
  return Math.round(props.currentPage?.canvas_height || 812)
})

// 色彩
const currentFillColor = ref('#0D99FF')
// 字号
const currentFontSize = ref(14)
// 圆角
const currentRadius = ref(0)
// 描边
const strokeWidth = ref(0)
const strokeColor = ref('#cbd5e1')
const strokeStyle = ref('solid')
// 阴影
const activeShadowId = ref('none')

const shadowOptions = [
  { id: 'none', label: '无阴影', value: 'none' },
  { id: 'soft', label: '柔和悬浮', value: '0 2px 8px rgba(0,0,0,0.06)' },
  { id: 'card', label: '卡片弥散', value: '0 4px 20px rgba(0,0,0,0.08)' },
  { id: 'deep', label: '立体浮动', value: '0 10px 25px rgba(0,0,0,0.15)' },
]

watch(
  () => props.elementInfo,
  (info) => {
    if (!info) return
    if (info.borderRadius !== undefined) currentRadius.value = info.borderRadius
    if (info.borderWidth !== undefined) strokeWidth.value = info.borderWidth
    if (info.borderColor) strokeColor.value = info.borderColor
    if (info.fontSize !== undefined) currentFontSize.value = info.fontSize
    if (info.backgroundColor && info.backgroundColor !== 'transparent' && info.backgroundColor !== 'rgba(0, 0, 0, 0)') {
      currentFillColor.value = rgbToHex(info.backgroundColor)
    }
  },
  { immediate: true, deep: true }
)

function rgbToHex(rgbStr: string): string {
  if (rgbStr.startsWith('#')) return rgbStr
  const match = rgbStr.match(/\d+/g)
  if (!match || match.length < 3) return '#0D99FF'
  const r = parseInt(match[0]).toString(16).padStart(2, '0')
  const g = parseInt(match[1]).toString(16).padStart(2, '0')
  const b = parseInt(match[2]).toString(16).padStart(2, '0')
  return `#${r}${g}${b}`
}

function onRadiusChange(e: Event) {
  const v = Math.max(0, parseInt((e.target as HTMLInputElement).value) || 0)
  currentRadius.value = v
  emit('update-radius', v)
}

function applyRadius(r: number) {
  currentRadius.value = r
  emit('update-radius', r)
}

function setStrokeWidth(w: number) {
  strokeWidth.value = w
  emit('update-stroke', {
    width: w,
    color: strokeColor.value,
    style: strokeStyle.value,
  })
}

function onStrokeColorInput(e: Event) {
  const c = (e.target as HTMLInputElement).value
  strokeColor.value = c
  if (strokeWidth.value === 0) strokeWidth.value = 1
  emit('update-stroke', {
    width: strokeWidth.value,
    color: c,
    style: strokeStyle.value,
  })
}

function applyShadow(eff: typeof shadowOptions[0]) {
  activeShadowId.value = eff.id
  emit('update-shadow', eff.value)
}

const isTextElement = computed(() => {
  if (props.elementInfo?.tagName) {
    return /^(h[1-6]|p|span|button|a|label)$/i.test(props.elementInfo.tagName)
  }
  if (!props.selectedElement) return false
  const t = props.selectedElement.type?.toLowerCase() || ''
  return t.includes('text') || t.includes('btn') || t.includes('button') || t.includes('label')
})

function onDimensionChange(key: 'width' | 'height', e: Event) {
  const val = Math.max(10, parseInt((e.target as HTMLInputElement).value) || 0)
  emit('update-dimension', { key, val })
}

function onPosChange(key: 'x' | 'y', e: Event) {
  const val = parseInt((e.target as HTMLInputElement).value) || 0
  emit('update-position', { key, val })
}

function onColorInput(e: Event) {
  currentFillColor.value = (e.target as HTMLInputElement).value
  emit('update-color', currentFillColor.value)
}

function onColorChange(e: Event) {
  currentFillColor.value = (e.target as HTMLInputElement).value
  emit('update-color', currentFillColor.value)
}

function applyPresetColor(color: string) {
  currentFillColor.value = color
  emit('update-color', color)
}

function stepFontSize(delta: number) {
  currentFontSize.value = Math.max(10, Math.min(60, currentFontSize.value + delta))
  emit('update-font-size', delta)
}
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
</style>
