<template>
  <div class="h-full flex flex-col bg-white overflow-hidden select-none">
    <!-- Category Filter Tabs -->
    <div class="p-2 border-b border-slate-100 flex gap-1 bg-white">
      <button
        v-for="cat in categories"
        :key="cat.id"
        class="flex-1 py-1 text-[11px] font-semibold rounded-md transition-all text-center cursor-pointer"
        :class="activeCat === cat.id
          ? 'bg-blue-50 text-blue-700 border border-blue-200/80 shadow-2xs'
          : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 border border-transparent'"
        @click="activeCat = cat.id"
      >
        {{ cat.name }}
      </button>
    </div>

    <!-- Components List 2-Column Grid Area -->
    <div class="flex-1 overflow-y-auto p-2.5 space-y-3 custom-scrollbar">
      <div
        v-for="cat in displayedCategories"
        :key="cat.id"
        class="space-y-1.5"
      >
        <div class="text-[10px] font-bold text-slate-400 px-1 uppercase tracking-wider">
          {{ cat.name }}
        </div>

        <div class="grid grid-cols-2 gap-2">
          <div
            v-for="item in cat.items"
            :key="item.id"
            class="palette-item group relative bg-slate-50/70 hover:bg-white border border-slate-200/80 hover:border-[#0D99FF] rounded-xl h-20 shadow-2xs hover:shadow-md transition-all cursor-grab active:cursor-grabbing flex items-center justify-center overflow-hidden p-2"
            draggable="true"
            :title="item.name"
            @dragstart="onDragStart($event, item)"
            @dragend="onDragEnd"
            @click="emit('addComponent', item)"
          >
            <!-- Quick Add Hover Button in top-right -->
            <button
              type="button"
              class="absolute top-1.5 right-1.5 w-5 h-5 rounded-md bg-white hover:bg-[#0D99FF] text-slate-400 hover:text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer shadow-xs border border-slate-200/80 hover:border-transparent z-10"
              :title="`添加 ${item.name}`"
              @click.stop="emit('addComponent', item)"
            >
              <Plus class="w-3 h-3" />
            </button>

            <!-- Visual Preview -->
            <div class="w-full h-full flex items-center justify-center pointer-events-none">
              <div v-html="item.previewHtml" class="scale-100 transform-origin-center"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus } from 'lucide-vue-next'

const props = defineProps<{
  targetPage?: { id: number; name: string } | null
}>()

const emit = defineEmits<{
  (e: 'addComponent', item: PaletteItem): void
  (e: 'dragStart', item: PaletteItem): void
  (e: 'dragEnd'): void
}>()

export interface PaletteItem {
  id: string
  name: string
  emoji: string
  tag: string
  description: string
  html: string
  previewHtml: string
}

export interface PaletteCategory {
  id: string
  name: string
  items: PaletteItem[]
}

const activeCat = ref<'all' | 'ios' | 'shapes' | 'controls'>('all')

const categories: { id: 'all' | 'ios' | 'shapes' | 'controls'; name: string }[] = [
  { id: 'all', name: '全部' },
  { id: 'ios', name: 'iOS 移动' },
  { id: 'shapes', name: '形状占位' },
  { id: 'controls', name: '交互控件' },
]

// iOS App Store 业务复合组件包
const iosItems: PaletteItem[] = [
  {
    id: 'ios-app-item',
    name: '应用列表单元项',
    emoji: '',
    tag: 'iOS App Item',
    description: '标准 App Store 列表单元项（图标+主副标题+获取按钮）',
    html: `<div class="wf-el wf-ios-app-item" style="display: flex; align-items: center; justify-content: space-between; width: 335px; padding: 10px 0; border-bottom: 1px solid #f1f5f9; background: #ffffff; box-sizing: border-box;"><div style="display: flex; align-items: center; gap: 12px; min-width: 0;"><img src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=120&auto=format&fit=crop&q=80" style="width: 52px; height: 52px; border-radius: 12px; object-fit: cover; box-shadow: 0 2px 6px rgba(0,0,0,0.08); flex-shrink: 0;" alt="App Icon" /><div style="display: flex; flex-direction: column; gap: 2px; min-width: 0;"><span style="font-size: 14px; font-weight: 700; color: #0f172a; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">Soul — 年轻人的社交元宇宙</span><span style="font-size: 11px; color: #64748b; line-height: 1.2;">有趣多元温暖真实</span></div></div><button class="wf-btn" style="padding: 5px 16px; background: #f1f5f9; color: #0284c7; border: none; border-radius: 9999px; font-size: 12px; font-weight: 700; cursor: pointer; flex-shrink: 0; margin-left: 8px;">获取</button></div>`,
    previewHtml: `<div style="width: 84px; display: flex; align-items: center; justify-content: space-between; gap: 4px;"><div style="width: 18px; height: 18px; background: #cbd5e1; border-radius: 4px; flex-shrink: 0;"></div><div style="flex: 1; display: flex; flex-direction: column; gap: 2px;"><div style="width: 32px; height: 3px; background: #334155; border-radius: 1px;"></div><div style="width: 22px; height: 2px; background: #94a3b8; border-radius: 1px;"></div></div><div style="width: 16px; height: 8px; background: #e0f2fe; border-radius: 4px;"></div></div>`,
  },
  {
    id: 'ios-story-card',
    name: 'App推荐大卡片',
    emoji: '',
    tag: 'Story Card',
    description: 'App Store 顶部主打推荐大卡片（封面+信息条+获取按钮）',
    html: `<div class="wf-el wf-ios-story-card" style="width: 335px; height: 280px; border-radius: 20px; overflow: hidden; position: relative; background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%); box-shadow: 0 10px 25px rgba(0,0,0,0.18); box-sizing: border-box; color: #ffffff;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover; opacity: 0.85; position: absolute; inset: 0;" alt="Feature Cover" /><div style="position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0.35) 0%, rgba(0,0,0,0.05) 50%, rgba(0,0,0,0.85) 100%);"></div><div style="position: absolute; top: 16px; left: 16px; right: 16px;"><div style="font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; opacity: 0.85; margin-bottom: 2px;">主打推荐</div><div style="font-size: 20px; font-weight: 800; line-height: 1.2;">Videoleap</div><div style="font-size: 12px; opacity: 0.9; margin-top: 2px;">创意混剪视频 点亮精彩瞬间</div></div><div style="position: absolute; bottom: 14px; left: 14px; right: 14px; display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.15); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); padding: 8px 12px; border-radius: 14px; border: 1px solid rgba(255,255,255,0.25);"><div style="display: flex; align-items: center; gap: 8px;"><div style="width: 32px; height: 32px; border-radius: 8px; background: #0284c7; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: #ffffff;">APP</div><div><div style="font-size: 12px; font-weight: 700;">Videoleap</div><div style="font-size: 10px; opacity: 0.8;">制作专业后期</div></div></div><button class="wf-btn" style="padding: 4px 14px; background: rgba(255,255,255,0.9); color: #0284c7; border: none; border-radius: 9999px; font-size: 11px; font-weight: 700; cursor: pointer;">获取</button></div></div>`,
    previewHtml: `<div style="width: 76px; height: 44px; background: linear-gradient(135deg, #334155, #0f172a); border-radius: 8px; position: relative; overflow: hidden; padding: 4px; box-shadow: 0 2px 6px rgba(0,0,0,0.2);"><div style="width: 24px; height: 3px; background: #fff; border-radius: 1px; margin-bottom: 2px;"></div><div style="width: 16px; height: 2px; background: rgba(255,255,255,0.7); border-radius: 1px;"></div><div style="position: absolute; bottom: 3px; left: 4px; right: 4px; height: 10px; background: rgba(255,255,255,0.3); border-radius: 3px; display: flex; align-items: center; justify-content: space-between; padding: 0 3px;"><div style="width: 6px; height: 6px; background: #fff; border-radius: 2px;"></div><div style="width: 10px; height: 4px; background: #0284c7; border-radius: 2px;"></div></div></div>`,
  },
  {
    id: 'ios-avatar-grid',
    name: '头像九宫格卡片',
    emoji: '',
    tag: 'Avatar Grid',
    description: '创意拼贴卡片（标题+圆角方形头像矩阵）',
    html: `<div class="wf-el wf-ios-avatar-grid" style="width: 335px; background: #ffffff; border-radius: 18px; padding: 14px; box-shadow: 0 4px 18px rgba(0,0,0,0.06); border: 1px solid #f1f5f9; box-sizing: border-box;"><div style="font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 2px;">释放创意</div><div style="font-size: 18px; font-weight: 800; color: #0f172a; margin-bottom: 12px;">迈开创作第一步</div><div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;"><img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="A" /><img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="B" /><img src="https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="C" /><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="D" /><img src="https://images.unsplash.com/photo-1517841905240-472988babdf9?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="E" /><img src="https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="F" /><img src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="G" /><img src="https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&auto=format&fit=crop&q=80" style="width: 100%; aspect-ratio: 1; border-radius: 12px; object-fit: cover;" alt="H" /></div></div>`,
    previewHtml: `<div style="width: 68px; height: 42px; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 3px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 2px;"><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div><div style="background: #94a3b8; border-radius: 2px;"></div><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div><div style="background: #e2e8f0; border-radius: 2px;"></div><div style="background: #94a3b8; border-radius: 2px;"></div><div style="background: #cbd5e1; border-radius: 2px;"></div></div>`,
  },
  {
    id: 'ios-tab-bar',
    name: 'iOS毛玻璃底部栏',
    emoji: '',
    tag: 'iOS TabBar',
    description: '标准 iOS 底部 4-Tab 导航栏（毛玻璃底色）',
    html: `<div class="wf-el wf-ios-tab-bar" style="width: 375px; height: 60px; background: rgba(255,255,255,0.85); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-top: 1px solid rgba(0,0,0,0.1); display: flex; align-items: center; justify-content: space-around; position: relative; box-sizing: border-box; z-index: 100;"><div style="display: flex; flex-direction: column; align-items: center; gap: 4px; cursor: pointer; color: #0284c7; font-weight: 700;"><div style="width: 18px; height: 18px; border-radius: 4px; background: #0284c7;"></div><span style="font-size: 10px;">Today</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 4px; cursor: pointer; color: #8e8e93;"><div style="width: 18px; height: 18px; border-radius: 4px; background: #cbd5e1;"></div><span style="font-size: 10px;">Browse</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 4px; cursor: pointer; color: #8e8e93;"><div style="width: 18px; height: 18px; border-radius: 4px; background: #cbd5e1;"></div><span style="font-size: 10px;">Radio</span></div><div style="display: flex; flex-direction: column; align-items: center; gap: 4px; cursor: pointer; color: #8e8e93;"><div style="width: 18px; height: 18px; border-radius: 4px; background: #cbd5e1;"></div><span style="font-size: 10px;">Library</span></div></div>`,
    previewHtml: `<div style="width: 76px; height: 18px; background: rgba(255,255,255,0.9); border-top: 1px solid #cbd5e1; border-radius: 4px; display: flex; align-items: center; justify-content: space-around; padding: 0 4px;"><div style="width: 6px; height: 6px; background: #000; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div><div style="width: 6px; height: 6px; background: #94a3b8; border-radius: 1px;"></div></div>`,
  },
  {
    id: 'ios-btn-pill',
    name: 'iOS获取胶囊按钮',
    emoji: '',
    tag: 'Pill Button',
    description: 'App Store 经典浅灰底天蓝字胶囊按钮',
    html: `<button class="wf-btn wf-ios-pill-btn" style="padding: 6px 18px; background: #f1f5f9; color: #007aff; border: none; border-radius: 9999px; font-size: 13px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">获取</button>`,
    previewHtml: `<div style="padding: 3px 12px; background: #f1f5f9; color: #007aff; font-size: 10px; font-weight: 700; border-radius: 9999px; border: 1px solid #e2e8f0;">获取</div>`,
  },
]

const shapeItems: PaletteItem[] = [
  {
    id: 'shape-box',
    name: '矩形占位方框',
    emoji: '',
    tag: 'Box',
    description: '',
    html: `<div class="wf-box" style="width: 335px; height: 90px; border: 2px dashed #94a3b8; border-radius: 12px; background: rgba(241, 245, 249, 0.85); box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 60px; height: 32px; border: 1.5px dashed #94a3b8; border-radius: 6px; background: rgba(241, 245, 249, 0.9);"></div>`,
  },
  {
    id: 'shape-container',
    name: '背景卡片框',
    emoji: '',
    tag: 'Container',
    description: '',
    html: `<div class="wf-container" style="width: 335px; height: 120px; background: #ffffff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 64px; height: 32px; background: #fff; border-radius: 6px; border: 1px solid #cbd5e1; box-shadow: 0 1px 4px rgba(0,0,0,0.06);"></div>`,
  },
  {
    id: 'shape-circle',
    name: '圆形头像占位',
    emoji: '',
    tag: 'Circle',
    description: '',
    html: `<div class="wf-avatar" style="width: 52px; height: 52px; border-radius: 50%; background: #e2e8f0; border: 2px solid #cbd5e1; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.1); box-sizing: border-box;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover;" alt="用户头像" /></div>`,
    previewHtml: `<div style="width: 32px; height: 32px; border-radius: 50%; background: #e2e8f0; border: 1.5px solid #cbd5e1; display: flex; align-items: center; justify-content: center;"><div style="width: 14px; height: 14px; border-radius: 50%; background: #94a3b8;"></div></div>`,
  },
  {
    id: 'shape-text',
    name: '文本',
    emoji: '',
    tag: 'Text',
    description: '',
    html: `<div class="wf-text" style="font-size: 16px; font-weight: 600; color: #0f172a; line-height: 1.5;">文本内容</div>`,
    previewHtml: `<div style="font-size: 16px; font-weight: 700; color: #0f172a; font-family: ui-sans-serif, system-ui, sans-serif;">Aa</div>`,
  },
]

const controlItems: PaletteItem[] = [
  {
    id: 'ctrl-btn-primary',
    name: '主按钮',
    emoji: '',
    tag: 'Primary Button',
    description: '',
    html: `<button class="wf-btn wf-btn-primary" style="padding: 10px 24px; background: #10b981; color: #ffffff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">按钮</button>`,
    previewHtml: `<div style="padding: 5px 16px; background: #10b981; color: #fff; font-size: 11px; font-weight: 600; border-radius: 6px; box-shadow: 0 2px 6px rgba(16,185,129,0.3);">按钮</div>`,
  },
  {
    id: 'ctrl-btn-secondary',
    name: '次按钮',
    emoji: '',
    tag: 'Secondary Button',
    description: '',
    html: `<button class="wf-btn wf-btn-secondary" style="padding: 9px 20px; background: #ffffff; color: #334155; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">次按钮</button>`,
    previewHtml: `<div style="padding: 4px 16px; background: #fff; border: 1.5px solid #cbd5e1; color: #334155; font-size: 11px; font-weight: 600; border-radius: 6px;">按钮</div>`,
  },
  {
    id: 'ctrl-switch',
    name: '开关',
    emoji: '',
    tag: 'Switch',
    description: '',
    html: `<div class="wf-sw" data-state="on" style="display: inline-flex; align-items: center; width: 48px; height: 28px; background: linear-gradient(180deg, #FF8336 0%, #FA6B19 100%); border-radius: 14px; position: relative; cursor: pointer; box-shadow: inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28); box-sizing: border-box;"><span class="wf-sw-k" style="width: 24px; height: 24px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; top: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.25);"></span></div>`,
    previewHtml: `<div style="width: 36px; height: 20px; background: #fa6b19; border-radius: 10px; position: relative;"><div style="width: 16px; height: 16px; background: #fff; border-radius: 50%; position: absolute; right: 2px; top: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-search',
    name: '搜索框',
    emoji: '',
    tag: 'Search Box',
    description: '',
    html: `<div class="wf-search-box" style="display: flex; align-items: center; width: 335px; height: 40px; background: #ffffff; border: 1.5px solid #cbd5e1; border-radius: 10px; padding: 0 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); box-sizing: border-box;"><svg class="w-4 h-4 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 16px; height: 16px; margin-right: 8px;"><circle cx="11" cy="11" r="8"></circle><path d="m21 21-4.3-4.3"></path></svg><input type="text" placeholder="搜索..." style="border: none; background: transparent; outline: none; font-size: 13px; color: #1e293b; width: 100%;" /></div>`,
    previewHtml: `<div style="width: 72px; height: 24px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; display: flex; align-items: center; padding: 0 6px; font-size: 11px; color: #94a3b8;"><div style="width: 8px; height: 8px; border: 1.5px solid #94a3b8; border-radius: 50%; margin-right: 4px;"></div>搜索</div>`,
  },
  {
    id: 'ctrl-modal',
    name: '弹窗',
    emoji: '',
    tag: 'Modal',
    description: '',
    html: `<div class="wf-modal wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.55); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; z-index: 9999; box-sizing: border-box;"><div style="width: 290px; background: #ffffff; border-radius: 18px; padding: 22px; box-shadow: 0 20px 30px -5px rgba(0,0,0,0.3); text-align: center;"><h4 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 8px 0;">操作确认</h4><p style="font-size: 13px; color: #64748b; margin: 0 0 20px 0; line-height: 1.5;">确认执行当前业务操作吗？修改将立即同步并持久化。</p><div style="display: flex; gap: 10px;"><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer;">取消</button><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: none; background: #10b981; border-radius: 10px; font-size: 13px; font-weight: 600; color: #ffffff; cursor: pointer;">确定</button></div></div></div>`,
    previewHtml: `<div style="width: 56px; height: 36px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; box-shadow: 0 3px 8px rgba(0,0,0,0.12); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;"><div style="width: 20px; height: 3px; background: #94a3b8; border-radius: 2px;"></div><div style="width: 32px; height: 7px; background: #10b981; border-radius: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-sheet',
    name: '抽屉',
    emoji: '',
    tag: 'Sheet',
    description: '',
    html: `<div class="wf-modal wf-bottom-sheet wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.45); display: flex; flex-direction: column; justify-content: flex-end; z-index: 9999; box-sizing: border-box;"><div style="width: 100%; background: #ffffff; border-radius: 20px 20px 0 0; padding: 18px 20px 28px 20px; box-shadow: 0 -4px 20px rgba(0,0,0,0.15);"><div style="width: 36px; height: 4px; background: #cbd5e1; border-radius: 2px; margin: 0 auto 14px auto;"></div><h4 style="font-size: 15px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0;">快捷面板操作</h4><div style="display: flex; flex-direction: column; gap: 8px;"><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 A：分享给好友</div><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 B：保存至草稿</div></div><button class="wf-modal-dismiss" style="width: 100%; margin-top: 14px; padding: 10px; background: #f1f5f9; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; color: #64748b; cursor: pointer;">取消关闭</button></div></div>`,
    previewHtml: `<div style="width: 56px; height: 32px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px 6px 0 0; display: flex; flex-direction: column; align-items: center; padding-top: 4px; box-shadow: 0 -2px 6px rgba(0,0,0,0.06);"><div style="width: 14px; height: 2px; background: #cbd5e1; border-radius: 1px;"></div></div>`,
  },
]

const displayedCategories = computed(() => {
  if (activeCat.value === 'ios') {
    return [{ id: 'ios', name: 'iOS App Store 业务组件', items: iosItems }]
  }
  if (activeCat.value === 'shapes') {
    return [{ id: 'shapes', name: '基础形状与占位框', items: shapeItems }]
  }
  if (activeCat.value === 'controls') {
    return [{ id: 'controls', name: '标准交互控件', items: controlItems }]
  }
  return [
    { id: 'ios', name: 'iOS App Store 业务组件', items: iosItems },
    { id: 'shapes', name: '基础形状与占位框', items: shapeItems },
    { id: 'controls', name: '标准交互控件', items: controlItems },
  ]
})

function onDragStart(event: DragEvent, item: PaletteItem) {
  ;(window as any).__wfDraggingComponent = item
  emit('dragStart', item)
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
  emit('dragEnd')
  window.dispatchEvent(new CustomEvent('wf-component-dragend'))
  if (event.target instanceof HTMLElement) {
    event.target.style.opacity = '1'
  }
  setTimeout(() => {
    ;(window as any).__wfDraggingComponent = null
  }, 100)
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
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
