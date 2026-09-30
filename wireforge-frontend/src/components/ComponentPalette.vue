<template>
  <div class="component-palette h-full flex flex-col bg-white dark:bg-[#252525] overflow-hidden select-none">
    <!-- 1. Search Bar -->
    <div class="p-2.5 pb-2 border-b border-slate-100 dark:border-[#383838] bg-white dark:bg-[#252525]">
      <div class="flex items-center gap-1.5 px-2.5 py-1.5 bg-slate-100/80 dark:bg-[#1e1e1e] rounded-xl border border-slate-200/60 dark:border-[#383838] text-slate-500 dark:text-slate-400 focus-within:border-[#0D99FF] focus-within:bg-white dark:focus-within:bg-[#1e1e1e] focus-within:ring-2 focus-within:ring-[#0D99FF]/20 transition-all">
        <Search class="w-3.5 h-3.5 text-slate-400 shrink-0" />
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t('searchComponents')"
          class="w-full text-xs bg-transparent border-none outline-none text-slate-800 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder-slate-500"
        />
        <button
          v-if="searchQuery"
          type="button"
          class="text-[11px] text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
          :title="t('clearSearch')"
          @click="searchQuery = ''"
        >
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- 2. Category Filter Tabs (Compact 6-Column Segmented Bar, Fits Perfectly in Sidebar) -->
    <div class="px-2.5 py-1.5 border-b border-slate-100 dark:border-[#383838] bg-white dark:bg-[#252525]">
      <div class="palette-categories grid grid-cols-3 gap-1">
        <button
          v-for="cat in componentCategories"
          :key="cat.id"
          type="button"
          class="palette-category py-1 text-xs font-medium rounded-md transition-colors text-center cursor-pointer select-none"
          :aria-pressed="activeCat === cat.id"
          :class="activeCat === cat.id
            ? 'bg-white dark:bg-[#383838] text-[#0D99FF] dark:text-[#38bdf8] shadow-2xs font-bold'
            : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white hover:bg-white/50 dark:hover:bg-[#333333]'"
          @click="activeCat = cat.id"
        >
          {{ t('cat_' + cat.id) || cat.name }}
        </button>
      </div>
    </div>

    <!-- 3. Components List 2-Column Grid Area -->
    <div class="flex-1 overflow-y-auto p-2.5 space-y-3 custom-scrollbar">
      <!-- Empty state when search produces no results -->
      <div v-if="displayedCategories.length === 0" class="py-12 text-center text-xs text-slate-400">
        {{ t('noMatchingComponents') }}
      </div>

      <div
        v-for="cat in displayedCategories"
        :key="cat.id"
        class="space-y-1.5"
      >
        <!-- Category Section Header (only shown when in 'all' view) -->
        <div v-if="activeCat === 'all'" class="text-[10px] font-bold text-slate-400 px-1 uppercase tracking-wider">
          {{ t('cat_' + cat.id) || cat.name }}
        </div>

        <div class="grid grid-cols-2 gap-2">
          <div
            v-for="item in cat.items"
            :key="item.id"
            class="palette-item group relative cursor-grab active:cursor-grabbing flex flex-col items-stretch overflow-hidden"
            role="button"
            tabindex="0"
            @keydown.enter.self.prevent="emit('addComponent', item)"
            @keydown.space.self.prevent="emit('addComponent', item)"
            draggable="true"
            :title="`${item.name} · ${item.description}`"
            @dragstart="onDragStart($event, item)"
            @dragend="onDragEnd"
            @click="emit('addComponent', item)"
          >
            <!-- Quick Add Hover Button in top-right -->
            <button
              type="button"
              class="absolute top-1.5 right-1.5 w-5 h-5 rounded-md bg-white dark:bg-[#383838] hover:bg-[#0D99FF] text-slate-400 hover:text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer shadow-xs border border-slate-200/80 dark:border-[#484848] hover:border-transparent z-10"
              :title="`${t('quickAddPrefix')} ${item.name}`"
              @click.stop="emit('addComponent', item)"
            >
              <Plus class="w-3 h-3" />
            </button>

            <ComponentThumbnail :html="item.previewHtml" />
            <div class="palette-name text-slate-900 dark:text-slate-100 truncate">
              {{ item.name }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus, Search, X } from 'lucide-vue-next'
import ComponentThumbnail from './ComponentThumbnail.vue'
import { t } from '../utils/i18n'
import {
  componentLibrary,
  componentCategories,
  type PaletteItem,
  type PaletteCategory,
} from '@/data/componentLibrary'

export type { PaletteItem, PaletteCategory }

const props = defineProps<{
  targetPage?: { id: number; name: string } | null
}>()

const emit = defineEmits<{
  (e: 'addComponent', item: PaletteItem): void
  (e: 'dragStart', item: PaletteItem): void
  (e: 'dragEnd'): void
}>()

const searchQuery = ref('')
const activeCat = ref<'all' | 'shapes' | 'nav' | 'controls' | 'display' | 'feedback'>('all')

const displayedCategories = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  const targetCats =
    activeCat.value === 'all'
      ? componentCategories.filter((c) => c.id !== 'all')
      : componentCategories.filter((c) => c.id === activeCat.value)

  return targetCats
    .map((cat) => {
      const items = componentLibrary.filter((item) => {
        if (item.category !== cat.id) return false
        if (!query) return true
        return (
          item.name.toLowerCase().includes(query) ||
          item.tag.toLowerCase().includes(query) ||
          item.description.toLowerCase().includes(query)
        )
      })
      return {
        id: cat.id,
        name: cat.name,
        items,
      }
    })
    .filter((group) => group.items.length > 0)
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
.component-palette { font-size: 12px; }
.palette-category { min-height: 28px; font-weight: 500; box-shadow: none; border: 1px solid transparent; }
.palette-category[aria-pressed='true'] { background: #e9f3ff; border-color: #c9e1ff; color: #0969c7; }
.palette-item { border: 1px solid var(--editor-border, #e6e8eb); border-radius: 8px; background: var(--editor-surface, #fff); transition: border-color .15s; }
.palette-item:hover { border-color: #7bb8f5; }
.palette-item:focus-visible { outline: 2px solid #0d99ff; outline-offset: 2px; }
.palette-item:focus-within > button { opacity: 1; }
.palette-name { padding: 9px 10px; font-size: 12px; font-weight: 500; line-height: 18px; }
.component-palette input { font-size: 12px; line-height: 20px; }
:global(.dark .palette-category[aria-pressed='true']) { background: #173b58; border-color: #285575; color: #8acbff; }
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
.palette-sketch {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
}
.palette-sketch :deep(svg) {
  shape-rendering: geometricPrecision;
}
.palette-sketch :deep(img) {
  image-rendering: auto;
}
</style>
