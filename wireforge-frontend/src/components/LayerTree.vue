<template>
  <div class="layer-tree-panel flex-1 flex flex-col h-full bg-white select-none">
    <div class="px-4 h-9 border-b border-[#e6e6e6] flex items-center justify-between gap-2 shrink-0">
      <span class="truncate" :title="currentPage?.name || '未选择画板'">
        {{ currentPage?.name || '未选择画板' }}
      </span>
      <div class="flex items-center gap-0.5 shrink-0">
        <button
          type="button"
          class="px-1.5 h-6 rounded hover:bg-[#f0f0f0]"
          title="先按住 Ctrl 逐个点选，或按住 Shift 连选，再编成一组 (Ctrl+G)"
          @click="emit('group-layers')"
        >成组</button>
        <button
          type="button"
          class="px-1.5 h-6 rounded hover:bg-[#f0f0f0]"
          title="解散选中的分组 (Ctrl+Shift+G)"
          @click="emit('ungroup-layers')"
        >解组</button>
      </div>
    </div>

    <div class="px-4 h-8 border-b border-[#f0f0f0] flex items-center gap-1.5 bg-white shrink-0">
      <Search class="w-3.5 h-3.5 text-black/40 shrink-0" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索图层"
        class="layer-search w-full bg-transparent outline-none placeholder:text-black/35"
      />
      <button
        v-if="searchQuery"
        class="p-0.5 text-black/40 hover:text-black/80 rounded"
        @click="searchQuery = ''"
      >
        <X class="w-3 h-3" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto custom-scrollbar">
      <div
        class="layer-row flex items-center cursor-pointer"
        :class="isFrameSelected ? 'bg-[#e5f4ff]' : 'hover:bg-[#f5f5f5]'"
        style="padding-left: 16px; padding-right: 10px"
        @click="emit('select-frame', currentPage?.id)"
      >
        <span class="w-4 shrink-0"></span>
        <Hash class="layer-icon w-3 h-3 shrink-0 text-black/75" />
        <span class="truncate ml-1.5">{{ currentPage?.name || 'Artboard' }}</span>
        <span class="ml-auto pl-2 text-black/40 tabular-nums">{{ layerCount || '' }}</span>
      </div>

      <template v-if="useDomLayers">
        <template v-if="visibleDomRows.length">
          <div
            v-for="row in visibleDomRows"
            :key="row.layer.uid"
            class="layer-row group relative flex items-center cursor-pointer"
            :class="isRowSelected(row.layer.uid) ? 'is-selected bg-[#0D99FF] text-white' : 'hover:bg-[#f5f5f5]'"
            :style="{ paddingLeft: (16 + row.depth * 18) + 'px', paddingRight: '8px' }"
            :draggable="renamingUid !== row.layer.uid"
            @click="onLayerRowClick(row.layer.uid, $event)"
            @contextmenu="onLayerContextMenu(row.layer.uid, $event)"
            @dragstart="onLayerDragStart($event, row.layer.uid)"
            @dragover="onLayerDragOver($event, row.layer.uid)"
            @drop="onLayerDrop($event, row.layer.uid)"
            @dragend="draggingUid = null; dropHint = null"
          >
            <span
              v-if="dropHint?.uid === row.layer.uid"
              class="drop-line"
              :class="dropHint.place === 'before' ? 'is-before' : 'is-after'"
            ></span>
            <button
              v-if="row.layer.children && row.layer.children.length"
              type="button"
              class="w-4 h-4 shrink-0 flex items-center justify-center rounded hover:bg-black/10"
              :title="collapsed[row.layer.uid] ? '展开' : '收起'"
              @click.stop="toggleCollapse(row.layer.uid)"
            >
              <ChevronDown v-if="!collapsed[row.layer.uid]" class="layer-icon w-3 h-3" />
              <ChevronRight v-else class="layer-icon w-3 h-3" />
            </button>
            <span v-else class="w-4 shrink-0"></span>
            <component
              :is="domLayerIcon(row.layer.kind)"
              class="layer-icon w-3 h-3 shrink-0"
              :class="isRowSelected(row.layer.uid) ? 'text-white' : 'text-black/75'"
            />
            <input
              v-if="renamingUid === row.layer.uid"
              ref="renameInputRef"
              v-model="renameDraft"
              class="layer-rename ml-1.5"
              @click.stop
              @mousedown.stop
              @dblclick.stop
              @dragstart.stop.prevent
              @keydown.enter.prevent="commitRename"
              @keydown.esc.prevent="cancelRename"
              @blur="commitRename"
            />
            <span
              v-else
              class="truncate ml-1.5"
              :class="row.layer.hidden && !isRowSelected(row.layer.uid) ? 'opacity-40' : ''"
              :title="row.layer.name + '（双击改名）'"
              @dblclick.stop="startRename(row.layer)"
            >{{ row.layer.name }}</span>
            <span class="flex-1"></span>
            <button
              type="button"
              class="w-5 h-5 shrink-0 flex items-center justify-center rounded hover:bg-black/10"
              :class="row.layer.locked || isRowSelected(row.layer.uid) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'"
              :title="row.layer.locked ? '解锁' : '锁定'"
              @click.stop="emit('toggle-layer-locked', row.layer)"
            >
              <Lock v-if="row.layer.locked" class="layer-icon w-3 h-3" />
              <LockOpen v-else class="layer-icon w-3 h-3" />
            </button>
            <button
              type="button"
              class="w-5 h-5 shrink-0 flex items-center justify-center rounded hover:bg-black/10"
              :class="row.layer.hidden || isRowSelected(row.layer.uid) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'"
              :title="row.layer.hidden ? '显示' : '隐藏'"
              @click.stop="emit('toggle-layer-hidden', row.layer)"
            >
              <EyeOff v-if="row.layer.hidden" class="layer-icon w-3 h-3" />
              <Eye v-else class="layer-icon w-3 h-3" />
            </button>
          </div>
        </template>
        <div v-else class="py-8 text-center text-black/40 text-[11px]">
          {{ searchQuery ? '未找到匹配图层' : '暂无图层元素' }}
        </div>
      </template>

      <!-- 旧模式：子图层列表 (倒序排列，贴合设计软件从顶层到底层习惯) -->
      <div v-else class="pl-3">
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
import { ref, computed, nextTick, watch } from 'vue'
import {
  Search,
  X,
  Hash,
  Type,
  Square,
  Circle,
  Minus,
  Image as ImageIcon,
  Eye,
  EyeOff,
  Lock,
  LockOpen,
  Frame,
  PenLine,
  RectangleHorizontal,
  TextCursorInput,
  ChevronDown,
  ChevronRight,
  Group,
  Compass,
  LayoutGrid,
  CreditCard,
  Sliders,
  Maximize,
  Box,
} from 'lucide-vue-next'
import type { Page, Element } from '../types'

type DomLayer = {
  uid: string
  name: string
  kind?: string
  hidden?: boolean
  locked?: boolean
  children?: DomLayer[]
}

const props = defineProps<{
  currentPage: Page | null
  selectedElementId: number | null
  hoveredElementId?: number | null
  /** 传入数组（含空数组）时改用 DOM 实时图层，不传则保持 page.elements */
  domLayers?: DomLayer[] | null
  /** DOM 图层选中态高亮（主选中，单选时用） */
  selectedLayerUid?: string | null
  /** 多选时全部高亮 */
  selectedLayerUids?: string[]
}>()

const emit = defineEmits<{
  (e: 'select-element', id: number): void
  (e: 'select-frame', pageId?: number): void
  (e: 'hover-element', id: number | null): void
  (e: 'select-dom-layer', uid: string, uids: string[]): void
  (e: 'open-layer-menu', payload: { x: number; y: number }): void
  (e: 'toggle-layer-hidden', layer: { uid: string; hidden?: boolean }): void
  (e: 'toggle-layer-locked', layer: { uid: string; locked?: boolean }): void
  (e: 'group-layers'): void
  (e: 'ungroup-layers'): void
  (e: 'rename-layer', payload: { uid: string; name: string }): void
  (e: 'reorder-layer', payload: { uid: string; targetUid: string; place: 'before' | 'after' }): void
}>()

const searchQuery = ref('')
const collapsed = ref<Record<string, boolean>>({})
const renamingUid = ref<string | null>(null)
const renameDraft = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)
const draggingUid = ref<string | null>(null)
const dropHint = ref<{ uid: string; place: 'before' | 'after' } | null>(null)
const anchorUid = ref<string | null>(null)

const selectedUidSet = computed(() => {
  const list = props.selectedLayerUids
  if (list && list.length) return new Set(list)
  return new Set(props.selectedLayerUid ? [props.selectedLayerUid] : [])
})

function isRowSelected(uid: string) {
  return selectedUidSet.value.has(uid)
}

watch(
  () => props.selectedLayerUids,
  (uids) => {
    const list = uids || []
    if (!list.length) {
      anchorUid.value = null
      return
    }
    if (!anchorUid.value || !list.includes(anchorUid.value)) {
      anchorUid.value = list[list.length - 1]
    }
  },
)

function onLayerRowClick(uid: string, e: MouseEvent) {
  const rows = visibleDomRows.value.map((row) => row.layer.uid)
  let next: string[]
  if (e.shiftKey) {
    const anchor = anchorUid.value && rows.includes(anchorUid.value)
      ? anchorUid.value
      : (props.selectedLayerUids?.length ? props.selectedLayerUids[props.selectedLayerUids.length - 1] : uid)
    const start = rows.indexOf(anchor)
    const end = rows.indexOf(uid)
    if (start < 0 || end < 0) {
      next = [uid]
    } else {
      const lo = Math.min(start, end)
      const hi = Math.max(start, end)
      next = rows.slice(lo, hi + 1)
    }
    if (e.ctrlKey || e.metaKey) {
      next = [...new Set([...(props.selectedLayerUids || []), ...next])]
    }
  } else if (e.ctrlKey || e.metaKey) {
    const cur = new Set(props.selectedLayerUids || [])
    if (cur.has(uid)) cur.delete(uid)
    else cur.add(uid)
    next = [...cur]
    anchorUid.value = uid
  } else {
    next = [uid]
    anchorUid.value = uid
  }
  emit('select-dom-layer', uid, next)
}

function onLayerContextMenu(uid: string, e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  if (!isRowSelected(uid)) {
    anchorUid.value = uid
    emit('select-dom-layer', uid, [uid])
  }
  emit('open-layer-menu', { x: e.clientX, y: e.clientY })
}

/** undefined = 旧模式；数组（含 []）= DOM 图层模式；null 也走旧模式 */
const useDomLayers = computed(() => Array.isArray(props.domLayers))

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

function countNodes(nodes: DomLayer[]): number {
  let n = 0
  for (const layer of nodes || []) n += 1 + countNodes(layer.children || [])
  return n
}

const visibleDomRows = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const out: { layer: DomLayer; depth: number }[] = []
  const walk = (nodes: DomLayer[], depth: number) => {
    for (const layer of nodes || []) {
      const kids = layer.children || []
      const matched = !q || layer.name.toLowerCase().includes(q)
      if (!q) {
        out.push({ layer, depth })
        if (kids.length && !collapsed.value[layer.uid]) walk(kids, depth + 1)
      } else if (matched) {
        out.push({ layer, depth })
      } else if (kids.length) {
        walk(kids, depth + 1)
      }
    }
  }
  walk(props.domLayers || [], 0)
  return out
})

function toggleCollapse(uid: string) {
  collapsed.value = { ...collapsed.value, [uid]: !collapsed.value[uid] }
}

function startRename(layer: DomLayer) {
  renamingUid.value = layer.uid
  renameDraft.value = layer.name
  nextTick(() => {
    const raw = renameInputRef.value as HTMLInputElement | HTMLInputElement[] | null
    const el = Array.isArray(raw) ? raw[0] : raw
    el?.focus()
    el?.select()
  })
}

function commitRename() {
  const uid = renamingUid.value
  const name = renameDraft.value.trim()
  renamingUid.value = null
  if (!uid || !name) return
  emit('rename-layer', { uid, name })
}

function cancelRename() {
  renamingUid.value = null
}

function onLayerDragStart(ev: DragEvent, uid: string) {
  draggingUid.value = uid
  ev.dataTransfer?.setData('text/plain', uid)
  if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move'
}

function onLayerDragOver(ev: DragEvent, uid: string) {
  if (!draggingUid.value || draggingUid.value === uid) return
  ev.preventDefault()
  if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move'
  const row = ev.currentTarget as HTMLElement
  const rect = row.getBoundingClientRect()
  const place = ev.clientY < rect.top + rect.height / 2 ? 'before' : 'after'
  dropHint.value = { uid, place }
}

function onLayerDrop(ev: DragEvent, uid: string) {
  ev.preventDefault()
  const from = draggingUid.value || ev.dataTransfer?.getData('text/plain')
  const place = dropHint.value?.uid === uid ? dropHint.value.place : 'before'
  draggingUid.value = null
  dropHint.value = null
  if (!from || from === uid) return
  emit('reorder-layer', { uid: from, targetUid: uid, place })
}

const layerCount = computed(() => {
  if (useDomLayers.value) return countNodes(props.domLayers || [])
  return layers.value.length
})

const isFrameSelected = computed(() => {
  if (useDomLayers.value) return !props.selectedLayerUid
  return props.selectedElementId == null
})

function domLayerIcon(kind?: string) {
  if (kind === 'text') return Type
  if (kind === 'image') return ImageIcon
  if (kind === 'ellipse') return Circle
  if (kind === 'line') return Minus
  if (kind === 'vector') return PenLine
  if (kind === 'frame') return Frame
  if (kind === 'group') return Group
  if (kind === 'button') return RectangleHorizontal
  if (kind === 'input') return TextCursorInput
  return Square
}

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
.layer-tree-panel {
  font-family: Inter, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.005em;
  line-height: 16px;
  color: rgba(0, 0, 0, 0.9);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.layer-row {
  height: 32px;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.005em;
}
.layer-icon {
  shape-rendering: crispEdges;
}
.layer-search,
.layer-rename {
  font-size: 11px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.9);
}
.layer-rename {
  width: 100%;
  min-width: 0;
  height: 20px;
  padding: 0 4px;
  border: 1px solid #0d99ff;
  border-radius: 2px;
  outline: none;
  background: #fff;
}
.layer-row.is-selected .layer-rename {
  color: #000;
}
.drop-line {
  position: absolute;
  left: 12px;
  right: 8px;
  height: 2px;
  background: #0d99ff;
  pointer-events: none;
  z-index: 2;
}
.drop-line.is-before { top: 0; }
.drop-line.is-after { bottom: 0; }
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #d4d4d4;
  border-radius: 4px;
}
</style>
