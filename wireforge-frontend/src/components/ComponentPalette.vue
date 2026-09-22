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

    <!-- Components List 2-Column Grid Area (Pure visual elements without wordy descriptions) -->
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
            class="palette-item group relative bg-slate-50/70 hover:bg-white border border-slate-200/80 hover:border-[#0D99FF] rounded-xl h-18 shadow-2xs hover:shadow-md transition-all cursor-grab active:cursor-grabbing flex items-center justify-center overflow-hidden p-2"
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

            <!-- Visual Preview (Pure component without explanatory text) -->
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
  targetPage?: { id: number, name: string } | null
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

const activeCat = ref<'all' | 'shapes' | 'controls'>('all')

const categories: { id: 'all' | 'shapes' | 'controls'; name: string }[] = [
  { id: 'all', name: '全部组件' },
  { id: 'shapes', name: '形状与占位' },
  { id: 'controls', name: '交互控件' },
]

const shapeItems: PaletteItem[] = [
  {
    id: 'shape-box',
    name: '矩形占位方框',
    emoji: '🔲',
    tag: 'Box',
    description: '',
    html: `<div class="wf-box" style="width: 335px; height: 90px; border: 2px dashed #94a3b8; border-radius: 12px; background: rgba(241, 245, 249, 0.85); box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 60px; height: 32px; border: 1.5px dashed #94a3b8; border-radius: 6px; background: rgba(241, 245, 249, 0.9);"></div>`,
  },
  {
    id: 'shape-container',
    name: '背景卡片框',
    emoji: '📦',
    tag: 'Container',
    description: '',
    html: `<div class="wf-container" style="width: 335px; height: 120px; background: #ffffff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; box-sizing: border-box;"></div>`,
    previewHtml: `<div style="width: 64px; height: 32px; background: #fff; border-radius: 6px; border: 1px solid #cbd5e1; box-shadow: 0 1px 4px rgba(0,0,0,0.06);"></div>`,
  },
  {
    id: 'shape-circle',
    name: '圆形头像占位',
    emoji: '⭕',
    tag: 'Circle',
    description: '',
    html: `<div class="wf-avatar" style="width: 52px; height: 52px; border-radius: 50%; background: #e2e8f0; border: 2px solid #cbd5e1; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.1); box-sizing: border-box;"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover;" alt="用户头像" /></div>`,
    previewHtml: `<div style="width: 32px; height: 32px; border-radius: 50%; background: #e2e8f0; border: 1.5px solid #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 14px;">👤</div>`,
  },
  {
    id: 'shape-text',
    name: '文本',
    emoji: '📝',
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
    emoji: '🟢',
    tag: 'Primary Button',
    description: '',
    html: `<button class="wf-btn wf-btn-primary" style="padding: 10px 24px; background: #10b981; color: #ffffff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">按钮</button>`,
    previewHtml: `<div style="padding: 5px 16px; background: #10b981; color: #fff; font-size: 11px; font-weight: 600; border-radius: 6px; box-shadow: 0 2px 6px rgba(16,185,129,0.3);">按钮</div>`,
  },
  {
    id: 'ctrl-btn-secondary',
    name: '次按钮',
    emoji: '⚪',
    tag: 'Secondary Button',
    description: '',
    html: `<button class="wf-btn wf-btn-secondary" style="padding: 9px 20px; background: #ffffff; color: #334155; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; box-sizing: border-box;">次按钮</button>`,
    previewHtml: `<div style="padding: 4px 16px; background: #fff; border: 1.5px solid #cbd5e1; color: #334155; font-size: 11px; font-weight: 600; border-radius: 6px;">按钮</div>`,
  },
  {
    id: 'ctrl-switch',
    name: '开关',
    emoji: '🔀',
    tag: 'Switch',
    description: '',
    html: `<div class="wf-sw" data-state="on" style="display: inline-flex; align-items: center; width: 48px; height: 28px; background: linear-gradient(180deg, #FF8336 0%, #FA6B19 100%); border-radius: 14px; position: relative; cursor: pointer; box-shadow: inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28); box-sizing: border-box;"><span class="wf-sw-k" style="width: 24px; height: 24px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; top: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.25);"></span></div>`,
    previewHtml: `<div style="width: 36px; height: 20px; background: #fa6b19; border-radius: 10px; position: relative;"><div style="width: 16px; height: 16px; background: #fff; border-radius: 50%; position: absolute; right: 2px; top: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-search',
    name: '搜索框',
    emoji: '🔍',
    tag: 'Search Box',
    description: '',
    html: `<div class="wf-search-box" style="display: flex; align-items: center; width: 335px; height: 40px; background: #ffffff; border: 1.5px solid #cbd5e1; border-radius: 10px; padding: 0 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); box-sizing: border-box;"><span style="color: #94a3b8; margin-right: 8px; font-size: 14px;">🔍</span><input type="text" placeholder="搜索..." style="border: none; background: transparent; outline: none; font-size: 13px; color: #1e293b; width: 100%;" /></div>`,
    previewHtml: `<div style="width: 72px; height: 24px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; display: flex; align-items: center; padding: 0 6px; font-size: 11px; color: #94a3b8;">🔍</div>`,
  },
  {
    id: 'ctrl-modal',
    name: '弹窗',
    emoji: '🪟',
    tag: 'Modal',
    description: '',
    html: `<div class="wf-modal wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.55); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; z-index: 9999; box-sizing: border-box;"><div style="width: 290px; background: #ffffff; border-radius: 18px; padding: 22px; box-shadow: 0 20px 30px -5px rgba(0,0,0,0.3); text-align: center;"><h4 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 8px 0;">操作确认</h4><p style="font-size: 13px; color: #64748b; margin: 0 0 20px 0; line-height: 1.5;">确认执行当前业务操作吗？修改将立即同步并持久化。</p><div style="display: flex; gap: 10px;"><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer;">取消</button><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: none; background: #10b981; border-radius: 10px; font-size: 13px; font-weight: 600; color: #ffffff; cursor: pointer;">确定</button></div></div></div>`,
    previewHtml: `<div style="width: 56px; height: 36px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; box-shadow: 0 3px 8px rgba(0,0,0,0.12); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;"><div style="width: 20px; height: 3px; background: #94a3b8; border-radius: 2px;"></div><div style="width: 32px; height: 7px; background: #10b981; border-radius: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-sheet',
    name: '抽屉',
    emoji: '📥',
    tag: 'Sheet',
    description: '',
    html: `<div class="wf-modal wf-bottom-sheet wf-show" style="position: absolute; inset: 0; background: rgba(0,0,0,0.45); display: flex; flex-direction: column; justify-content: flex-end; z-index: 9999; box-sizing: border-box;"><div style="width: 100%; background: #ffffff; border-radius: 20px 20px 0 0; padding: 18px 20px 28px 20px; box-shadow: 0 -4px 20px rgba(0,0,0,0.15);"><div style="width: 36px; height: 4px; background: #cbd5e1; border-radius: 2px; margin: 0 auto 14px auto;"></div><h4 style="font-size: 15px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0;">快捷面板操作</h4><div style="display: flex; flex-direction: column; gap: 8px;"><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 A：分享给好友</div><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 B：保存至草稿</div></div><button class="wf-modal-dismiss" style="width: 100%; margin-top: 14px; padding: 10px; background: #f1f5f9; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; color: #64748b; cursor: pointer;">取消关闭</button></div></div>`,
    previewHtml: `<div style="width: 56px; height: 32px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px 6px 0 0; display: flex; flex-direction: column; align-items: center; padding-top: 4px; box-shadow: 0 -2px 6px rgba(0,0,0,0.06);"><div style="width: 14px; height: 2px; background: #cbd5e1; border-radius: 1px;"></div></div>`,
  },
]

const displayedCategories = computed(() => {
  if (activeCat.value === 'shapes') {
    return [{ id: 'shapes', name: '基础形状与占位框', items: shapeItems }]
  }
  if (activeCat.value === 'controls') {
    return [{ id: 'controls', name: '标准交互控件', items: controlItems }]
  }
  return [
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
  // 携带标准 HTML 模板
  event.dataTransfer.setData('text/html', item.html)
  event.dataTransfer.setData('text/plain', item.html)
  // 携带富数据对象
  event.dataTransfer.setData('application/wireforge-component', JSON.stringify(item))
  // 给正在被拖拽的节点微调透明度
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
