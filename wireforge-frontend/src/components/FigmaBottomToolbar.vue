<template>
  <div
    ref="toolbarRef"
    class="figma-bottom-toolbar absolute bottom-6 left-1/2 -translate-x-1/2 z-[120] select-none flex flex-col items-center pointer-events-auto"
    @mousedown.stop
    @pointerdown.stop
    @mouseup.stop
    @click.stop
  >
    <!-- ================================================================= -->
    <!-- 1. 浮动原子组件抽屉面板 (Quick Component Popover)                 -->
    <!-- ================================================================= -->
    <Transition name="figma-popover">
      <div
        v-if="isResourcesPopoverOpen"
        class="absolute bottom-[calc(100%+12px)] left-1/2 -translate-x-1/2 w-[420px] max-h-[480px] bg-white/95 backdrop-blur-2xl border border-slate-200/90 shadow-2xl shadow-slate-950/20 rounded-2xl flex flex-col overflow-hidden z-50"
        @click.stop
      >
        <!-- Popover Header -->
        <div class="px-3.5 py-2.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
          <div class="flex items-center gap-2">
            <div class="w-5 h-5 rounded-md bg-[#0D99FF]/10 text-[#0D99FF] flex items-center justify-center font-bold text-xs">
              ❖
            </div>
            <span class="text-xs font-bold text-slate-800">原子组件与插件库</span>
            <span class="text-[10px] text-slate-400 font-mono">Resources</span>
          </div>
          <button
            type="button"
            class="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded-lg transition-colors cursor-pointer"
            title="关闭面板"
            @click="isResourcesPopoverOpen = false"
          >
            <X class="w-3.5 h-3.5" />
          </button>
        </div>

        <!-- Search Input -->
        <div class="px-3 pt-2.5 pb-1.5">
          <div class="flex items-center gap-1.5 px-2.5 py-1.5 bg-slate-100/80 rounded-xl border border-slate-200/60 text-slate-500 focus-within:border-[#0D99FF] focus-within:bg-white focus-within:ring-2 focus-within:ring-[#0D99FF]/20 transition-all">
            <Search class="w-3.5 h-3.5 text-slate-400 shrink-0" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索组件..."
              class="w-full text-xs bg-transparent border-none outline-none text-slate-800 placeholder:text-slate-400"
            />
            <button
              v-if="searchQuery"
              class="text-[11px] text-slate-400 hover:text-slate-600 cursor-pointer"
              @click="searchQuery = ''"
            >
              <X class="w-3 h-3" />
            </button>
          </div>
        </div>

        <!-- Category Tabs -->
        <div class="px-3 py-1.5 border-b border-slate-100 bg-white">
          <div class="grid grid-cols-6 gap-1 bg-slate-100/80 p-0.5 rounded-lg border border-slate-200/50">
            <button
              v-for="cat in componentCategories"
              :key="cat.id"
              class="py-1 text-[11px] font-medium rounded-md transition-all text-center cursor-pointer select-none"
              :class="activeCat === cat.id
                ? 'bg-white text-[#0D99FF] shadow-2xs font-bold'
                : 'text-slate-500 hover:text-slate-800 hover:bg-white/50'"
              @click="activeCat = cat.id"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>

        <!-- Component Cards Grid -->
        <div class="flex-1 overflow-y-auto p-3 space-y-3 custom-scrollbar max-h-[320px]">
          <div v-if="filteredItems.length === 0" class="py-8 text-center text-xs text-slate-400">
            无匹配组件，换个关键词搜搜看
          </div>

          <div v-else class="grid grid-cols-2 gap-2">
            <div
              v-for="item in filteredItems"
              :key="item.id"
              class="group relative bg-slate-50/70 hover:bg-white border border-slate-200/80 hover:border-[#0D99FF] rounded-xl h-18 shadow-2xs hover:shadow-md transition-all cursor-grab active:cursor-grabbing flex items-center justify-center overflow-hidden p-2"
              draggable="true"
              :title="item.name"
              @dragstart="onDragStart($event, item)"
              @dragend="onDragEnd"
              @click.stop="insertComponent(item)"
            >
              <!-- Quick Add Hover Button in top-right -->
              <button
                type="button"
                class="absolute top-1.5 right-1.5 w-5 h-5 rounded-md bg-white hover:bg-[#0D99FF] text-slate-400 hover:text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer shadow-xs border border-slate-200/80 hover:border-transparent z-10"
                :title="`添加 ${item.name}`"
                @click.stop="insertComponent(item)"
              >
                <Plus class="w-3 h-3" />
              </button>

              <!-- Visual Preview (Pure preview without explanatory labels) -->
              <div class="w-full h-full flex items-center justify-center pointer-events-none overflow-hidden">
                <div v-html="item.previewHtml" class="scale-100 transform-origin-center"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ================================================================= -->
    <!-- 2. 形状工具子菜单气泡 (Shapes Submenu Popover)                     -->
    <!-- ================================================================= -->
    <Transition name="figma-popover">
      <div
        v-if="isShapesMenuOpen"
        class="absolute bottom-[calc(100%+10px)] left-20 -translate-x-1/2 bg-white/95 backdrop-blur-xl border border-slate-200/90 shadow-2xl shadow-slate-900/15 rounded-xl p-1 min-w-[210px] flex flex-col gap-0.5 z-50"
        @click.stop
      >
        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'rect' }"
          @click="selectShapeTool('rect')"
        >
          <div class="flex items-center gap-2">
            <Square class="w-3.5 h-3.5 text-slate-500" />
            <span>矩形 (Rectangle)</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">R</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'circle' }"
          @click="selectShapeTool('circle')"
        >
          <div class="flex items-center gap-2">
            <Circle class="w-3.5 h-3.5 text-slate-500" />
            <span>椭圆 / 圆形 (Ellipse)</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">O</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'line' }"
          @click="selectShapeTool('line')"
        >
          <div class="flex items-center gap-2">
            <Minus class="w-3.5 h-3.5 text-slate-500" />
            <span>直线 / 分割线 (Line)</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">L</span>
        </button>

        <div class="h-[1px] bg-slate-100 my-0.5"></div>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'container' }"
          @click="selectShapeTool('container')"
        >
          <div class="flex items-center gap-2">
            <Box class="w-3.5 h-3.5 text-slate-500" />
            <span>空白容器卡片 (Container)</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">Box</span>
        </button>
      </div>
    </Transition>

    <!-- ================================================================= -->
    <!-- 3. 画板框架子菜单气泡 (Frame Menu Popover)                          -->
    <!-- ================================================================= -->
    <Transition name="figma-popover">
      <div
        v-if="isFrameMenuOpen"
        class="absolute bottom-[calc(100%+10px)] left-12 -translate-x-1/2 bg-white/95 backdrop-blur-xl border border-slate-200/90 shadow-2xl shadow-slate-900/15 rounded-xl p-1.5 min-w-[210px] flex flex-col gap-0.5 z-50"
        @click.stop
      >
        <div class="px-2 py-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
          放置新画板 (Frame)
        </div>
        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'frame' }"
          @click="selectFrameTool"
        >
          <div class="flex items-center gap-2">
            <Frame class="w-3.5 h-3.5 text-[#0D99FF]" />
            <span>点击画布任意位置放置</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">F</span>
        </button>

        <div class="h-[1px] bg-slate-100 my-1"></div>
        <div class="px-2 py-0.5 text-[10px] font-bold text-slate-400">
          快速创建预设画板
        </div>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          @click="createFramePreset('iPhone 16 Pro', 375, 812)"
        >
          <div class="flex items-center gap-2">
            <Smartphone class="w-3.5 h-3.5 text-slate-500" />
            <span>iPhone 16 Pro</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">375×812</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          @click="createFramePreset('Android', 360, 800)"
        >
          <div class="flex items-center gap-2">
            <Smartphone class="w-3.5 h-3.5 text-slate-500" />
            <span>Android 手机</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">360×800</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          @click="createFramePreset('Desktop Web', 1440, 900)"
        >
          <div class="flex items-center gap-2">
            <Monitor class="w-3.5 h-3.5 text-slate-500" />
            <span>Web 桌面端</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">1440×900</span>
        </button>
      </div>
    </Transition>

    <!-- ================================================================= -->
    <!-- 4. Figma UI3 主浮动工具栏底座                                     -->
    <!-- ================================================================= -->
    <div
      class="bg-white/95 backdrop-blur-xl border border-slate-200/80 shadow-2xl shadow-slate-900/15 rounded-2xl p-1.5 flex items-center gap-1 select-none"
    >
      <!-- 【左侧核心创作工具组】 -->

      <!-- 1) 指针 / 选择 (Select - V) -->
      <button
        type="button"
        class="h-9 px-2 rounded-xl flex items-center gap-0.5 transition-all cursor-pointer"
        :class="activeTool === 'select'
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="指针 / 选择 (V)"
        @click="onSelectToolClick"
      >
        <MousePointer2 class="w-4 h-4 fill-current" />
        <ChevronDown class="w-2.5 h-2.5 opacity-60" />
      </button>

      <!-- 2) 抓手 / 拖拽漫游 (Hand - H) -->
      <button
        type="button"
        class="h-9 px-2 rounded-xl flex items-center justify-center transition-all cursor-pointer"
        :class="activeTool === 'hand'
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="抓手 / 拖拽漫游 (H)"
        @click="onHandToolClick"
      >
        <Hand class="w-4 h-4" />
      </button>

      <!-- 2) 框架 / 画板 (Frame - F) -->
      <button
        type="button"
        class="h-9 px-2 rounded-xl flex items-center gap-0.5 transition-all cursor-pointer"
        :class="activeTool === 'frame'
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="框架 / 画板 (F)"
        @click="onFrameToolClick"
      >
        <Frame class="w-4 h-4" />
        <ChevronDown class="w-2.5 h-2.5 opacity-60" />
      </button>

      <!-- 3) 形状工具 (Shapes - R) -->
      <button
        type="button"
        class="h-9 px-2 rounded-xl flex items-center gap-0.5 transition-all cursor-pointer"
        :class="isShapeActive
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="形状工具 (R) - 点击切换矩形、卡片、圆形等"
        @click="onShapeToolClick"
      >
        <component :is="activeShapeIcon" class="w-4 h-4" />
        <ChevronDown class="w-2.5 h-2.5 opacity-60" />
      </button>

      <!-- 4) 钢笔 / 铅笔工具组 (Pen / Pencil - P / Shift+P) -->
      <div class="relative">
        <button
          type="button"
          class="h-9 px-2 rounded-xl flex items-center gap-0.5 transition-all cursor-pointer"
          :class="isPenGroupActive
            ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
            : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
          :title="activeTool === 'pencil' ? '铅笔工具 (Shift+P)' : '钢笔工具 (P)'"
          @click="onPenButtonClick"
        >
          <component :is="activePenIcon" class="w-4 h-4" />
          <span
            class="pen-dropdown-trigger flex items-center justify-center p-0.5 -mr-0.5 rounded transition-colors"
            :class="isPenGroupActive ? 'hover:bg-white/20' : 'hover:bg-black/10'"
            title="选择钢笔或铅笔"
            @click.stop="togglePenMenu"
          >
            <ChevronDown class="w-2.5 h-2.5 opacity-70" />
          </span>
        </button>

        <!-- 暗色气泡下拉菜单 (Dark Popover) -->
        <Transition name="figma-popover">
          <div
            v-if="isPenMenuOpen"
            class="pen-menu-popover absolute bottom-[calc(100%+10px)] left-1/2 -translate-x-1/2 bg-slate-900/95 text-white backdrop-blur-xl border border-white/10 shadow-2xl rounded-xl p-1 text-xs min-w-[210px] flex flex-col gap-0.5 z-50 pointer-events-auto"
            @click.stop
          >
            <!-- 钢笔 (Pen) -->
            <button
              type="button"
              class="pen-option-pen w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-200 hover:text-white hover:bg-white/10 transition-colors cursor-pointer group"
              :class="{ 'bg-white/15 text-white font-semibold': activeTool === 'pen' }"
              title="用于锚点与贝塞尔曲线绘制"
              @click="activatePen"
            >
              <div class="flex items-center gap-2">
                <span class="w-3.5 h-3.5 flex items-center justify-center text-[#0D99FF]">
                  <Check v-if="activeTool === 'pen'" class="w-3.5 h-3.5 stroke-[2.5]" />
                </span>
                <PenTool class="w-3.5 h-3.5 text-slate-300 group-hover:text-white" />
                <span>钢笔 (Pen)</span>
              </div>
              <span class="text-[10px] font-mono text-slate-300 group-hover:text-white">P</span>
            </button>

            <!-- 铅笔 (Pencil) -->
            <button
              type="button"
              class="pen-option-pencil w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-200 hover:text-white hover:bg-white/10 transition-colors cursor-pointer group"
              :class="{ 'bg-white/15 text-white font-semibold': activeTool === 'pencil' }"
              title="用于手绘涂鸦线条"
              @click="activatePencil"
            >
              <div class="flex items-center gap-2">
                <span class="w-3.5 h-3.5 flex items-center justify-center text-[#0D99FF]">
                  <Check v-if="activeTool === 'pencil'" class="w-3.5 h-3.5 stroke-[2.5]" />
                </span>
                <Pencil class="w-3.5 h-3.5 text-slate-300 group-hover:text-white" />
                <span>铅笔 (Pencil)</span>
              </div>
              <span class="text-[10px] font-mono text-slate-300 group-hover:text-white">Shift+P</span>
            </button>
          </div>
        </Transition>
      </div>

      <!-- 5) 文本工具 (Text - T) -->
      <button
        type="button"
        class="h-9 w-9 rounded-xl flex items-center justify-center transition-all cursor-pointer"
        :class="activeTool === 'text'
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="文本工具 (T)"
        @click="onTextToolClick"
      >
        <Type class="w-4 h-4 font-bold" />
      </button>

      <!-- 6) 原子组件与插件库 (❖+ / Resources) -->
      <button
        type="button"
        class="h-9 px-2 rounded-xl flex items-center gap-1 transition-all cursor-pointer"
        :class="isResourcesPopoverOpen
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="原子组件与插件库 (❖+ / Resources)"
        @click="toggleResourcesPopover"
      >
        <Component class="w-4 h-4" />
        <span class="text-xs font-bold leading-none -ml-0.5">+</span>
      </button>

      <!-- 7) 业务说明 / 标注 (💬) -->
      <button
        type="button"
        class="h-9 w-9 rounded-xl flex items-center justify-center transition-all cursor-pointer"
        :class="showAnnotations
          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/80 shadow-2xs'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        :title="showAnnotations ? '点击关闭业务说明标注' : '点击展开业务说明标注'"
        @click="onToggleAnnotationsClick"
      >
        <MessageSquare class="w-4 h-4" />
      </button>

      <!-- 8) Figma 评论工具 (Comment - C) -->
      <button
        type="button"
        class="h-9 w-9 rounded-xl flex items-center justify-center transition-all cursor-pointer relative"
        :class="activeTool === 'comment'
          ? 'bg-amber-500 text-white shadow-sm shadow-amber-500/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="评论模式 (C)：在画板打点添加评审意见与团队讨论"
        @click="onCommentToolClick"
      >
        <MessageCircle class="w-4 h-4" />
        <span
          v-if="commentCount && commentCount > 0"
          class="absolute -top-1 -right-1 px-1 min-w-[14px] h-[14px] bg-red-500 text-white text-[9px] font-bold rounded-full flex items-center justify-center font-mono border border-white"
        >
          {{ commentCount > 99 ? '99+' : commentCount }}
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  MousePointer2,
  Hand,
  Frame,
  Square,
  Box,
  Circle,
  Minus,
  PenTool,
  Pencil,
  Check,
  Type,
  Component,
  MessageSquare,
  MessageCircle,
  Zap,
  Play,
  ChevronDown,
  Plus,
  GripVertical,
  X,
  Search,
  Smartphone,
  Monitor,
} from 'lucide-vue-next'

export type ActiveToolType = 'select' | 'hand' | 'frame' | 'rect' | 'circle' | 'container' | 'line' | 'text' | 'comment' | 'pen' | 'pencil'
export type WorkbenchModeType = 'design' | 'interactive'

import {
  componentLibrary,
  componentCategories,
  type PaletteItem,
  type PaletteCategory,
} from '@/data/componentLibrary'

export type { PaletteItem, PaletteCategory }

const props = withDefaults(
  defineProps<{
    activeTool?: ActiveToolType
    workbenchMode?: WorkbenchModeType
    showAnnotations?: boolean
    targetPage?: { id: number; name: string } | null
    commentCount?: number
  }>(),
  {
    activeTool: 'select',
    workbenchMode: 'design',
    showAnnotations: true,
    targetPage: null,
    commentCount: 0,
  }
)

const emit = defineEmits<{
  (e: 'update:activeTool', tool: ActiveToolType): void
  (e: 'toolChange', tool: string): void
  (e: 'addComponent', item: PaletteItem): void
  (e: 'setWorkbenchMode', mode: WorkbenchModeType): void
  (e: 'openPurePreview'): void
  (e: 'toggleAnnotations'): void
  (e: 'createFrame', preset?: { name: string; width: number; height: number }): void
}>()

// Root DOM reference for click outside detection
const toolbarRef = ref<HTMLElement | null>(null)

// Popover States
const isResourcesPopoverOpen = ref(false)
const isShapesMenuOpen = ref(false)
const isFrameMenuOpen = ref(false)
const isPenMenuOpen = ref(false)

// Search & Filter in Resources
const searchQuery = ref('')
const activeCat = ref<'all' | 'shapes' | 'nav' | 'controls' | 'display' | 'feedback'>('all')

const categories = componentCategories

// Filtered components based on Category and Search Query
const filteredItems = computed(() => {
  let list = componentLibrary
  if (activeCat.value !== 'all') {
    list = list.filter((item) => item.category === activeCat.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        item.tag.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q)
    )
  }
  return list
})

// Dynamic shape icon
const isShapeActive = computed(() => ['rect', 'circle', 'container', 'line'].includes(props.activeTool))
const activeShapeIcon = computed(() => {
  if (props.activeTool === 'circle') return Circle
  if (props.activeTool === 'container') return Box
  if (props.activeTool === 'line') return Minus
  return Square
})

const isPenGroupActive = computed(() => props.activeTool === 'pen' || props.activeTool === 'pencil')
const activePenIcon = computed(() => (props.activeTool === 'pencil' ? Pencil : PenTool))

// Tool Actions
function onSelectToolClick() {
  closeAllMenus()
  emit('update:activeTool', 'select')
  emit('toolChange', 'select')
}

function onHandToolClick() {
  closeAllMenus()
  emit('update:activeTool', 'hand')
  emit('toolChange', 'hand')
}

function onFrameToolClick() {
  const willOpen = !isFrameMenuOpen.value
  closeAllMenus()
  emit('update:activeTool', 'frame')
  emit('toolChange', 'frame')
  isFrameMenuOpen.value = willOpen
}

function selectFrameTool() {
  emit('update:activeTool', 'frame')
  emit('toolChange', 'frame')
  isFrameMenuOpen.value = false
}

function createFramePreset(name: string, width: number, height: number) {
  closeAllMenus()
  emit('createFrame', { name, width, height })
}

function onShapeToolClick() {
  const willOpen = !isShapesMenuOpen.value
  closeAllMenus()
  // If active tool is already a shape, toggle submenu. If not, activate rect and open menu.
  if (!isShapeActive.value) {
    emit('update:activeTool', 'rect')
    emit('toolChange', 'rect')
  }
  isShapesMenuOpen.value = willOpen
}

function selectShapeTool(shape: 'rect' | 'circle' | 'container' | 'line') {
  emit('update:activeTool', shape)
  emit('toolChange', shape)
  isShapesMenuOpen.value = false
}

function insertDividerDirectly() {
  const dividerItem = componentLibrary.find((item) => item.id === 'shape-divider')
  if (dividerItem) {
    emit('addComponent', dividerItem)
  }
  isShapesMenuOpen.value = false
}

function activatePen() {
  closeAllMenus()
  emit('update:activeTool', 'pen')
  emit('toolChange', 'pen')
}

function activatePencil() {
  closeAllMenus()
  emit('update:activeTool', 'pencil')
  emit('toolChange', 'pencil')
}

function onPenButtonClick() {
  if (!isPenGroupActive.value) {
    activatePen()
  } else {
    togglePenMenu()
  }
}

function togglePenMenu() {
  const willOpen = !isPenMenuOpen.value
  closeAllMenus()
  isPenMenuOpen.value = willOpen
}

function onTextToolClick() {
  closeAllMenus()
  emit('update:activeTool', 'text')
  emit('toolChange', 'text')
}

function toggleResourcesPopover() {
  const willOpen = !isResourcesPopoverOpen.value
  closeAllMenus()
  isResourcesPopoverOpen.value = willOpen
}

function insertComponent(item: PaletteItem) {
  emit('addComponent', item)
}

function onToggleAnnotationsClick() {
  emit('toggleAnnotations')
}

function toggleWorkbenchMode() {
  const nextMode = props.workbenchMode === 'interactive' ? 'design' : 'interactive'
  emit('setWorkbenchMode', nextMode)
}

function onPlayClick() {
  emit('openPurePreview')
}

function onCommentToolClick() {
  closeAllMenus()
  const nextTool = props.activeTool === 'comment' ? 'select' : 'comment'
  emit('update:activeTool', nextTool)
  emit('toolChange', nextTool)
}

function closeAllMenus() {
  isShapesMenuOpen.value = false
  isResourcesPopoverOpen.value = false
  isFrameMenuOpen.value = false
  isPenMenuOpen.value = false
}

// Drag Handlers
function onDragStart(event: DragEvent, item: PaletteItem) {
  ;(window as any).__wfDraggingComponent = item
  window.dispatchEvent(new CustomEvent('wf-component-dragstart', { detail: item }))
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/html', item.html)
  event.dataTransfer.setData('text/plain', item.html)
  event.dataTransfer.setData('application/wireforge-component', JSON.stringify(item))
  if (event.target instanceof HTMLElement) {
    event.target.style.opacity = '0.5'
  }
}

function onDragEnd(event: DragEvent) {
  window.dispatchEvent(new CustomEvent('wf-component-dragend'))
  if (event.target instanceof HTMLElement) {
    event.target.style.opacity = '1'
  }
  setTimeout(() => {
    ;(window as any).__wfDraggingComponent = null
  }, 100)
}

// Click outside handler
function handleDocumentPointerDown(e: PointerEvent) {
  if (!toolbarRef.value) return
  const target = e.target as Node | null
  if (target && !toolbarRef.value.contains(target)) {
    closeAllMenus()
  }
}

// Keyboard shortcuts handler
function handleKeyDown(e: KeyboardEvent) {
  const activeTag = (document.activeElement?.tagName || '').toLowerCase()
  if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
    return
  }
  if (e.ctrlKey || e.metaKey || e.altKey) return

  if (e.key === 'P' && e.shiftKey) {
    e.preventDefault()
    activatePencil()
  } else if (e.key.toLowerCase() === 'p' && !e.shiftKey) {
    e.preventDefault()
    activatePen()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  window.addEventListener('keydown', handleKeyDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
/* Popover Animation */
.figma-popover-enter-active,
.figma-popover-leave-active {
  transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
}

.figma-popover-enter-from,
.figma-popover-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.97);
}

/* Custom Scrollbar for Popover */
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
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
