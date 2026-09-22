<template>
  <div class="design-inspector-panel flex-1 flex flex-col h-full bg-white select-none text-xs overflow-y-auto custom-scrollbar">
    <!-- 1. 顶部当前选中对象标识 -->
    <div class="px-3 py-2.5 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-2 min-w-0">
        <component
          :is="selectedElement ? Box : Hash"
          class="w-3.5 h-3.5 text-slate-500 shrink-0"
        />
        <span class="font-bold text-slate-800 truncate text-[11px]">
          {{ selectedElement?.label || (selectedElement ? selectedElement.type : (currentPage?.name || '未选画板')) }}
        </span>
      </div>
      <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 font-mono">
        {{ selectedElement ? 'Element' : 'Frame' }}
      </span>
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
            :value="Math.round(selectedElement ? selectedElement.x : (currentPage?.canvas_x || 0))"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!selectedElement"
            @change="onPosChange('x', $event)"
          />
        </div>
        <!-- Y 坐标 -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">Y</span>
          <input
            type="number"
            :value="Math.round(selectedElement ? selectedElement.y : (currentPage?.canvas_y || 0))"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!selectedElement"
            @change="onPosChange('y', $event)"
          />
        </div>
        <!-- 宽度 W -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">W</span>
          <input
            type="number"
            :value="Math.round(selectedElement ? selectedElement.width : (currentPage?.canvas_width || 375))"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            @change="onDimensionChange('width', $event)"
          />
        </div>
        <!-- 高度 H -->
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <span class="text-[10px] text-slate-400 font-mono w-3.5">H</span>
          <input
            type="number"
            :value="Math.round(selectedElement ? selectedElement.height : (currentPage?.canvas_height || 812))"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            @change="onDimensionChange('height', $event)"
          />
        </div>
      </div>
    </div>

    <!-- 3. 填充与色彩 (Fill / Appearance) -->
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
            v-for="c in ['#0D99FF', '#10b981', '#f97316', '#ffffff']"
            :key="c"
            class="w-3.5 h-3.5 rounded-xs border border-black/10 cursor-pointer hover:scale-125 transition-transform"
            :style="{ backgroundColor: c }"
            @click="applyPresetColor(c)"
          />
        </div>
      </div>
    </div>

    <!-- 4. 文字属性 (Typography - 仅当选中文字元素时呈现) -->
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

    <!-- 5. 快速操作与导出 (Actions & Export) -->
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
          v-if="selectedElement"
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
import { ref, computed } from 'vue'
import {
  Hash,
  Box,
  Copy,
  Trash2,
} from 'lucide-vue-next'
import type { Page, Element } from '../types'

const props = defineProps<{
  currentPage: Page | null
  selectedElement: Element | null
}>()

const emit = defineEmits<{
  (e: 'update-dimension', payload: { key: 'width' | 'height'; val: number }): void
  (e: 'update-position', payload: { key: 'x' | 'y'; val: number }): void
  (e: 'update-color', color: string): void
  (e: 'update-font-size', delta: number): void
  (e: 'duplicate-selection'): void
  (e: 'delete-selection'): void
}>()

const currentFillColor = ref('#0D99FF')
const currentFontSize = ref(14)

const isTextElement = computed(() => {
  if (!props.selectedElement) return false
  const t = (props.selectedElement.type || '').toLowerCase()
  return t === 'text' || t === 'title' || t === 'label' || t === 'button'
})

function onPosChange(key: 'x' | 'y', e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v)) {
    emit('update-position', { key, val: v })
  }
}

function onDimensionChange(key: 'width' | 'height', e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v) && v > 0) {
    emit('update-dimension', { key, val: v })
  }
}

function onColorInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  currentFillColor.value = v
  emit('update-color', v)
}

function onColorChange(e: Event) {
  const v = (e.target as HTMLInputElement).value
  currentFillColor.value = v
  emit('update-color', v)
}

function applyPresetColor(c: string) {
  currentFillColor.value = c
  emit('update-color', c)
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
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
</style>
