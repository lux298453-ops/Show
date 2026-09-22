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
              placeholder="搜索组件或占位控件..."
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
        <div class="px-3 py-1.5 flex gap-1 border-b border-slate-100 bg-white">
          <button
            v-for="cat in categories"
            :key="cat.id"
            class="flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all text-center cursor-pointer"
            :class="activeCat === cat.id
              ? 'bg-[#0D99FF]/10 text-[#0D99FF] border border-[#0D99FF]/20 font-bold'
              : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 border border-transparent'"
            @click="activeCat = cat.id"
          >
            {{ cat.name }}
          </button>
        </div>

        <!-- Component Cards Grid -->
        <div class="flex-1 overflow-y-auto p-3 space-y-3 custom-scrollbar max-h-[300px]">
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

              <!-- Visual Preview (Pure component without explanatory text) -->
              <div class="w-full h-full flex items-center justify-center pointer-events-none">
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
            <span class="text-sm">🔲</span>
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
            <span class="text-sm">⭕</span>
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
            <span class="text-sm">➖</span>
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
            <span class="text-sm">📦</span>
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
            <span class="text-sm">📱</span>
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
            <span class="text-sm">📱</span>
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
            <span class="text-sm">💻</span>
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

      <!-- 4) 钢笔工具 (Pen - P) -->
      <button
        type="button"
        class="h-9 w-9 rounded-xl flex items-center justify-center transition-all cursor-pointer"
        :class="isPenActive
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-700 hover:text-slate-950 hover:bg-slate-100/90'"
        title="钢笔工具 (P)"
        @click="onPenToolClick"
      >
        <PenTool class="w-4 h-4" />
      </button>

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
  Frame,
  Square,
  Box,
  Circle,
  Minus,
  PenTool,
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
} from 'lucide-vue-next'

export type ActiveToolType = 'select' | 'frame' | 'rect' | 'circle' | 'container' | 'line' | 'text' | 'comment'
export type WorkbenchModeType = 'design' | 'interactive'

export interface PaletteItem {
  id: string
  name: string
  emoji: string
  tag: string
  description: string
  category: 'ios' | 'shapes' | 'controls'
  html: string
  previewHtml: string
}

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
const isPenSelected = ref(false)

// Search & Filter in Resources
const searchQuery = ref('')
const activeCat = ref<'all' | 'ios' | 'shapes' | 'controls'>('all')

const categories = [
  { id: 'all' as const, name: '全部' },
  { id: 'ios' as const, name: 'iOS 移动' },
  { id: 'shapes' as const, name: '形状占位' },
  { id: 'controls' as const, name: '交互控件' },
]

// All Atom Component Library Items
const atomComponents: PaletteItem[] = [
  {
    id: 'ios-app-item',
    name: '应用列表单元项',
    emoji: '📱',
    tag: 'iOS App Item',
    category: 'ios',
    description: '标准 App Store 列表单元项（图标+主副标题+获取按钮）',
    html: `<div class="wf-el wf-ios-app-item" style="display: flex; align-items: center; justify-content: space-between; width: 335px; padding: 10px 0; border-bottom: 1px solid #f1f5f9; background: #ffffff; box-sizing: border-box;"><div style="display: flex; align-items: center; gap: 12px; min-width: 0;"><img src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=120&auto=format&fit=crop&q=80" style="width: 52px; height: 52px; border-radius: 12px; object-fit: cover; box-shadow: 0 2px 6px rgba(0,0,0,0.08); flex-shrink: 0;" alt="App Icon" /><div style="display: flex; flex-direction: column; gap: 2px; min-width: 0;"><span style="font-size: 14px; font-weight: 700; color: #0f172a; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">Soul — 年轻人的社交元宇宙</span><span style="font-size: 11px; color: #64748b; line-height: 1.2;">有趣多元温暖真实</span></div></div><button class="wf-btn" style="padding: 5px 16px; background: #f1f5f9; color: #0284c7; border: none; border-radius: 9999px; font-size: 12px; font-weight: 700; cursor: pointer; flex-shrink: 0; margin-left: 8px;">获取</button></div>`,
    previewHtml: `<div style="width: 84px; display: flex; align-items: center; justify-content: space-between; gap: 4px;"><div style="width: 18px; height: 18px; background: #cbd5e1; border-radius: 4px; flex-shrink: 0;"></div><div style="flex: 1; display: flex; flex-direction: column; gap: 2px;"><div style="width: 32px; height: 3px; background: #334155; border-radius: 1px;"></div><div style="width: 22px; height: 2px; background: #94a3b8; border-radius: 1px;"></div></div><div style="width: 16px; height: 8px; background: #e0f2fe; border-radius: 4px;"></div></div>`,
  },
  {
    id: 'ios-story-card',
    name: 'App推荐大卡片',
    emoji: '🎬',
    tag: 'Story Card',
    category: 'ios',
    description: 'App Store 顶部主打推荐大卡片（封面+信息条+获取按钮）',
    html: `<div class="wf-el wf-ios-story-card" style="width: 335px; height: 280px; border-radius: 20px; overflow: hidden; position: relative; background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%); box-shadow: 0 10px 25px rgba(0,0,0,0.18); box-sizing: border-box; color: #ffffff;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover; opacity: 0.85; position: absolute; inset: 0;" alt="Feature Cover" /><div style="position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0.35) 0%, rgba(0,0,0,0.05) 50%, rgba(0,0,0,0.85) 100%);"></div><div style="position: absolute; top: 16px; left: 16px; right: 16px;"><div style="font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; opacity: 0.85; margin-bottom: 2px;">主打推荐</div><div style="font-size: 20px; font-weight: 800; line-height: 1.2;">Videoleap</div><div style="font-size: 12px; opacity: 0.9; margin-top: 2px;">创意混剪视频 点亮精彩瞬间</div></div><div style="position: absolute; bottom: 14px; left: 14px; right: 14px; display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.15); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); padding: 8px 12px; border-radius: 14px; border: 1px solid rgba(255,255,255,0.25);"><div style="display: flex; align-items: center; gap: 8px;"><div style="width: 32px; height: 32px; border-radius: 8px; background: #ffffff; display: flex; align-items: center; justify-content: center; font-size: 16px;">🎬</div><div><div style="font-size: 12px; font-weight: 700;">Videoleap</div><div style="font-size: 10px; opacity: 0.8;">制作专业后期</div></div></div><button class="wf-btn" style="padding: 4px 14px; background: rgba(255,255,255,0.9); color: #0284c7; border: none; border-radius: 9999px; font-size: 11px; font-weight: 700; cursor: pointer;">获取</button></div></div>`,
    previewHtml: `<div style="width: 76px; height: 44px; background: linear-gradient(135deg, #334155, #0f172a); border-radius: 8px; position: relative; overflow: hidden; padding: 4px; box-shadow: 0 2px 6px rgba(0,0,0,0.2);"><div style="width: 24px; height: 3px; background: #fff; border-radius: 1px; margin-bottom: 2px;"></div><div style="width: 16px; height: 2px; background: rgba(255,255,255,0.7); border-radius: 1px;"></div><div style="position: absolute; bottom: 3px; left: 4px; right: 4px; height: 10px; background: rgba(255,255,255,0.3); border-radius: 3px; display: flex; align-items: center; justify-content: space-between; padding: 0 3px;"><div style="width: 6px; height: 6px; background: #fff; border-radius: 2px;"></div><div style="width: 10px; height: 4px; background: #0284c7; border-radius: 2px;"></div></div></div>`,
  },
  {
    id: 'ios-avatar-grid',
    name: '头像九宫格卡片',
    emoji: '👥',
    tag: 'Avatar Grid',
    category: 'ios',
    description: '创意拼贴卡片（标题+圆角方形头像矩阵）',
    html: `<div class="wf-el wf-ios-avatar-grid" style="width: 335px; background: #ffffff; border-radius: 18px; padding: 14px; box-shadow: 0 4px 18px rgba(0,0,0,0.06); border: 1px solid #f1f5f9; box-sizing: border-box;"><div style="font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 2px;">释放创意</div><div style="font-size: 18px; font-weight: 800; color: #0f172a; margin-bottom: 12px;">迈开创作第一步</div><div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;"><img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="A" /><img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="B" /><img src="https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="C" /><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="D" /><img src="https://images.unsplash.com/photo-1517841905240-472988babdf9?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="E" /><img src="https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="F" /><img src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="G" /><img src="https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="H" /></div></div>`,
    previewHtml: `<div style="width: 68px; height: 42px; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 3px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 2px;"><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div><div style="background: #94a3b8; border-radius: 2px;"></div><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #94a3b8; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div></div>`,
  },
  {
    id: 'ios-tab-bar',
    name: 'iOS毛玻璃底部栏',
    emoji: '🧭',
    tag: 'iOS TabBar',
    category: 'ios',
    description: '标准 iOS 底部 4-Tab 导航栏（毛玻璃底色）',
    html: `<div class="wf-el wf-ios-tab-bar" style="width: 375px; height: 60px; background: rgba(255,255,255,0.85); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-top: 1px solid rgba(0,0,0,0.1); display: flex; align-items: center; justify-content: space-around; position: relative; box-sizing: border-box; z-index: 100;"><div style="display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: pointer; color: #000000; font-weight: 700;"><span style="font-size: 16px;">🏠</span><span style="font-size: 10px;">Today</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: pointer; color: #8e8e93;"><span style="font-size: 16px;">🔍</span><span style="font-size: 10px;">Browse</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: pointer; color: #8e8e93;"><span style="font-size: 16px;">📻</span><span style="font-size: 10px;">Radio</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: pointer; color: #8e8e93;"><span style="font-size: 16px;">📚</span><span style="font-size: 10px;">Library</span></div></div>`,
    previewHtml: `<div style="width: 76px; height: 18px; background: rgba(255,255,255,0.9); border-top: 1px solid #cbd5e1; border-radius: 4px; display: flex; align-items: center; justify-content: space-around; padding: 0 4px;"><div style="width: 6px; height: 6px; background: #000; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div></div>`,
  },
  {
    id: 'ios-btn-pill',
    name: 'iOS获取胶囊按钮',
    emoji: '💊',
    tag: 'Pill Button',
    category: 'ios',
    description: 'App Store 经典浅灰底天蓝字胶囊按钮',
    html: `<button class="wf-btn wf-ios-pill-btn" style="padding: 6px 18px; background: #f1f5f9; color: #007aff; border: none; border-radius: 9999px; font-size: 13px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">获取</button>`,
    previewHtml: `<div style="padding: 3px 12px; background: #f1f5f9; color: #007aff; font-size: 10px; font-weight: 700; border-radius: 9999px; border: 1px solid #e2e8f0;">获取</div>`,
  },
  {
    id: 'shape-box',
    name: '矩形/正方形方框',
    emoji: '🔲',
    tag: 'Box',
    category: 'shapes',
    description: '',
    html: `<div class="wf-box" style="width: 335px; height: 90px; border: 2px dashed #94a3b8; border-radius: 12px; background: rgba(241, 245, 249, 0.85); box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 70px; height: 32px; border: 1.5px dashed #94a3b8; border-radius: 6px; background: #f8fafc;"></div>`,
  },
  {
    id: 'shape-container',
    name: '卡片容器',
    emoji: '📦',
    tag: 'Card',
    category: 'shapes',
    description: '',
    html: `<div class="wf-container" style="width: 335px; height: 110px; background: #ffffff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 70px; height: 32px; background: #fff; border-radius: 6px; border: 1px solid #cbd5e1; box-shadow: 0 1px 3px rgba(0,0,0,0.06);"></div>`,
  },
  {
    id: 'shape-circle',
    name: '圆形头像',
    emoji: '⭕',
    tag: 'Avatar',
    category: 'shapes',
    description: '',
    html: `<div class="wf-avatar" style="width: 52px; height: 52px; border-radius: 50%; background: #e2e8f0; border: 2px solid #cbd5e1; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.1); box-sizing: border-box;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover;" alt="用户头像" /></div>`,
    previewHtml: `<div style="width: 28px; height: 28px; border-radius: 50%; background: #e2e8f0; border: 1.5px solid #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 12px;">👤</div>`,
  },
  {
    id: 'shape-text',
    name: '文本',
    emoji: '📝',
    tag: 'Text',
    category: 'shapes',
    description: '',
    html: `<div class="wf-text-block" style="width: 300px; box-sizing: border-box;"><h3 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 4px 0;">文字内容</h3><p style="font-size: 13px; color: #475569; line-height: 1.6; margin: 0;">双击任意文本可敲键盘改字。</p></div>`,
    previewHtml: `<div style="font-size: 13px; font-weight: 700; color: #334155; font-family: sans-serif;">Aa</div>`,
  },
  {
    id: 'shape-divider',
    name: '分割线',
    emoji: '➖',
    tag: 'Divider',
    category: 'shapes',
    description: '',
    html: `<div class="wf-divider" style="width: 335px; height: 1px; background: #e2e8f0; margin: 16px 0; box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 60px; height: 2px; background: #cbd5e1; border-radius: 1px;"></div>`,
  },
  {
    id: 'ctrl-btn-primary',
    name: '主按钮',
    emoji: '🟢',
    tag: 'Btn',
    category: 'controls',
    description: '',
    html: `<button class="wf-btn wf-btn-primary" style="padding: 10px 24px; background: linear-gradient(135deg, #10b981, #059669); color: #ffffff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: 0 4px 12px rgba(16,185,129,0.35); display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">主要按钮</button>`,
    previewHtml: `<div style="padding: 4px 16px; background: #10b981; color: #fff; font-size: 11px; font-weight: 600; border-radius: 6px;">Button</div>`,
  },
  {
    id: 'ctrl-btn-secondary',
    name: '次按钮',
    emoji: '⚪',
    tag: 'Outline',
    category: 'controls',
    description: '',
    html: `<button class="wf-btn wf-btn-secondary" style="padding: 9px 20px; background: #ffffff; color: #334155; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">次按钮</button>`,
    previewHtml: `<div style="padding: 4px 16px; background: #fff; border: 1.5px solid #cbd5e1; color: #334155; font-size: 11px; font-weight: 600; border-radius: 6px;">Button</div>`,
  },
  {
    id: 'ctrl-search',
    name: '搜索框',
    emoji: '🔍',
    tag: 'Search',
    category: 'controls',
    description: '',
    html: `<div class="wf-search-box" style="display: flex; align-items: center; width: 335px; height: 40px; background: #ffffff; border: 1.5px solid #cbd5e1; border-radius: 10px; padding: 0 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); box-sizing: border-box;"><span style="color: #94a3b8; margin-right: 8px; font-size: 14px;">🔍</span><input type="text" placeholder="搜索..." style="border: none; background: transparent; outline: none; font-size: 13px; color: #1e293b; width: 100%;" /></div>`,
    previewHtml: `<div style="width: 72px; height: 24px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; display: flex; align-items: center; padding: 0 6px; font-size: 11px; color: #94a3b8;">🔍</div>`,
  },
  {
    id: 'ctrl-switch',
    name: '开关',
    emoji: '🔀',
    tag: 'Switch',
    category: 'controls',
    description: '',
    html: `<div class="wf-sw" data-state="on" style="display: inline-flex; align-items: center; width: 48px; height: 28px; background: linear-gradient(180deg, #FF8336 0%, #FA6B19 100%); border-radius: 14px; position: relative; cursor: pointer; box-shadow: inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28); box-sizing: border-box;"><span class="wf-sw-k" style="width: 24px; height: 24px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; top: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.25);"></span></div>`,
    previewHtml: `<div style="width: 36px; height: 20px; background: #fa6b19; border-radius: 10px; position: relative;"><div style="width: 16px; height: 16px; background: #fff; border-radius: 50%; position: absolute; right: 2px; top: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-modal',
    name: '弹窗',
    emoji: '🪟',
    tag: 'Modal',
    category: 'controls',
    description: '',
    html: `<div class="wf-modal wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.55); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; z-index: 9999; box-sizing: border-box;"><div style="width: 290px; background: #ffffff; border-radius: 18px; padding: 22px; box-shadow: 0 20px 30px -5px rgba(0,0,0,0.3); text-align: center;"><h4 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 8px 0;">操作确认</h4><p style="font-size: 13px; color: #64748b; margin: 0 0 20px 0; line-height: 1.5;">确认执行当前业务操作吗？修改将立即同步并持久化。</p><div style="display: flex; gap: 10px;"><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer;">取消</button><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: none; background: #10b981; border-radius: 10px; font-size: 13px; font-weight: 600; color: #ffffff; cursor: pointer;">确定</button></div></div></div>`,
    previewHtml: `<div style="width: 56px; height: 36px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; box-shadow: 0 3px 8px rgba(0,0,0,0.12); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;"><div style="width: 20px; height: 3px; background: #94a3b8; border-radius: 2px;"></div><div style="width: 32px; height: 7px; background: #10b981; border-radius: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-sheet',
    name: '抽屉',
    emoji: '📥',
    tag: 'Sheet',
    category: 'controls',
    description: '',
    html: `<div class="wf-modal wf-bottom-sheet wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.45); display: flex; flex-direction: column; justify-content: flex-end; z-index: 9999; box-sizing: border-box;"><div style="width: 100%; background: #ffffff; border-radius: 20px 20px 0 0; padding: 18px 20px 28px 20px; box-shadow: 0 -4px 20px rgba(0,0,0,0.15);"><div style="width: 36px; height: 4px; background: #cbd5e1; border-radius: 2px; margin: 0 auto 14px auto;"></div><h4 style="font-size: 15px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0;">快捷面板操作</h4><div style="display: flex; flex-direction: column; gap: 8px;"><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 A：分享给好友</div><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 B：保存至草稿</div></div><button class="wf-modal-dismiss" style="width: 100%; margin-top: 14px; padding: 10px; background: #f1f5f9; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; color: #64748b; cursor: pointer;">取消关闭</button></div></div>`,
    previewHtml: `<div style="width: 56px; height: 32px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px 6px 0 0; display: flex; flex-direction: column; align-items: center; padding-top: 4px; box-shadow: 0 -2px 6px rgba(0,0,0,0.06);"><div style="width: 14px; height: 2px; background: #cbd5e1; border-radius: 1px;"></div></div>`,
  },
]

// Filtered components based on Category and Search Query
const filteredItems = computed(() => {
  let list = atomComponents
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

const isPenActive = computed(() => isPenSelected.value || (props.activeTool as string) === 'pen')

// Tool Actions
function onSelectToolClick() {
  closeAllMenus()
  isPenSelected.value = false
  emit('update:activeTool', 'select')
  emit('toolChange', 'select')
}

function onFrameToolClick() {
  const willOpen = !isFrameMenuOpen.value
  closeAllMenus()
  isPenSelected.value = false
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
  isPenSelected.value = false
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
  const dividerItem = atomComponents.find((item) => item.id === 'shape-divider')
  if (dividerItem) {
    emit('addComponent', dividerItem)
  }
  isShapesMenuOpen.value = false
}

function onPenToolClick() {
  closeAllMenus()
  isPenSelected.value = !isPenSelected.value
  emit('toolChange', 'pen')
}

function onTextToolClick() {
  closeAllMenus()
  isPenSelected.value = false
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
  isPenSelected.value = false
  const nextTool = props.activeTool === 'comment' ? 'select' : 'comment'
  emit('update:activeTool', nextTool)
  emit('toolChange', nextTool)
}

function closeAllMenus() {
  isShapesMenuOpen.value = false
  isResourcesPopoverOpen.value = false
  isFrameMenuOpen.value = false
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

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
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
