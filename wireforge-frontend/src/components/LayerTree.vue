<template>
  <div class="layer-tree-panel flex-1 flex flex-col h-full bg-white select-none text-xs">
    <!-- 当前画板选择器头部 -->
    <div class="px-3 py-2 border-b border-slate-100 bg-slate-50/70 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-1.5 min-w-0">
        <span class="text-[11px] font-bold text-slate-400">#</span>
        <span class="font-bold text-slate-700 truncate" :title="currentPage?.name || '未选择画板'">
          {{ currentPage?.name || '未选择画板' }}
        </span>
      </div>
      <span v-if="layers.length" class="text-[10px] text-slate-400 font-mono">
        {{ layers.length }} 个图层
      </span>
    </div>

    <!-- 图层搜索/过滤栏 (极简设计) -->
    <div class="px-2.5 py-1.5 border-b border-slate-100 flex items-center gap-1.5 bg-white shrink-0">
      <Search class="w-3 h-3 text-slate-400 shrink-0" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索图层..."
        class="w-full text-[11px] bg-transparent outline-none placeholder:text-slate-400 text-slate-700"
      />
      <button
        v-if="searchQuery"
        class="p-0.5 text-slate-400 hover:text-slate-600 rounded"
        @click="searchQuery = ''"
      >
        <X class="w-3 h-3" />
      </button>
    </div>

    <!-- 图层树列表 -->
    <div class="flex-1 overflow-y-auto py-1 custom-scrollbar">
      <!-- 根节点：当前画板 Frame -->
      <div
        class="flex items-center justify-between px-2.5 py-1 text-slate-600 hover:bg-slate-50 cursor-pointer font-semibold"
        :class="{ 'bg-blue-50/80 text-[#0D99FF]': selectedElementId == null }"
        @click="emit('select-frame', currentPage?.id)"
      >
        <div class="flex items-center gap-1.5 min-w-0">
          <Hash class="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <span class="truncate">{{ currentPage?.name || 'Artboard' }}</span>
        </div>
        <span class="text-[10px] text-slate-400 font-mono">
          {{ currentPage?.canvas_width || 375 }}×{{ currentPage?.canvas_height || 812 }}
        </span>
      </div>

      <!-- 子图层列表 (倒序排列，贴合设计软件从顶层到底层习惯) -->
      <div class="pl-3">
        <template v-if="filteredLayers.length">
          <div
            v-for="el in filteredLayers"
            :key="el.id"
            class="layer-item flex items-center justify-between px-2 py-1 my-0.5 rounded-md cursor-pointer transition-colors group"
            :class="[
              selectedElementId === el.id
                ? 'bg-[#0D99FF] text-white font-medium shadow-2xs'
                : hoveredElementId === el.id
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-slate-600 hover:bg-slate-100/80'
            ]"
            @mouseenter="emit('hover-element', el.id)"
            @mouseleave="emit('hover-element', null)"
            @click="emit('select-element', el.id)"
          >
            <div class="flex items-center gap-1.5 min-w-0 pr-1">
              <!-- 类型专属小图标 -->
              <component
                :is="getLayerIcon(el)"
                class="w-3.5 h-3.5 shrink-0"
                :class="selectedElementId === el.id ? 'text-white' : 'text-slate-400 group-hover:text-slate-600'"
              />
              <span class="truncate text-[11px]" :title="el.label || el.type">
                {{ el.label || getDefaultName(el) }}
              </span>
            </div>

            <!-- 图层右侧尺寸/快捷操作 (悬浮显示) -->
            <div class="flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
              <span
                class="text-[9px] font-mono px-1 py-0.5 rounded"
                :class="selectedElementId === el.id ? 'text-white/80' : 'text-slate-400'"
              >
                {{ Math.round(el.width) }}×{{ Math.round(el.height) }}
              </span>
            </div>
          </div>
        </template>
        <div v-else class="py-8 text-center text-slate-400 text-[11px]">
          {{ searchQuery ? '未找到匹配图层' : '暂无图层元素' }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Search,
  X,
  Hash,
  Type,
  Square,
  Circle,
  Image as ImageIcon,
  Compass,
  LayoutGrid,
  CreditCard,
  Sliders,
  Maximize,
  Box,
} from 'lucide-vue-next'
import type { Page, Element } from '../types'

const props = defineProps<{
  currentPage: Page | null
  selectedElementId: number | null
  hoveredElementId?: number | null
}>()

const emit = defineEmits<{
  (e: 'select-element', id: number): void
  (e: 'select-frame', pageId?: number): void
  (e: 'hover-element', id: number | null): void
}>()

const searchQuery = ref('')

const layers = computed<Element[]>(() => {
  if (!props.currentPage?.elements) return []
  // 倒序：层级越高(数组越靠后)展示在最顶上
  return [...props.currentPage.elements].reverse()
})

const filteredLayers = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return layers.value
  return layers.value.filter(
    (el) =>
      (el.label && el.label.toLowerCase().includes(q)) ||
      (el.type && el.type.toLowerCase().includes(q))
  )
})

function getLayerIcon(el: Element) {
  const t = (el.type || '').toLowerCase()
  if (t === 'text' || t === 'title' || t === 'label') return Type
  if (t === 'button') return CreditCard
  if (t === 'image' || t === 'avatar') return ImageIcon
  if (t === 'icon') return Compass
  if (t === 'shape-circle' || t === 'circle') return Circle
  if (t === 'shape-rect' || t === 'box') return Square
  if (t === 'container' || t === 'card') return Box
  if (t === 'tabs' || t === 'navbar' || t === 'tabbar') return LayoutGrid
  if (t === 'switch' || t === 'slider') return Sliders
  return Square
}

function getDefaultName(el: Element) {
  const t = (el.type || '').toLowerCase()
  if (t === 'button') return 'Button'
  if (t === 'text') return 'Text'
  if (t === 'container' || t === 'card') return 'Container'
  if (t === 'box') return 'Rectangle'
  if (t === 'avatar') return 'Avatar'
  if (t === 'icon') return 'Icon'
  return el.type || 'Element'
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
