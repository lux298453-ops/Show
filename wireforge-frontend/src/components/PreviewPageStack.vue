<template>
  <div class="preview-page-stack relative w-full" :style="{ height: `${basePage?.canvas_height || page.canvas_height || 812}px` }"
    :aria-busy="pending" :data-preview-current="displayedId">
    <!-- Hidden peers keep a real viewport for layout, decoded images and local state. -->
    <div v-for="entry in cachedPages" :key="entry.id" :data-preview-page="entry.id"
      class="preview-page-panel absolute inset-0 w-full h-full overflow-auto"
      :style="{ visibility: layers.includes(entry.id) ? 'visible' : 'hidden', zIndex: layers.indexOf(entry.id) + 1,
        background: overlays.get(entry.id)?.needsBackdrop ? 'rgb(0 0 0 / .45)' : 'transparent' }"
      :aria-hidden="entry.id !== displayedId" :inert="entry.id !== displayedId || pending">
      <PageCanvas :ref="el => setCanvas(entry.id, el)" :page="entry" :all-pages="allPages"
        :show-wireframe="true" :show-design="false" :show-annotations="false"
        :edit-mode="false" :interactive="true" :box-w="0" :gap="0"
        @frame-loading="markLoading(entry.id)" @frame-ready="markReady(entry.id)"
        @navigate="(...args) => forward(entry.id, 'navigate', args)"
        @back="forward(entry.id, 'back', [])"
        @miss-click="(...args) => forward(entry.id, 'miss-click', args)"
        @element-click="(...args) => forward(entry.id, 'element-click', args)" />
    </div>
    <div v-if="pending" class="preview-pending absolute inset-0 z-10" aria-label="正在准备页面">
      <span v-if="showLoading" role="status" class="preview-loading">正在打开…</span>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowReactive, watch } from 'vue'
import type { Page } from '../types'
import { tabTargetIds } from '../utils/previewTabs'
import { animatePreviewContent, type PreviewTransition } from '../utils/previewMotion'
import { preparePreviewOverlay, type PreviewOverlay } from '../utils/previewOverlay'
import PageCanvas from './PageCanvas.vue'

const props = defineProps<{ page: Page; allPages: Page[]; transition?: PreviewTransition }>()
const emit = defineEmits(['navigate', 'back', 'miss-click', 'element-click', 'display-page'])
const ids = ref<number[]>([])
const displayedId = ref<number | null>(null)
const layers = ref<number[]>([])
const paths = shallowReactive(new Map<number, number[]>())
const overlays = shallowReactive(new Map<number, PreviewOverlay>())
const sources = shallowReactive(new Map<number, Page>())
const ready = shallowReactive(new Set<number>())
const canvases = shallowReactive(new Map<number, any>())
const showLoading = ref(false)
const returningPath = computed(() => props.transition === 'backward' ? paths.get(props.page.id) : undefined)
const pending = computed(() => displayedId.value !== props.page.id || !(returningPath.value || [props.page.id]).every(id => ready.has(id)))
const basePage = computed(() => sources.get(layers.value[0]))
let loadingTimer: ReturnType<typeof setTimeout> | undefined
let cancelMotion = () => {}
let disposed = false

watch(() => props.page, page => {
  if (sources.get(page.id)?.html_content !== page.html_content) ready.delete(page.id)
  sources.set(page.id, page)
  const peers = tabTargetIds(page).filter(id => id !== page.id && props.allPages.some(p => p.id === id)).slice(0, 4)
  // Keep the visible page until the destination is ready. Only preload direct peers.
  const returning = returningPath.value || []
  const next = [...new Set([...ids.value, page.id, ...peers, ...returning])]
    .filter(id => id === page.id || props.allPages.some(p => p.id === id))
  while (next.length > 6) {
    const index = next.findIndex(id => id !== page.id && !layers.value.includes(id) && !peers.includes(id) && !returning.includes(id))
    if (index < 0) break // Never evict the live background of an open dialog.
    next.splice(index, 1)
  }
  // Do not reorder retained DOM nodes: moving an iframe can reload its document.
  ids.value = next
  for (const id of ids.value) if (!sources.has(id)) sources.set(id, props.allPages.find(p => p.id === id)!)
  for (const id of sources.keys()) if (!ids.value.includes(id)) { restoreOverlay(id); sources.delete(id); ready.delete(id) }
}, { immediate: true })
const cachedPages = computed(() => ids.value.map(id => sources.get(id)).filter((p): p is Page => !!p))

watch(pending, value => {
  clearTimeout(loadingTimer)
  showLoading.value = false
  if (value) loadingTimer = setTimeout(() => { showLoading.value = true }, 220)
}, { immediate: true })
watch(() => [props.page, (returningPath.value || [props.page.id]).every(id => ready.has(id))] as const, ([page, isReady]) => {
  if (!isReady) return
  const id = page.id
  if (displayedId.value === id) {
    if (layers.value.indexOf(id) > 0 && !overlays.has(id)) {
      const overlay = preparePreviewOverlay(canvases.get(id)?.htmlFrameRef)
      if (overlay) overlays.set(id, overlay)
    }
    emit('display-page', sources.get(id)); return
  }
  cancelMotion()
  const hadPage = displayedId.value != null
  const closing = layers.value.includes(id)
  let next = closing ? layers.value.slice(0, layers.value.indexOf(id) + 1) : returningPath.value?.slice()
  if (!next && hadPage) {
    const overlay = preparePreviewOverlay(canvases.get(id)?.htmlFrameRef)
    if (overlay) { overlays.set(id, overlay); next = [...layers.value, id] }
  }
  next ||= [id]
  for (const layer of [...overlays.keys()]) if (!next.includes(layer) || next[0] === layer) restoreOverlay(layer)
  for (const layer of next.slice(1)) {
    if (!overlays.has(layer)) {
      const overlay = preparePreviewOverlay(canvases.get(layer)?.htmlFrameRef)
      if (overlay) overlays.set(layer, overlay)
    }
  }
  // Prepare the first animation frame while the destination is still hidden.
  // Publishing visibility first could expose its final state before entry starts.
  if (!disposed && hadPage && !closing) {
    cancelMotion = animatePreviewContent(canvases.get(id)?.htmlFrameRef, props.transition || 'tab')
  }
  layers.value = next
  paths.set(id, next.slice())
  displayedId.value = id
  emit('display-page', sources.get(id))
}, { flush: 'post' })

function restoreOverlay(id: number) { overlays.get(id)?.restore(); overlays.delete(id) }
function markLoading(id: number) { restoreOverlay(id); ready.delete(id) }
function markReady(id: number) { if (sources.has(id)) ready.add(id) }
function setCanvas(id: number, el: any) {
  if (el) { canvases.set(id, el); if (!sources.get(id)?.html_content) markReady(id) }
  else canvases.delete(id)
}
function forward(id: number, event: 'navigate' | 'back' | 'miss-click' | 'element-click', args: any[]) {
  if (!pending.value && id === displayedId.value) emit(event, ...args)
}
onBeforeUnmount(() => { disposed = true; clearTimeout(loadingTimer); cancelMotion(); for (const id of overlays.keys()) restoreOverlay(id) })
defineExpose({ htmlFrameRef: computed(() => canvases.get(displayedId.value!)?.htmlFrameRef) })
</script>
<style scoped>
.preview-page-stack { isolation: isolate; }
.preview-pending { cursor: progress; }
.preview-loading { position: absolute; bottom: 88px; left: 50%; transform: translateX(-50%); padding: 7px 12px; border-radius: 16px; background: rgb(15 23 42 / .82); color: white; font: 12px system-ui; white-space: nowrap; }
</style>
