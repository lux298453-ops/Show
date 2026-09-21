<template>
  <div class="h-full flex flex-col bg-white overflow-hidden select-none">
    <!-- Header / Title -->
    <div class="px-3.5 py-2.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
      <div class="flex items-center gap-2">
        <div class="w-5 h-5 rounded-md bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-xs">
          🧩
        </div>
        <span class="text-xs font-bold text-slate-800">原子组件库</span>
      </div>
      <span class="text-[10px] text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full font-semibold border border-blue-200/60">可拖拽 / 可点加</span>
    </div>

    <!-- Target Page Indicator Banner -->
    <div class="px-3 py-1.5 bg-blue-50/70 border-b border-blue-100/80 flex items-center justify-between text-[11px]">
      <div class="flex items-center gap-1.5 min-w-0">
        <span class="text-blue-700 font-bold shrink-0">📍 目标画板:</span>
        <span class="font-bold text-slate-800 truncate max-w-[120px]" :title="targetPage?.name || '点击画板选定'">
          {{ targetPage?.name || '点击画板选定' }}
        </span>
      </div>
      <span class="text-[10px] text-blue-600 font-medium shrink-0">拖入或直接添加</span>
    </div>

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

    <!-- Components List Area -->
    <div class="flex-1 overflow-y-auto p-2.5 space-y-4 custom-scrollbar">
      <div
        v-for="cat in displayedCategories"
        :key="cat.id"
        class="space-y-2"
      >
        <div class="flex items-center justify-between text-[11px] font-bold text-slate-400 px-1 uppercase tracking-wider">
          <span>{{ cat.name }}</span>
          <span class="text-[10px] font-normal text-slate-300">{{ cat.items.length }} 项</span>
        </div>

        <div class="grid grid-cols-1 gap-2">
          <div
            v-for="item in cat.items"
            :key="item.id"
            class="palette-item group relative bg-white border border-slate-200/80 hover:border-blue-400 rounded-xl p-2.5 shadow-2xs hover:shadow-md transition-all cursor-grab active:cursor-grabbing flex flex-col gap-1.5"
            draggable="true"
            @dragstart="onDragStart($event, item)"
            @dragend="onDragEnd"
          >
            <!-- Header: Icon, Name & Tag -->
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="text-base leading-none">{{ item.emoji }}</span>
                <span class="text-xs font-bold text-slate-800 group-hover:text-blue-600 transition-colors">{{ item.name }}</span>
              </div>
              <span class="text-[10px] text-slate-400 font-mono scale-90">{{ item.tag }}</span>
            </div>

            <!-- Description -->
            <p class="text-[11px] text-slate-500 line-clamp-1 leading-snug">
              {{ item.description }}
            </p>

            <!-- Visual Preview Pill / Mini Box -->
            <div class="w-full py-2 px-3 bg-slate-50/90 rounded-lg border border-slate-100 flex items-center justify-center overflow-hidden pointer-events-none group-hover:bg-blue-50/30 transition-colors">
              <div v-html="item.previewHtml" class="scale-90 transform-origin-center"></div>
            </div>

            <!-- Drag Overlay Hint & Quick Add CTA -->
            <div class="flex items-center justify-between pt-1.5 border-t border-slate-100/80 text-[10px] text-slate-400 font-medium">
              <span class="flex items-center gap-1 group-hover:text-blue-500 transition-colors">
                <GripVertical class="w-3 h-3 text-slate-300 group-hover:text-blue-400" />
                拖拽放入画板
              </span>
              <button
                type="button"
                class="px-2.5 py-0.5 bg-blue-50 hover:bg-blue-600 text-blue-600 hover:text-white rounded-md font-bold transition-all flex items-center gap-1 cursor-pointer shadow-2xs hover:shadow-xs active:scale-95"
                :title="`直接添加「${item.name}」到「${targetPage?.name || '当前画板'}」`"
                @click.stop="emit('addComponent', item)"
              >
                <span>➕ 添加</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer Tips -->
    <div class="p-2.5 bg-slate-50 border-t border-slate-100 text-[10px] text-slate-500 space-y-1">
      <div class="flex items-start gap-1">
        <span class="text-blue-600 font-bold shrink-0">➕ 添加:</span>
        <span>拖拽至任意画板，或点击「➕ 添加」插入当前选中的画板。</span>
      </div>
      <div class="flex items-start gap-1">
        <span class="text-rose-500 font-bold shrink-0">🗑️ 删除:</span>
        <span>画板内按住 Alt/Shift 点击元素，或选中后敲 Backspace 键删除 (支持 Ctrl+Z 撤回)。</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { GripVertical } from 'lucide-vue-next'

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

const activeCat = ref<'all' | 'shapes' | 'controls'>('all')

const categories: { id: 'all' | 'shapes' | 'controls'; name: string }[] = [
  { id: 'all', name: '全部组件' },
  { id: 'shapes', name: '形状与占位' },
  { id: 'controls', name: '交互控件' },
]

const shapeItems: PaletteItem[] = [
  {
    id: 'shape-box',
    name: '矩形/正方形方框',
    emoji: '🔲',
    tag: 'Box',
    description: '基础占位线框或几何边框',
    html: `<div class="wf-box" style="width: 100%; height: 90px; border: 2px dashed #94a3b8; border-radius: 10px; background: rgba(241, 245, 249, 0.7); display: flex; align-items: center; justify-content: center; color: #64748b; font-size: 12px; font-weight: 600; margin: 10px 0;">🔲 矩形占位方框</div>`,
    previewHtml: `<div style="width: 80px; height: 36px; border: 1.5px dashed #94a3b8; border-radius: 6px; background: #f1f5f9; display: flex; align-items: center; justify-content: center; font-size: 10px; color: #64748b;">方框占位</div>`,
  },
  {
    id: 'shape-container',
    name: '纯色背景卡片框',
    emoji: '📦',
    tag: 'Container',
    description: '带阴影与圆角的容器背景卡片',
    html: `<div class="wf-container" style="width: 100%; padding: 14px; background: #ffffff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; margin: 10px 0;"><h4 style="margin: 0 0 6px 0; font-size: 14px; font-weight: 700; color: #1e293b;">📦 卡片容器标题</h4><p style="margin: 0; font-size: 12px; color: #64748b; line-height: 1.5;">在此卡片内放置自定义组件、图文信息或操作入口。</p></div>`,
    previewHtml: `<div style="width: 100%; padding: 6px 10px; background: #fff; border-radius: 8px; border: 1px solid #e2e8f0; box-shadow: 0 1px 4px rgba(0,0,0,0.05);"><div style="font-size: 11px; font-weight: bold; color: #1e293b;">卡片容器</div><div style="font-size: 9px; color: #94a3b8;">纯色背景卡片</div></div>`,
  },
  {
    id: 'shape-circle',
    name: '圆形头像占位框',
    emoji: '⭕',
    tag: 'Circle',
    description: '用于用户头像或圆形微缩图标',
    html: `<div class="wf-avatar" style="width: 48px; height: 48px; border-radius: 50%; background: #e2e8f0; border: 2px solid #cbd5e1; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; margin: 8px 0; box-shadow: 0 2px 6px rgba(0,0,0,0.08);"><img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80" style="width: 100%; height: 100%; object-fit: cover;" alt="用户头像" /></div>`,
    previewHtml: `<div style="width: 32px; height: 32px; border-radius: 50%; background: #e2e8f0; border: 1.5px solid #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 12px;">👤</div>`,
  },
  {
    id: 'shape-text',
    name: '通用文本块',
    emoji: '📝',
    tag: 'Text Block',
    description: '标题加多行描述，双击直接修改文案',
    html: `<div class="wf-text-block" style="margin: 10px 0;"><h3 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 4px 0;">这是标题文字</h3><p style="font-size: 13px; color: #475569; line-height: 1.6; margin: 0;">双击任意文本可直接敲键盘改字，失焦后自动持久化落库。</p></div>`,
    previewHtml: `<div style="width: 100%;"><div style="font-size: 11px; font-weight: bold; color: #0f172a;">标题文案</div><div style="font-size: 9px; color: #64748b;">双击即可直接敲字编辑</div></div>`,
  },
]

const controlItems: PaletteItem[] = [
  {
    id: 'ctrl-btn-primary',
    name: '主行动按钮',
    emoji: '🟢',
    tag: 'Primary Button',
    description: '高对比度核心操作按钮',
    html: `<button class="wf-btn wf-btn-primary" style="padding: 10px 20px; background: linear-gradient(135deg, #10b981, #059669); color: #ffffff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: 0 4px 12px rgba(16,185,129,0.3); display: inline-flex; align-items: center; justify-content: center; gap: 6px; margin: 8px 0; transition: transform 0.1s;">立即提交</button>`,
    previewHtml: `<div style="padding: 4px 14px; background: #10b981; color: #fff; font-size: 10px; font-weight: bold; border-radius: 6px;">主行动按钮</div>`,
  },
  {
    id: 'ctrl-btn-secondary',
    name: '描边次按钮',
    emoji: '⚪',
    tag: 'Secondary Button',
    description: '浅色背景与深色细边框次要操作',
    html: `<button class="wf-btn wf-btn-secondary" style="padding: 9px 18px; background: #ffffff; color: #334155; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; margin: 8px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.05); transition: transform 0.1s;">取消/返回</button>`,
    previewHtml: `<div style="padding: 4px 14px; background: #fff; border: 1.5px solid #cbd5e1; color: #334155; font-size: 10px; font-weight: bold; border-radius: 6px;">描边次按钮</div>`,
  },
  {
    id: 'ctrl-switch',
    name: '开关切换 (Switch)',
    emoji: '🔀',
    tag: 'Switch',
    description: '高保真平滑切换的物理滑块开关',
    html: `<div class="wf-sw" data-state="on" style="display: inline-flex; align-items: center; width: 46px; height: 26px; background: linear-gradient(180deg, #FF8336 0%, #FA6B19 100%); border-radius: 13px; position: relative; cursor: pointer; margin: 8px 0; box-shadow: inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28);"><span class="wf-sw-k" style="width: 22px; height: 22px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.25);"></span></div>`,
    previewHtml: `<div style="width: 36px; height: 20px; background: #fa6b19; border-radius: 10px; position: relative;"><div style="width: 16px; height: 16px; background: #fff; border-radius: 50%; position: absolute; right: 2px; top: 2px;"></div></div>`,
  },
  {
    id: 'ctrl-search',
    name: '搜索框',
    emoji: '🔍',
    tag: 'Search Box',
    description: '带搜索图标与圆角输入底框',
    html: `<div class="wf-search-box" style="display: flex; align-items: center; width: 100%; height: 38px; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 10px; padding: 0 12px; margin: 8px 0;"><span style="color: #94a3b8; margin-right: 8px; font-size: 14px;">🔍</span><input type="text" placeholder="搜索内容或关键词..." style="border: none; background: transparent; outline: none; font-size: 13px; color: #1e293b; width: 100%;" /></div>`,
    previewHtml: `<div style="width: 100%; height: 26px; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 6px; display: flex; align-items: center; padding: 0 8px; font-size: 10px; color: #94a3b8;">🔍 搜索...</div>`,
  },
  {
    id: 'ctrl-modal',
    name: '居中弹窗 (Modal Dialog)',
    emoji: '🪟',
    tag: 'Modal Dialog',
    description: '居中遮罩弹窗，包含标题、内容与操作',
    html: `<div class="wf-modal wf-show" style="position: fixed; inset: 0; background: rgba(0,0,0,0.55); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; z-index: 9999;"><div style="width: 290px; background: #ffffff; border-radius: 18px; padding: 22px; box-shadow: 0 20px 30px -5px rgba(0,0,0,0.3); text-align: center;"><h4 style="font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 8px 0;">操作确认</h4><p style="font-size: 13px; color: #64748b; margin: 0 0 20px 0; line-height: 1.5;">确认执行当前业务操作吗？修改将立即同步并持久化。</p><div style="display: flex; gap: 10px;"><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer;">取消</button><button class="wf-modal-dismiss" style="flex: 1; padding: 9px; border: none; background: #10b981; border-radius: 10px; font-size: 13px; font-weight: 600; color: #ffffff; cursor: pointer;">确定</button></div></div></div>`,
    previewHtml: `<div style="padding: 4px 8px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px; text-align: center; width: 100%;"><span style="font-size: 10px; font-weight: bold; color: #1e293b;">居中弹窗卡片</span></div>`,
  },
  {
    id: 'ctrl-sheet',
    name: '底部抽屉 (Bottom Sheet)',
    emoji: '📥',
    tag: 'Bottom Sheet',
    description: '移动端原生感底部滑出操作面板',
    html: `<div class="wf-modal wf-bottom-sheet wf-show" style="position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; flex-direction: column; justify-content: flex-end; z-index: 9999;"><div style="width: 100%; background: #ffffff; border-radius: 20px 20px 0 0; padding: 18px 20px 28px 20px; box-shadow: 0 -4px 20px rgba(0,0,0,0.15);"><div style="width: 36px; height: 4px; background: #cbd5e1; border-radius: 2px; margin: 0 auto 14px auto;"></div><h4 style="font-size: 15px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0;">快捷面板操作</h4><div style="display: flex; flex-direction: column; gap: 8px;"><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 A：分享给好友</div><div style="padding: 10px 14px; background: #f8fafc; border-radius: 10px; font-size: 13px; color: #334155; font-weight: 500; cursor: pointer;">选项 B：保存至草稿</div></div><button class="wf-modal-dismiss" style="width: 100%; margin-top: 14px; padding: 10px; background: #f1f5f9; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; color: #64748b; cursor: pointer;">取消关闭</button></div></div>`,
    previewHtml: `<div style="width: 100%; padding: 4px 6px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px 6px 0 0; text-align: center;"><div style="width: 16px; height: 2px; background: #94a3b8; margin: 0 auto 2px auto;"></div><span style="font-size: 9px; font-weight: bold; color: #334155;">底部滑出抽屉</span></div>`,
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
