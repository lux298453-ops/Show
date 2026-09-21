<template>
  <div
    ref="toolbarRef"
    class="figma-bottom-toolbar absolute bottom-6 left-1/2 -translate-x-1/2 z-50 select-none flex flex-col items-center"
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
              class="group relative bg-white border border-slate-200/80 hover:border-[#0D99FF] rounded-xl p-2.5 shadow-2xs hover:shadow-md transition-all cursor-grab active:cursor-grabbing flex flex-col justify-between gap-1.5"
              draggable="true"
              @dragstart="onDragStart($event, item)"
              @dragend="onDragEnd"
            >
              <!-- Card Header -->
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-1.5 min-w-0">
                  <span class="text-sm leading-none shrink-0">{{ item.emoji }}</span>
                  <span class="text-[11px] font-bold text-slate-800 group-hover:text-[#0D99FF] transition-colors truncate">
                    {{ item.name }}
                  </span>
                </div>
                <span class="text-[9px] text-slate-400 font-mono shrink-0">{{ item.tag }}</span>
              </div>

              <!-- Mini Visual Preview Box -->
              <div class="w-full h-12 py-1 px-2 bg-slate-50 rounded-lg border border-slate-100 flex items-center justify-center overflow-hidden pointer-events-none group-hover:bg-blue-50/30 transition-colors">
                <div v-html="item.previewHtml" class="scale-90 transform-origin-center"></div>
              </div>

              <!-- Action Footer: Drag Hint & Quick Insert Button -->
              <div class="flex items-center justify-between pt-1 border-t border-slate-100 text-[10px] text-slate-400">
                <span class="flex items-center gap-1 text-[10px] group-hover:text-[#0D99FF] transition-colors">
                  <GripVertical class="w-3 h-3 text-slate-300 group-hover:text-[#0D99FF]" />
                  按住拖出
                </span>
                <button
                  type="button"
                  class="px-2 py-0.5 bg-[#0D99FF]/10 hover:bg-[#0D99FF] text-[#0D99FF] hover:text-white rounded-md font-bold transition-all flex items-center gap-0.5 cursor-pointer active:scale-95 text-[10px]"
                  title="直接插入到当前画板"
                  @click.stop="insertComponent(item)"
                >
                  <Plus class="w-2.5 h-2.5" />
                  <span>添加</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Popover Footer -->
        <div class="px-3 py-1.5 bg-slate-50 border-t border-slate-100 text-[10px] text-slate-500 flex items-center justify-between">
          <span>按住卡片直接拖入画板，或点击 ➕ 插入</span>
          <span class="text-[#0D99FF] font-medium">支持快捷定位</span>
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
            <span>矩形/正方形方框</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">R</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'container' }"
          @click="selectShapeTool('container')"
        >
          <div class="flex items-center gap-2">
            <span class="text-sm">📦</span>
            <span>纯色卡片框</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">Box</span>
        </button>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'circle' }"
          @click="selectShapeTool('circle')"
        >
          <div class="flex items-center gap-2">
            <span class="text-sm">⭕</span>
            <span>圆形头像占位</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">O</span>
        </button>

        <div class="h-[1px] bg-slate-100 my-0.5"></div>

        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          @click="insertDividerDirectly"
        >
          <div class="flex items-center gap-2">
            <span class="text-sm">➖</span>
            <span>水平分割线</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">Line</span>
        </button>
      </div>
    </Transition>

    <!-- ================================================================= -->
    <!-- 3. 画板框架子菜单气泡 (Frame Menu Popover)                          -->
    <!-- ================================================================= -->
    <Transition name="figma-popover">
      <div
        v-if="isFrameMenuOpen"
        class="absolute bottom-[calc(100%+10px)] left-12 -translate-x-1/2 bg-white/95 backdrop-blur-xl border border-slate-200/90 shadow-2xl shadow-slate-900/15 rounded-xl p-1 min-w-[190px] flex flex-col gap-0.5 z-50"
        @click.stop
      >
        <button
          type="button"
          class="w-full px-2.5 py-1.5 flex items-center justify-between rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950 transition-colors cursor-pointer"
          :class="{ 'bg-[#0D99FF]/10 text-[#0D99FF] font-bold': activeTool === 'frame' }"
          @click="selectFrameTool"
        >
          <div class="flex items-center gap-2">
            <Frame class="w-3.5 h-3.5" />
            <span>框架/画板 (Frame)</span>
          </div>
          <span class="text-[10px] font-mono text-slate-400">F</span>
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

      <!-- 【中部分割线】 -->
      <div class="h-5 w-[1px] bg-slate-200/80 mx-1"></div>

      <!-- 【右侧模式与运行操作组】 -->

      <!-- 8) 交互连线模式 (⚡ Prototype) -->
      <button
        type="button"
        class="h-9 px-2.5 rounded-xl flex items-center gap-1.5 text-xs font-bold transition-all cursor-pointer"
        :class="workbenchMode === 'interactive'
          ? 'bg-[#0D99FF] text-white shadow-sm shadow-[#0D99FF]/30'
          : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/90'"
        title="切换需求走查 (Design) 与交互连线 (Prototype) 模式"
        @click="toggleWorkbenchMode"
      >
        <Zap class="w-3.5 h-3.5 fill-current" />
        <span>交互连线</span>
      </button>

      <!-- 9) 纯原型演示 (▶ Play) -->
      <button
        type="button"
        class="h-9 px-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white flex items-center gap-1.5 text-xs font-bold shadow-sm shadow-emerald-600/30 transition-all cursor-pointer active:scale-95"
        title="打开 Figma 原型分享级别的全屏纯净演示模式"
        @click="onPlayClick"
      >
        <Play class="w-3.5 h-3.5 fill-white" />
        <span>演示</span>
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
  PenTool,
  Type,
  Component,
  MessageSquare,
  Zap,
  Play,
  ChevronDown,
  Plus,
  GripVertical,
  X,
  Search,
} from 'lucide-vue-next'

export type ActiveToolType = 'select' | 'frame' | 'rect' | 'circle' | 'container' | 'text'
export type WorkbenchModeType = 'design' | 'interactive'

export interface PaletteItem {
  id: string
  name: string
  emoji: string
  tag: string
  description: string
  category: 'shapes' | 'controls'
  html: string
  previewHtml: string
}

const props = withDefaults(
  defineProps<{
    activeTool?: ActiveToolType
    workbenchMode?: WorkbenchModeType
    showAnnotations?: boolean
    targetPage?: { id: number; name: string } | null
  }>(),
  {
    activeTool: 'select',
    workbenchMode: 'design',
    showAnnotations: true,
    targetPage: null,
  }
)

const emit = defineEmits<{
  (e: 'update:activeTool', tool: ActiveToolType): void
  (e: 'toolChange', tool: string): void
  (e: 'addComponent', item: PaletteItem): void
  (e: 'setWorkbenchMode', mode: WorkbenchModeType): void
  (e: 'openPurePreview'): void
  (e: 'toggleAnnotations'): void
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
const activeCat = ref<'all' | 'shapes' | 'controls'>('all')

const categories = [
  { id: 'all' as const, name: '全部组件' },
  { id: 'shapes' as const, name: '形状与占位' },
  { id: 'controls' as const, name: '交互控件' },
]

// All Atom Component Library Items
const atomComponents: PaletteItem[] = [
  {
    id: 'shape-box',
    name: '矩形/正方形方框',
    emoji: '🔲',
    tag: 'Box',
    category: 'shapes',
    description: '基础占位线框或几何边框',
    html: `<div class="wf-box" style="width: 335px; height: 90px; border: 2px dashed #94a3b8; border-radius: 12px; background: rgba(241, 245, 249, 0.85); display: flex; align-items: center; justify-content: center; color: #475569; font-size: 13px; font-weight: 600; box-sizing: border-box;">🔲 矩形占位方框</div>`,
    previewHtml: `<div style="width: 70px; height: 30px; border: 1.5px dashed #94a3b8; border-radius: 6px; background: #f1f5f9; display: flex; align-items: center; justify-content: center; font-size: 9px; color: #64748b;">方框占位</div>`,
  },
  {
    id: 'shape-container',
    name: '纯色背景卡片框',
    emoji: '📦',
    tag: 'Card',
    category: 'shapes',
    description: '带阴影与圆角的容器背景卡片',
    html: `<div class="wf-container" style="width: 335px; padding: 16px; background: #ffffff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; box-sizing: border-box;"><h4 style="margin: 0 0 6px 0; font-size: 14px; font-weight: 700; color: #1e293b;">📦 卡片容器标题</h4><p style="margin: 0; font-size: 12px; color: #64748b; line-height: 1.5;">在此卡片内放置自定义组件、图文信息或操作入口。</p></div>`,
    previewHtml: `<div style="width: 100%; padding: 4px 6px; background: #fff; border-radius: 6px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.05); text-align: center;"><span style="font-size: 10px; font-weight: bold; color: #1e293b;">卡片容器</span></div>`,
  },
  {
    id: 'shape-circle',
    name: '圆形头像占位框',
    emoji: '⭕',
    tag: 'Avatar',
    category: 'shapes',
    description: '用于用户头像或圆形微缩图标',
    html: `<div class="wf-avatar" style="width: 52px; height: 52px; border-radius: 50%; background: #e2e8f0; border: 2px solid #cbd5e1; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.1); box-sizing: border-box;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover;" alt="用户头像" /></div>`,
    previewHtml: `<div style="width: 26px; height: 26px; border-radius: 50%; background: #e2e8f0; border: 1.5px solid #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 11px;">👤</div>`,
  },
  {
    id: 'shape-text',
    name: '通用文本块',
    emoji: '📝',
    tag: 'Text',
    category: 'shapes',
    description: '标题加多行描述，双击直接修改文案',
    html: `<div class="wf-text-block" style="width: 300px; box-sizing: border-box;"><h3 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 4px 0;">这是标题文字</h3><p style="font-size: 13px; color: #475569; line-height: 1.6; margin: 0;">双击任意文本可直接敲键盘改字，失焦后自动持久化落库。</p></div>`,
    previewHtml: `<div style="width: 100%; text-align: left;"><div style="font-size: 10px; font-weight: bold; color: #0f172a;">标题文案</div><div style="font-size: 8px; color: #64748b;">双击敲字编辑</div></div>`,
  },
  {
    id: 'shape-divider',
    name: '水平分割线',
    emoji: '➖',
    tag: 'Divider',
    category: 'shapes',
    description: '用于板块内容分隔与视觉层级沉降',
    html: `<div class="wf-divider" style="width: 335px; height: 1px; background: #e2e8f0; margin: 16px 0; box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 90%; height: 1.5px; background: #cbd5e1; border-radius: 1px;"></div>`,
  },
  {
    id: 'ctrl-btn-primary',
    name: '主行动按钮',
    emoji: '🟢',
    tag: 'Btn',
    category: 'controls',
    description: '高对比度核心操作按钮',
    html: `<button class="wf-btn wf-btn-primary" style="padding: 10px 24px; background: linear-gradient(135deg, #10b981, #059669); color: #ffffff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: 0 4px 12px rgba(16,185,129,0.35); display: inline-flex; align-items: center; justify-content: center; gap: 6px; box-sizing: border-box; transition: transform 0.1s;">立即提交</button>`,
    previewHtml: `<div style="padding: 3px 10px; background: #10b981; color: #fff; font-size: 9px; font-weight: bold; border-radius: 5px;">主行动按钮</div>`,
  },
  {
    id: 'ctrl-btn-secondary',
    name: '描边次按钮',
    emoji: '⚪',
    tag: 'Outline',
    category: 'controls',
    description: '浅色背景与深色细边框次要操作',
    html: `<button class="wf-btn wf-btn-secondary" style="padding: 9px 20px; background: #ffffff; color: #334155; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); box-sizing: border-box; transition: transform 0.1s;">取消/返回</button>`,
    previewHtml: `<div style="padding: 3px 10px; background: #fff; border: 1.5px solid #cbd5e1; color: #334155; font-size: 9px; font-weight: bold; border-radius: 5px;">描边次按钮</div>`,
  },
  {
    id: 'ctrl-search',
    name: '搜索框',
    emoji: '🔍',
    tag: 'Search',
    category: 'controls',
    description: '带搜索图标与圆角输入底框',
    html: `<div class="wf-search-box" style="display: flex; align-items: center; width: 335px; height: 40px; background: #ffffff; border: 1.5px solid #cbd5e1; border-radius: 10px; padding: 0 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); box-sizing: border-box;"><span style="color: #94a3b8; margin-right: 8px; font-size: 14px;">🔍</span><input type="text" placeholder="搜索内容或关键词..." style="border: none; background: transparent; outline: none; font-size: 13px; color: #1e293b; width: 100%;" /></div>`,
    previewHtml: `<div style="width: 100%; height: 22px; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 5px; display: flex; align-items: center; padding: 0 6px; font-size: 9px; color: #94a3b8;">🔍 搜索...</div>`,
  },
  {
    id: 'ctrl-switch',
    name: '开关切换 (Switch)',
    emoji: '🔀',
    tag: 'Switch',
    category: 'controls',
    description: '高保真平滑切换的物理滑块开关',
    html: `<div class="wf-sw" data-state="on" style="display: inline-flex; align-items: center; width: 48px; height: 28px; background: linear-gradient(180deg, #FF8336 0%, #FA6B19 100%); border-radius: 14px; position: relative; cursor: pointer; box-shadow: inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28); box-sizing: border-box;"><span class="wf-sw-k" style="width: 24px; height: 24px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; top: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.25);"></span></div>`,
    previewHtml: `<div style="width: 30px; height: 16px; background: #fa6b19; border-radius: 8px; position: relative;"><div style="width: 12px; height: 12px; background: #fff; border-radius: 50%; position: absolute; right: 2px; top: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-modal',
    name: '居中弹窗 (Modal)',
    emoji: '🪟',
    tag: 'Modal',
    category: 'controls',
    description: '居中遮罩弹窗，包含标题、内容与操作',
    html: `<div class="wf-modal wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.55); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; z-index: 9999; box-sizing: border-box;"><div style="width: 290px; background: #ffffff; border-radius: 18px; padding: 22px; box-shadow: 0 20px 30px -5px rgba(0,0,0,0.3); text-align: center;"><h4 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 8px 0;">操作确认</h4><p style="font-size: 13px; color: #64748b; margin: 0 0 20px 0; line-height: 1.5;">确认执行当前业务操作吗？修改将立即同步并持久化。</p><div style="display: flex; gap: 10px;"><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer;">取消</button><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: none; background: #10b981; border-radius: 10px; font-size: 13px; font-weight: 600; color: #ffffff; cursor: pointer;">确定</button></div></div></div>`,
    previewHtml: `<div style="padding: 2px 6px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 4px; text-align: center; width: 100%;"><span style="font-size: 9px; font-weight: bold; color: #1e293b;">居中弹窗</span></div>`,
  },
  {
    id: 'ctrl-sheet',
    name: '底部抽屉 (Sheet)',
    emoji: '📥',
    tag: 'Sheet',
    category: 'controls',
    description: '移动端原生感底部滑出操作面板',
    html: `<div class="wf-modal wf-bottom-sheet wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.45); display: flex; flex-direction: column; justify-content: flex-end; z-index: 9999; box-sizing: border-box;"><div style="width: 100%; background: #ffffff; border-radius: 20px 20px 0 0; padding: 18px 20px 28px 20px; box-shadow: 0 -4px 20px rgba(0,0,0,0.15);"><div style="width: 36px; height: 4px; background: #cbd5e1; border-radius: 2px; margin: 0 auto 14px auto;"></div><h4 style="font-size: 15px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0;">快捷面板操作</h4><div style="display: flex; flex-direction: column; gap: 8px;"><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 A：分享给好友</div><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 B：保存至草稿</div></div><button class="wf-modal-dismiss" style="width: 100%; margin-top: 14px; padding: 10px; background: #f1f5f9; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; color: #64748b; cursor: pointer;">取消关闭</button></div></div>`,
    previewHtml: `<div style="width: 100%; padding: 3px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 4px 4px 0 0; text-align: center;"><span style="font-size: 8px; font-weight: bold; color: #334155;">底部抽屉</span></div>`,
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
const isShapeActive = computed(() => ['rect', 'circle', 'container'].includes(props.activeTool))
const activeShapeIcon = computed(() => {
  if (props.activeTool === 'circle') return Circle
  if (props.activeTool === 'container') return Box
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

function selectShapeTool(shape: 'rect' | 'circle' | 'container') {
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
