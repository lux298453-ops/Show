<template>
  <section class="autowire-page-preview" :data-preview-source="source" :aria-label="`${page?.name || '尚未选择页面'}的只读预览`">
    <div ref="viewport" class="preview-viewport">
      <div v-if="page && (page.html_content?.trim() || page.background_image)" class="preview-sheet" :style="sheetStyle">
        <div class="preview-logical-page" :style="logicalStyle">
          <iframe v-if="page.html_content?.trim()" ref="frame" :key="page.id" :srcdoc="readonlyHtml" sandbox="allow-same-origin" referrerpolicy="no-referrer" :title="`${page.name} · 只读预览`" :style="frameStyle" tabindex="-1" @load="onFrameLoad" />
          <img v-else :src="getFileUrl(page.background_image)" :alt="`${page.name}的设计稿`" class="preview-background" draggable="false" @load="onImageLoad" @error="onImageError" />
          <div v-if="source && highlight && !imageFailed" class="preview-click-area" :style="highlightStyle" aria-label="当前关系的点击区域"><span>点击区域</span></div>
        </div>
      </div>
      <div v-else class="preview-empty"><FileImage :size="34" :stroke-width="1.4" /><strong>{{ page ? '这个页面还没有预览' : '选择要到达的页面' }}</strong><p>{{ page ? '页面名称已显示，生成原型或上传设计稿后可查看画面。' : '选择后可在这里核对目标页面。' }}</p></div>
      <div v-if="imageFailed" class="preview-image-error">设计稿暂时无法加载</div>
    </div>
    <div v-if="page" class="preview-caption">
      <span v-if="source && hasContent" :class="highlight ? 'is-located' : 'is-unlocated'">{{ !ready ? '正在加载页面…' : highlight && !imageFailed ? '点击区域已标出' : '无法定位该控件' }}</span>
      <span v-else>{{ page.html_content?.trim() ? '原型预览' : page.background_image ? '设计稿预览' : '暂无画面' }}</span>
      <button v-if="hasContent && !longPage" type="button" class="preview-zoom" :aria-label="`${page.name}：${zoomed ? '显示完整页面' : '放大查看'}`" @click="toggleZoom">{{ zoomed ? '完整页面' : '放大查看' }}</button>
      <span v-else-if="hasContent && canScroll" class="scroll-hint">可滚动查看</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FileImage } from 'lucide-vue-next'
import type { Page } from '../types'
import type { NavigationInfo } from '../types/autowire'
import { getFileUrl } from '../api/http'
import { createReadonlyPreviewHtml, locatePreviewHighlight, previewDimensions, type PreviewHighlight } from '../utils/autowirePreview'

const props = withDefaults(defineProps<{ page?: Page; elementId?: number; navigation?: NavigationInfo | null; source?: boolean }>(), { source: false })
const viewport = ref<HTMLDivElement>()
const frame = ref<HTMLIFrameElement>()
const viewportWidth = ref(340)
const viewportHeight = ref(460)
const contentHeight = ref(812)
const ready = ref(false)
const imageFailed = ref(false)
const highlight = ref<PreviewHighlight | null>(null)
const zoomed = ref(false)
let observer: ResizeObserver | undefined
let locateTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false
const dimensions = computed(() => previewDimensions(props.page))
const hasContent = computed(() => !!(props.page?.html_content?.trim() || props.page?.background_image))
const longPage = computed(() => contentHeight.value > dimensions.value.height * 1.25 || contentHeight.value / dimensions.value.width > 3)
// Single screens show their complete composition first. Long pages retain a
// readable width and scroll; the optional zoom never changes the prototype.
const scale = computed(() => Math.min(1, Math.max(0.05, (viewportWidth.value - 32) / dimensions.value.width),
  zoomed.value || longPage.value ? 1 : Math.max(0.05, (viewportHeight.value - 32) / contentHeight.value)))
const canScroll = computed(() => contentHeight.value * scale.value > viewportHeight.value - 32 + 1)
const readonlyHtml = computed(() => createReadonlyPreviewHtml(props.page?.html_content || '', getFileUrl))
const sheetStyle = computed(() => ({ width: `${dimensions.value.width * scale.value}px`, height: `${contentHeight.value * scale.value}px` }))
const logicalStyle = computed(() => ({ width: `${dimensions.value.width}px`, height: `${contentHeight.value}px`, transform: `scale(${scale.value})` }))
const frameStyle = computed(() => ({ width: `${dimensions.value.width}px`, height: `${contentHeight.value}px` }))
const highlightStyle = computed(() => highlight.value ? { left: `${highlight.value.rect.x}px`, top: `${highlight.value.rect.y}px`, width: `${highlight.value.rect.width}px`, height: `${highlight.value.rect.height}px`, borderWidth: `${2 / scale.value}px`, '--preview-inverse-scale': 1 / scale.value } : {})

async function toggleZoom() { zoomed.value = !zoomed.value; await nextTick(); await locate() }

async function locate(scrollToControl = true) {
  if (disposed || !props.page) return
  const doc = props.page.html_content?.trim() ? frame.value?.contentDocument || undefined : undefined
  highlight.value = props.source ? locatePreviewHighlight(props.page, props.elementId, props.navigation, doc, contentHeight.value) : null
  await nextTick()
  if (scrollToControl && viewport.value) {
    const middle = highlight.value ? (highlight.value.rect.y + highlight.value.rect.height / 2) * scale.value + 16 : 0
    viewport.value.scrollTop = Math.max(0, middle - viewport.value.clientHeight / 2)
    viewport.value.scrollLeft = 0
  }
}

async function onFrameLoad() {
  const doc = frame.value?.contentDocument
  if (!doc || !props.page || disposed) return
  // No scripts are allowed inside this frame. The parent reads layout only;
  // preview clicks never invoke the editor's postMessage protocol.
  doc.body.inert = true
  doc.addEventListener('click', event => event.preventDefault(), true)
  doc.addEventListener('submit', event => event.preventDefault(), true)
  contentHeight.value = Math.min(100000, Math.max(dimensions.value.height, doc.body.scrollHeight, doc.documentElement.scrollHeight))
  await nextTick()
  ready.value = true
  await locate()
  // Images/fonts can settle after the document load event, so remeasure once.
  clearTimeout(locateTimer)
  locateTimer = setTimeout(() => { if (!disposed) void locate() }, 200)
}
function onImageLoad() { ready.value = true; void locate() }
function onImageError() { ready.value = true; imageFailed.value = true; highlight.value = null }

watch(() => [props.page?.id, props.page?.html_content, props.page?.background_image, props.page?.canvas_width, props.page?.canvas_height], () => {
  contentHeight.value = dimensions.value.height
  ready.value = !props.page?.html_content?.trim() && !props.page?.background_image
  imageFailed.value = false
  zoomed.value = false
  highlight.value = null
  if (viewport.value) viewport.value.scrollTop = 0
}, { immediate: true })
watch(() => [props.elementId, props.navigation, props.source], () => { if (ready.value) void locate() })
onMounted(() => {
  observer = new ResizeObserver(entries => {
    const entry = entries[0]
    if (!entry) return
    viewportWidth.value = viewport.value?.clientWidth || entry.contentRect.width
    viewportHeight.value = viewport.value?.clientHeight || entry.contentRect.height
  })
  if (viewport.value) observer.observe(viewport.value)
})
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); clearTimeout(locateTimer) })
</script>

<style scoped>
.autowire-page-preview{display:flex;flex-direction:column;min-height:0;height:100%;width:100%;background:#f4f6f9;border:1px solid #e3e7ee;border-radius:12px;overflow:hidden}
.preview-viewport{position:relative;flex:1;min-height:0;overflow:auto;padding:16px;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#c7cdd8 transparent}
.preview-sheet{position:relative;margin:0 auto;background:white;box-shadow:0 3px 14px #18243816;border-radius:5px}
.preview-logical-page{position:absolute;left:0;top:0;transform-origin:top left;background:white}
iframe{display:block;border:0;background:white}
.preview-background{display:block;width:100%;height:100%;object-fit:fill;user-select:none}
.preview-click-area{position:absolute;z-index:2147483646;box-sizing:border-box;border:3px solid #2563eb;border-radius:6px;background:#2563eb14;box-shadow:0 0 0 3px #ffffffcc;pointer-events:none}
.preview-click-area span{position:absolute;right:-3px;bottom:calc(100% + 5px);white-space:nowrap;background:#2563eb;color:white;font-size:11px;font-weight:600;line-height:21px;padding:0 6px;border-radius:4px;transform:scale(var(--preview-inverse-scale,1));transform-origin:bottom right}
.preview-click-area[style*="top: 0px"] span{bottom:auto;top:calc(100% + 5px)}
.preview-caption{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:10px 13px;border-top:1px solid #e3e7ee;background:#f9fafc;color:#7c8698;font-size:11px;line-height:16px;flex-shrink:0}
.preview-caption .is-located{color:#2563eb}.preview-caption .is-unlocated{color:#997036}
.preview-zoom{border:0;background:none;color:#2563eb;font:inherit;white-space:nowrap;cursor:pointer;padding:0}.preview-zoom:hover{text-decoration:underline}.preview-zoom:focus-visible{outline:2px solid #2563eb;outline-offset:3px}
.preview-empty{min-height:220px;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:12px;color:#929cad;padding:28px;box-sizing:border-box}
.preview-empty strong{color:#68748a;font-size:13px;font-weight:500}.preview-empty p{margin:0;max-width:240px;font-size:12px;line-height:1.7}
.preview-image-error{position:absolute;inset:16px;display:grid;place-items:center;background:#f4f6f9e8;color:#8a6370;font-size:13px}
@media(max-width:760px){.preview-caption{padding:8px;font-size:10px}.scroll-hint{display:none}.preview-viewport{padding:12px}}
</style>
