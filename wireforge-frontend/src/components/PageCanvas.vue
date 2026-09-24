<template>
  <div
    ref="rootRef"
    class="page-canvas"
    :class="{ 'preview-mode': !showDesign && !showAnnotations }"
    :style="{ width: `${stageW}px`, height: `${stageH}px` }"
  >
    <!-- 纯设计稿画框：仅在只有设计稿图且无原型时展示 -->
    <div
      v-if="isDesignFrame"
      class="design-slot single-frame-slot"
      :style="{
        left: `0px`,
        width: `${canvasW * scale}px`,
        height: `${canvasH * scale}px`,
      }"
    >
      <img
        v-if="page.background_image"
        :src="getFileUrl(page.background_image)"
        class="design-img rounded-xl object-contain w-full h-full"
        loading="lazy"
        decoding="async"
        draggable="false"
      />
      <div v-else class="design-empty flex items-center justify-center h-full text-slate-400 text-sm">
        空白设计稿
      </div>
    </div>

    <!-- 原型画框（空白画框或可交互原型图）：全功能 iframe / 线框矢量组件 -->
    <div
      v-else
      class="canvas-slot single-frame-slot"
      :style="{
        left: `0px`,
        width: `${canvasW * scale}px`,
        height: `${canvasH * scale}px`,
      }"
      @dragover="onSlotDragOver"
      @drop="onSlotDrop"
    >
      <!-- Stitch 式整页直出视图：AI 生成的完整 HTML/CSS 页面（唯一展示形态，支持 data-nav 跨页跳转） -->
      <iframe
        v-if="htmlMode"
        ref="htmlFrameRef"
        class="html-frame"
        :class="{ 'pointer-events-none': draggingComponent }"
        :style="[{ width: `${Math.round(canvasW * scale)}px`, height: `${Math.round(canvasH * scale)}px` }, draggingComponent ? { pointerEvents: 'none' } : {}]"
        :srcdoc="navRuntimeHtml"
        sandbox="allow-scripts allow-same-origin"
        title="prototype-html"
        @load="onFrameLoad"
      ></iframe>

      <div
        v-else
        class="canvas"
        :style="[{ width: `${canvasW}px`, height: `${canvasH}px`, transform: `scale(${scale})` }, canvasBgStyle]"
      >
        <!-- 背景铺底层：仅在 AI 没有取到任何真实背景色时才回退渲染素材图；
             有取色时用颜色作画布底色，不再叠素材渐变图（避免与设计稿观感冲突、干扰元素层级） -->
        <div v-if="showWireframe && backgroundEls.length && !hasBgColor" class="canvas-bg-layer">
          <img
            v-for="bg in backgroundEls"
            v-show="!bgErrors[bg.id]"
            :key="bg.id"
            :src="assetUrlFor(bg)"
            class="canvas-bg"
            @error="onBgError(bg.id)"
            alt=""
          />
        </div>
        <div v-if="showWireframe" class="element-layer">
          <WireframeElement
            v-for="el in normalEls"
            :key="el.id"
            :el="el"
            :type="el.type"
            :has-interaction="hasNavigate(el)"
            :hovered="hoveredElementId === el.id || selectedElementId === el.id"
            :suppress-inner="!!suppressInnerMap[el.id]"
            :on-bg="backgroundEls.length > 0"
            @click="onElementClick"
            @hover="onElementHover"
          />
        </div>
      </div>

      <!-- 独占微调保护提示：仅严丝合缝贴合线框原型手机屏幕，绝不超出 -->
      <div
        v-if="lockedByOther"
        class="absolute inset-0 z-20 rounded-[14px] pointer-events-auto cursor-not-allowed flex flex-col items-center justify-end pb-8 bg-slate-950/20 backdrop-blur-[1px] transition-all"
        @click.stop="emit('lockedClick')"
        @mousedown.stop
      >
        <div class="px-3.5 py-1.5 bg-slate-900/90 text-white rounded-full shadow-lg border border-white/10 flex items-center gap-1.5 text-xs font-medium backdrop-blur-md">
          <Lock class="w-3.5 h-3.5 text-amber-400" />
          <span>{{ lockedByOther }} 正在独占微调</span>
        </div>
      </div>
    </div>

    <!-- 标注引线：默认隐藏，点击线稿元素或说明条目时显示对应的线 -->
    <svg v-if="showAnnotations" class="leader-svg" :viewBox="`0 0 ${stageW} ${stageH}`">
      <path v-for="line in activeLines" :key="line.id" :d="line.path" class="leader-line highlight" fill="none" />
    </svg>

    <!-- 说明面板：默认收起，点击面板头或线框元素时展开 -->
    <div
      v-if="showAnnotations && annItems.length"
      class="ann-panel"
      :class="{ open: isPanelOpen }"
      :style="{ left: `${panelX}px`, maxHeight: `${panelMaxH}px` }"
    >
      <div ref="panelHeaderRef" class="panel-header" @click.stop="panelOpen = !panelOpen" @mousedown.stop>
        <span class="panel-title flex items-center gap-1.5">
          <FileText class="w-3.5 h-3.5 text-emerald-600" />
          <span>说明 ({{ annItems.length }})</span>
        </span>
        <span class="panel-chevron">
          <ChevronDown v-if="isPanelOpen" class="w-3.5 h-3.5" />
          <ChevronRight v-else class="w-3.5 h-3.5" />
        </span>
      </div>
      <div v-if="isPanelOpen" ref="panelBodyRef" class="panel-body" @scroll="recomputeLines" @mousedown.stop>
        <div
          v-for="ap in annItems"
          :key="ap.id"
          :ref="(el: any) => setBoxRef(ap.id, el)"
          class="ann-box group"
          :class="{
            selected: selectedElementId != null && selectedElementId === ap.elementId,
            editing: editingAnnId === ap.id,
            dragging: draggingAnnId === ap.id,
            'drag-over': dragOverAnnId === ap.id,
          }"
          draggable="true"
          @dragstart="onCanvasDragStart($event, ap.id)"
          @dragover.prevent="onCanvasDragOver($event, ap.id)"
          @dragleave="onCanvasDragLeave($event, ap.id)"
          @drop.prevent="onCanvasDrop($event, ap.id)"
          @dragend="onCanvasDragEnd"
          @mouseenter="onAnnHover(ap.id)"
          @mouseleave="onAnnHover(null)"
          @click.stop="onAnnClick(ap)"
        >
          <!-- Editing Mode Form -->
          <div v-if="editingAnnId === ap.id" class="ann-edit-form" @click.stop @mousedown.stop>
            <div class="edit-row">
              <span class="edit-label">标题</span>
              <input
                ref="editingTitleInputRef"
                v-model="editTitle"
                class="ann-edit-title-input"
                placeholder="组件名称..."
                @keydown.enter.prevent="finishEdit(ap)"
                @keydown.esc.stop="cancelEdit"
              />
            </div>
            <div class="edit-row">
              <span class="edit-label">说明</span>
              <textarea
                ref="editingTextareaRef"
                v-model="editText"
                class="ann-edit-textarea"
                rows="3"
                placeholder="业务说明详情..."
                @keydown.ctrl.enter.prevent="finishEdit(ap)"
                @keydown.esc.stop="cancelEdit"
              ></textarea>
            </div>
            <div class="ann-edit-actions">
              <button class="btn-cancel" @click.stop="cancelEdit">取消</button>
              <button class="btn-save" @click.stop="finishEdit(ap)">保存</button>
            </div>
          </div>

          <!-- Normal Display Mode -->
          <template v-else>
            <div class="ann-head">
              <span class="ann-drag-handle" title="长按拖拽调整顺序" @mousedown.stop>
                <GripVertical class="w-3.5 h-3.5" />
              </span>
              <span class="ann-title" :title="ap.title">{{ ap.title }}</span>
              <button
                class="ann-edit-btn opacity-0 group-hover:opacity-100 transition-opacity p-0.5 text-slate-400 hover:text-emerald-600 rounded cursor-pointer"
                title="编辑标题与说明"
                @click.stop="startEdit(ap)"
                @mousedown.stop
              >
                <Pencil class="w-3 h-3" />
              </button>
              <span
                v-if="ap.interactionType"
                class="ann-badge"
                :class="ap.interactionType"
                :title="ap.interactionTarget ? `${ap.interactionLabel}: ${ap.interactionTarget}` : (ap.interactionLabel || '')"
              >
                {{ ap.interactionType === 'navigate' ? '跳转' : (ap.interactionType === 'modal' ? '弹窗' : '切换') }}
              </span>
            </div>
            <div v-if="ap.interactionTarget" class="ann-target-hint">
              <span class="hint-dot"></span>
              <span class="hint-text">去向: {{ ap.interactionTarget }}</span>
            </div>
            <div class="ann-text" :class="{ empty: !ap.annotation.text }" @dblclick.stop="startEdit(ap)" title="双击快速编辑说明">
              {{ ap.annotation.text || '双击编辑说明…' }}
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 素材库替换弹窗 -->
    <el-dialog
      v-model="showAssetPicker"
      title="素材库替换图片/头像"
      width="560px"
      append-to-body
      :close-on-click-modal="true"
      class="wf-asset-dialog"
    >
      <div class="space-y-3 select-none">
        <!-- 分类切换 -->
        <div class="flex items-center gap-1.5 p-1 bg-slate-100 rounded-xl">
          <button
            v-for="c in assetCategories"
            :key="c.id"
            class="flex-1 py-1.5 text-xs font-bold rounded-lg transition-all cursor-pointer"
            :class="activeAssetCat === c.id ? 'bg-white text-blue-600 shadow-2xs border border-slate-200/80' : 'text-slate-500 hover:text-slate-800'"
            @click="activeAssetCat = c.id"
          >
            {{ c.name }}
          </button>
        </div>

        <!-- 素材缩略图网格 -->
        <div class="grid grid-cols-4 gap-3 max-h-[360px] overflow-y-auto p-1 custom-scrollbar">
          <div
            v-for="a in filteredAssets"
            :key="a.id"
            class="group relative border border-slate-200/90 rounded-xl p-2.5 flex flex-col items-center gap-1.5 hover:border-blue-500 hover:shadow-md cursor-pointer transition-all bg-white hover:bg-blue-50/20"
            @click="selectAsset(a)"
          >
            <div class="w-16 h-16 rounded-lg overflow-hidden bg-slate-100 flex items-center justify-center border border-slate-200/70 shrink-0">
              <img :src="a.url" class="w-full h-full object-cover group-hover:scale-105 transition-transform" />
            </div>
            <span class="text-[11px] font-semibold text-slate-700 truncate max-w-[90px]" :title="a.name">{{ a.name }}</span>
            <span class="text-[9px] text-blue-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">点击替换</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-between items-center text-xs text-slate-400">
          <span>点击上方任意图片即可直接替换选中元素并自动落库保存</span>
          <button
            class="px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
            @click="showAssetPicker = false"
          >
            取消
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { FileText, ChevronDown, ChevronRight, GripVertical, Pencil, Lock, Image as ImageIcon } from 'lucide-vue-next'
import { getFileUrl } from '../api/http'
import { projectApi } from '../api/project'
import type { Annotation, Element, Page } from '../types'
import WireframeElement from './WireframeElement.vue'

const props = withDefaults(
  defineProps<{
    page: Page
    showWireframe?: boolean
    showAnnotations?: boolean
    /** 画框专职类型：明确是设计稿原图画框还是原型图画框 */
    frameType?: 'design' | 'prototype'
    selectedElementId?: number | null
    hoveredElementId?: number | null
    hoveredAnnId?: number | null
    showDesign?: boolean
    boxW?: number
    gap?: number
    canvasScale?: number
    /** 整页原型微调模式：iframe 内可拖动元素 / 滚轮调字号 / Del 隐藏 */
    editMode?: boolean
    /** 是否处于真机原型预览交互模式：true 时才响应跳转、开关、勾选、Tab 切换等操作；false 时为纯静态视图（保护画布微调） */
    interactive?: boolean
    /** 全局所有页面列表，用于解析交互跳转的目标页面名称 */
    allPages?: Page[]
    /** 业务说明自定义排序 */
    customOrders?: Record<number, number[]>
    /** 业务说明自定义标题缓存 */
    customTitles?: Record<number, string>
    /** 被其他人独占锁定时编辑者名称（非空时线框手机屏幕处于独占保护） */
    lockedByOther?: string | null
    /** 是否正在从组件库拖拽组件（为 true 时屏蔽 iframe 鼠标事件，确保拖拽精准捕获） */
    draggingComponent?: boolean
    /** 交互连线模式：在原型组件右侧显示可拖拽的蓝点 */
    protoHotspot?: boolean
  }>(),
  {
    showWireframe: true,
    showAnnotations: true,
    selectedElementId: null,
    hoveredElementId: null,
    hoveredAnnId: null,
    showDesign: true,
    boxW: 220,
    draggingComponent: false,
    gap: 16,
    canvasScale: 1,
    editMode: false,
    interactive: false,
    lockedByOther: null,
    protoHotspot: false,
  },
)

const emit = defineEmits<{
  (e: 'elementClick', el: Element): void
  (e: 'elementHover', el: Element, on: boolean): void
  (e: 'annHover', annId: number | null): void
  (e: 'annClick', annId: number): void
  (e: 'annSave', annId: number, text: string, title?: string): void
  (e: 'annOrderChange', pageId: number, order: number[]): void
  (e: 'navigate', pageName: string, uids?: string[]): void
  (e: 'back'): void
  (e: 'saveHtml', payload: { pageId: number; html: string }): void
  (e: 'lockedClick'): void
  (e: 'missClick', pos?: { x: number; y: number; uids?: string[] }): void
  (e: 'requestEdit'): void
  (e: 'elementSelected', info: any): void
  (e: 'elementDeselected'): void
  (e: 'frameFill', color: string): void
  (e: 'selectionChanged', uids: string[]): void
  (e: 'contextMenu', pos: { x: number; y: number }): void
  (e: 'layers-changed', payload: { pageId: number; layers: Array<{ uid: string; name: string; kind?: string; hidden?: boolean; locked?: boolean; children?: unknown[] }> }): void
  (e: 'frameFocus'): void
  (e: 'editVector', payload: any): void
  (e: 'hotspot', payload: { x: number; y: number; w: number; h: number; label: string; uid: string }): void
  (e: 'hotspotClear'): void
}>()

// Stitch 式整页直出：页面有 AI 生成的 HTML 时只展示整页视图（无 HTML 的未分析页回退组件渲染）。
// 注意：必须放在 props 声明之后，否则 watch/computed 初始化时访问 props 会触发 TDZ 引用错误导致组件崩溃
const htmlMode = computed(() => !!props.page.html_content)

/**
 * 微调模式下的 HTML 快照：编辑期间冻结 srcdoc，避免自动保存更新 html_content 导致
 * iframe 重载、清空 undo 历史栈与丢失正在进行的调整；退出微调或切换页面后再同步最新内容。
 */
const editSourceHtml = ref(props.page.html_content || '')
let isInternalSaving = false
let saveResetTimer: any = null

watch(
  () => props.page.html_content,
  (v) => {
    if (isInternalSaving || props.editMode) {
      // 内部微调保存引起的变更或微调模式下，不触发 iframe srcdoc 刷新，保护历史记录
      return
    }
    editSourceHtml.value = v || ''
  },
)
watch(
  () => props.editMode,
  (on, old) => {
    if (!on && old) {
      // 退出微调模式时同步最新内容
      editSourceHtml.value = props.page.html_content || ''
    }
    sendEditMode()
  },
)
// 切换页面 / 强制重新生成后重置
watch(
  () => props.page?.id,
  () => {
    fitSent.value = false
    editSourceHtml.value = props.page.html_content || ''
  },
)
watch(
  () => props.page?.html_content,
  () => {
    if (!isInternalSaving && !props.editMode) {
      fitSent.value = false
    }
  },
)

/** 向 AI 生成的 HTML 注入运行时：跨页跳转 + 防溢出兜底 + 微调编辑器 */
const navRuntimeHtml = computed(() => injectNavRuntime(editSourceHtml.value, !!props.interactive))

const htmlFrameRef = ref<HTMLIFrameElement | null>(null)
/** 整页 HTML 的实际内容高度（由注入脚本测量后回传）；未上报时回退画布高度 */
const iframeH = ref(0)
/** 高度兜底是否已下发（防止 wf-fit 后的二次回传触发重复缩放） */
const fitSent = ref(false)
/** 当前选中元素的图层 id，属性修改时带回 iframe，避免选中态丢失后改了没反应 */
const focusedLayerUid = ref('')

function sendEditMode() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-edit', on: !!props.editMode }, '*')
}

function sendInteractiveMode() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-interactive', on: !!props.interactive }, '*')
}

/** 用户刚改过画板尺寸时，短时间内不要再把内容缩回旧比例，否则看起来像没变化 */
let suppressFitUntil = 0

function pushFrameSize() {
  const w = props.page.canvas_width || 375
  const h = props.page.canvas_height || 812
  suppressFitUntil = Date.now() + 800
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-frame-size', width: w, height: h }, '*')
}

function sendProtoHotspot() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-proto-hotspot', on: !!props.protoHotspot }, '*')
}

function stampElementNav(uid: string, pageName: string | null) {
  if (!uid) return
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-set-nav', uid, page: pageName || '' }, '*')
}

function onFrameLoad() {
  sendEditMode()
  sendInteractiveMode()
  sendProtoHotspot()
  pushFrameSize()
}

watch(
  () => [props.page.canvas_width, props.page.canvas_height] as const,
  () => {
    fitSent.value = false
    pushFrameSize()
  },
)

watch(
  () => props.interactive,
  () => {
    sendInteractiveMode()
  },
)

watch(
  () => props.protoHotspot,
  () => {
    sendProtoHotspot()
  },
)

function injectNavRuntime(html: string, initialInteractive = false): string {
  // 清理可能已残留的旧版本注入运行时（确保始终采用最新微调系统与通信逻辑）
  const cleanHtml = html
    .replace(/<style\b[^>]*\bdata-wf-inject[^>]*>[\s\S]*?<\/style>/gi, '')
    .replace(/<script\b[^>]*\bdata-wf-inject[^>]*>[\s\S]*?<\/script>/gi, '')
  // 防溢出兜底：AI 常把字号写得过大（按 1080px 设计稿而非 375px 容器标定），
  // 导致一行折成两行、横向按钮组挤成竖排、元素相互重叠。
  // 样式层：禁止按钮/tab 折行；脚本层：检测到横向溢出时先逐步收敛最大字号，仍溢出则整体等比微缩。
  // 样式层兜底（非破坏性）：按钮/tab 不折行、无宽高 svg 矫正、导航项 flex 居中。
  // 注意：不要在这里做任何"检测溢出就整体 scale"的逻辑——装饰元素（烟花/光效）天然会稍微
  // 出界几个像素，触发缩放后整页缩小但布局占位不变，右侧和下侧会留出大片空白。
  // 轻微出界让浏览器自然裁掉即可，远比整页缩小好。
  const guard = `<style data-wf-inject>
    *{box-sizing:border-box}
    /* 防横向溢出：AI 生成的整页偶尔会把根容器/区块写得比 375 宽，
       手机壳 overflow:hidden 会直接裁掉右侧。这里把每个元素宽度锁在视口内，
       彻底消除"右侧显示不全"；轻微出界的装饰元素由 body overflow-x:hidden 自然裁掉。 */
    html,body{width:100%;overflow-x:hidden}
    .wf-layer-hidden{visibility:hidden !important;pointer-events:none !important}
    .wf-layer-locked{pointer-events:none !important}
    *{max-width:100%}
    /* 文字渲染最优化：无论 AI 生成字号多少，统一保持清晰锐利的文字渲染，
       解决 iframe 内 scale 缩放后文字发虚/模糊的问题 */
    html{-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale;text-rendering:optimizeLegibility}
    img,svg{max-width:100%}
    button,[data-nav],a,nav,.tab,.tabs,[class*="btn"],[class*="button"],[class*="tab"]{white-space:nowrap}
    /* 弹窗/对话框说明文字：仅允许段落内自然换行、行高舒适，不做任何宽度覆盖
       （width:auto/max-width 会误伤全屏遮罩层导致弹窗整体消失，故不在此处理；弹窗宽度由提示词约束）。 */
    [class*="modal"] p,[class*="dialog"] p,[class*="popup"] p,[class*="toast"] p,[class*="sheet"] p{
      white-space:normal !important; line-height:1.6;
    }
    /* 没写宽高的 svg 浏览器默认 300x150，按周围文字大小渲染 */
    svg:not([width]):not([height]):not([style*="width"]){width:1em;height:1em}
    button svg,[data-nav] svg,nav a svg,.tab svg,[class*="btn"] svg,[class*="button"] svg{max-width:1.6em;max-height:1.6em}
    /* 导航栏/选项卡本身作为横向 flex 容器排布各项（底部标签栏本就该左右排列） */
    nav, [class*="navbar"], [class*="tabbar"], [class*="nav"], [class*="tab"]{
      display:flex; align-items:center;
    }
    /* 导航项：图标+文字必须包进同一个 flex 容器并垂直居中；方向(上下/左右)由模型按设计稿用
       flex-direction 指定（此处不强制方向，避免把上下结构压成左右）。仅做居中兜底，防对不齐。 */
    nav a, nav button, [class*="navbar"] a, [class*="navbar"] button,
    [class*="tabbar"] a, [class*="tabbar"] button,
    [class*="tab"], [class*="nav-item"], [class*="menu-item"]{
      display:flex; align-items:center; justify-content:center; gap:6px;
    }
    /* 仅在真机原型预览交互模式下，按钮与控件具有真机按压弹性触感 (Active Feedback) */
    body.wf-interactive .wf-btn:active,
    body.wf-interactive .wf-act:active,
    body.wf-interactive .wf-sw:active,
    body.wf-interactive .wf-ck:active,
    body.wf-interactive .wf-pill:active,
    body.wf-interactive .wf-tabit:active {
      transform: scale(0.96) !important;
      transition: transform 0.06s cubic-bezier(0.4, 0, 0.2, 1) !important;
    }
    /* 业务说明联动发光呼吸框 */
    .wf-spotlight-target {
      outline: 2.5px solid #0d99ff !important;
      outline-offset: 2px !important;
      border-radius: 8px !important;
      animation: wfSpotlightPulse 1.6s ease-in-out infinite !important;
      z-index: 9999 !important;
    }
    @keyframes wfSpotlightPulse {
      0%, 100% {
        box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.75), 0 0 10px rgba(16, 185, 129, 0.5) !important;
      }
      50% {
        box-shadow: 0 0 0 6px rgba(16, 185, 129, 0), 0 0 22px rgba(16, 185, 129, 0.85) !important;
      }
    }

    /* 原生质感弹层遮罩与动画 */
    .wf-modal {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(10px);
      -webkit-backdrop-filter: blur(10px);
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.25s cubic-bezier(0.32, 0.72, 0, 1);
      z-index: 99999;
    }
    .wf-modal.wf-show {
      opacity: 1;
      pointer-events: auto;
    }
    .wf-multi-selected {
      outline: 1px solid #0D99FF !important;
      outline-offset: 0 !important;
    }
    #wf-marquee-box {
      position: absolute;
      border: 1.5px solid #0D99FF;
      background: rgba(13, 153, 255, 0.15);
      pointer-events: none;
      z-index: 10000;
      display: none;
      border-radius: 2px;
    }
  </style>`
  const runtime = `<script data-wf-inject>(function(){
    var isInteractive = ${initialInteractive ? 'true' : 'false'};
    if (isInteractive) {
      document.body.classList.add('wf-interactive');
    }
    window.addEventListener('message', function(ev){
      if(!ev.data) return;
      if(ev.data.type === 'wf-interactive'){
        isInteractive = !!ev.data.on;
        if(isInteractive) document.body.classList.add('wf-interactive');
        else document.body.classList.remove('wf-interactive');
      }

      if(ev.data.type === 'wf-spotlight'){
        var prev = document.querySelectorAll('.wf-spotlight-target');
        for (var i = 0; i < prev.length; i++) prev[i].classList.remove('wf-spotlight-target');

        var info = ev.data;
        if (!info || !info.active) return;

        var candidates = document.querySelectorAll('.wf-el, .wf-btn, .wf-ic, .wf-act, .wf-sw, .wf-ck, .wf-t, [data-nav], .wf-tabit, .wf-card');
        var bodyRect = document.body.getBoundingClientRect();
        var bestCandidate = null;
        var highestScore = -999999;

        var targetCenterX = (info.x != null && info.w != null) ? (info.x + info.w / 2) : info.x;
        var targetCenterY = (info.y != null && info.h != null) ? (info.y + info.h / 2) : info.y;
        var cleanLabel = (info.label || '').trim();
        var reqType = (info.elementType || '').toLowerCase();

        for (var k = 0; k < candidates.length; k++) {
          var c = candidates[k];
          var cbRect = c.getBoundingClientRect();
          var w = cbRect.width;
          var h = cbRect.height;
          if (w <= 0 || h <= 0 || (w >= 360 && h >= 700)) continue;

          var cLeft = cbRect.left - bodyRect.left;
          var cTop = cbRect.top - bodyRect.top;
          var cx = cLeft + w / 2;
          var cy = cTop + h / 2;

          var score = 0;

          // 1. 类型判定 (核心防串位：文本绝不误指到图标，图标绝不误指到文本)
          var cCls = c.className || '';
          var isTextEl = cCls.indexOf('wf-t') !== -1;
          var isIconEl = cCls.indexOf('wf-ic') !== -1 || cCls.indexOf('wf-tabic') !== -1;
          var isBtnEl = cCls.indexOf('wf-btn') !== -1 || cCls.indexOf('wf-act') !== -1;
          var isCardEl = cCls.indexOf('wf-card') !== -1;
          var isTabEl = cCls.indexOf('wf-tab') !== -1;

          if (reqType === 'text') {
            if (isTextEl) score += 700;
            else if (isIconEl) score -= 1000;
          } else if (reqType === 'icon') {
            if (isIconEl) score += 700;
            else if (isTextEl) score -= 1000;
          } else if (reqType === 'button') {
            if (isBtnEl) score += 600;
          } else if (reqType === 'card') {
            if (isCardEl) score += 500;
          } else if (reqType === 'tabs' || reqType === 'tab') {
            if (isTabEl) score += 600;
          }

          // 2. 文案匹配
          var cText = (c.innerText || c.textContent || '').trim();
          if (cleanLabel) {
            if (cText === cleanLabel) {
              score += 500;
            } else if (cText && (cText.indexOf(cleanLabel) !== -1 || cleanLabel.indexOf(cText) !== -1)) {
              score += 250;
            }
          }

          // 3. 导航属性匹配
          var cNav = c.getAttribute('data-nav') || '';
          if (info.targetPageName && cNav) {
            if (cNav === info.targetPageName) {
              score += (reqType === 'text' && !isTextEl) ? 50 : 400;
            } else if (cNav.indexOf(info.targetPageName) !== -1 || info.targetPageName.indexOf(cNav) !== -1) {
              score += (reqType === 'text' && !isTextEl) ? 20 : 200;
            }
          }
          if (cleanLabel && cNav && cNav === cleanLabel) {
            score += (reqType === 'text' && !isTextEl) ? 50 : 300;
          }

          // 4. 坐标中心距离衰减打分
          if (targetCenterX != null && targetCenterY != null) {
            var d = Math.hypot(cx - targetCenterX, cy - targetCenterY);
            if (d < 25) score += 450;
            else if (d < 50) score += 300;
            else if (d < 90) score += 150;
            else if (d < 160) score += 50;
            else score -= (d - 160) * 2.5;
          }

          // 5. 尺寸相似度加成
          if (info.w != null && info.h != null) {
            var dw = Math.abs(w - info.w);
            var dh = Math.abs(h - info.h);
            if (dw < 15 && dh < 15) score += 150;
          }

          if (score > highestScore) {
            highestScore = score;
            bestCandidate = c;
          }
        }

        if (bestCandidate && highestScore > 0) {
          bestCandidate.classList.add('wf-spotlight-target');
          var bRect = bestCandidate.getBoundingClientRect();
          var finalLeft = Math.round(bRect.left - bodyRect.left);
          var finalTop = Math.round(bRect.top - bodyRect.top);
          var finalW = Math.round(bRect.width);
          var finalH = Math.round(bRect.height);
          parent.postMessage({
            type: 'wf-spotlight-rect',
            rect: { x: finalLeft, y: finalTop, width: finalW, height: finalH },
            elementId: info.elementId,
            pageId: info.pageId
          }, '*');
        }
      }
    });

    function openModal(id){var m=document.getElementById('wf-modal-'+id);if(m)m.classList.add('wf-show');}
    function closeModal(m){if(m)m.classList.remove('wf-show');}
    function collectUids(node){
      var uids = [];
      var n = node;
      var guard = 0;
      while(n && n !== document.body && n !== document.documentElement && guard < 16){
        guard++;
        if(n.getAttribute){
          var uid = n.getAttribute('data-wf-uid');
          if(uid) uids.push(uid);
        }
        n = n.parentElement;
      }
      return uids;
    }



    document.addEventListener('click',function(e){
      // 严格动静分离：若不在真机预览交互模式下，完全忽略所有点击交互，彻底保护画布微调不受干扰
      if(!isInteractive) return;

      var t=e.target;

      // 1. 开关组件 (Switch) 点击切换交互
      var sw = t && t.closest ? t.closest('.wf-sw') : null;
      if (sw) {
        e.preventDefault(); e.stopPropagation();
        var knob = sw.querySelector('.wf-sw-k');
        var bgStr = sw.style.background || '';
        var isOff = sw.getAttribute('data-state') === 'off'
          || (knob && knob.style.left === '2px')
          || bgStr.indexOf('189') !== -1
          || bgStr.indexOf('E5E7EB') !== -1
          || bgStr.indexOf('bdbdbd') !== -1
          || bgStr.indexOf('229, 231, 235') !== -1;
        if (isOff) {
          sw.setAttribute('data-state', 'on');
          sw.style.background = 'linear-gradient(180deg, #FF8336 0%, #FA6B19 100%)';
          sw.style.boxShadow = 'inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28)';
          if (knob) { knob.style.left = 'auto'; knob.style.right = '2px'; }
        } else {
          sw.setAttribute('data-state', 'off');
          sw.style.background = 'linear-gradient(180deg, #E5E7EB 0%, #D1D5DB 100%)';
          sw.style.boxShadow = 'inset 0 1px 3px rgba(0,0,0,0.15), 0 1px 3px rgba(0,0,0,0.06)';
          if (knob) { knob.style.right = 'auto'; knob.style.left = '2px'; }
        }
        return;
      }

      // 2. 复选勾选框 (Checkbox) 点击切换交互
      var ck = t && t.closest ? t.closest('.wf-ck') : null;
      if (ck) {
        e.preventDefault(); e.stopPropagation();
        var isChecked = ck.classList.contains('checked');
        var box = ck.querySelector('.wf-ck-box');
        if (isChecked) {
          ck.classList.remove('checked');
          if (box) {
            box.style.background = '#FFFFFF';
            box.style.borderColor = 'rgba(0,0,0,0.30)';
            box.innerHTML = '';
          }
        } else {
          ck.classList.add('checked');
          if (box) {
            box.style.background = '#ff7043';
            box.style.borderColor = '#ff7043';
            box.innerHTML = '<svg viewBox="0 0 16 16" width="10" height="10" fill="none" stroke="#FFF" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 4L6 11L3 8"/></svg>';
          }
        }
        return;
      }

      // 3. 选项卡 (Tabs) 点击切换交互（支持胶囊分段器与下划线 Tab）
      var segItem = t && t.closest ? t.closest('.wf-segs span, .wf-tab-underline span, .wf-tabs [data-tab]') : null;
      if (segItem) {
        var segNav = segItem.getAttribute('data-nav') || (segItem.closest('.wf-tabs') ? segItem.closest('.wf-tabs').getAttribute('data-nav') : null);
        if (segNav) {
          e.preventDefault(); e.stopPropagation();
          parent.postMessage({type:'wf-nav',page:segNav},'*');
          return;
        }
        e.preventDefault(); e.stopPropagation();
        var pBox = segItem.parentNode;
        var siblings = pBox.querySelectorAll('span, [data-tab]');
        for (var si = 0; si < siblings.length; si++) {
          siblings[si].classList.remove('on');
          if (siblings[si].style.borderBottom) {
            siblings[si].style.borderBottom = 'none';
            siblings[si].style.color = '#6B7280';
          }
        }
        segItem.classList.add('on');
        if (pBox.classList.contains('wf-tab-underline') || segItem.classList.contains('wf-tab-item')) {
          segItem.style.borderBottom = '2.5px solid #F85A18';
          segItem.style.color = '#F85A18';
        }
        return;
      }

      // 4. 通用导航与弹窗交互
      if (t && t.classList && t.classList.contains('wf-modal')) {
        e.preventDefault(); e.stopPropagation();
        closeModal(t);
        return;
      }
      var el=t&&t.closest?t.closest('[data-nav],[data-modal],[data-action],.wf-modal-dismiss'):null;
      if(!el){
        if(isInteractive){
          var hitX = Math.round((e.clientX || 0) + (window.scrollX || 0));
          var hitY = Math.round((e.clientY || 0) + (window.scrollY || 0));
          parent.postMessage({type:'wf-miss-click', x: hitX, y: hitY, uids: collectUids(t)},'*');
        }
        return;
      }
      e.preventDefault();e.stopPropagation();
      if(el.classList.contains('wf-modal-dismiss') || el.getAttribute('data-action') === 'close'){
        var m=el.closest('.wf-modal');
        if(m) { closeModal(m); return; }
      }
      if(el.hasAttribute('data-modal')){openModal(el.getAttribute('data-modal'));return;}
      var a=el.getAttribute('data-action')||'';
      if(a==='back'||a==='close'){
        var inModal=el.closest('.wf-modal');
        if(inModal){closeModal(inModal);return;}
        var prev = document.querySelectorAll('.wf-spotlight-target');
        for (var pi = 0; pi < prev.length; pi++) prev[pi].classList.remove('wf-spotlight-target');
        parent.postMessage({type:'wf-back'},'*');return;
      }
      if(a==='tab'){
        var segs=el.querySelectorAll('.wf-segs span');
        if(segs.length){var cur=el.querySelector('.wf-segs span.on')||segs[0];
          var idx=Array.prototype.indexOf.call(segs,cur);
          for(var i=0;i<segs.length;i++)segs[i].classList.remove('on');
          segs[(idx+1)%segs.length].classList.add('on');}
        return;
      }
      var nav=el.getAttribute('data-nav');
      if(nav){
        var prev2 = document.querySelectorAll('.wf-spotlight-target');
        for (var pi2 = 0; pi2 < prev2.length; pi2++) prev2[pi2].classList.remove('wf-spotlight-target');
        parent.postMessage({type:'wf-nav',page:nav, uids: collectUids(t)},'*');
      }
    },true);
  })();<\/script>`
  // 内容高度上报：等防溢出 fit 跑完后把整页实际高度发给父级，让 iframe/手机壳随内容自适应（避免固定高度裁剪产生滚动）
  const sizer = `<script data-wf-inject>
  (function(){
    function report(){
      try{
        var h=Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)||0;
        if(h>0)parent.postMessage({type:'wf-size',h:h},'*');
      }catch(e){}
    }
    if(document.readyState==='complete'){setTimeout(report,150);}
    else{window.addEventListener('load',function(){setTimeout(report,150);});}
    window.addEventListener('resize',report);
  })();
  <\/script>`
  // 微调编辑器：选中与8点缩放控制盒、双击/点按钮编辑文字、替换图片、Del/Backspace删除、Ctrl+Z撤回、Ctrl+Y重做、方向键微调、拖动移动、自动落库
  const editor = `<style data-wf-inject>
    /* 彻底杜绝浏览器原生图片拖出副本与文字选区拖蓝 */
    body.wf-edit-mode img,
    body.wf-edit-mode a,
    body.wf-edit-mode button,
    body.wf-edit-mode div,
    body.wf-edit-mode span,
    body.wf-edit-mode p,
    body.wf-edit-mode h1,
    body.wf-edit-mode h2,
    body.wf-edit-mode h3 {
      -webkit-user-drag: none !important;
      user-drag: none !important;
    }
    body.wf-edit-mode,
    body.wf-edit-mode * {
      -webkit-user-select: none;
      user-select: none;
    }
    body.wf-edit-mode [data-wf-editing-text="true"],
    body.wf-edit-mode [contenteditable="true"],
    body.wf-edit-mode input,
    body.wf-edit-mode textarea {
      -webkit-user-select: text !important;
      user-select: text !important;
    }

    #wf-transform-box {
      position: absolute;
      display: none;
      z-index: 999999;
      pointer-events: none;
      border: 1px solid #0D99FF;
      box-sizing: border-box;
      border-radius: 0;
    }
    .wf-handle {
      width: 4px;
      height: 4px;
      background: #ffffff;
      border: 1px solid #0D99FF;
      border-radius: 0;
      position: absolute;
      pointer-events: auto;
      box-sizing: border-box;
      z-index: 1000000;
    }
    .wf-handle::after {
      content: '';
      position: absolute;
      inset: -5px;
    }
    .wf-handle:hover {
      background: #0D99FF;
    }
    #wf-dim-badge {
      position: absolute;
      background: #0D99FF;
      color: #ffffff;
      font-size: 10px;
      font-weight: 700;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      padding: 2px 7px;
      border-radius: 4px;
      pointer-events: none;
      white-space: nowrap;
      box-shadow: 0 2px 6px rgba(13,153,255,0.35);
      line-height: 1.2;
      z-index: 1000000;
  </style>
  <script data-wf-inject>
  (function(){
    var EDIT = ${initialInteractive ? 'false' : 'true'}, hovered = null, touched = [], st = null, PROTO_HOTSPOT = false;
    var PAGE_ID = ${JSON.stringify(props.page?.id ?? 0)};
    var selectedEl = null;
    var selectedEls = [];
    var resizing = null;
    var drag = null;
    var marquee = null;
    var history = [], hIdx = -1, MAX_HIST = 30;

    function findVectorElement(uid){
      if(!uid) return null;
      var el = document.querySelector('[data-wf-uid="' + uid + '"]');
      if(el) return el;
      return document.getElementById(uid);
    }

    function isSnapChrome(el){
      if(!el || !el.tagName) return true;
      var tag = String(el.tagName || '').toUpperCase();
      if(tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'LINK' || tag === 'META') return true;
      if(el.id === 'wf-transform-box' || el.id === 'wf-marquee-box' || el.id === 'wf-edit-toast') return true;
      return false;
    }

    function snapshotHtml(){
      var parts = [];
      var kids = document.body ? document.body.children : [];
      for(var i = 0; i < kids.length; i++){
        if(isSnapChrome(kids[i])) continue;
        parts.push(kids[i].outerHTML);
      }
      var bodyBg = '';
      var rootBg = '';
      try { bodyBg = document.body.style.background || ''; } catch(e) {}
      try { rootBg = document.documentElement.style.background || ''; } catch(e) {}
      return 'WFBG:' + encodeURIComponent(bodyBg) + '|' + encodeURIComponent(rootBg) + '\\n' + parts.join('');
    }

    function restoreSnapshot(raw){
      var html = raw || '';
      var bodyBg = null;
      var rootBg = '';
      if(html.indexOf('WFBG:') === 0){
        var nl = html.indexOf('\\n');
        var head = nl >= 0 ? html.slice(5, nl) : html.slice(5);
        html = nl >= 0 ? html.slice(nl + 1) : '';
        var bits = head.split('|');
        try { bodyBg = decodeURIComponent(bits[0] || ''); } catch(e) { bodyBg = ''; }
        try { rootBg = decodeURIComponent(bits[1] || ''); } catch(e) { rootBg = ''; }
      }
      var gone = [];
      var kids = document.body.children;
      for(var i = 0; i < kids.length; i++){
        if(!isSnapChrome(kids[i])) gone.push(kids[i]);
      }
      for(var j = 0; j < gone.length; j++){
        if(gone[j].parentNode) gone[j].parentNode.removeChild(gone[j]);
      }
      var wrap = document.createElement('div');
      wrap.innerHTML = html || '';
      var anchor = document.body.firstChild;
      while(wrap.firstChild) document.body.insertBefore(wrap.firstChild, anchor);
      if(bodyBg !== null){
        document.body.style.background = bodyBg;
        document.documentElement.style.background = rootBg || '';
      }
    }

    function pushSnapshot(){
      try{
        var html = snapshotHtml();
        if(hIdx < history.length - 1) history = history.slice(0, hIdx + 1);
        if(hIdx >= 0 && history[hIdx] === html) return;
        history.push(html);
        if(history.length > MAX_HIST) history.shift();
        hIdx = history.length - 1;
      }catch(e){}
    }

    function showToast(msg){
      var t = document.getElementById('wf-edit-toast');
      if(!t){
        t = document.createElement('div');
        t.id = 'wf-edit-toast';
        t.style.cssText = 'position:fixed;top:14px;left:50%;transform:translateX(-50%);background:rgba(15,23,42,0.92);color:#fff;font-size:11px;font-weight:600;padding:6px 14px;border-radius:20px;z-index:9999999;box-shadow:0 4px 14px rgba(0,0,0,0.3);pointer-events:none;transition:opacity 0.2s;opacity:0;font-family:sans-serif;';
        document.body.appendChild(t);
      }
      t.innerText = msg;
      t.style.opacity = '1';
      clearTimeout(t._st);
      t._st = setTimeout(function(){ t.style.opacity = '0'; }, 1800);
    }

    function undo(){
      try{
        var live = snapshotHtml();
        // 最近一次操作后的画面还没入栈。先补上，撤回才落在「这一步之前」，而不是再往前跳一格。
        if(hIdx < 0 || history[hIdx] !== live){
          if(hIdx < history.length - 1) history = history.slice(0, hIdx + 1);
          history.push(live);
          if(history.length > MAX_HIST) history.shift();
          hIdx = history.length - 1;
        }
        if(hIdx <= 0){
          showToast('已是最初状态，无更多可撤回');
          return;
        }
        hIdx--;
        restoreSnapshot(history[hIdx]);
        history[hIdx] = snapshotHtml();
        deselect();
        clearHover();
        scheduleSave();
        publishLayers();
        showToast('已撤回操作 (Ctrl+Z)');
      }catch(e){}
    }

    function redo(){
      if(hIdx < history.length - 1){
        hIdx++;
        restoreSnapshot(history[hIdx]);
        history[hIdx] = snapshotHtml();
        deselect();
        clearHover();
        scheduleSave();
        publishLayers();
        showToast('已重做 (Ctrl+Y)');
      }
    }

    var uidSeq = 0;
    function ensureUid(el){
      if(!el || !el.getAttribute) return '';
      var uid = el.getAttribute('data-wf-uid');
      if(uid) return uid;
      uidSeq++;
      uid = 'wf-' + Date.now().toString(36) + '-' + uidSeq;
      try { el.setAttribute('data-wf-uid', uid); } catch(e){}
      return uid;
    }

    function isLayerLocked(el){
      if(!el || !el.getAttribute) return false;
      if(el.getAttribute('data-wf-locked') === '1') return true;
      return !!(el.closest && el.closest('[data-wf-locked="1"]'));
    }

    function layerKind(el){
      if(!el || !el.tagName) return 'rect';
      var tag = String(el.tagName || '').toUpperCase();
      var cls = '';
      try { cls = typeof el.className === 'string' ? el.className : ''; } catch(e) { cls = ''; }
      if(tag === 'IMG' || cls.indexOf('wf-avatar') >= 0) return 'image';
      if(tag === 'SVG' || cls.indexOf('wf-vector') >= 0 || (el.getAttribute && el.getAttribute('data-wf-vector'))) return 'vector';
      if(cls.indexOf('wf-shape-circle') >= 0 || cls.indexOf('wf-shape-ellipse') >= 0) return 'ellipse';
      if(cls.indexOf('wf-shape-line') >= 0) return 'line';
      if(tag === 'BUTTON' || cls.indexOf('wf-btn') >= 0) return 'button';
      if(tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return 'input';
      if(/^(H[1-6]|P|SPAN|A|LABEL|B|STRONG|EM|SMALL)$/.test(tag) || cls.indexOf('wf-text') >= 0) return 'text';
      var kids = el.children ? el.children.length : 0;
      if(kids === 1 && el.children[0].tagName === 'IMG') return 'image';
      if(kids === 1 && el.children[0].tagName === 'SVG') return 'vector';
      try {
        var cs = window.getComputedStyle(el);
        var br = cs.borderRadius || '';
        if(br.indexOf('50%') === 0 || br === '9999px' || br === '999px') return 'ellipse';
        var h = el.offsetHeight || parseFloat(cs.height) || 0;
        var w = el.offsetWidth || parseFloat(cs.width) || 0;
        if(h > 0 && h <= 3 && w >= 16) return 'line';
      } catch(e) {}
      if(kids === 0 && String(el.innerText || '').trim()) return 'text';
      if(kids > 0) return 'frame';
      return 'rect';
    }

    function layerShortName(el){
      try{
        var txt = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
        if(txt) return txt.slice(0, 12);
      }catch(e){}
      if(el.classList && el.classList.length){
        for(var i = 0; i < el.classList.length; i++){
          var c = el.classList[i];
          if(c && c.indexOf('wf-') !== 0) return c;
        }
        if(el.classList[0]) return el.classList[0];
      }
      return (el.tagName || 'el').toLowerCase();
    }

    function isChromeNode(el){
      if(!el || !el.tagName) return true;
      var tag = String(el.tagName || '').toUpperCase();
      if(tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'LINK' || tag === 'META') return true;
      if(el.id === 'wf-transform-box' || el.id === 'wf-marquee-box' || el.id === 'wf-edit-toast') return true;
      return false;
    }

    function listableChild(el){
      if(isChromeNode(el)) return false;
      if(el.getAttribute && (el.getAttribute('data-wf-group') === '1' || el.getAttribute('data-wf-uid'))) return true;
      var tag = String(el.tagName || '').toUpperCase();
      if(/^(IMG|SVG|BUTTON|INPUT|TEXTAREA|SELECT|H1|H2|H3|H4|H5|H6|P)$/.test(tag)) return true;
      var cls = '';
      try { cls = typeof el.className === 'string' ? el.className : ''; } catch(e) { cls = ''; }
      if(cls.indexOf('wf-') >= 0) return true;
      var w = el.offsetWidth || 0;
      var h = el.offsetHeight || 0;
      return w >= 12 && h >= 8;
    }

    function layerDisplayName(el, kind){
      var named = '';
      try { named = el.getAttribute && el.getAttribute('data-wf-name') || ''; } catch(e) { named = ''; }
      if(named) return String(named).replace(/\s+/g, ' ').trim().slice(0, 24);
      if(kind === 'group') return '分组';
      return layerShortName(el);
    }

    function serializeLayer(el, depth){
      var uid = ensureUid(el);
      var isGroup = !!(el.getAttribute && el.getAttribute('data-wf-group') === '1');
      var kind = isGroup ? 'group' : layerKind(el);
      var children = [];
      // 只有用户主动建的分组才展开子图层。方框、卡片内部的文字和图片仍算这一层自己，不当成组。
      if(isGroup && depth < 8 && el.children){
        for(var i = el.children.length - 1; i >= 0; i--){
          var c = el.children[i];
          if(isChromeNode(c)) continue;
          var cTag = String(c.tagName || '').toUpperCase();
          if(cTag === 'BR') continue;
          children.push(serializeLayer(c, depth + 1));
        }
      }
      return {
        uid: uid,
        name: layerDisplayName(el, kind),
        kind: kind,
        hidden: el.getAttribute('data-wf-hidden') === '1' || (el.classList && el.classList.contains('wf-layer-hidden')),
        locked: el.getAttribute('data-wf-locked') === '1',
        children: children
      };
    }

    function publishLayers(){
      try{
        var layers = [];
        var kids = document.body ? document.body.children : [];
        for(var i = kids.length - 1; i >= 0; i--){
          var el = kids[i];
          if(!listableChild(el)) continue;
          layers.push(serializeLayer(el, 0));
        }
        parent.postMessage({ type: 'wf-layers', layers: layers }, '*');
      }catch(e){}
    }

    var copiedElement = null;
    var pasteCount = 0;

    function copyElement(el){
      if(!el || el === document.body || el === document.documentElement) return;
      try{
        var clone = el.cloneNode(true);
        clone.style.outline = '';
        clone.style.outlineOffset = '';
        if(clone.id && clone.id.indexOf('wf-') === 0 && !clone.id.startsWith('wf-modal-')){
          clone.removeAttribute('id');
        }
        var rect = el.getBoundingClientRect();
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
        var curL = parseFloat(el.style.left);
        var curT = parseFloat(el.style.top);
        if(isNaN(curL)) curL = rect.left + scrollX;
        if(isNaN(curT)) curT = rect.top + scrollY;

        var elName = (el.innerText || el.getAttribute('data-name') || el.className || '元素').replace(/\s+/g, ' ').trim().slice(0, 16) || '组件';

        var data = {
          html: clone.outerHTML,
          left: Math.round(curL),
          top: Math.round(curT),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
          name: elName
        };

        copiedElement = data;
        pasteCount = 0;

        try {
          if (window.parent) {
            window.parent.__wfCopiedElement = data;
            window.parent.__wfLastCopyType = 'element';
            window.parent.postMessage({ type: 'wf-element-copied', data: data }, '*');
          }
        } catch(e){}

        try {
          localStorage.setItem('wf_clipboard_element', JSON.stringify(data));
          localStorage.setItem('wf_last_copy_type', 'element');
        } catch(e){}

        try {
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(clone.outerHTML).catch(function(){});
          }
        } catch(e){}

        showToast('已复制「' + elName + '」(Ctrl+C)，按 Ctrl+V 粘贴');
      }catch(err){
        console.error('Copy failed', err);
      }
    }

    function getCopiedData(){
      if(copiedElement) return copiedElement;
      try{
        if(window.parent && window.parent.__wfCopiedElement) return window.parent.__wfCopiedElement;
      }catch(e){}
      try{
        var local = localStorage.getItem('wf_clipboard_element');
        if(local) return JSON.parse(local);
      }catch(e){}
      return null;
    }

    function pasteElement(targetX, targetY){
      var data = getCopiedData();
      if(!data || !data.html){
        showToast('剪贴板为空，请先按 Ctrl+C 复制元素');
        return;
      }

      pushSnapshot();
      pasteCount++;
      var offset = pasteCount * 16;

      var temp = document.createElement('div');
      temp.innerHTML = data.html.trim();
      var newEl = temp.firstElementChild || temp;

      if(newEl.id && !newEl.id.startsWith('wf-modal-')){
        newEl.id = newEl.id + '-copy-' + Date.now();
      }

      var posX, posY;
      if(typeof targetX === 'number' && !isNaN(targetX)){
        posX = targetX;
        posY = (typeof targetY === 'number' && !isNaN(targetY)) ? targetY : 120;
      } else if(selectedEl && selectedEl !== document.body && selectedEl.isConnected){
        var curL = parseFloat(selectedEl.style.left);
        var curT = parseFloat(selectedEl.style.top);
        if(isNaN(curL)) curL = selectedEl.getBoundingClientRect().left;
        if(isNaN(curT)) curT = selectedEl.getBoundingClientRect().top;
        posX = Math.round(curL + 16);
        posY = Math.round(curT + 16);
      } else {
        posX = Math.round((data.left != null ? data.left : 20) + offset);
        posY = Math.round((data.top != null ? data.top : 120) + offset);
      }

      posX = Math.max(8, Math.min(330, posX));
      posY = Math.max(20, Math.min(1600, posY));

      var isModal = newEl.classList && (newEl.classList.contains('wf-modal') || newEl.classList.contains('wf-bottom-sheet'));
      if(!isModal){
        newEl.classList.add('wf-el', 'wf-inserted-component');
        newEl.style.position = 'absolute';
        newEl.style.left = posX + 'px';
        newEl.style.top = posY + 'px';
        newEl.style.zIndex = '999';
        if(!newEl.style.maxWidth && !newEl.style.width){
          newEl.style.maxWidth = '335px';
        }
      } else {
        newEl.style.zIndex = '9999';
      }

      document.body.appendChild(newEl);
      ensureUid(newEl);

      EDIT = true;
      document.body.style.cursor = 'default';
      selectElement(newEl);
      publishLayers();
      scheduleSave();
      showToast('已粘贴「' + (data.name || '元素') + '」(Ctrl+V)');
    }

    function duplicateElement(el){
      if(!el || el === document.body || el === document.documentElement) return;
      copyElement(el);
      pasteElement();
      showToast('已创建副本 (Ctrl+D)');
    }

    function outline(el, on){
      if(!el || el === document.body || el === document.documentElement) return;
      if(on){
        el.style.outline = '1.5px dashed #0D99FF';
        el.style.outlineOffset = '1px';
        touched.push(el);
      }else{
        el.style.outline = '';
        el.style.outlineOffset = '';
      }
    }
    function clearHover(){
      if(hovered) outline(hovered, false);
      hovered = null;
    }

    function cleanupStyles(){
      document.body.style.cursor = '';
      document.body.classList.remove('wf-edit-mode');
      for(var i = 0; i < touched.length; i++){
        try{ touched[i].style.outline = ''; touched[i].style.outlineOffset = ''; }catch(e){}
      }
      touched = [];
      var curEditing = document.querySelector('[data-wf-editing-text="true"]');
      if(curEditing){
        curEditing.contentEditable = 'false';
        curEditing.removeAttribute('data-wf-editing-text');
        curEditing.style.outline = '';
        curEditing.style.outlineOffset = '';
        curEditing.style.cursor = '';
      }
    }

    function doExport(){
      try{
        var clone = document.documentElement.cloneNode(true);
        var bad = clone.querySelectorAll('[data-wf-inject],#wf-edit-toast,#wf-transform-box,#wf-marquee-box,[data-wf-editing-text]');
        for(var i = 0; i < bad.length; i++) bad[i].parentNode.removeChild(bad[i]);
        var multi = clone.querySelectorAll('.wf-multi-selected');
        for(var mi = 0; mi < multi.length; mi++) multi[mi].classList.remove('wf-multi-selected');
        var targets = clone.querySelectorAll('.wf-current-asset-target');
        for(var ti = 0; ti < targets.length; ti++) targets[ti].classList.remove('wf-current-asset-target');
        var editings = clone.querySelectorAll('[contenteditable]');
        for(var ei = 0; ei < editings.length; ei++) editings[ei].removeAttribute('contenteditable');
        var outlined = clone.querySelectorAll('*');
        for(var oi = 0; oi < outlined.length; oi++){
          if(outlined[oi].style.outline) outlined[oi].style.outline = '';
          if(outlined[oi].style.outlineOffset) outlined[oi].style.outlineOffset = '';
        }
        var hiddenVectors = clone.querySelectorAll('.wf-vector-shape, [data-wf-vector]');
        for(var hvi = 0; hvi < hiddenVectors.length; hvi++){
          if(hiddenVectors[hvi].style.visibility === 'hidden'){
            hiddenVectors[hvi].style.visibility = '';
          }
        }
        parent.postMessage({ type: 'wf-save', html: '<!DOCTYPE html>\\n' + clone.outerHTML }, '*');
      }catch(e){}
    }
    function scheduleSave(){ clearTimeout(st); st = setTimeout(doExport, 500); }

    function getTransformBox(){
      var b = document.getElementById('wf-transform-box');
      if(!b){
        b = document.createElement('div');
        b.id = 'wf-transform-box';
        b.setAttribute('data-wf-inject', 'true');

        var dirs = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
        var cursors = {
          nw: 'nwse-resize', n: 'ns-resize', ne: 'nesw-resize', e: 'ew-resize',
          se: 'nwse-resize', s: 'ns-resize', sw: 'nesw-resize', w: 'ew-resize'
        };
        var positions = {
          nw: 'left:-2px;top:-2px;',
          n:  'left:calc(50% - 2px);top:-2px;',
          ne: 'right:-2px;top:-2px;',
          e:  'right:-2px;top:calc(50% - 2px);',
          se: 'right:-2px;bottom:-2px;',
          s:  'left:calc(50% - 2px);bottom:-2px;',
          sw: 'left:-2px;bottom:-2px;',
          w:  'left:-2px;top:calc(50% - 2px);'
        };

        dirs.forEach(function(dir){
          var h = document.createElement('div');
          h.className = 'wf-handle wf-handle-' + dir;
          h.setAttribute('data-dir', dir);
          h.style.cssText = positions[dir] + 'cursor:' + cursors[dir] + ';';
          b.appendChild(h);
        });

        var dim = document.createElement('div');
        dim.id = 'wf-dim-badge';
        b.appendChild(dim);

        document.body.appendChild(b);
      }
      return b;
    }

    function getMarqueeBox(){
      var mb = document.getElementById('wf-marquee-box');
      if(!mb){
        mb = document.createElement('div');
        mb.id = 'wf-marquee-box';
        mb.setAttribute('data-wf-inject', 'true');
        mb.style.cssText = 'position:absolute;border:1.5px solid #0D99FF;background:rgba(13,153,255,0.15);pointer-events:none;z-index:10000;display:none;border-radius:2px;';
        document.body.appendChild(mb);
      }
      return mb;
    }

    function getSelectableElements(){
      var selector = '.wf-inserted-component, .wf-el, .wf-card, .wf-box, .wf-container, .wf-avatar, .wf-btn, .wf-search-box, .wf-text-block, .wf-shape, .wf-text, button, [role="button"]';
      var list = Array.from(document.querySelectorAll(selector));
      var bodyKids = Array.from(document.body.children);
      for(var bi = 0; bi < bodyKids.length; bi++){
        var bk = bodyKids[bi];
        if(!['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE'].includes(bk.tagName) &&
           bk.id !== 'wf-transform-box' && bk.id !== 'wf-marquee-box' && bk.id !== 'wf-edit-toast' &&
           !list.includes(bk)){
          list.push(bk);
        }
      }
      return list.filter(function(el){
        if(!el || el === document || el === document.body || el === document.documentElement) return false;
        if(el.id === 'wf-transform-box' || el.id === 'wf-marquee-box' || el.id === 'wf-edit-toast' || (el.closest && el.closest('#wf-transform-box,#wf-marquee-box'))) return false;
        var p = el.parentElement ? el.parentElement.closest('.wf-inserted-component, .wf-el, .wf-card, .wf-box, .wf-container, .wf-avatar, .wf-btn, .wf-search-box, .wf-text-block, .wf-shape') : null;
        return !p;
      });
    }

    function rgbToHex(col){
      if(!col || col === 'transparent' || col === 'rgba(0, 0, 0, 0)') return '';
      if(col.indexOf('#') === 0) return col;
      var m = col.match(/\d+/g);
      if(!m || m.length < 3) return '';
      var r = parseInt(m[0], 10).toString(16); if(r.length < 2) r = '0' + r;
      var g = parseInt(m[1], 10).toString(16); if(g.length < 2) g = '0' + g;
      var b = parseInt(m[2], 10).toString(16); if(b.length < 2) b = '0' + b;
      return '#' + r + g + b;
    }

    function applyColor(target, colorVal){
      pushSnapshot();
      var isVector = target && (
        target.tagName.toLowerCase() === 'svg' ||
        (target.classList && target.classList.contains('wf-vector-shape')) ||
        (target.hasAttribute && target.hasAttribute('data-wf-vector'))
      );
      if(isVector){
        var paths = target.tagName.toLowerCase() === 'path' ? [target] : target.querySelectorAll('path');
        for(var pi = 0; pi < paths.length; pi++){
          paths[pi].setAttribute('fill', colorVal);
          paths[pi].style.fill = colorVal;
        }
        var vDataStr = target.getAttribute('data-wf-vector');
        if(vDataStr){
          try{
            var vObj = JSON.parse(vDataStr);
            vObj.fillColor = colorVal;
            target.setAttribute('data-wf-vector', JSON.stringify(vObj));
          }catch(e){}
        }
        target.style.background = 'none';
        target.style.backgroundColor = 'transparent';
        scheduleSave();
        showToast('颜色已修改并保存');
        return;
      }

      var isPureRectOrBox = target.classList && (target.classList.contains('wf-shape-rect') || target.classList.contains('wf-box'));
      var isPureCircle = target.classList && target.classList.contains('wf-shape-circle');
      var isPureLine = target.classList && target.classList.contains('wf-shape-line');

      if(colorVal === 'transparent'){
        target.style.background = 'transparent';
        if(!target.style.borderColor || target.style.borderColor === 'transparent'){
          target.style.borderColor = '#0D99FF';
        }
      } else if(isPureLine){
        target.style.background = colorVal;
        target.style.borderColor = colorVal;
      } else if(isPureRectOrBox || isPureCircle){
        target.style.background = colorVal;
        target.style.borderColor = colorVal;
      } else {
        var bg = window.getComputedStyle(target).backgroundColor;
        var hasBg = bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)';
        var isContainerLike = target.classList && (
          target.classList.contains('wf-btn') ||
          target.classList.contains('wf-card') ||
          target.classList.contains('wf-container') ||
          target.classList.contains('wf-search-box') ||
          target.classList.contains('wf-inserted-component')
        );
        if(hasBg || isContainerLike || target.tagName === 'BUTTON'){
          target.style.background = colorVal;
        } else {
          target.style.color = colorVal;
          var textSubs = target.querySelectorAll ? target.querySelectorAll('h1,h2,h3,h4,h5,h6,p,span,div,label,button,.wf-text') : [];
          for(var si = 0; si < textSubs.length; si++){
            textSubs[si].style.color = colorVal;
          }
        }
      }
      scheduleSave();
      showToast('颜色已修改并保存');
    }

    function trimNum(n){
      return Math.round(n * 100) / 100;
    }

    function fitVectorElementBox(el){
      if(!el || resizing) return;
      var tag = el.tagName ? String(el.tagName).toLowerCase() : '';
      var isVector = tag === 'svg' || (el.classList && el.classList.contains('wf-vector-shape')) || (el.hasAttribute && el.hasAttribute('data-wf-vector'));
      if(!isVector) return;
      var svg = tag === 'svg' ? el : (el.querySelector ? el.querySelector('svg') : null);
      if(!svg) return;
      var nodes = svg.querySelectorAll('path, polygon, polyline, circle, ellipse, rect, line');
      if(!nodes.length) return;
      var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity, maxStroke = 0;
      for(var i = 0; i < nodes.length; i++){
        var node = nodes[i];
        var bb = null;
        try { bb = node.getBBox(); } catch(err) { bb = null; }
        if(!bb || !isFinite(bb.width) || !isFinite(bb.height)) continue;
        if(bb.x < minX) minX = bb.x;
        if(bb.y < minY) minY = bb.y;
        if(bb.x + bb.width > maxX) maxX = bb.x + bb.width;
        if(bb.y + bb.height > maxY) maxY = bb.y + bb.height;
        var sw = parseFloat(node.getAttribute('stroke-width') || '0');
        if(!isNaN(sw) && sw > maxStroke) maxStroke = sw;
      }
      if(!isFinite(minX) || !isFinite(minY)) return;
      var pad = maxStroke / 2;
      var ux = minX - pad;
      var uy = minY - pad;
      var uw = Math.max(1, (maxX - minX) + pad * 2);
      var uh = Math.max(1, (maxY - minY) + pad * 2);
      var vb = svg.viewBox && svg.viewBox.baseVal;
      var vbX = vb && vb.width ? vb.x : 0;
      var vbY = vb && vb.height ? vb.y : 0;
      var vbW = vb && vb.width ? vb.width : (parseFloat(svg.style.width) || svg.clientWidth || uw);
      var vbH = vb && vb.height ? vb.height : (parseFloat(svg.style.height) || svg.clientHeight || uh);
      if(!vbW || !vbH) return;
      var cssW = parseFloat(svg.style.width) || svg.clientWidth || vbW;
      var cssH = parseFloat(svg.style.height) || svg.clientHeight || vbH;
      var scaleX = cssW / vbW;
      var scaleY = cssH / vbH;
      var left = parseFloat(svg.style.left);
      var top = parseFloat(svg.style.top);
      if(isNaN(left)) left = svg.offsetLeft || 0;
      if(isNaN(top)) top = svg.offsetTop || 0;
      var newLeft = left + (ux - vbX) * scaleX;
      var newTop = top + (uy - vbY) * scaleY;
      var newW = uw * scaleX;
      var newH = uh * scaleY;
      if(Math.abs(newLeft - left) < 1 && Math.abs(newTop - top) < 1 && Math.abs(newW - cssW) < 1 && Math.abs(newH - cssH) < 1) return;
      svg.setAttribute('viewBox', trimNum(ux) + ' ' + trimNum(uy) + ' ' + trimNum(uw) + ' ' + trimNum(uh));
      svg.style.left = trimNum(newLeft) + 'px';
      svg.style.top = trimNum(newTop) + 'px';
      svg.style.width = trimNum(newW) + 'px';
      svg.style.height = trimNum(newH) + 'px';
      svg.style.overflow = 'visible';
      svg.style.maxWidth = 'none';
      var raw = svg.getAttribute('data-wf-vector');
      if(raw){
        try {
          var data = JSON.parse(raw);
          var shiftX = ux - vbX;
          var shiftY = uy - vbY;
          if(data.points && data.points.length){
            for(var p = 0; p < data.points.length; p++){
              data.points[p].x = (Number(data.points[p].x) || 0) - shiftX;
              data.points[p].y = (Number(data.points[p].y) || 0) - shiftY;
            }
          }
          data.origW = trimNum(uw);
          data.origH = trimNum(uh);
          svg.setAttribute('data-wf-vector', JSON.stringify(data));
        } catch(err) {}
      }
      scheduleSave();
    }

    function updateTransformBox(target){
      if(!target || !target.isConnected){
        deselect();
        return;
      }
      fitVectorElementBox(target);
      var box = getTransformBox();
      var rect = target.getBoundingClientRect();
      var scrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;
      var scrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
      var l = rect.left + scrollX;
      var t = rect.top + scrollY;
      var w = rect.width;
      var h = rect.height;

      box.style.left = l + 'px';
      box.style.top = t + 'px';
      box.style.width = Math.max(12, w) + 'px';
      box.style.height = Math.max(12, h) + 'px';
      box.style.display = 'block';

      var dim = document.getElementById('wf-dim-badge');
      if(dim){
        dim.textContent = Math.round(w) + ' × ' + Math.round(h);
        if(t < 38){
          dim.style.bottom = 'auto';
          dim.style.top = '-24px';
        } else {
          dim.style.bottom = '-24px';
          dim.style.top = 'auto';
        }
      }
    }

    function updateMultiTransformBox(){
      if(!selectedEls || selectedEls.length === 0){
        deselect();
        return;
      }
      if(selectedEls.length === 1){
        updateTransformBox(selectedEls[0]);
        return;
      }
      var box = getTransformBox();
      var minL = Infinity, minT = Infinity, maxR = -Infinity, maxB = -Infinity;
      var scrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;
      var scrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;

      for(var i = 0; i < selectedEls.length; i++){
        var el = selectedEls[i];
        if(!el || !el.isConnected) continue;
        if(!resizing) fitVectorElementBox(el);
        var r = el.getBoundingClientRect();
        var l = r.left + scrollX;
        var t = r.top + scrollY;
        var ri = l + r.width;
        var b = t + r.height;
        if(l < minL) minL = l;
        if(t < minT) minT = t;
        if(ri > maxR) maxR = ri;
        if(b > maxB) maxB = b;
      }

      var w = maxR - minL;
      var h = maxB - minT;
      box.style.left = minL + 'px';
      box.style.top = minT + 'px';
      box.style.width = Math.max(12, w) + 'px';
      box.style.height = Math.max(12, h) + 'px';
      box.style.display = 'block';

      var dim = document.getElementById('wf-dim-badge');
      if(dim){
        dim.textContent = '已选中 ' + selectedEls.length + ' 个元素 (' + Math.round(w) + ' × ' + Math.round(h) + ')';
        if(minT < 38){
          dim.style.bottom = 'auto';
          dim.style.top = '-24px';
        } else {
          dim.style.bottom = '-24px';
          dim.style.top = 'auto';
        }
      }
    }

    function setMultiSelection(elements){
      for(var i = 0; i < selectedEls.length; i++){
        try { selectedEls[i].classList.remove('wf-multi-selected'); } catch(e){}
      }
      selectedEls = elements.filter(function(el){ return el && el.isConnected && el !== document.body && el !== document.documentElement; });
      for(var j = 0; j < selectedEls.length; j++){
        ensureUid(selectedEls[j]);
        if(selectedEls.length > 1) selectedEls[j].classList.add('wf-multi-selected');
      }

      if(selectedEls.length === 0){
        deselect();
      } else if(selectedEls.length === 1){
        selectedEl = selectedEls[0];
        updateTransformBox(selectedEl);
        notifySelectedElementInfo(selectedEl);
        publishSelection();
      } else {
        selectedEl = selectedEls[selectedEls.length - 1];
        updateMultiTransformBox();
        notifySelectedElementInfo(selectedEl);
        publishSelection();
      }
      publishLayers();
    }

    function publishSelection(){
      var uids = [];
      for(var i = 0; i < selectedEls.length; i++){
        if(selectedEls[i] && selectedEls[i].isConnected) uids.push(ensureUid(selectedEls[i]));
      }
      try { window.parent.postMessage({ type: 'wf-selection', uids: uids }, '*'); } catch(e) {}
    }

    function toggleMultiSelect(el){
      if(!el || el === document.body || el === document.documentElement) return;
      ensureUid(el);
      var idx = selectedEls.indexOf(el);
      if(idx !== -1){
        el.classList.remove('wf-multi-selected');
        selectedEls.splice(idx, 1);
        if(selectedEls.length === 0){
          deselect();
        } else {
          selectedEl = selectedEls[selectedEls.length - 1];
          if(selectedEls.length === 1){
            selectedEls[0].classList.remove('wf-multi-selected');
            updateTransformBox(selectedEl);
          } else {
            updateMultiTransformBox();
          }
          publishSelection();
        }
      } else {
        selectedEls.push(el);
        selectedEl = el;
        if(selectedEls.length === 1){
          updateTransformBox(selectedEl);
        } else {
          for(var k = 0; k < selectedEls.length; k++) selectedEls[k].classList.add('wf-multi-selected');
          updateMultiTransformBox();
        }
        publishSelection();
      }
      if(selectedEl) notifySelectedElementInfo(selectedEl);
      publishLayers();
    }

    function outermostGroup(node){
      var found = null;
      var cur = node;
      while(cur && cur !== document.body && cur !== document.documentElement){
        if(cur.getAttribute && cur.getAttribute('data-wf-group') === '1') found = cur;
        cur = cur.parentElement;
      }
      return found;
    }

    function pickInsideGroup(node, group){
      if(!node || !group || node === group) return null;
      var leaf = node.closest ? node.closest('button, img, svg, input, textarea, select, h1, h2, h3, h4, h5, h6, p, span, a, label, .wf-shape, .wf-btn, .wf-text, .wf-avatar, .wf-box, .wf-card') : null;
      if(leaf && leaf !== group && group.contains(leaf)) return leaf;
      var walk = node;
      while(walk.parentElement && walk.parentElement !== group) walk = walk.parentElement;
      if(walk && walk !== group && group.contains(walk)) return walk;
      return null;
    }

    function markInsertedAsGroup(el){
      if(!el || !el.setAttribute) return;
      el.classList.add('wf-group', 'wf-el');
      var count = document.querySelectorAll('[data-wf-group="1"]').length + 1;
      el.setAttribute('data-wf-group', '1');
      if(!el.getAttribute('data-wf-name')) el.setAttribute('data-wf-name', '分组 ' + count);
    }

    function resolveTargetElement(t, e){
      if(!t || t === document.body || t === document.documentElement) return null;
      if(t.id === 'wf-transform-box' || (t.closest && t.closest('#wf-transform-box'))) return null;
      if(t.id === 'wf-marquee-box' || (t.closest && t.closest('#wf-marquee-box'))) return null;

      // 分组：单击永远选中整组，方便整组移动。双击才进入组内元素。
      // 已经双击选中了组内某个元素时，再按住它拖动，保持选中这个元素。
      var group = outermostGroup(t);
      if(group){
        if(selectedEl && selectedEl !== group && group.contains(selectedEl) && (selectedEl === t || (selectedEl.contains && selectedEl.contains(t)))){
          return selectedEl;
        }
        return group;
      }

      // 1. 优先查找当前点击是否位于某个顶级插入组件或复合模块内部
      var compRoot = t.closest ? t.closest('.wf-inserted-component, .wf-card, .wf-box, .wf-container, .wf-ios-app-item, .wf-ios-story-card, .wf-ios-avatar-grid, .wf-ios-tab-bar, .wf-modal, .wf-nav, .wf-switch-row, .wf-search-bar') : null;

      // 如果点击的目标位于某个复合组件内：
      if(compRoot && compRoot !== document.body && compRoot !== document.documentElement){
        // 如果当前选中的正是这个复合组件本身（用户二次点击该组件内部）：
        // 允许钻取 (Drill-down) 深入点选内部具体的子控件/文字/图片/按钮
        if(selectedEl === compRoot && compRoot.contains(t) && compRoot !== t){
          var subItem = t.closest ? t.closest('button, img, input, textarea, h1, h2, h3, h4, h5, h6, p, span, a, label, .wf-avatar, .wf-btn, [class*="avatar"], [class*="btn"]') : null;
          if(subItem && compRoot.contains(subItem)) return subItem;
          return t;
        }

        // 如果当前选中的已经是该组件内的某个子元素，且本次点击的也是组件内的子元素：
        if(selectedEl && compRoot.contains(selectedEl) && compRoot !== selectedEl){
          var subItem = t.closest ? t.closest('button, img, input, textarea, h1, h2, h3, h4, h5, h6, p, span, a, label, .wf-avatar, .wf-btn, [class*="avatar"], [class*="btn"]') : null;
          if(subItem && compRoot.contains(subItem)) return subItem;
          return t;
        }

        // 默认（用户首次点击该组件，或点击组件内空白处）：
        // 100% 选中整个组件整体！使用户可以直接拖动、缩放、移动整个组件！
        return compRoot;
      }

      // 2. 如果不是复合组件内部，命中普通独立控件
      var btn = t.closest ? t.closest('button, .wf-btn, [role="button"]') : null;
      if(btn && btn !== document.body) return btn;

      if(t.tagName === 'IMG') return t;
      var img = t.closest ? t.closest('.wf-avatar, [class*="avatar"]') : null;
      if(img && img !== document.body) return img;

      var input = t.closest ? t.closest('input, textarea, select') : null;
      if(input && input !== document.body) return input;

      if(t.tagName && /^(H[1-6]|P|SPAN|A|LABEL|B|STRONG|EM)$/i.test(t.tagName)){
        return t;
      }

      var listItem = t.closest ? t.closest('.wf-ios-app-item') : null;
      if(listItem && listItem !== document.body) return listItem;

      var comp = t.closest ? t.closest('.wf-shape,.wf-box,.wf-container,.wf-search-box,.wf-text-block,.wf-ios-story-card,.wf-ios-avatar-grid,.wf-ios-tab-bar') : null;
      if(comp && comp !== document.body && comp !== document.documentElement) return comp;

      var wrap = t.closest ? t.closest('.wf-inserted-component, .wf-el') : null;
      return wrap || t;
    }

    function notifySelectedElementInfo(el){
      if(!el || el === document.body || el === document.documentElement) return;
      try {
        var cs = window.getComputedStyle(el);
        var rect = el.getBoundingClientRect();
        var isImg = el.tagName === 'IMG' || !!(el.style && el.style.backgroundImage && el.style.backgroundImage.indexOf('url(') !== -1);
        var imgSrc = el.tagName === 'IMG' ? el.src : (el.style.backgroundImage ? el.style.backgroundImage.replace(/^url\(["']?|["']?\)$/g, '') : '');
        var isPureShape = el.classList && (el.classList.contains('wf-shape-rect') || el.classList.contains('wf-shape-circle') || el.classList.contains('wf-shape-line'));
        var tText = findTextTarget(el);
        var hasText = tText !== null && !isPureShape;
        var textContent = '';
        if(hasText && tText){
          textContent = (tText.tagName === 'INPUT' || tText.tagName === 'TEXTAREA') ? tText.value : (tText.innerText || tText.textContent || '').trim();
        }
        var parentCard = el.parentElement ? (el.parentElement.closest ? el.parentElement.closest('.wf-ios-story-card, .wf-ios-avatar-grid, .wf-ios-app-item, .wf-ios-tab-bar, .wf-container, .wf-box, .wf-card, .wf-inserted-component') : null) : null;
        var hasParentContainer = !!(parentCard && parentCard !== el && parentCard !== document.body);

        var isVector = el.tagName.toLowerCase() === 'svg' || (el.classList && el.classList.contains('wf-vector-shape')) || (el.hasAttribute && el.hasAttribute('data-wf-vector'));
        var pathEl = isVector ? (el.tagName.toLowerCase() === 'path' ? el : (el.querySelector ? el.querySelector('path') : null)) : null;
        var bgColor = cs.backgroundColor || 'transparent';
        var bWidth = parseInt(cs.borderTopWidth) || 0;
        var bColor = cs.borderTopColor || '#cbd5e1';
        if(isVector && pathEl){
          var pFill = pathEl.getAttribute('fill') || (window.getComputedStyle(pathEl).fill);
          if(pFill && pFill !== 'none') bgColor = pFill;
          var pStroke = pathEl.getAttribute('stroke') || (window.getComputedStyle(pathEl).stroke);
          if(pStroke && pStroke !== 'none') bColor = pStroke;
          var pStrokeW = parseInt(pathEl.getAttribute('stroke-width')) || parseInt(window.getComputedStyle(pathEl).strokeWidth) || 0;
          bWidth = pStrokeW;
        }

        var clsStr = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');

        window.parent.postMessage({
          type: 'wf-element-selected',
          info: {
            tagName: el.tagName.toLowerCase(),
            className: clsStr,
            x: Math.round(el.offsetLeft || rect.left),
            y: Math.round(el.offsetTop || rect.top),
            width: Math.round(el.offsetWidth || rect.width),
            height: Math.round(el.offsetHeight || rect.height),
            borderRadius: parseInt(cs.borderRadius) || 0,
            borderWidth: bWidth,
            borderColor: bColor,
            borderStyle: cs.borderTopStyle || 'solid',
            boxShadow: cs.boxShadow || 'none',
            effects: el.getAttribute('data-wf-effects') || '',
            inlineShadow: (el.style && el.style.boxShadow) || '',
            inlineFilter: (el.style && el.style.filter) || '',
            backgroundColor: bgColor,
            fontSize: parseInt(cs.fontSize) || 14,
            isImage: isImg,
            imgSrc: imgSrc,
            hasText: hasText,
            textContent: textContent,
            hasParentContainer: hasParentContainer,
            layerUid: ensureUid(el)
          }
        }, '*');
      } catch(e) {}
    }

    function selectElement(el, forceDirect, isMultiToggle){
      if(!el || el === document || el === document.body || el === document.documentElement){
        deselect();
        return;
      }
      if(el.closest && (el.closest('#wf-transform-box') || el.id === 'wf-transform-box')) return;
      if(el.closest && (el.closest('#wf-marquee-box') || el.id === 'wf-marquee-box')) return;

      var targetEl = el;
      if(!forceDirect){
        targetEl = resolveTargetElement(el) || el;
      }
      if(targetEl === document || targetEl === document.body || targetEl === document.documentElement) targetEl = el;
      if(!targetEl || targetEl === document || targetEl === document.body || targetEl === document.documentElement){
        deselect();
        return;
      }

      clearHover();

      if(isMultiToggle){
        toggleMultiSelect(targetEl);
        return;
      }

      ensureUid(targetEl);
      setMultiSelection([targetEl]);
    }
    window.__wf_selectElement = selectElement;
    window.__wf_getSelectedEl = function(){ return selectedEl; };
    window.__wf_getSelectedEls = function(){ return selectedEls; };

    function publishFrameFill(){
      var bg = '#ffffff';
      try {
        var cs = window.getComputedStyle(document.body);
        if(cs && cs.backgroundColor && cs.backgroundColor !== 'rgba(0, 0, 0, 0)') bg = cs.backgroundColor;
      } catch(e) {}
      try { window.parent.postMessage({ type: 'wf-frame-fill', color: bg }, '*'); } catch(e) {}
    }

    function deselect(){
      for(var i = 0; i < selectedEls.length; i++){
        try { selectedEls[i].classList.remove('wf-multi-selected'); } catch(e){}
      }
      selectedEls = [];
      selectedEl = null;
      var b = document.getElementById('wf-transform-box');
      if(b) b.style.display = 'none';
      try {
        window.parent.postMessage({ type: 'wf-element-deselected' }, '*');
        window.parent.postMessage({ type: 'wf-selection', uids: [] }, '*');
        publishFrameFill();
      } catch(e) {}
    }

    function findTextTarget(t){
      if(!t) return null;
      if(t.classList && t.classList.contains('wf-text')) return t;
      if(t.tagName === 'INPUT' || t.tagName === 'TEXTAREA') return t;
      if(/^(H[1-6]|P|SPAN|BUTTON|A|LABEL|B|STRONG|EM|I|SMALL)$/i.test(t.tagName)) return t;
      var sem = t.querySelector ? t.querySelector('.wf-text,h1,h2,h3,h4,h5,h6,p,span,button,a,label,.wf-btn-label') : null;
      if(sem) return sem;
      var box = t.querySelector ? t.querySelector('.wf-box,.wf-container,.wf-text-block,.wf-shape') : null;
      if(box){
        var deep = box.querySelector ? box.querySelector('.wf-text,h1,h2,h3,h4,h5,h6,p,span,button,a,label') : null;
        if(deep) return deep;
        return box;
      }
      return t;
    }

    function startTextEdit(target){
      if(!target) return;
      var t = findTextTarget(target);
      if(!t) return;

      var b = document.getElementById('wf-transform-box');
      if(b) b.style.display = 'none';

      if(t.tagName === 'INPUT' || t.tagName === 'TEXTAREA'){
        t.focus();
        try{ t.select(); }catch(e){}
        function onInputBlur(){
          t.removeEventListener('blur', onInputBlur);
          pushSnapshot();
          scheduleSave();
          if(selectedEl) updateTransformBox(selectedEl);
        }
        t.addEventListener('blur', onInputBlur);
        return;
      }

      t.contentEditable = 'true';
      t.setAttribute('data-wf-editing-text', 'true');
      t.style.outline = '2px dashed #0D99FF';
      t.style.outlineOffset = '2px';
      t.style.cursor = 'text';
      t.focus();

      try{
        var rng = document.createRange();
        rng.selectNodeContents(t);
        var s = window.getSelection();
        s.removeAllRanges();
        s.addRange(rng);
      }catch(err){}

      showToast('正在编辑文案：直接打字，失焦或回车自动保存');

      function commitText(){
        if(t.getAttribute('data-wf-editing-text') !== 'true') return;
        t.contentEditable = 'false';
        t.removeAttribute('data-wf-editing-text');
        t.style.outline = '';
        t.style.outlineOffset = '';
        t.style.cursor = '';
        t.removeEventListener('blur', onTextBlur);
        t.removeEventListener('keydown', onTextKey);
        pushSnapshot();
        scheduleSave();
        if(selectedEl) updateTransformBox(selectedEl);
        showToast('文案已自动修改并保存');
      }

      function onTextBlur(){ commitText(); }
      function onTextKey(ke){
        if(ke.key === 'Enter' && !ke.shiftKey && !['TEXTAREA', 'P'].includes(t.tagName)){
          ke.preventDefault();
          t.blur();
        } else if(ke.key === 'Escape'){
          t.blur();
        }
      }

      t.addEventListener('blur', onTextBlur);
      t.addEventListener('keydown', onTextKey);
    }

    function resolveEditTarget(d){
      if(!d || !d.uid) return selectedEl;
      var byUid = document.querySelector('[data-wf-uid="' + d.uid + '"]');
      if(byUid){
        selectedEl = byUid;
        selectedEls = [byUid];
      }
      return selectedEl;
    }

    function applyFrameSize(width, height){
      var fw = Math.max(50, Number(width) || 375);
      var fh = Math.max(50, Number(height) || 812);
      var body = document.body;
      var de = document.documentElement;
      body.style.transform = 'none';
      body.style.width = fw + 'px';
      body.style.height = fh + 'px';
      body.style.minHeight = fh + 'px';
      de.style.width = fw + 'px';
      de.style.height = fh + 'px';
      var nodes = document.querySelectorAll('.mobile-screen,.page-container,.wireframe-root,.screen');
      for(var i = 0; i < nodes.length; i++){
        nodes[i].style.maxWidth = 'none';
        nodes[i].style.width = '100%';
        nodes[i].style.height = '100%';
        nodes[i].style.minHeight = '100%';
      }
    }

    function boxInParent(el, parent){
      var pr = parent.getBoundingClientRect();
      var r = el.getBoundingClientRect();
      return { el: el, l: r.left - pr.left, t: r.top - pr.top, w: r.width, h: r.height };
    }

    function groupSelection(){
      var raw = selectedEls && selectedEls.length ? selectedEls.slice() : (selectedEl ? [selectedEl] : []);
      var els = [];
      for(var i = 0; i < raw.length; i++){
        var el = raw[i];
        if(!el || !el.parentNode || el === document.body || el === document.documentElement) continue;
        if(isLayerLocked(el)) continue;
        var nested = false;
        for(var j = 0; j < raw.length; j++){
          if(i !== j && raw[j] && raw[j].contains && raw[j] !== el && raw[j].contains(el)) nested = true;
        }
        if(!nested) els.push(el);
      }
      if(els.length < 2){
        showToast('按住 Shift 多选至少两个图层，再按 Ctrl+G 成组');
        return;
      }
      var parent = els[0].parentNode;
      for(var k = 1; k < els.length; k++){
        if(els[k].parentNode !== parent){
          showToast('只能把同一层里的图层编成一组');
          return;
        }
      }
      pushSnapshot();
      var boxes = [];
      var minL = Infinity, minT = Infinity, maxR = -Infinity, maxB = -Infinity;
      for(var n = 0; n < els.length; n++){
        var b = boxInParent(els[n], parent);
        boxes.push(b);
        if(b.l < minL) minL = b.l;
        if(b.t < minT) minT = b.t;
        if(b.l + b.w > maxR) maxR = b.l + b.w;
        if(b.t + b.h > maxB) maxB = b.t + b.h;
      }
      var g = document.createElement('div');
      var count = document.querySelectorAll('[data-wf-group="1"]').length + 1;
      g.className = 'wf-group wf-el';
      g.setAttribute('data-wf-group', '1');
      g.setAttribute('data-wf-name', '分组 ' + count);
      g.style.position = 'absolute';
      g.style.left = Math.round(minL) + 'px';
      g.style.top = Math.round(minT) + 'px';
      g.style.width = Math.max(1, Math.round(maxR - minL)) + 'px';
      g.style.height = Math.max(1, Math.round(maxB - minT)) + 'px';
      g.style.margin = '0';
      g.style.boxSizing = 'border-box';
      var last = els[0];
      for(var a = 1; a < els.length; a++){
        if(last.compareDocumentPosition(els[a]) & Node.DOCUMENT_POSITION_FOLLOWING) last = els[a];
      }
      parent.insertBefore(g, last.nextSibling);
      var ordered = boxes.slice().sort(function(p, q){
        var pos = p.el.compareDocumentPosition(q.el);
        if(pos & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
        if(pos & Node.DOCUMENT_POSITION_PRECEDING) return 1;
        return 0;
      });
      for(var m = 0; m < ordered.length; m++){
        var item = ordered[m];
        item.el.style.position = 'absolute';
        item.el.style.left = Math.round(item.l - minL) + 'px';
        item.el.style.top = Math.round(item.t - minT) + 'px';
        item.el.style.width = Math.round(item.w) + 'px';
        item.el.style.height = Math.round(item.h) + 'px';
        item.el.style.margin = '0';
        item.el.style.maxWidth = 'none';
        item.el.style.boxSizing = 'border-box';
        g.appendChild(item.el);
      }
      ensureUid(g);
      selectElement(g, true, false);
      scheduleSave();
      publishLayers();
      showToast('已编成「分组 ' + count + '」。单击移动整组，双击再选组里的单个元素');
    }

    function ungroupSelection(){
      var g = selectedEl;
      if(!g || !g.getAttribute || g.getAttribute('data-wf-group') !== '1'){
        showToast('请先选中一个分组，再按 Ctrl+Shift+G 解组');
        return;
      }
      if(isLayerLocked(g)){
        showToast('分组已锁定，先解锁再解组');
        return;
      }
      pushSnapshot();
      var parent = g.parentNode;
      var gl = parseFloat(g.style.left) || 0;
      var gt = parseFloat(g.style.top) || 0;
      var kids = [];
      for(var i = 0; i < g.children.length; i++) kids.push(g.children[i]);
      for(var k = 0; k < kids.length; k++){
        var c = kids[k];
        if(isChromeNode(c)) continue;
        var cl = parseFloat(c.style.left) || 0;
        var ct = parseFloat(c.style.top) || 0;
        c.style.position = 'absolute';
        c.style.left = Math.round(gl + cl) + 'px';
        c.style.top = Math.round(gt + ct) + 'px';
        parent.insertBefore(c, g);
      }
      deselect();
      if(g.parentNode) g.parentNode.removeChild(g);
      scheduleSave();
      publishLayers();
      showToast('已解散分组');
    }

    function renameLayer(uid, name){
      var safeUid = String(uid || '').replace(/"/g, '');
      var el = null;
      try { el = document.querySelector('[data-wf-uid="' + safeUid + '"]'); } catch(err) {}
      if(!el) return;
      var next = String(name || '').replace(/\s+/g, ' ').trim().slice(0, 40);
      if(!next) return;
      pushSnapshot();
      el.setAttribute('data-wf-name', next);
      scheduleSave();
      publishLayers();
    }

    function reorderLayer(uid, targetUid, place){
      var safeUid = String(uid || '').replace(/"/g, '');
      var safeTarget = String(targetUid || '').replace(/"/g, '');
      if(!safeUid || !safeTarget || safeUid === safeTarget) return;
      var el = null;
      var target = null;
      try {
        el = document.querySelector('[data-wf-uid="' + safeUid + '"]');
        target = document.querySelector('[data-wf-uid="' + safeTarget + '"]');
      } catch(err) {}
      if(!el || !target || !el.parentNode || el.parentNode !== target.parentNode){
        showToast('只能在同一层里调整上下顺序');
        return;
      }
      if(el.contains(target)) return;
      pushSnapshot();
      var parent = el.parentNode;
      if(place === 'before'){
        parent.insertBefore(el, target.nextSibling);
      } else {
        parent.insertBefore(el, target);
      }
      var z = 1;
      for(var i = 0; i < parent.children.length; i++){
        var c = parent.children[i];
        if(isChromeNode(c)) continue;
        var zi = parseInt(c.style.zIndex, 10);
        if(zi >= 9000) continue;
        c.style.zIndex = String(z);
        z += 1;
      }
      scheduleSave();
      publishLayers();
    }

    function restackSelection(edge){
      var raw = selectedEls && selectedEls.length ? selectedEls.slice() : (selectedEl ? [selectedEl] : []);
      var els = [];
      for(var i = 0; i < raw.length; i++){
        var el = raw[i];
        if(!el || !el.parentNode || isChromeNode(el) || isLayerLocked(el)) continue;
        if(el === document.body || el === document.documentElement) continue;
        els.push(el);
      }
      if(!els.length) return;
      var parent = els[0].parentNode;
      for(var j = 1; j < els.length; j++){
        if(els[j].parentNode !== parent){
          showToast('只能在同一层里调整前后顺序');
          return;
        }
      }
      els.sort(function(a, b){
        if(a === b) return 0;
        var pos = a.compareDocumentPosition(b);
        return (pos & Node.DOCUMENT_POSITION_FOLLOWING) ? -1 : 1;
      });
      pushSnapshot();
      if(edge === 'front'){
        for(var f = 0; f < els.length; f++) parent.appendChild(els[f]);
      } else {
        for(var b = els.length - 1; b >= 0; b--) parent.insertBefore(els[b], parent.firstChild);
      }
      var z = 1;
      for(var k = 0; k < parent.children.length; k++){
        var c = parent.children[k];
        if(isChromeNode(c)) continue;
        var zi = parseInt(c.style.zIndex, 10);
        if(zi >= 9000) continue;
        c.style.zIndex = String(z);
        z += 1;
      }
      scheduleSave();
      publishLayers();
      if(selectedEls.length > 1) updateMultiTransformBox();
      else if(selectedEl) updateTransformBox(selectedEl);
    }

    function effectNum(v){
      var n = Number(v);
      return isNaN(n) ? 0 : n;
    }
    function effectAlpha(v){
      var n = effectNum(v) / 100;
      if(n < 0) n = 0;
      if(n > 1) n = 1;
      return n;
    }
    function effectRgba(fx){
      var hex = String((fx && fx.color) || '#000000').replace('#', '');
      if(hex.length === 3) hex = hex.charAt(0)+hex.charAt(0)+hex.charAt(1)+hex.charAt(1)+hex.charAt(2)+hex.charAt(2);
      var r = parseInt(hex.slice(0, 2), 16); if(isNaN(r)) r = 0;
      var g = parseInt(hex.slice(2, 4), 16); if(isNaN(g)) g = 0;
      var b = parseInt(hex.slice(4, 6), 16); if(isNaN(b)) b = 0;
      return 'rgba(' + r + ',' + g + ',' + b + ',' + effectAlpha(fx && fx.opacity) + ')';
    }
    function isVectorShadowEl(el){
      var tag = el && el.tagName ? String(el.tagName).toLowerCase() : '';
      return !!(el && (tag === 'svg' || (el.classList && el.classList.contains('wf-vector-shape')) || (el.hasAttribute && el.hasAttribute('data-wf-vector'))));
    }
    function vectorSvgOf(el){
      if(!el) return null;
      if(String(el.tagName || '').toLowerCase() === 'svg') return el;
      return el.querySelector ? el.querySelector('svg') : null;
    }
    function clearVectorEffectDefs(el){
      var svg = vectorSvgOf(el);
      if(!svg) return;
      var defs = svg.querySelector('defs[data-wf-effect-defs]');
      if(defs && defs.parentNode) defs.parentNode.removeChild(defs);
      if(svg.style && svg.style.filter && svg.style.filter.indexOf('url(#wf-fx-') === 0) svg.style.filter = 'none';
    }
    function writeVectorEffectFilter(el, drops, inners, layerBlur){
      var svg = vectorSvgOf(el);
      if(!svg) return false;
      var NS = 'http://www.w3.org/2000/svg';
      var defs = svg.querySelector('defs[data-wf-effect-defs]');
      if(!defs){
        defs = document.createElementNS(NS, 'defs');
        defs.setAttribute('data-wf-effect-defs', '1');
        svg.insertBefore(defs, svg.firstChild);
      }
      var fid = 'wf-fx-' + String(el.getAttribute('data-wf-uid') || 'v').replace(/[^a-zA-Z0-9_-]/g, '');
      var filter = defs.querySelector('filter');
      if(!filter){
        filter = document.createElementNS(NS, 'filter');
        defs.appendChild(filter);
      }
      filter.setAttribute('id', fid);
      filter.setAttribute('x', '-80%');
      filter.setAttribute('y', '-80%');
      filter.setAttribute('width', '260%');
      filter.setAttribute('height', '260%');
      filter.setAttribute('filterUnits', 'objectBoundingBox');
      filter.setAttribute('primitiveUnits', 'userSpaceOnUse');
      filter.setAttribute('color-interpolation-filters', 'sRGB');
      while(filter.firstChild) filter.removeChild(filter.firstChild);
      function prim(name, attrs){
        var node = document.createElementNS(NS, name);
        for(var k in attrs){
          if(attrs[k] !== undefined && attrs[k] !== null) node.setAttribute(k, String(attrs[k]));
        }
        filter.appendChild(node);
        return node;
      }
      var dropResults = [];
      for(var i = 0; i < drops.length; i++){
        var drop = drops[i];
        var alpha = 'SourceAlpha';
        if(effectNum(drop.spread) > 0){
          prim('feMorphology', { in: alpha, operator: 'dilate', radius: effectNum(drop.spread), result: 'dsp' + i });
          alpha = 'dsp' + i;
        }
        prim('feOffset', { in: alpha, dx: effectNum(drop.x), dy: effectNum(drop.y), result: 'doff' + i });
        var blurIn = 'doff' + i;
        if(effectNum(drop.blur) > 0){
          prim('feGaussianBlur', { in: blurIn, stdDeviation: effectNum(drop.blur) / 2, result: 'dblur' + i });
          blurIn = 'dblur' + i;
        }
        prim('feFlood', { 'flood-color': drop.color || '#000000', 'flood-opacity': effectAlpha(drop.opacity), result: 'dflood' + i });
        prim('feComposite', { in: 'dflood' + i, in2: blurIn, operator: 'in', result: 'dsh' + i });
        dropResults.push('dsh' + i);
      }
      var innerResults = [];
      for(var n = 0; n < inners.length; n++){
        var inn = inners[n];
        prim('feOffset', { in: 'SourceAlpha', dx: effectNum(inn.x), dy: effectNum(inn.y), result: 'ioff' + n });
        var iBlur = 'ioff' + n;
        if(effectNum(inn.blur) > 0){
          prim('feGaussianBlur', { in: iBlur, stdDeviation: effectNum(inn.blur) / 2, result: 'iblur' + n });
          iBlur = 'iblur' + n;
        }
        if(effectNum(inn.spread) > 0){
          prim('feMorphology', { in: iBlur, operator: 'dilate', radius: effectNum(inn.spread), result: 'isp' + n });
          iBlur = 'isp' + n;
        }
        prim('feComposite', { in: 'SourceAlpha', in2: iBlur, operator: 'out', result: 'iinv' + n });
        prim('feFlood', { 'flood-color': inn.color || '#000000', 'flood-opacity': effectAlpha(inn.opacity), result: 'iflood' + n });
        prim('feComposite', { in: 'iflood' + n, in2: 'iinv' + n, operator: 'in', result: 'ish' + n });
        innerResults.push('ish' + n);
      }
      var merge = prim('feMerge', { result: 'merged' });
      function mergeNode(inputName){
        var mn = document.createElementNS(NS, 'feMergeNode');
        if(inputName) mn.setAttribute('in', inputName);
        merge.appendChild(mn);
      }
      for(var di = 0; di < dropResults.length; di++) mergeNode(dropResults[di]);
      mergeNode('SourceGraphic');
      for(var ii = 0; ii < innerResults.length; ii++) mergeNode(innerResults[ii]);
      if(layerBlur && effectNum(layerBlur.blur) > 0){
        prim('feGaussianBlur', { in: 'merged', stdDeviation: effectNum(layerBlur.blur) / 2 });
      }
      svg.style.filter = 'url(#' + fid + ')';
      svg.style.overflow = 'visible';
      svg.style.boxShadow = 'none';
      if(svg !== el){
        el.style.filter = 'none';
        el.style.boxShadow = 'none';
      }
      return true;
    }
    function applyEffectList(el, effects){
      if(!el) return;
      if(typeof effects === 'string'){
        try { effects = JSON.parse(effects); } catch(err){ effects = []; }
      }
      if(!effects || !effects.length){
        try { el.removeAttribute('data-wf-effects'); } catch(err){}
        el.style.boxShadow = 'none';
        el.style.filter = 'none';
        el.style.backdropFilter = 'none';
        el.style.webkitBackdropFilter = 'none';
        clearVectorEffectDefs(el);
        return;
      }
      try { el.setAttribute('data-wf-effects', JSON.stringify(effects)); } catch(err){}
      var drops = [], inners = [], layerBlur = null, bgBlur = null;
      for(var i = 0; i < effects.length; i++){
        var fx = effects[i];
        if(!fx || fx.visible === false) continue;
        if(fx.type === 'drop-shadow') drops.push(fx);
        else if(fx.type === 'inner-shadow') inners.push(fx);
        else if(fx.type === 'layer-blur') layerBlur = fx;
        else if(fx.type === 'background-blur') bgBlur = fx;
      }
      var bgCss = (bgBlur && effectNum(bgBlur.blur) > 0) ? ('blur(' + effectNum(bgBlur.blur) + 'px)') : 'none';
      el.style.backdropFilter = bgCss;
      el.style.webkitBackdropFilter = bgCss;
      if(isVectorShadowEl(el)){
        el.style.boxShadow = 'none';
        el.style.overflow = 'visible';
        var needSvg = inners.length > 0;
        if(!needSvg){
          for(var s = 0; s < drops.length; s++){
            if(effectNum(drops[s].spread) > 0) needSvg = true;
          }
        }
        if(needSvg){
          writeVectorEffectFilter(el, drops, inners, layerBlur);
        }else{
          clearVectorEffectDefs(el);
          var css = [];
          if(layerBlur && effectNum(layerBlur.blur) > 0) css.push('blur(' + effectNum(layerBlur.blur) + 'px)');
          for(var d = 0; d < drops.length; d++){
            var one = drops[d];
            css.push('drop-shadow(' + effectNum(one.x) + 'px ' + effectNum(one.y) + 'px ' + effectNum(one.blur) + 'px ' + effectRgba(one) + ')');
          }
          el.style.filter = css.length ? css.join(' ') : 'none';
        }
      }else{
        clearVectorEffectDefs(el);
        var box = [];
        for(var b = 0; b < drops.length; b++){
          var outer = drops[b];
          box.push(effectNum(outer.x) + 'px ' + effectNum(outer.y) + 'px ' + effectNum(outer.blur) + 'px ' + effectNum(outer.spread) + 'px ' + effectRgba(outer));
        }
        for(var m = 0; m < inners.length; m++){
          var inner = inners[m];
          box.push('inset ' + effectNum(inner.x) + 'px ' + effectNum(inner.y) + 'px ' + effectNum(inner.blur) + 'px ' + effectNum(inner.spread) + 'px ' + effectRgba(inner));
        }
        el.style.boxShadow = box.length ? box.join(', ') : 'none';
        el.style.filter = (layerBlur && effectNum(layerBlur.blur) > 0) ? ('blur(' + effectNum(layerBlur.blur) + 'px)') : 'none';
      }
    }

    window.addEventListener('message', function(e){
      var d = e.data || {};
      if(d.type === 'wf-frame-size'){
        applyFrameSize(d.width, d.height);
        return;
      }
      if(d.type === 'wf-publish-layers'){
        publishLayers();
        return;
      }
      if(d.type === 'wf-layer-flag'){
        var flagUid = String(d.uid || '').replace(/"/g, '');
        if(!flagUid) return;
        var flagEl = null;
        try { flagEl = document.querySelector('[data-wf-uid="' + flagUid + '"]'); } catch(err) {}
        if(!flagEl) return;
        pushSnapshot();
        var flagOn = !!d.on;
        if(d.flag === 'hidden'){
          if(flagOn){
            flagEl.setAttribute('data-wf-hidden', '1');
            flagEl.classList.add('wf-layer-hidden');
          } else {
            flagEl.removeAttribute('data-wf-hidden');
            flagEl.classList.remove('wf-layer-hidden');
            if(flagEl.style && flagEl.style.visibility === 'hidden') flagEl.style.visibility = '';
          }
          if(selectedEl === flagEl){
            var hb = document.getElementById('wf-transform-box');
            if(flagOn){
              if(hb) hb.style.display = 'none';
            } else if(selectedEl){
              updateTransformBox(selectedEl);
            }
          }
        } else if(d.flag === 'locked'){
          if(flagOn){
            flagEl.setAttribute('data-wf-locked', '1');
            flagEl.classList.add('wf-layer-locked');
          } else {
            flagEl.removeAttribute('data-wf-locked');
            flagEl.classList.remove('wf-layer-locked');
          }
        }
        scheduleSave();
        publishLayers();
        return;
      }
      if(d.type === 'wf-proto-hotspot'){
        PROTO_HOTSPOT = !!d.on;
        if(!PROTO_HOTSPOT){
          try { parent.postMessage({ type: 'wf-hotspot-clear', pageId: PAGE_ID }, '*'); } catch(err) {}
        }
        return;
      }
      if(d.type === 'wf-set-nav'){
        var navUid = String(d.uid || '').replace(/"/g, '');
        if(!navUid) return;
        var navEl = null;
        try { navEl = document.querySelector('[data-wf-uid="' + navUid + '"]'); } catch(err) {}
        if(!navEl) return;
        if(d.page){
          navEl.setAttribute('data-nav', String(d.page));
          navEl.style.cursor = 'pointer';
        } else {
          navEl.removeAttribute('data-nav');
        }
        scheduleSave();
        return;
      }
      if(d.uid && d.type !== 'wf-select-uid') resolveEditTarget(d);
      if(d.type === 'wf-interactive'){
        if(d.on){
          deselect();
          clearHover();
        }
        return;
      }
      if(d.type === 'wf-edit'){
        EDIT = !!d.on;
        document.body.style.cursor = EDIT ? 'default' : '';
        document.body.classList.toggle('wf-edit-mode', EDIT);
        if(EDIT){
          pushSnapshot();
          showToast('微调模式已开启：点击选框缩放/移动，双击改文案，拖入新组件');
        } else {
          deselect();
          cleanupStyles();
        }
      }else if(d.type === 'wf-undo'){ undo(); }
      else if(d.type === 'wf-redo'){ redo(); }
      else if(d.type === 'wf-copy'){
        if(selectedEl) copyElement(selectedEl);
      }else if(d.type === 'wf-paste'){
        pasteElement(d.x, d.y);
      }else if(d.type === 'wf-duplicate'){
        if(selectedEl) duplicateElement(selectedEl);
      }else if(d.type === 'wf-select'){
        if(d.selector){
          var selTarget = document.querySelector(d.selector);
          if(selTarget) selectElement(selTarget, true);
        }
      }else if(d.type === 'wf-align'){
        if(selectedEls && selectedEls.length > 0){
          pushSnapshot();
          var alignType = d.alignType;
          var pad = 0;
          // 对齐前统一 absolute，用当前 offsetLeft/offsetTop 作为起点，避免静态流乱跳
          for(var ai = 0; ai < selectedEls.length; ai++){
            var aEl = selectedEls[ai];
            if(!aEl || !aEl.isConnected) continue;
            var aL = aEl.offsetLeft;
            var aT = aEl.offsetTop;
            aEl.style.position = 'absolute';
            aEl.style.left = aL + 'px';
            aEl.style.top = aT + 'px';
          }
          if(selectedEls.length === 1){
            var oneEl = selectedEls[0];
            var parent = oneEl.offsetParent || document.body;
            var pW = parent.clientWidth || document.body.clientWidth || 375;
            var pH = parent.clientHeight || document.body.clientHeight || 812;
            var eW = oneEl.offsetWidth || 100;
            var eH = oneEl.offsetHeight || 40;
            if(alignType === 'left'){
              oneEl.style.left = pad + 'px';
            }else if(alignType === 'center-h'){
              oneEl.style.left = Math.round((pW - eW) / 2) + 'px';
            }else if(alignType === 'right'){
              oneEl.style.left = Math.max(0, Math.round(pW - eW - pad)) + 'px';
            }else if(alignType === 'top'){
              oneEl.style.top = pad + 'px';
            }else if(alignType === 'center-v'){
              oneEl.style.top = Math.round((pH - eH) / 2) + 'px';
            }else if(alignType === 'bottom'){
              oneEl.style.top = Math.max(0, Math.round(pH - eH - pad)) + 'px';
            }
          } else {
            // 多选：对齐到选中元素自身包围盒（Figma 行为）
            var minL = Infinity, minT = Infinity, maxR = -Infinity, maxB = -Infinity;
            var infos = [];
            for(var bi = 0; bi < selectedEls.length; bi++){
              var bEl = selectedEls[bi];
              if(!bEl || !bEl.isConnected) continue;
              var bl = parseFloat(bEl.style.left);
              var bt = parseFloat(bEl.style.top);
              if(isNaN(bl)) bl = bEl.offsetLeft;
              if(isNaN(bt)) bt = bEl.offsetTop;
              var bw = bEl.offsetWidth || 100;
              var bh = bEl.offsetHeight || 40;
              infos.push({ el: bEl, left: bl, top: bt, w: bw, h: bh });
              if(bl < minL) minL = bl;
              if(bt < minT) minT = bt;
              if(bl + bw > maxR) maxR = bl + bw;
              if(bt + bh > maxB) maxB = bt + bh;
            }
            var midX = (minL + maxR) / 2;
            var midY = (minT + maxB) / 2;
            for(var ci = 0; ci < infos.length; ci++){
              var info = infos[ci];
              if(alignType === 'left'){
                info.el.style.left = Math.round(minL) + 'px';
              }else if(alignType === 'center-h'){
                info.el.style.left = Math.round(midX - info.w / 2) + 'px';
              }else if(alignType === 'right'){
                info.el.style.left = Math.round(maxR - info.w) + 'px';
              }else if(alignType === 'top'){
                info.el.style.top = Math.round(minT) + 'px';
              }else if(alignType === 'center-v'){
                info.el.style.top = Math.round(midY - info.h / 2) + 'px';
              }else if(alignType === 'bottom'){
                info.el.style.top = Math.round(maxB - info.h) + 'px';
              }
            }
          }
          if(selectedEls.length > 1) updateMultiTransformBox();
          else if(selectedEl) updateTransformBox(selectedEl);
          if(selectedEl) notifySelectedElementInfo(selectedEl);
          scheduleSave();
          showToast('已对齐元素位置并保存');
        }
      }else if(d.type === 'wf-layout'){
        if(selectedEl){
          pushSnapshot();
          var cs = window.getComputedStyle ? window.getComputedStyle(selectedEl) : null;
          var pos = selectedEl.style.position || (cs ? cs.position : 'static');
          var isAbsolute = pos === 'absolute';

          if(d.key === 'x'){
            if(isAbsolute){
              selectedEl.style.left = d.val + 'px';
            } else {
              var curOffset = selectedEl.offsetLeft || 0;
              var curStyleL = parseFloat(selectedEl.style.left) || 0;
              var diff = d.val - curOffset;
              selectedEl.style.position = 'relative';
              selectedEl.style.left = Math.round(curStyleL + diff) + 'px';
            }
          } else if(d.key === 'y'){
            if(isAbsolute){
              selectedEl.style.top = d.val + 'px';
            } else {
              var curOffset = selectedEl.offsetTop || 0;
              var curStyleT = parseFloat(selectedEl.style.top) || 0;
              var diff = d.val - curOffset;
              selectedEl.style.position = 'relative';
              selectedEl.style.top = Math.round(curStyleT + diff) + 'px';
            }
          } else if(d.key === 'width'){
            var targetW = Math.max(10, d.val);
            selectedEl.style.width = targetW + 'px';
            selectedEl.style.maxWidth = 'none';
            selectedEl.style.flex = 'none';
            selectedEl.style.boxSizing = 'border-box';
            if(selectedEl.classList && (selectedEl.classList.contains('wf-avatar') || selectedEl.classList.contains('wf-shape-circle'))){
              selectedEl.style.height = targetW + 'px';
            }
          } else if(d.key === 'height'){
            var targetH = Math.max(10, d.val);
            selectedEl.style.height = targetH + 'px';
            selectedEl.style.maxHeight = 'none';
            selectedEl.style.flex = 'none';
            selectedEl.style.boxSizing = 'border-box';
            if(selectedEl.classList && (selectedEl.classList.contains('wf-avatar') || selectedEl.classList.contains('wf-shape-circle'))){
              selectedEl.style.width = targetH + 'px';
            }
          }
          updateTransformBox(selectedEl);
          notifySelectedElementInfo(selectedEl);
          scheduleSave();
        }
      }else if(d.type === 'wf-radius'){
        if(selectedEl){
          pushSnapshot();
          selectedEl.style.borderRadius = (typeof d.radius === 'number' ? (d.radius >= 999 ? '9999px' : d.radius + 'px') : d.radius);
          updateTransformBox(selectedEl);
          scheduleSave();
        }
      }else if(d.type === 'wf-stroke'){
        if(selectedEl){
          pushSnapshot();
          var isVector = selectedEl.tagName.toLowerCase() === 'svg' ||
            (selectedEl.classList && selectedEl.classList.contains('wf-vector-shape')) ||
            (selectedEl.hasAttribute && selectedEl.hasAttribute('data-wf-vector'));

          if(isVector){
            var paths = selectedEl.tagName.toLowerCase() === 'path' ? [selectedEl] : selectedEl.querySelectorAll('path');
            var strokeCol = d.width === 0 ? 'none' : (d.color || '#cbd5e1');
            var strokeW = d.width !== undefined ? d.width : 2;
            for(var pi = 0; pi < paths.length; pi++){
              if(d.width === 0){
                paths[pi].setAttribute('stroke', 'none');
                paths[pi].style.stroke = 'none';
              } else {
                paths[pi].setAttribute('stroke', strokeCol);
                paths[pi].setAttribute('stroke-width', strokeW);
                paths[pi].style.stroke = strokeCol;
                paths[pi].style.strokeWidth = strokeW + 'px';
              }
            }
            var vDataStr = selectedEl.getAttribute('data-wf-vector');
            if(vDataStr){
              try{
                var vObj = JSON.parse(vDataStr);
                vObj.strokeColor = strokeCol;
                vObj.strokeWidth = strokeW;
                selectedEl.setAttribute('data-wf-vector', JSON.stringify(vObj));
              }catch(e){}
            }
            selectedEl.style.border = 'none';
            updateTransformBox(selectedEl);
            scheduleSave();
            showToast('描边已修改并保存');
          } else {
            if(d.width === 0){
              selectedEl.style.border = 'none';
            }else{
              selectedEl.style.border = d.width + 'px ' + (d.style || 'solid') + ' ' + (d.color || '#cbd5e1');
            }
            selectedEl.style.boxSizing = 'border-box';
            updateTransformBox(selectedEl);
            scheduleSave();
          }
        }
      }else if(d.type === 'wf-effects'){
        if(selectedEl){
          if(!d.live) pushSnapshot();
          applyEffectList(selectedEl, d.effects || []);
          updateTransformBox(selectedEl);
          scheduleSave();
        }
      }else if(d.type === 'wf-shadow'){
        if(selectedEl){
          pushSnapshot();
          var isVectorShadow = String(selectedEl.tagName || '').toLowerCase() === 'svg'
            || (selectedEl.classList && selectedEl.classList.contains('wf-vector-shape'))
            || (selectedEl.hasAttribute && selectedEl.hasAttribute('data-wf-vector'));
          if(isVectorShadow){
            selectedEl.style.boxShadow = 'none';
            selectedEl.style.overflow = 'visible';
            if(!d.shadow || d.shadow === 'none'){
              selectedEl.style.filter = 'none';
            }else{
              selectedEl.style.filter = 'drop-shadow(' + d.shadow + ')';
            }
          }else{
            if(selectedEl.style.filter && selectedEl.style.filter.indexOf('drop-shadow') === 0){
              selectedEl.style.filter = 'none';
            }
            selectedEl.style.boxShadow = d.shadow || 'none';
          }
          updateTransformBox(selectedEl);
          scheduleSave();
        }
      }else if(d.type === 'wf-frame-color'){
        pushSnapshot();
        var frameColor = d.color || '#ffffff';
        document.body.style.background = frameColor;
        document.documentElement.style.background = frameColor;
        scheduleSave();
        publishFrameFill();
        showToast('画框颜色已保存');
      }else if(d.type === 'wf-query-frame-fill'){
        publishFrameFill();
      }else if(d.type === 'wf-color'){
        if(selectedEl){
          applyColor(selectedEl, d.color);
        }
      }else if(d.type === 'wf-font-size'){
        if(selectedEl){
          var tText = findTextTarget(selectedEl) || selectedEl;
          var curFs = parseInt(window.getComputedStyle(tText).fontSize) || 14;
          var nextFs = Math.max(10, Math.min(60, curFs + d.delta));
          pushSnapshot();
          tText.style.fontSize = nextFs + 'px';
          var textNodes = selectedEl.querySelectorAll ? selectedEl.querySelectorAll(tText.tagName) : [];
          for(var ti = 0; ti < textNodes.length; ti++){
            if(textNodes[ti].tagName === tText.tagName) textNodes[ti].style.fontSize = nextFs + 'px';
          }
          updateTransformBox(selectedEl);
          scheduleSave();
        }
      }else if(d.type === 'wf-start-text-edit'){
        if(selectedEl) startTextEdit(selectedEl);
      }else if(d.type === 'wf-update-text'){
        if(selectedEl){
          var tText = findTextTarget(selectedEl) || selectedEl;
          if(tText){
            pushSnapshot();
            if(tText.tagName === 'INPUT' || tText.tagName === 'TEXTAREA'){
              tText.value = d.text || '';
            } else {
              tText.innerText = d.text || '';
            }
            updateTransformBox(selectedEl);
            scheduleSave();
          }
        }
      }else if(d.type === 'wf-select-parent'){
        if(selectedEl && selectedEl.parentElement){
          var parentCard = selectedEl.parentElement.closest ? selectedEl.parentElement.closest('.wf-ios-story-card, .wf-ios-avatar-grid, .wf-ios-app-item, .wf-ios-tab-bar, .wf-container, .wf-box, .wf-card, .wf-inserted-component') : null;
          if(parentCard && parentCard !== selectedEl && parentCard !== document.body){
            selectElement(parentCard, true);
          }
        }
      }else if(d.type === 'wf-delete'){
        if(selectedEl && isLayerLocked(selectedEl)){
          showToast('图层已锁定，先解锁再删除');
          return;
        }
        if(selectedEl && selectedEl !== document.body){
          pushSnapshot();
          var toDel = selectedEl;
          deselect();
          if(toDel.parentNode) toDel.parentNode.removeChild(toDel);
          scheduleSave();
          publishLayers();
          showToast('已删除元素 (Ctrl+Z 可撤回)');
        }
      }else if(d.type === 'wf-open-asset-picker'){
        if(selectedEl){
          var prev = document.querySelectorAll('.wf-current-asset-target');
          for(var pi = 0; pi < prev.length; pi++) prev[pi].classList.remove('wf-current-asset-target');
          selectedEl.classList.add('wf-current-asset-target');
          var src = selectedEl.tagName === 'IMG' ? selectedEl.src : (selectedEl.style && selectedEl.style.backgroundImage ? selectedEl.style.backgroundImage.replace(/^url\(["']?|["']?\)$/g, '') : '');
          parent.postMessage({ type: 'wf-pick-asset', src: src }, '*');
        }
      }
      else if(d.type === 'wf-export'){ doExport(); }
      else if(d.type === 'wf-fit'){
        var th = d.h; if(!th) return;
        var hh = document.documentElement.scrollHeight || document.body.scrollHeight || 0;
        if(hh > th * 1.08){
          var r = th / hh;
          document.body.style.transformOrigin = 'top center';
          document.body.style.transform = 'scale(' + r + ')';
          /* 缩放后强制开启字体子像素渲染，防止 transform 导致文字发虚 */
          document.body.style.webkitFontSmoothing = 'antialiased';
          document.body.style.backfaceVisibility = 'hidden';
          parent.postMessage({ type: 'wf-size', h: th }, '*');
        }
      }else if(d.type === 'wf-replace-asset'){
        var cur = document.querySelector('.wf-current-asset-target');
        if(cur && d.src){
          pushSnapshot();
          if(cur.tagName === 'IMG'){
            cur.src = d.src;
          } else {
            cur.style.backgroundImage = 'url(' + d.src + ')';
            cur.style.backgroundSize = 'cover';
            cur.style.backgroundPosition = 'center';
          }
          cur.classList.remove('wf-current-asset-target');
          scheduleSave();
          if(selectedEl) updateTransformBox(selectedEl);
          showToast('素材图片替换成功并落库');
        }
      }else if(d.type === 'wf-insert-html'){
        if(d.html){
          pushSnapshot();
          var tDiv = document.createElement('div');
          tDiv.innerHTML = d.html.trim();
          var newChild = tDiv.firstElementChild || tDiv;

          var targetX = (typeof d.dropX === 'number' && !isNaN(d.dropX)) ? d.dropX : 20;
          var targetY = (typeof d.dropY === 'number' && !isNaN(d.dropY)) ? d.dropY : 220;

          var isModal = newChild.classList && (newChild.classList.contains('wf-modal') || newChild.classList.contains('wf-bottom-sheet'));
          if(!isModal){
            newChild.classList.add('wf-el', 'wf-inserted-component');
            markInsertedAsGroup(newChild);
            newChild.style.position = 'absolute';
            newChild.style.left = targetX + 'px';
            newChild.style.top = targetY + 'px';
            newChild.style.zIndex = '999';
            if(!newChild.style.maxWidth && !newChild.style.width){
              newChild.style.maxWidth = '335px';
            }
          } else {
            newChild.style.zIndex = '9999';
          }

          document.body.appendChild(newChild);
          if(d.fitInside && !isModal){
            var frameW = window.innerWidth || document.documentElement.clientWidth || 375;
            var frameH = window.innerHeight || document.documentElement.clientHeight || 812;
            var boxW = newChild.offsetWidth || 0;
            var boxH = newChild.offsetHeight || 0;
            var fitL = parseFloat(newChild.style.left) || 0;
            var fitT = parseFloat(newChild.style.top) || 0;
            if(boxW > 0 && fitL + boxW > frameW) fitL = Math.max(0, frameW - boxW);
            if(boxH > 0 && fitT + boxH > frameH) fitT = Math.max(0, frameH - boxH);
            if(fitL < 0) fitL = 0;
            if(fitT < 0) fitT = 0;
            newChild.style.left = Math.round(fitL) + 'px';
            newChild.style.top = Math.round(fitT) + 'px';
          }
          ensureUid(newChild);

          // 自动激活编辑态并选中新插入的组件，立即呈现8点缩放盒与【编辑文字】按钮
          EDIT = true;
          document.body.style.cursor = 'default';
          selectElement(newChild);
          publishLayers();
          if(d.autoEditText){
            setTimeout(function(){
              startTextEdit(newChild);
            }, 60);
          }
          scheduleSave();
          showToast(isModal ? '组件已添加' : '组件已成组：单击移动整组，双击再选里面的单个元素');
        }
      }else if(d.type === 'wf-select-uid'){
        if(d.uid){
          var uidTarget = document.querySelector('[data-wf-uid="' + d.uid + '"]');
          if(uidTarget) selectElement(uidTarget, true, !!d.multi);
        }
      }else if(d.type === 'wf-select-uids'){
        var want = Array.isArray(d.uids) ? d.uids : [];
        var picked = [];
        for(var si = 0; si < want.length; si++){
          var safePick = String(want[si] || '').replace(/"/g, '');
          if(!safePick) continue;
          var pickedEl = null;
          try { pickedEl = document.querySelector('[data-wf-uid="' + safePick + '"]'); } catch(err) {}
          if(pickedEl) picked.push(pickedEl);
        }
        setMultiSelection(picked);
      }else if(d.type === 'wf-group'){
        groupSelection();
      }else if(d.type === 'wf-ungroup'){
        ungroupSelection();
      }else if(d.type === 'wf-rename-layer'){
        renameLayer(d.uid, d.name);
      }else if(d.type === 'wf-reorder-layer'){
        reorderLayer(d.uid, d.targetUid, d.place === 'before' ? 'before' : 'after');
      }else if(d.type === 'wf-restack'){
        restackSelection(d.edge === 'back' ? 'back' : 'front');
      }else if(d.type === 'wf-hide-vector-original'){
        var vEl = findVectorElement(d.elementUid);
        if(vEl){
          vEl.style.visibility = 'hidden';
          if(selectedEl === vEl) deselect();
          // 注意：隐藏期间严禁调用 scheduleSave()，避免将 visibility:hidden 存入数据库！
        }
      }else if(d.type === 'wf-restore-vector-original'){
        var vEl = findVectorElement(d.elementUid);
        if(vEl){
          vEl.style.visibility = '';
        }
      }else if(d.type === 'wf-remove-vector-original'){
        var vEl = findVectorElement(d.elementUid);
        if(vEl && vEl.parentNode){
          pushSnapshot();
          if(selectedEl === vEl) deselect();
          vEl.parentNode.removeChild(vEl);
          scheduleSave();
          publishLayers();
        }
      }else if(d.type === 'wf-replace-vector-original'){
        var vEl = findVectorElement(d.elementUid);
        if(!vEl && selectedEl && (selectedEl.classList.contains('wf-vector-shape') || selectedEl.hasAttribute('data-wf-vector'))){
          vEl = selectedEl;
        }
        if(vEl && d.newHtml){
          pushSnapshot();
          var tDiv = document.createElement('div');
          tDiv.innerHTML = d.newHtml.trim();
          var newSvg = tDiv.firstElementChild || tDiv;
          newSvg.style.visibility = '';
          if(d.elementUid && !newSvg.getAttribute('data-wf-uid')){
            newSvg.setAttribute('data-wf-uid', d.elementUid);
          }
          ensureUid(newSvg);
          if(vEl.parentNode){
            vEl.parentNode.replaceChild(newSvg, vEl);
          }
          selectElement(newSvg, true);
          publishLayers();
          scheduleSave();
          showToast('矢量图形已更新并保存');
        }
      }else if(d.type === 'wf-insert-vector-shapes'){
        var shapes = d.shapes || [];
        if(shapes.length > 0){
          pushSnapshot();
          // 只有明确带了要替换的 uid 才删旧图形。铅笔连续画时每一笔都是新增，不能把上一笔选中项删掉。
          var oldEl = d.elementUid ? findVectorElement(d.elementUid) : null;
          if(!oldEl && d.elementUid && selectedEl && (selectedEl.classList.contains('wf-vector-shape') || selectedEl.hasAttribute('data-wf-vector'))){
            oldEl = selectedEl;
          }
          if(oldEl && oldEl.parentNode){
            if(selectedEl === oldEl) deselect();
            oldEl.parentNode.removeChild(oldEl);
          }
          var lastSvg = null;
          var newEls = [];
          for(var si = 0; si < shapes.length; si++){
            var sItem = shapes[si];
            var sHtml = typeof sItem === 'string' ? sItem : (sItem && sItem.html ? sItem.html : '');
            if(!sHtml) continue;
            var tDiv = document.createElement('div');
            tDiv.innerHTML = sHtml.trim();
            var sEl = tDiv.firstElementChild || tDiv;
            sEl.style.visibility = '';
            ensureUid(sEl);
            document.body.appendChild(sEl);
            lastSvg = sEl;
            newEls.push(sEl);
          }
          if(d.select === false){
            deselect();
          } else if(newEls.length > 1){
            setMultiSelection(newEls);
          } else if(lastSvg){
            selectElement(lastSvg, true);
          }
          publishLayers();
          scheduleSave();
          if(shapes.length > 1) showToast('已拆分为多个独立矢量图形');
        }
      }
    });

    document.addEventListener('mouseover', function(e){
      if(!EDIT || document.body.classList.contains('wf-interactive')) return;
      var activeText = document.querySelector('[data-wf-editing-text="true"]');
      if(activeText || resizing || drag) return;
      var t = e.target;
      if(!t || t === document.body || t === document.documentElement || (t.closest && t.closest('#wf-transform-box'))) return;
      if(selectedEl && (t === selectedEl || (selectedEl.contains && selectedEl.contains(t)))) return;
      if(hovered && hovered !== t) outline(hovered, false);
      hovered = t;
      outline(hovered, true);
    }, true);

    document.addEventListener('mouseout', function(e){
      if(hovered && hovered === e.target){
        outline(hovered, false);
        hovered = null;
      }
    }, true);

    // 交互连线：鼠标停在组件上时，把这块的位置报给画布，用来画右侧蓝点
    function pickHotspotEl(node){
      var el = node;
      var control = null;
      var card = null;
      var viewW = window.innerWidth || 375;
      var viewH = window.innerHeight || 812;
      while(el && el !== document.body && el !== document.documentElement){
        if(el.id === 'wf-transform-box' || el.id === 'wf-marquee-box' || el.id === 'wf-edit-toast') return null;
        var r = el.getBoundingClientRect();
        if(r.width >= 18 && r.height >= 14 && r.width <= viewW * 0.96 && r.height <= viewH * 0.7){
          var tag = (el.tagName || '').toLowerCase();
          var clickable = tag === 'button' || tag === 'a' || tag === 'input' || el.getAttribute('role') === 'button' || el.hasAttribute('data-nav');
          if(clickable && r.width <= 300 && r.height <= 120) return el;
          if(!control && r.width <= 260 && r.height <= 88) control = el;
          if(!card && r.height <= 160) card = el;
        }
        el = el.parentElement;
      }
      return control || card;
    }
    var hotspotRaf = 0;
    var hotspotNode = null;
    document.addEventListener('mousemove', function(e){
      if(!PROTO_HOTSPOT) return;
      hotspotNode = e.target;
      if(hotspotRaf) return;
      hotspotRaf = requestAnimationFrame(function(){
        hotspotRaf = 0;
        if(!PROTO_HOTSPOT) return;
        var el = pickHotspotEl(hotspotNode);
        if(!el){
          try { parent.postMessage({ type: 'wf-hotspot-clear', pageId: PAGE_ID }, '*'); } catch(err) {}
          return;
        }
        var r = el.getBoundingClientRect();
        var raw = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('alt') || '').replace(/\s+/g, ' ').trim();
        try {
          parent.postMessage({
            type: 'wf-hotspot',
            pageId: PAGE_ID,
            x: Math.round(r.left + (window.scrollX || 0)),
            y: Math.round(r.top + (window.scrollY || 0)),
            w: Math.round(r.width),
            h: Math.round(r.height),
            label: raw.slice(0, 24),
            uid: ensureUid(el)
          }, '*');
        } catch(err) {}
      });
    }, true);
    document.documentElement.addEventListener('mouseleave', function(){
      if(!PROTO_HOTSPOT) return;
      try { parent.postMessage({ type: 'wf-hotspot-clear', pageId: PAGE_ID }, '*'); } catch(err) {}
    });

    // 双击任意元素深入选中并激活对应编辑（文字打字、图片替换、矢量图形编辑）
    document.addEventListener('dblclick', function(e){
      var t = e.target;
      if(!t || t === document.body || t === document.documentElement) return;

      // 分组里双击：选中光标下的那个元素，方便单独挪动。再双击同一个元素才进入改字。
      var groupHit = outermostGroup(t);
      if(groupHit){
        var inner = pickInsideGroup(t, groupHit);
        if(inner && inner !== groupHit && selectedEl !== inner){
          e.preventDefault();
          e.stopPropagation();
          selectElement(inner, true, false);
          return;
        }
      }

      // 检测是否双击了带有 .wf-vector-shape 或 [data-wf-vector] 的 SVG 元素
      var vectorEl = t && t.closest ? t.closest('.wf-vector-shape, [data-wf-vector]') : null;
      if (!vectorEl && t && (t.tagName === 'svg' || (t.ownerSVGElement && t.ownerSVGElement.tagName === 'svg'))) {
        var svgEl = t.tagName === 'svg' ? t : t.ownerSVGElement;
        var sCls = typeof svgEl.className === 'string' ? svgEl.className : (svgEl.getAttribute('class') || '');
        if (sCls.indexOf('wf-vector-shape') !== -1 || svgEl.hasAttribute('data-wf-vector')) {
          vectorEl = svgEl;
        }
      }
      if (vectorEl) {
        e.preventDefault();
        e.stopPropagation();
        var vData = vectorEl.getAttribute('data-wf-vector');
        var uid = vectorEl.getAttribute('data-wf-uid') || ('v_' + Date.now());
        vectorEl.setAttribute('data-wf-uid', uid);
        var elW = vectorEl.offsetWidth;
        if (!elW && vectorEl.getBoundingClientRect) elW = Math.round(vectorEl.getBoundingClientRect().width);
        var elH = vectorEl.offsetHeight;
        if (!elH && vectorEl.getBoundingClientRect) elH = Math.round(vectorEl.getBoundingClientRect().height);
        var elL = parseFloat(vectorEl.style.left);
        if (isNaN(elL)) elL = vectorEl.offsetLeft || 0;
        var elT = parseFloat(vectorEl.style.top);
        if (isNaN(elT)) elT = vectorEl.offsetTop || 0;
        window.parent.postMessage({
          type: 'wf-edit-vector',
          pageId: PAGE_ID,
          elementUid: uid,
          vectorData: vData,
          width: elW,
          height: elH,
          left: elL,
          top: elT
        }, '*');
        return;
      }

      var isInserted = t.closest && t.closest('.wf-inserted-component,.wf-box,.wf-container,.wf-avatar,.wf-text-block,.wf-btn,.wf-search-box,.wf-shape,.wf-text');
      if(!EDIT && !isInserted) return;

      if(!EDIT && isInserted){
        EDIT = true;
        parent.postMessage({ type: 'wf-request-edit' }, '*');
      }

      e.preventDefault();
      e.stopPropagation();
      clearHover();

      // 双击直接穿透选中具体的叶子元素
      selectElement(t, true);

      // 如果双击的是图片，唤起替换素材
      if(t.tagName === 'IMG' || (t.style && t.style.backgroundImage)){
        t.classList.add('wf-current-asset-target');
        parent.postMessage({ type: 'wf-pick-asset' }, '*');
        showToast('已唤起图片素材库，请选择新图片');
        return;
      }

      // 如果是文字或按钮标签，激活打字编辑
      var textTarget = findTextTarget(t) || t;
      if(textTarget){
        startTextEdit(textTarget);
      }
    }, true);

    // 拦截画布内部元素的原生拖拽，杜绝图片被拖动时生成原生副本
    document.addEventListener('dragstart', function(e){
      var isFromPalette = false;
      try {
        isFromPalette = !!(window.parent && window.parent.__wfDraggingComponent);
      } catch(err){}
      if(!isFromPalette){
        e.preventDefault();
      }
    }, true);

    // 接收从原子组件库拖入的 drop 事件，将方框或组件插入页面 HTML 并持久化
    document.addEventListener('dragover', function(e){
      e.preventDefault();
      if(e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    }, true);

    document.addEventListener('drop', function(e){
      e.preventDefault();
      e.stopPropagation();

      // 仅允许从左侧/底部组件库拖入的组件插入！杜绝画板内部图片/元素拖动时产生副本！
      var isFromPalette = false;
      try {
        isFromPalette = !!(window.parent && window.parent.__wfDraggingComponent);
      } catch(err){}
      if(!isFromPalette) return;

      var html = '';
      try {
        if(window.parent && window.parent.__wfDraggingComponent){
          html = window.parent.__wfDraggingComponent.html || '';
        }
      }catch(err){}
      if(!html && e.dataTransfer){
        html = e.dataTransfer.getData('application/wireforge-component') || e.dataTransfer.getData('text/html') || e.dataTransfer.getData('text/plain') || '';
      }
      if(!html) return;
      try {
        if(window.parent) window.parent.__wfDraggingComponent = null;
      }catch(err){}

      pushSnapshot();
      var temp = document.createElement('div');
      temp.innerHTML = html.trim();
      var newEl = temp.firstElementChild || temp;

      var targetX = Math.round(Math.max(16, Math.min(320, e.clientX - 20)));
      var targetY = Math.round(Math.max(60, Math.min(720, e.clientY - 20)));

      var isModal = newEl.classList && (newEl.classList.contains('wf-modal') || newEl.classList.contains('wf-bottom-sheet'));
      if(!isModal){
        newEl.classList.add('wf-el', 'wf-inserted-component');
        markInsertedAsGroup(newEl);
        newEl.style.position = 'absolute';
        newEl.style.left = targetX + 'px';
        newEl.style.top = targetY + 'px';
        newEl.style.zIndex = '999';
        if(!newEl.style.maxWidth && !newEl.style.width) newEl.style.maxWidth = '335px';
      } else {
        newEl.style.zIndex = '9999';
      }

      document.body.appendChild(newEl);
      ensureUid(newEl);

      selectElement(newEl);
      publishLayers();
      scheduleSave();
      showToast(isModal ? '组件已添加' : '组件已成组：单击移动整组，双击再选里面的单个元素');
    }, true);

    // 鼠标按下：拖动8点把手缩放、拖动元素位移、Alt/Shift快捷删除、单选元素
    document.addEventListener('mousedown', function(e){
      if(document.body.classList.contains('wf-interactive')) return;
      if(e.button === 0){
        try { parent.postMessage({ type: 'wf-frame-focus', pageId: PAGE_ID }, '*'); } catch(err) {}
      }
      var handle = e.target.closest ? e.target.closest('.wf-handle') : null;
      if(handle && (selectedEl || selectedEls.length > 0)){
        var handleLocked = false;
        var lockCheck = selectedEls.length ? selectedEls : [selectedEl];
        for(var lci = 0; lci < lockCheck.length; lci++){
          if(isLayerLocked(lockCheck[lci])) handleLocked = true;
        }
        if(handleLocked) return;
        e.preventDefault();
        e.stopPropagation();
        pushSnapshot();

        var dir = handle.getAttribute('data-dir');
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;

        // 多选：以包围盒为基准，按比例同时缩放每个选中元素
        if(selectedEls.length > 1){
          var minL = Infinity, minT = Infinity, maxR = -Infinity, maxB = -Infinity;
          var prep = [];
          for(var mi = 0; mi < selectedEls.length; mi++){
            var mEl = selectedEls[mi];
            if(!mEl || !mEl.isConnected) continue;
            var mRect = mEl.getBoundingClientRect();
            var mL = mRect.left + scrollX;
            var mT = mRect.top + scrollY;
            var mW = mRect.width;
            var mH = mRect.height;
            mEl.style.position = 'absolute';
            mEl.style.left = Math.round(mL) + 'px';
            mEl.style.top = Math.round(mT) + 'px';
            mEl.style.width = Math.round(mW) + 'px';
            mEl.style.height = Math.round(mH) + 'px';
            mEl.style.maxWidth = 'none';
            mEl.style.boxSizing = 'border-box';
            prep.push({ el: mEl, left: mL, top: mT, w: mW, h: mH });
            if(mL < minL) minL = mL;
            if(mT < minT) minT = mT;
            if(mL + mW > maxR) maxR = mL + mW;
            if(mT + mH > maxB) maxB = mT + mH;
          }
          var boxW = Math.max(1, maxR - minL);
          var boxH = Math.max(1, maxB - minT);
          var items = [];
          for(var mj = 0; mj < prep.length; mj++){
            var p = prep[mj];
            items.push({
              el: p.el,
              relL: (p.left - minL) / boxW,
              relT: (p.top - minT) / boxH,
              relW: p.w / boxW,
              relH: p.h / boxH
            });
          }
          resizing = {
            dir: dir,
            startX: e.clientX,
            startY: e.clientY,
            initW: boxW,
            initH: boxH,
            initLeft: minL,
            initTop: minT,
            multi: true,
            items: items,
            el: selectedEl
          };
          return;
        }

        var rect = selectedEl.getBoundingClientRect();
        resizing = {
          dir: dir,
          startX: e.clientX,
          startY: e.clientY,
          initW: rect.width,
          initH: rect.height,
          initLeft: rect.left + scrollX,
          initTop: rect.top + scrollY,
          el: selectedEl
        };
        return;
      }

      // 如果之前有正在打字编辑的文本，且当前点击的不是该文本自身：
      // 立即自动失焦并提交上一条文本编辑，绝不阻断本次单选或拖拽！
      var activeText = document.querySelector('[data-wf-editing-text="true"], [contenteditable="true"]');
      if(activeText && activeText !== e.target && !activeText.contains(e.target)){
        try {
          activeText.blur();
        } catch(err){}
      }

      // 如果点击的是当前正在编辑的文本或输入框内部，允许光标落点打字，不抢占为拖拽
      if(e.target.isContentEditable || (e.target.getAttribute && e.target.getAttribute('data-wf-editing-text') === 'true')){
        return;
      }

      if(e.button !== 0) return;

      var t = e.target;
      var isArtboardBg = !t || t === document || t === document.body || t === document.documentElement ||
                         (t.id && t.id === 'wf-artboard-bg') ||
                         (t.classList && (t.classList.contains('wireframe-root') || t.classList.contains('page-container') || t.classList.contains('mobile-screen')));
      if(isArtboardBg){
        if(!e.ctrlKey && !e.shiftKey && !e.metaKey){
          deselect();
        }
        marquee = {
          startX: e.clientX,
          startY: e.clientY,
          hasMoved: false
        };
        return;
      }

      // Alt + 点击快速删除（移除了 Shift，Shift 专用于多选）
      if(e.altKey && !e.shiftKey && !e.ctrlKey && !e.metaKey){
        e.preventDefault();
        e.stopPropagation();
        var toDel = t.closest ? (t.closest('.wf-el,.wf-btn,.wf-card,.wf-box,.wf-container,.wf-avatar,.wf-search-box,.wf-text-block,.wf-inserted-component,.wf-shape,.wf-shape-rect,.wf-shape-circle,.wf-shape-line,.wf-shape-card,.wf-text') || t) : t;
        if(isLayerLocked(toDel)){
          showToast('图层已锁定，先解锁再删除');
          return;
        }
        pushSnapshot();
        deselect();
        if(toDel && toDel.parentNode){
          toDel.parentNode.removeChild(toDel);
          scheduleSave();
          publishLayers();
          clearHover();
          showToast('已删除元素 (Ctrl+Z 可撤回)');
        }
        return;
      }

      var isInserted = t.closest && t.closest('.wf-inserted-component,.wf-box,.wf-container,.wf-avatar,.wf-text-block,.wf-btn,.wf-search-box,.wf-shape,.wf-text');
      if(!EDIT && !isInserted) return;

      if(!EDIT && isInserted){
        EDIT = true;
        document.body.classList.add('wf-edit-mode');
        parent.postMessage({ type: 'wf-request-edit' }, '*');
      }

      var isMulti = !!(e.ctrlKey || e.metaKey || e.shiftKey);
      var moveEl = resolveTargetElement(t, e) || t;
      if(isLayerLocked(moveEl)) return;

      if(isMulti){
        selectElement(moveEl, true, true);
      } else {
        if(selectedEls.length > 1 && selectedEls.includes(moveEl)){
          // 用户点击多选中的某个元素进行整体拖拽：保留当前多选集合
        } else {
          selectElement(moveEl, true, false);
        }
      }

      // 准备拖动位移：无论单选还是多选，收集 selectedEls 里的所有元素初始坐标
      if(selectedEls.length > 0){
        var items = [];
        var sX = window.pageXOffset || document.documentElement.scrollLeft || 0;
        var sY = window.pageYOffset || document.documentElement.scrollTop || 0;
        for(var si = 0; si < selectedEls.length; si++){
          var sEl = selectedEls[si];
          if(!sEl || sEl === document.body || sEl === document.documentElement) continue;
          if(isLayerLocked(sEl)) continue;
          var rect = sEl.getBoundingClientRect();
          var posVal = sEl.style.position || (window.getComputedStyle ? window.getComputedStyle(sEl).position : '');
          var isAbsolute = posVal === 'absolute';
          var curL = parseFloat(sEl.style.left);
          var curT = parseFloat(sEl.style.top);
          if(isAbsolute){
            if(isNaN(curL)) curL = rect.left + sX;
            if(isNaN(curT)) curT = rect.top + sY;
          } else {
            if(isNaN(curL)) curL = 0;
            if(isNaN(curT)) curT = 0;
          }
          items.push({
            el: sEl,
            initLeft: curL,
            initTop: curT,
            isAbsolute: isAbsolute
          });
        }
        drag = {
          items: items,
          startX: e.clientX,
          startY: e.clientY,
          hasMoved: false
        };
      } else {
        drag = null;
      }
    }, true);

    document.addEventListener('contextmenu', function(e){
      if(!EDIT) return;
      e.preventDefault();
      e.stopPropagation();
      var t = e.target;
      var hit = t ? (resolveTargetElement(t, e) || null) : null;
      if(hit && hit !== document.body && hit !== document.documentElement && !isLayerLocked(hit)){
        var already = selectedEls.indexOf(hit) !== -1;
        if(!already) selectElement(hit, true, false);
      }
      if(!selectedEls || !selectedEls.length) return;
      try {
        window.parent.postMessage({
          type: 'wf-context-menu',
          x: e.clientX,
          y: e.clientY
        }, '*');
      } catch(err) {}
    }, true);

    document.addEventListener('mousemove', function(e){
      if(resizing){
        var dx = e.clientX - resizing.startX;
        var dy = e.clientY - resizing.startY;
        var w = resizing.initW;
        var h = resizing.initH;
        var l = resizing.initLeft;
        var t = resizing.initTop;

        // 多选包围盒等比缩放
        if(resizing.multi && resizing.items){
          var multiMinW = 20;
          var multiMinH = 16;
          if(resizing.dir.indexOf('e') !== -1) w = Math.max(multiMinW, resizing.initW + dx);
          if(resizing.dir.indexOf('w') !== -1){
            w = Math.max(multiMinW, resizing.initW - dx);
            l = resizing.initLeft + (resizing.initW - w);
          }
          if(resizing.dir.indexOf('s') !== -1) h = Math.max(multiMinH, resizing.initH + dy);
          if(resizing.dir.indexOf('n') !== -1){
            h = Math.max(multiMinH, resizing.initH - dy);
            t = resizing.initTop + (resizing.initH - h);
          }
          for(var ri = 0; ri < resizing.items.length; ri++){
            var rit = resizing.items[ri];
            var nw = Math.max(2, rit.relW * w);
            var nh = Math.max(2, rit.relH * h);
            var nl = l + rit.relL * w;
            var nt = t + rit.relT * h;
            rit.el.style.position = 'absolute';
            rit.el.style.left = Math.round(nl) + 'px';
            rit.el.style.top = Math.round(nt) + 'px';
            rit.el.style.width = Math.round(nw) + 'px';
            rit.el.style.height = Math.round(nh) + 'px';
            rit.el.style.maxWidth = 'none';
            rit.el.style.boxSizing = 'border-box';
          }
          updateMultiTransformBox();
          return;
        }

        var isLine = resizing.el.classList && resizing.el.classList.contains('wf-shape-line');
        var minW = isLine ? 2 : 20;
        var minH = isLine ? 2 : 16;

        if(resizing.dir.indexOf('e') !== -1) w = Math.max(minW, resizing.initW + dx);
        if(resizing.dir.indexOf('w') !== -1){
          w = Math.max(minW, resizing.initW - dx);
          l = resizing.initLeft + (resizing.initW - w);
        }
        if(resizing.dir.indexOf('s') !== -1) h = Math.max(minH, resizing.initH + dy);
        if(resizing.dir.indexOf('n') !== -1){
          h = Math.max(minH, resizing.initH - dy);
          t = resizing.initTop + (resizing.initH - h);
        }

        // 头像始终正圆；纯圆仅在按住 Shift 时强制正圆
        if(resizing.el.classList){
          if(resizing.el.classList.contains('wf-avatar')){
            var sideA = Math.max(w, h);
            w = sideA;
            h = sideA;
          } else if(resizing.el.classList.contains('wf-shape-circle') && e.shiftKey){
            var sideC = Math.max(w, h);
            w = sideC;
            h = sideC;
          }
        }

        resizing.el.style.width = Math.round(w) + 'px';
        resizing.el.style.height = Math.round(h) + 'px';
        resizing.el.style.maxWidth = 'none';
        resizing.el.style.boxSizing = 'border-box';

        var rPos = resizing.el.style.position || (window.getComputedStyle ? window.getComputedStyle(resizing.el).position : '');
        if(resizing.dir.indexOf('w') !== -1){
          if(rPos === 'absolute'){
            resizing.el.style.left = Math.round(l) + 'px';
          } else {
            resizing.el.style.position = 'relative';
            var curRelL = parseFloat(resizing.el.style.left) || 0;
            resizing.el.style.left = Math.round(curRelL + (resizing.initW - w)) + 'px';
          }
        }
        if(resizing.dir.indexOf('n') !== -1){
          if(rPos === 'absolute'){
            resizing.el.style.top = Math.round(t) + 'px';
          } else {
            resizing.el.style.position = 'relative';
            var curRelT = parseFloat(resizing.el.style.top) || 0;
            resizing.el.style.top = Math.round(curRelT + (resizing.initH - h)) + 'px';
          }
        }

        if(selectedEls.length > 1) updateMultiTransformBox();
        else updateTransformBox(resizing.el);
        return;
      }

      if(marquee){
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft || 0;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop || 0;
        var curX = e.clientX + scrollX;
        var curY = e.clientY + scrollY;
        var startX = marquee.startX + scrollX;
        var startY = marquee.startY + scrollY;

        var mX = Math.min(startX, curX);
        var mY = Math.min(startY, curY);
        var mW = Math.abs(curX - startX);
        var mH = Math.abs(curY - startY);

        if(!marquee.hasMoved && (mW > 4 || mH > 4)){
          marquee.hasMoved = true;
          getMarqueeBox().style.display = 'block';
        }

        if(marquee.hasMoved){
          var mBox = getMarqueeBox();
          mBox.style.left = mX + 'px';
          mBox.style.top = mY + 'px';
          mBox.style.width = mW + 'px';
          mBox.style.height = mH + 'px';

          var candidates = getSelectableElements();
          var matched = [];
          for(var ci = 0; ci < candidates.length; ci++){
            var cEl = candidates[ci];
            var cRect = cEl.getBoundingClientRect();
            var cL = cRect.left + scrollX;
            var cT = cRect.top + scrollY;
            var cR = cL + cRect.width;
            var cB = cT + cRect.height;

            // AABB 矩形碰撞判定
            var isIntersect = !(cL > mX + mW || cR < mX || cT > mY + mH || cB < mY);
            if(isIntersect && !isLayerLocked(cEl) && !(cEl.getAttribute && cEl.getAttribute('data-wf-hidden') === '1')){
              matched.push(cEl);
            }
          }
          setMultiSelection(matched);
        }
        return;
      }

      if(drag){
        var dx = e.clientX - drag.startX;
        var dy = e.clientY - drag.startY;
        var dist = Math.hypot(dx, dy);

        // 仅当鼠标移动超过 3px 时才判定为用户想要拖拽移动
        if(!drag.hasMoved && dist > 3){
          pushSnapshot();
          drag.hasMoved = true;
          for(var di = 0; di < drag.items.length; di++){
            var it = drag.items[di];
            if(!it.isAbsolute){
              it.el.style.position = 'relative';
            }
            it.el.style.zIndex = '999';
          }
        }

        if(drag.hasMoved){
          for(var di = 0; di < drag.items.length; di++){
            var it = drag.items[di];
            it.el.style.left = Math.round(it.initLeft + dx) + 'px';
            it.el.style.top = Math.round(it.initTop + dy) + 'px';
          }
          if(selectedEls.length > 1){
            updateMultiTransformBox();
          } else if(selectedEl){
            updateTransformBox(selectedEl);
          }
        }
        return;
      }
    }, true);

    document.addEventListener('mouseup', function(){
      if(resizing){
        var rEl = resizing.el;
        scheduleSave();
        if(resizing.multi){
          showToast('已调整 ' + (resizing.items ? resizing.items.length : selectedEls.length) + ' 个元素尺寸并保存');
          if(selectedEls.length > 1) updateMultiTransformBox();
          else if(rEl) updateTransformBox(rEl);
          if(selectedEl) notifySelectedElementInfo(selectedEl);
        } else if(rEl){
          showToast('已调整尺寸: ' + Math.round(rEl.offsetWidth) + ' × ' + Math.round(rEl.offsetHeight) + ' 并保存');
          notifySelectedElementInfo(rEl);
        }
        resizing = null;
      }
      if(marquee){
        var mBox = document.getElementById('wf-marquee-box');
        if(mBox) mBox.style.display = 'none';
        if(marquee.hasMoved && selectedEls.length > 0){
          showToast('已框选 ' + selectedEls.length + ' 个元素，可整体拖拽移动');
        }
        marquee = null;
      }
      if(drag){
        if(drag.hasMoved){
          scheduleSave();
          var count = drag.items.length;
          showToast(count > 1 ? ('已更新 ' + count + ' 个元素位置并保存') : '已更新元素位置并保存');
          if(selectedEl) notifySelectedElementInfo(selectedEl);
        }
        drag = null;
      }
    }, true);

    window.addEventListener('scroll', function(){
      if(selectedEls.length > 1) updateMultiTransformBox();
      else if(selectedEl) updateTransformBox(selectedEl);
    }, true);
    window.addEventListener('resize', function(){
      if(selectedEls.length > 1) updateMultiTransformBox();
      else if(selectedEl) updateTransformBox(selectedEl);
    });

    document.addEventListener('keydown', function(e){
      var activeText = document.querySelector('[data-wf-editing-text="true"]');
      var isEditing = !!activeText || (e.target && (e.target.isContentEditable || e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA'));

      var isZ = e.key === 'z' || e.key === 'Z';
      var isY = e.key === 'y' || e.key === 'Y';
      if((e.ctrlKey || e.metaKey) && isZ && !e.shiftKey && !isEditing){
        e.preventDefault(); undo(); return;
      }
      if((e.ctrlKey || e.metaKey) && (isY || (isZ && e.shiftKey)) && !isEditing){
        e.preventDefault(); redo(); return;
      }

      if(isEditing) return;

      if((e.ctrlKey || e.metaKey) && (e.key === 'g' || e.key === 'G')){
        e.preventDefault();
        if(e.shiftKey) ungroupSelection();
        else groupSelection();
        return;
      }

      if(!e.ctrlKey && !e.metaKey && !e.altKey && (e.key === 'h' || e.key === 'H')){
        e.preventDefault();
        try { parent.postMessage({ type: 'wf-tool-key', key: 'H' }, '*'); } catch(err) {}
        return;
      }

      var isC = e.key === 'c' || e.key === 'C';
      var isV = e.key === 'v' || e.key === 'V';
      var isD = e.key === 'd' || e.key === 'D';

      // 复制元素 (Ctrl+C / ⌘C)
      if((e.ctrlKey || e.metaKey) && isC && !e.shiftKey){
        if(selectedEl && selectedEl !== document.body){
          e.preventDefault();
          e.stopPropagation();
          copyElement(selectedEl);
          return;
        }
      }

      // 粘贴元素 (Ctrl+V / ⌘V)
      if((e.ctrlKey || e.metaKey) && isV && !e.shiftKey){
        var data = getCopiedData();
        if(data && data.html){
          e.preventDefault();
          e.stopPropagation();
          pasteElement();
          return;
        }
      }

      // 快速克隆副本 (Figma Duplicate: Ctrl+D / ⌘D)
      if((e.ctrlKey || e.metaKey) && isD && !e.shiftKey){
        if(selectedEl && selectedEl !== document.body){
          e.preventDefault();
          e.stopPropagation();
          duplicateElement(selectedEl);
          return;
        }
      }

      // Delete / Backspace 删除选中元素（支持单个与多选批量删除）
      if((e.key === 'Delete' || e.key === 'Backspace') && selectedEls.length > 0 && !isEditing){
        e.preventDefault();
        var victims = [];
        for(var vi = 0; vi < selectedEls.length; vi++){
          if(selectedEls[vi] && !isLayerLocked(selectedEls[vi])) victims.push(selectedEls[vi]);
        }
        if(!victims.length){
          showToast('图层已锁定，先解锁再删除');
          return;
        }
        pushSnapshot();
        var count = victims.length;
        for(var di = 0; di < victims.length; di++){
          var it = victims[di];
          if(it && it.parentNode) it.parentNode.removeChild(it);
        }
        deselect();
        scheduleSave();
        publishLayers();
        showToast('已删除 ' + count + ' 个元素 (按 Ctrl+Z 撤回)');
        return;
      }

      // 方向键微调像素位置（支持多选元素同时平移微调）
      if(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key) && selectedEls.length > 0 && !isEditing){
        e.preventDefault();
        var step = e.shiftKey ? 10 : 1;
        var dx = 0, dy = 0;
        if(e.key === 'ArrowLeft') dx = -step;
        if(e.key === 'ArrowRight') dx = step;
        if(e.key === 'ArrowUp') dy = -step;
        if(e.key === 'ArrowDown') dy = step;

        for(var si = 0; si < selectedEls.length; si++){
          var sEl = selectedEls[si];
          if(isLayerLocked(sEl)) continue;
          var posVal = sEl.style.position || (window.getComputedStyle ? window.getComputedStyle(sEl).position : '');
          if(posVal !== 'absolute') sEl.style.position = 'relative';
          var curL = parseFloat(sEl.style.left) || 0;
          var curT = parseFloat(sEl.style.top) || 0;
          sEl.style.left = Math.round(curL + dx) + 'px';
          sEl.style.top = Math.round(curT + dy) + 'px';
        }
        if(selectedEls.length === 1) updateTransformBox(selectedEl);
        else updateMultiTransformBox();
        scheduleSave();
        return;
      }

      // [ 键和 ] 键快捷调整字号 (在选中文本元素时生效)
      if((e.key === '[' || e.key === ']') && selectedEl && selectedEl !== document.body && !isEditing){
        var tText = findTextTarget(selectedEl);
        if(tText){
          e.preventDefault();
          var curSize = parseInt(window.getComputedStyle(tText).fontSize, 10) || 14;
          var delta = e.key === ']' ? 2 : -2;
          var newSize = Math.max(10, Math.min(60, curSize + delta));
          pushSnapshot();
          tText.style.fontSize = newSize + 'px';
          var textNodes = selectedEl.querySelectorAll ? selectedEl.querySelectorAll(tText.tagName) : [];
          for(var ti = 0; ti < textNodes.length; ti++){
            if(textNodes[ti].tagName === tText.tagName) textNodes[ti].style.fontSize = newSize + 'px';
          }
          updateTransformBox(selectedEl);
          scheduleSave();
          try {
            window.parent.postMessage({
              type: 'wf-element-selected',
              info: {
                tagName: selectedEl.tagName.toLowerCase(),
                fontSize: newSize
              }
            }, '*');
          } catch(err){}
          showToast('已调整字号: ' + newSize + 'px');
          return;
        }
      }

      if(e.key === 'Escape'){
        deselect();
        cleanupStyles();
      }
    }, true);

    try { setTimeout(function(){ publishLayers(); publishFrameFill(); }, 80); } catch(e){}
  })();
  <\/script>`
  const payload = guard + runtime + sizer + editor
  if (/<\/body>/i.test(cleanHtml)) {
    return cleanHtml.replace(/<\/body>/i, `${payload}</body>`)
  }
  return cleanHtml + payload
}

// ===== 素材库选择与拖拽插入 =====
const showAssetPicker = ref(false)
const activeAssetCat = ref('all')
interface AssetItem {
  id: string
  name: string
  category: string
  url: string
}
const assetList = ref<AssetItem[]>([])

const defaultFallbackAssets: AssetItem[] = [
  { id: 'avatar-01', name: '商务头像 1', category: 'avatar', url: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80' },
  { id: 'avatar-02', name: '职场头像 2', category: 'avatar', url: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80' },
  { id: 'avatar-03', name: '极简头像 3', category: 'avatar', url: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80' },
  { id: 'avatar-04', name: '萌宠头像 4', category: 'avatar', url: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150&auto=format&fit=crop&q=80' },
  { id: 'banner-01', name: '科技质感背景', category: 'background', url: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80' },
  { id: 'banner-02', name: '暖色渐变背景', category: 'background', url: 'https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=400&auto=format&fit=crop&q=80' },
  { id: 'prod-01', name: '数码产品展示', category: 'product', url: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=300&auto=format&fit=crop&q=80' },
  { id: 'prod-02', name: '潮流生活鞋靴', category: 'product', url: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=300&auto=format&fit=crop&q=80' },
]

const assetCategories = [
  { id: 'all', name: '全部素材' },
  { id: 'avatar', name: '人物头像' },
  { id: 'product', name: '商品展示' },
  { id: 'background', name: '背景纹理' },
]

async function openAssetPicker() {
  showAssetPicker.value = true
  try {
    const res = await projectApi.getAssets()
    if (Array.isArray(res) && res.length > 0) {
      assetList.value = res.map((a: any) => ({
        id: a.id || a.assetId,
        name: a.name || a.id,
        category: a.category || 'other',
        url: a.url || `/api/assets/${a.id || a.assetId}`,
      }))
    } else {
      assetList.value = defaultFallbackAssets
    }
  } catch {
    assetList.value = defaultFallbackAssets
  }
}

const filteredAssets = computed(() => {
  const list = assetList.value.length ? assetList.value : defaultFallbackAssets
  if (activeAssetCat.value === 'all') return list
  return list.filter((a) => a.category === activeAssetCat.value)
})

function selectAsset(asset: AssetItem) {
  htmlFrameRef.value?.contentWindow?.postMessage({
    type: 'wf-replace-asset',
    src: asset.url,
  }, '*')
  showAssetPicker.value = false
}

function onSlotDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
}

/** 拖进来的组件以落点为中心，再整块收回画框，避免宽卡片从落点往右伸出一半 */
function fitComponentInFrame(x: number, y: number, html: string, canvasW: number, canvasH: number) {
  const open = html.trim().match(/^<[\w-]+[^>]*\bstyle="([^"]*)"/i)
  const style = open?.[1] || ''
  const px = (prop: string) => {
    const m = style.match(new RegExp('(?:^|;)\\s*' + prop + '\\s*:\\s*(\\d+(?:\\.\\d+)?)px', 'i'))
    return m ? Number(m[1]) : 0
  }
  const w = px('width')
  const h = px('height')
  let left = w > 0 ? x - w / 2 : x
  let top = h > 0 ? y - h / 2 : y
  if (w > 0) left = Math.min(Math.max(0, left), Math.max(0, canvasW - w))
  else left = Math.min(Math.max(0, left), canvasW)
  if (h > 0) top = Math.min(Math.max(0, top), Math.max(0, canvasH - h))
  else top = Math.min(Math.max(0, top), canvasH)
  return { x: Math.round(left), y: Math.round(top) }
}

function onSlotDrop(e: DragEvent) {
  const isFromPalette = !!(window as any).__wfDraggingComponent
  const html = (window as any).__wfDraggingComponent?.html || (isFromPalette ? e.dataTransfer?.getData('text/html') : '')
  if (html && isFromPalette) {
    e.preventDefault()
    e.stopPropagation()
    const rect = (e.currentTarget as HTMLElement)?.getBoundingClientRect()
    const slotW = canvasW.value || 375
    const slotH = canvasH.value || 812
    const dropX = rect && rect.width
      ? Math.round(Math.max(0, Math.min(slotW, (e.clientX - rect.left) * (slotW / rect.width))))
      : 20
    const dropY = rect && rect.height
      ? Math.round(Math.max(0, Math.min(slotH, (e.clientY - rect.top) * (slotH / rect.height))))
      : 220
    const fitted = fitComponentInFrame(dropX, dropY, html, slotW, slotH)
    insertComponent(html, fitted.x, fitted.y, false, true)
    ;(window as any).__wfDraggingComponent = null
  }
}

function onIframeMessage(e: MessageEvent) {
  // 关键：只处理当前组件 iframe 自己发出的消息。iframe 的 postMessage 会广播给
  // 父窗口所有监听器，若不校验来源，A 页微调保存会污染所有页面的 HTML（全变同一页）。
  if (e.source !== htmlFrameRef.value?.contentWindow) return
  const d = e.data as { type?: string; page?: string; html?: string; h?: number; src?: string } | null
  if (!d) return
  if (d.type === 'wf-nav' && d.page) {
    const uids = Array.isArray((d as any).uids) ? (d as any).uids.map(String) : []
    emit('navigate', d.page, uids)
  } else if (d.type === 'wf-back') {
    emit('back')
  } else if (d.type === 'wf-pick-asset') {
    openAssetPicker()
  } else if (d.type === 'wf-miss-click') {
    const rawUids = (d as any).uids
    emit('missClick', {
      x: Number((d as any).x) || 0,
      y: Number((d as any).y) || 0,
      uids: Array.isArray(rawUids) ? rawUids.map(String) : [],
    })
  } else if (d.type === 'wf-save' && typeof d.html === 'string' && d.html.length > 50) {
    isInternalSaving = true
    clearTimeout(saveResetTimer)
    emit('saveHtml', { pageId: props.page.id, html: d.html })
    saveResetTimer = setTimeout(() => {
      isInternalSaving = false
    }, 1500)
  } else if (d.type === 'wf-request-edit') {
    emit('requestEdit')
  } else if (d.type === 'wf-element-selected') {
    focusedLayerUid.value = (d as any).info?.layerUid || ''
    emit('elementSelected', (d as any).info)
  } else if (d.type === 'wf-element-deselected') {
    focusedLayerUid.value = ''
    emit('elementDeselected')
  } else if (d.type === 'wf-frame-fill') {
    emit('frameFill', String((d as any).color || ''))
  } else if (d.type === 'wf-selection') {
    const uids = Array.isArray((d as any).uids) ? (d as any).uids.map(String) : []
    emit('selectionChanged', uids)
  } else if (d.type === 'wf-context-menu') {
    const frame = htmlFrameRef.value
    const rect = frame?.getBoundingClientRect()
    const localX = Number((d as any).x) || 0
    const localY = Number((d as any).y) || 0
    const scaleX = frame && frame.clientWidth ? (rect?.width || 0) / frame.clientWidth : 1
    const scaleY = frame && frame.clientHeight ? (rect?.height || 0) / frame.clientHeight : 1
    emit('contextMenu', {
      x: (rect?.left || 0) + localX * scaleX,
      y: (rect?.top || 0) + localY * scaleY,
    })
  } else if (d.type === 'wf-hotspot') {
    const box = d as any
    emit('hotspot', {
      x: Number(box.x) || 0,
      y: Number(box.y) || 0,
      w: Number(box.w) || 0,
      h: Number(box.h) || 0,
      label: String(box.label || ''),
      uid: String(box.uid || ''),
    })
  } else if (d.type === 'wf-hotspot-clear') {
    emit('hotspotClear')
  } else if (d.type === 'wf-layers') {
    emit('layers-changed', { pageId: props.page.id, layers: (d as any).layers || [] })
  } else if (d.type === 'wf-frame-focus') {
    emit('frameFocus')
  } else if (d.type === 'wf-edit-vector') {
    emit('editVector', d)
  } else if (d.type === 'wf-size' && typeof d.h === 'number' && d.h > 0) {
    const target = props.page.canvas_height
    iframeH.value = target && target > 0 ? Math.min(Math.round(d.h), target) : Math.round(d.h)
    if (Date.now() < suppressFitUntil) return
    // 高度兜底：实测高度比设计稿画布高度超出 8% 时，通知 iframe 等比缩放收进画布高度
    if (!fitSent.value && target && target > 0 && d.h > target * 1.08) {
      fitSent.value = true
      htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-fit', h: target }, '*')
    }
  }
}
onMounted(() => window.addEventListener('message', onIframeMessage))
onUnmounted(() => window.removeEventListener('message', onIframeMessage))

function triggerHotspots() {
  // 热区提示已完全停用
}

const canvasW = computed(() => props.page.canvas_width || 375)
const canvasH = computed(() => props.page.canvas_height || 812)

// 容器/列表：内部已有独立子元素时，抑制其自身的内部装饰（占位行等），只画外框
const suppressInnerMap = computed<Record<number, boolean>>(() => {
  const els = props.page.elements
  const map: Record<number, boolean> = {}
  for (const outer of els) {
    if (!['container', 'list'].includes(outer.type)) continue
    let count = 0
    for (const inner of els) {
      if (inner.id === outer.id) continue
      if (inner.width * inner.height >= outer.width * outer.height * 0.8) continue
      const cx = inner.x + inner.width / 2
      const cy = inner.y + inner.height / 2
      if (cx >= outer.x && cx <= outer.x + outer.width && cy >= outer.y && cy <= outer.y + outer.height) {
        count++
      }
    }
    if (count >= 1) map[outer.id] = true
  }
  return map
})

// 导航栏/选项卡：AI 常把导航项再输出成独立的 icon/tabs 元素，造成双重渲染。
// 当 navbar/tabs 内部包含 ≥2 个小元素时，隐藏这些内部重复元素，由 navbar 自身统一渲染图标+文字。
// 但如果 outer 自身 label 过短（<2字符），它无法提供有意义的统一内容，此时保留内部子元素自行渲染。
const hiddenDupIds = computed<Set<number>>(() => {
  const els = props.page.elements
  const hidden = new Set<number>()
  for (const outer of els) {
    if (!['navbar', 'tabs'].includes(outer.type)) continue
    const outerLabel = (outer.label || '').trim()
    if (outerLabel.length < 2) continue
    const inners = els.filter((inner) => {
      if (inner.id === outer.id) return false
      if (!['icon', 'tabs'].includes(inner.type)) return false
      if (inner.width * inner.height >= outer.width * outer.height * 0.8) return false
      const cx = inner.x + inner.width / 2
      const cy = inner.y + inner.height / 2
      return cx >= outer.x && cx <= outer.x + outer.width && cy >= outer.y && cy <= outer.y + outer.height
    })
    if (inners.length >= 2) inners.forEach((i) => hidden.add(i.id))
  }

  // 通用重复去重：AI 有时把同一文本输出两次（如按钮自带 label 又套一层 text 元素），
  // 文案相同且重叠面积超过较小元素 50% 时，隐藏被包含/面积更小的那个，消除重复文字与相互遮挡
  const norm = (s: string) =>
    (s || '')
      .replace(/[\s\p{P}\p{S}]+/gu, '')
      .toLowerCase()
  const overlapRatio = (a: Element, b: Element) => {
    const x1 = Math.max(a.x, b.x)
    const y1 = Math.max(a.y, b.y)
    const x2 = Math.min(a.x + a.width, b.x + b.width)
    const y2 = Math.min(a.y + a.height, b.y + b.height)
    if (x2 <= x1 || y2 <= y1) return 0
    return ((x2 - x1) * (y2 - y1)) / Math.max(1, Math.min(a.width * a.height, b.width * b.height))
  }
  for (let i = 0; i < els.length; i++) {
    for (let j = i + 1; j < els.length; j++) {
      const a = els[i]
      const b = els[j]
      if (hidden.has(a.id) || hidden.has(b.id)) continue
      if (a.type === 'background' || b.type === 'background') continue
      if (a.type === 'container' && b.type === 'container') continue // 卡片嵌套是正常结构
      const la = norm(a.label)
      const lb = norm(b.label)
      if (!la || la !== lb) continue
      if (overlapRatio(a, b) < 0.5) continue
      hidden.add(a.width * a.height <= b.width * b.height ? a.id : b.id)
    }
  }
  return hidden
})
const isDesignFrame = computed(() => {
  if (props.frameType === 'design') return true;
  if (props.frameType === 'prototype') return false;
  return !!(props.page.background_image && !props.page.html_content);
})
const showDesign = computed(() => props.showDesign ?? true)
const boxW = computed(() => props.boxW ?? 220)
const gap = computed(() => props.gap ?? 16)
const scale = computed(() => props.canvasScale || 1)

// 背景类元素（type: 'background'）单独作为画布底层背景渲染，其余元素正常叠加
const backgroundEls = computed(() => props.page.elements.filter((e) => e.type === 'background'))

// 画布底色：优先用 AI 从设计稿取样的真实背景色（style.fill / style.gradient），
// 没有颜色信息时才回退素材图/默认灰。解决"黄色设计稿配紫色渐变素材"的失真问题
const canvasBgStyle = computed<Record<string, string>>(() => {
  for (const bg of backgroundEls.value) {
    if (!bg.style) continue
    try {
      const o = JSON.parse(bg.style) as Record<string, unknown>
      const out: Record<string, string> = {}
      const isColor = (v: unknown) => typeof v === 'string' && /^(#([0-9a-f]{3,8})|rgb)/i.test(v.trim())
      if (typeof o.gradient === 'string' && o.gradient.trim()) out.background = o.gradient.trim()
      else if (isColor(o.fill)) out.background = (o.fill as string).trim()
      if (out.background) return out
    } catch {
      /* 非法 JSON 忽略，尝试下一个 background 元素 */
    }
  }
  return {}
})

/** 画布是否有 AI 取样的真实背景色（有则不渲染素材底图层，颜色优先） */
const hasBgColor = computed(() => Object.keys(canvasBgStyle.value).length > 0)

const normalEls = computed(
  () => props.page.elements.filter((e) => e.type !== 'background' && !hiddenDupIds.value.has(e.id)),
)

function assetUrlFor(el: Element): string {
  return el.asset_id ? `/api/assets/${el.asset_id}` : ''
}

const bgErrors = ref<Record<number, boolean>>({})
function onBgError(id: number) {
  bgErrors.value[id] = true
}

const rootRef = ref<HTMLElement | null>(null)
const panelHeaderRef = ref<HTMLElement | null>(null)
const panelBodyRef = ref<HTMLElement | null>(null)
const boxRefs = new Map<number, HTMLElement>()

function setBoxRef(annId: number, el: HTMLElement | null) {
  if (el) boxRefs.set(annId, el)
  else boxRefs.delete(annId)
}

const editingAnnId = ref<number | null>(null)
const editTitle = ref('')
const editText = ref('')
const editingTextareaRef = ref<HTMLTextAreaElement | null>(null)
const editingTitleInputRef = ref<HTMLInputElement | null>(null)

const draggingAnnId = ref<number | null>(null)
const dragOverAnnId = ref<number | null>(null)

function onCanvasDragStart(e: DragEvent, annId: number) {
  draggingAnnId.value = annId
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(annId))
  }
}

function onCanvasDragOver(e: DragEvent, targetId: number) {
  if (draggingAnnId.value === targetId) return
  dragOverAnnId.value = targetId
}

function onCanvasDragLeave(e: DragEvent, targetId: number) {
  if (dragOverAnnId.value === targetId) dragOverAnnId.value = null
}

function onCanvasDrop(e: DragEvent, targetId: number) {
  const sourceId = draggingAnnId.value
  if (!sourceId || sourceId === targetId) {
    dragOverAnnId.value = null
    draggingAnnId.value = null
    return
  }

  const currentList = [...annItems.value]
  const srcIdx = currentList.findIndex((a) => a.id === sourceId)
  const tgtIdx = currentList.findIndex((a) => a.id === targetId)

  if (srcIdx !== -1 && tgtIdx !== -1) {
    const [moved] = currentList.splice(srcIdx, 1)
    currentList.splice(tgtIdx, 0, moved)
    const newOrder = currentList.map((a) => a.id)
    emit('annOrderChange', props.page.id, newOrder)
    nextTick(recomputeLines)
  }

  dragOverAnnId.value = null
  draggingAnnId.value = null
}

function onCanvasDragEnd() {
  dragOverAnnId.value = null
  draggingAnnId.value = null
}

// 线框画布 X 偏移：左侧为设计稿原图
const wireX = computed(() => 0)

// ===== 说明面板状态 =====
const panelOpen = ref(true)
// 选中的线框元素属于本页时自动展开面板
const selectedOnThisPage = computed(
  () => props.selectedElementId != null && props.page.elements.some((e) => e.id === props.selectedElementId),
)
const isPanelOpen = computed(() => panelOpen.value || selectedOnThisPage.value)

const panelX = computed(() => wireX.value + canvasW.value * scale.value + gap.value + 12)
const panelMaxH = computed(() => Math.max(canvasH.value * scale.value, 320))

interface AnnItem {
  id: number
  index: number
  elementId: number | null
  annotation: Annotation
  title: string
  anchorX: number
  anchorY: number
  interactionType?: 'navigate' | 'modal' | 'toggle' | null
  interactionLabel?: string | null
  interactionTarget?: string | null
}

const annItems = computed<AnnItem[]>(() => {
  const s = scale.value
  const rawList = props.page.annotations.map((ann, idx) => {
    const el = props.page.elements.find((e) => e.id === ann.element_id)
    const logicalX = el ? el.x + el.width / 2 : (ann.x ?? 100)
    const logicalY = el ? el.y + Math.min(16, el.height / 2) : (ann.y ?? 100)

    let interactionType: 'navigate' | 'modal' | 'toggle' | null = null
    let interactionLabel: string | null = null
    let interactionTarget: string | null = null

    if (el) {
      if (el.interaction) {
        if (el.interaction.action === 'navigate') {
          interactionType = 'navigate'
          interactionLabel = '页面跳转'
          if (el.interaction.target_page_id && props.allPages) {
            const targetP = props.allPages.find((p) => p.id === el.interaction?.target_page_id)
            if (targetP) interactionTarget = targetP.name
          }
          if (!interactionTarget && el.interaction.params) {
            try {
              const p = JSON.parse(el.interaction.params)
              interactionTarget = p.target_name || null
            } catch {
              interactionTarget = el.interaction.params
            }
          }
        } else if (el.interaction.action === 'modal') {
          interactionType = 'modal'
          interactionLabel = '唤起弹窗'
          interactionTarget = el.interaction.params || '业务弹窗'
        }
      } else if (el.type === 'switch' || el.type === 'checkbox') {
        interactionType = 'toggle'
        interactionLabel = el.type === 'switch' ? '开关切换' : '勾选切换'
      }
    }

    const customTitle = props.customTitles?.[ann.id]
    const effectiveTitle = customTitle || el?.label || `说明 ${idx + 1}`

    return {
      id: ann.id,
      index: idx,
      elementId: el?.id ?? null,
      annotation: ann,
      title: effectiveTitle,
      anchorX: logicalX * s + wireX.value,
      anchorY: logicalY * s,
      interactionType,
      interactionLabel,
      interactionTarget,
    }
  })

  // 按用户调整后的自定义顺序排序
  const customOrder = props.customOrders?.[props.page.id]
  if (customOrder && customOrder.length) {
    return [...rawList].sort((a, b) => {
      const idxA = customOrder.indexOf(a.id)
      const idxB = customOrder.indexOf(b.id)
      if (idxA !== -1 && idxB !== -1) return idxA - idxB
      if (idxA !== -1) return -1
      if (idxB !== -1) return 1
      return 0
    })
  }

  return rawList
})

// ===== 引线：从元素锚点连到面板中对应说明条目的位置（DOM 实测，随滚动更新） =====
const activeLines = ref<{ id: number; path: string }[]>([])

function clientToStageRatio(): number {
  if (!rootRef.value) return 1
  const rect = rootRef.value.getBoundingClientRect()
  return rect.width && stageW.value ? rect.width / stageW.value : 1
}

function recomputeLines() {
  const list: { id: number; path: string }[] = []
  const rootRect = rootRef.value?.getBoundingClientRect()
  if (!rootRect) {
    activeLines.value = list
    return
  }
  const ratio = clientToStageRatio()
  const bodyRect = panelBodyRef.value?.getBoundingClientRect()

  for (const ap of annItems.value) {
    const visible =
      (props.selectedElementId != null && ap.elementId === props.selectedElementId) ||
      (props.hoveredAnnId != null && ap.id === props.hoveredAnnId) ||
      (props.hoveredElementId != null && ap.elementId === props.hoveredElementId)
    if (!visible) continue

    // 终点：面板展开时指向对应条目（限制在面板可视区内），收起时指向面板头
    let endX = panelX.value
    let endY = 24
    const boxEl = isPanelOpen.value ? boxRefs.get(ap.id) : null
    const targetEl = boxEl ?? panelHeaderRef.value
    if (targetEl) {
      const r = targetEl.getBoundingClientRect()
      let cy = r.top + r.height / 2
      if (boxEl && bodyRect) {
        cy = Math.max(bodyRect.top + 8, Math.min(cy, bodyRect.bottom - 8))
      }
      endX = (r.left - rootRect.left) / ratio
      endY = (cy - rootRect.top) / ratio
    }

    const sx = ap.anchorX
    const midX = Math.min(endX - 12, Math.max(sx + 10, endX - 8))
    list.push({
      id: ap.id,
      path: `M ${sx} ${ap.anchorY} L ${midX} ${ap.anchorY} L ${midX} ${endY} L ${endX} ${endY}`,
    })
  }
  activeLines.value = list
}

watch(
  () => [props.selectedElementId, props.hoveredAnnId, props.hoveredElementId, isPanelOpen.value, annItems.value],
  () => nextTick(recomputeLines),
  { immediate: true, deep: false },
)

// 选中元素时把对应说明滚动到可见位置
watch(
  () => props.selectedElementId,
  (elId) => {
    if (elId == null) return
    nextTick(() => {
      const target = annItems.value.find((a) => a.elementId === elId)
      if (target) {
        boxRefs.get(target.id)?.scrollIntoView({ block: 'nearest' })
      }
      recomputeLines()
    })
  },
)
const hasAnnotations = computed(() => !!(props.showAnnotations && annItems.value.length))

const stageW = computed(() => {
  if (hasAnnotations.value) {
    return panelX.value + boxW.value + 28
  }
  
  return canvasW.value * scale.value
})
// 整页模式：高度随 iframe 内容自适应（避免固定高度把页面裁掉产生滚动）；预览模式去除额外的 8px 安全边距
const stageH = computed(() => {
  const baseH = canvasH.value * scale.value
  if (!showDesign.value && !hasAnnotations.value) {
    return baseH
  }
  return baseH + 8
})

function hasNavigate(el: Element): boolean {
  return el.interaction?.action === 'navigate' && !!el.interaction?.target_page_id
}

function onElementClick(el: Element) {
  emit('elementClick', el)
}

function onElementHover(el: Element, on: boolean) {
  emit('elementHover', el, on)
}

function onAnnHover(annId: number | null) {
  emit('annHover', annId)
}

function onAnnClick(ap: AnnItem) {
  emit('annClick', ap.id)
}

// ===== 双击/点击编辑标题与说明 =====
function startEdit(ap: AnnItem) {
  editingAnnId.value = ap.id
  editTitle.value = ap.title
  editText.value = ap.annotation.text || ''
  nextTick(() => {
    editingTitleInputRef.value?.focus()
    editingTitleInputRef.value?.select()
  })
}

function finishEdit(ap: AnnItem) {
  if (editingAnnId.value !== ap.id) return
  const t = editTitle.value.trim() || ap.title
  const txt = editText.value.trim()
  editingAnnId.value = null
  emit('annSave', ap.id, txt, t)
  nextTick(recomputeLines)
}

function cancelEdit() {
  editingAnnId.value = null
}

function insertComponent(html: string, dropX = 20, dropY = 220, autoEditText = false, fitInside = false) {
  if (!html) return
  htmlFrameRef.value?.contentWindow?.postMessage({
    type: 'wf-insert-html',
    html,
    dropX,
    dropY,
    autoEditText,
    fitInside,
  }, '*')
}

function copySelectedElement() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-copy' }, '*')
}

function pasteCopiedElement(x?: number, y?: number) {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-paste', x, y }, '*')
}

function duplicateSelectedElement() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-duplicate' }, '*')
}

function alignSelectedElement(alignType: string) {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-align', alignType }, '*')
}

function postToFrame(payload: Record<string, unknown>) {
  const uid = (payload.uid as string) || focusedLayerUid.value
  htmlFrameRef.value?.contentWindow?.postMessage(uid ? { ...payload, uid } : payload, '*')
}

function updateElementRadius(radius: number) {
  postToFrame({ type: 'wf-radius', radius })
}

function updateElementStroke(stroke: { width: number; color: string; style: string }) {
  postToFrame({ type: 'wf-stroke', ...stroke })
}

function updateElementEffects(effects: unknown[], live = false) {
  postToFrame({ type: 'wf-effects', effects, live })
}

function updateElementShadow(shadow: string) {
  postToFrame({ type: 'wf-shadow', shadow })
}

function updateElementColor(color: string) {
  postToFrame({ type: 'wf-color', color })
}

function updateFrameColor(color: string) {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-frame-color', color }, '*')
}

function updateElementFontSize(delta: number) {
  postToFrame({ type: 'wf-font-size', delta })
}

function startTextEdit() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-start-text-edit' }, '*')
}

function updateText(text: string) {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-update-text', text }, '*')
}

function selectParentContainer() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-select-parent' }, '*')
}

function deleteSelectedElement() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-delete' }, '*')
}

function openAssetPickerForSelected() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-open-asset-picker' }, '*')
}

function updateElementPosition(key: 'x' | 'y', val: number, uid?: string) {
  postToFrame({ type: 'wf-layout', key, val, uid })
}

function updateElementDimension(key: 'width' | 'height', val: number, uid?: string) {
  postToFrame({ type: 'wf-layout', key, val, uid })
}

function selectLayerByUid(uid: string, multi = false) {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-select-uid', uid, multi }, '*')
}

function hideVectorOriginal(elementUid: string) {
  postToFrame({ type: 'wf-hide-vector-original', elementUid })
}

function restoreVectorOriginal(elementUid: string) {
  postToFrame({ type: 'wf-restore-vector-original', elementUid })
}

function removeVectorOriginal(elementUid: string) {
  postToFrame({ type: 'wf-remove-vector-original', elementUid })
}

function replaceVectorOriginal(elementUid: string, newHtml: string) {
  postToFrame({ type: 'wf-replace-vector-original', elementUid, newHtml })
}

function insertVectorShapes(shapes: any[], elementUid?: string, select = true) {
  postToFrame({ type: 'wf-insert-vector-shapes', shapes, elementUid, select })
}

// 暴露尺寸与热区提示及组件插入/复制/粘贴/样式修改方法，供父组件调用
defineExpose({
  stageW,
  stageH,
  triggerHotspots,
  insertComponent,
  copySelectedElement,
  pasteCopiedElement,
  duplicateSelectedElement,
  deleteSelectedElement,
  alignSelectedElement,
  updateElementRadius,
  updateElementStroke,
  updateElementEffects,
  updateElementShadow,
  updateElementColor,
  updateFrameColor,
  updateElementFontSize,
  updateElementPosition,
  updateElementDimension,
  selectLayerByUid,
  startTextEdit,
  updateText,
  selectParentContainer,
  openAssetPickerForSelected,
  hideVectorOriginal,
  restoreVectorOriginal,
  removeVectorOriginal,
  replaceVectorOriginal,
  insertVectorShapes,
  stampElementNav,
})
</script>

<style scoped lang="scss">
.page-canvas {
  position: relative;
  flex-shrink: 0;
}

.design-slot {
  position: absolute;
  top: 0;
  overflow: hidden;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.12);

  .design-img {
    width: 100%;
    height: 100%;
    object-fit: fill;
    user-select: none;
  }
  .design-empty {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f3f4f6;
    color: #9ca3af;
    font-size: 12px;
  }
}

.canvas-slot {
  position: absolute;
  top: 0;

  /* ===== Stitch 式整页直出视图 ===== */
  .html-frame {
    display: block;
    border: none;
    background: #fff;
    border-radius: 14px;
    box-shadow: 0 10px 34px rgba(0, 0, 0, 0.35);
    /* 防止 GPU 合成层导致文字渲染模糊：将 iframe 固定在整像素边界 */
    transform: translateZ(0);
    -webkit-font-smoothing: subpixel-antialiased;
    image-rendering: -webkit-optimize-contrast;
  }

  /* ===== 整页/组件视图切换 ===== */
  .mode-toggle {
    position: absolute;
    top: -30px;
    right: 0;
    z-index: 30;
    display: flex;
    gap: 2px;
    padding: 3px;
    background: rgba(255, 255, 255, 0.12);
    border-radius: 8px;
    backdrop-filter: blur(6px);

    button {
      padding: 3px 12px;
      font-size: 12px;
      color: rgba(255, 255, 255, 0.75);
      background: transparent;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s;

      &:hover {
        color: #fff;
      }

      &.active {
        color: #1a1a24;
        background: #fff;
        font-weight: 600;
      }
    }
  }
  overflow: visible;
}

.page-canvas.preview-mode {
  .canvas-slot {
    left: 0 !important;
    .html-frame {
      border-radius: 0;
      box-shadow: none;
    }
  }
}

.canvas {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  overflow: hidden;
  background: #fafafa;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.12);

  .element-layer {
    position: absolute;
    inset: 0;
    z-index: 1;
  }

  /* 背景素材铺底层 */
  .canvas-bg-layer {
    position: absolute;
    inset: 0;
    z-index: 0;
    overflow: hidden;
  }

  .canvas-bg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    z-index: 0;
  }
}

.leader-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}

.leader-line {
  stroke: #9ca3af;
  stroke-width: 1;
  transition: stroke 0.15s, stroke-width 0.15s;
  pointer-events: none;

  &.highlight {
    stroke: #0d99ff;
    stroke-width: 1.5;
  }
}

/* ===== 说明面板 ===== */
.ann-panel {
  position: absolute;
  top: 0;
  z-index: 30;
  width: 240px;
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  background: #ffffff;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  box-shadow: 0 4px 16px -2px rgba(15, 23, 42, 0.08), 0 2px 6px -1px rgba(15, 23, 42, 0.04);
  overflow: hidden;
  transition: width 0.2s ease;

  &:not(.open) {
    width: 140px;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 12px;
    font-size: 12px;
    font-weight: 600;
    color: #000000;
    cursor: pointer;
    user-select: none;
    flex-shrink: 0;
    background: #fafafa;
    border-bottom: 1px solid #f5f5f5;

    &:hover {
      background: #f5f5f5;
    }
    .panel-chevron {
      color: #333333;
    }
  }

  .panel-body {
    flex: 1;
    overflow-y: auto;
    padding: 8px 10px 10px;
  }
}

.ann-box {
  background: #ffffff;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 8px 10px;
  margin-top: 8px;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: #8fd0ff;
    background: #f2f9ff;
    box-shadow: 0 2px 8px rgba(13, 153, 255, 0.12);

    .ann-edit-btn {
      opacity: 1;
    }
  }
  &.selected {
    border-color: #0d99ff;
    background: #e5f4ff;
    box-shadow: 0 0 0 1px #0d99ff, 0 2px 10px rgba(13, 153, 255, 0.15);
  }
  &.dragging {
    opacity: 0.4;
    border-style: dashed;
    border-color: #0d99ff;
  }
  &.drag-over {
    border-color: #0d99ff;
    background: #e5f4ff;
    transform: translateY(-2px);
    box-shadow: 0 -3px 0 0 #0d99ff;
  }

  .ann-head {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
    gap: 4px;

    .ann-drag-handle {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #333333;
      cursor: grab;
      padding: 1px;
      border-radius: 4px;

      &:hover {
        color: #0d99ff;
        background: #e5e5e5;
      }
      &:active {
        cursor: grabbing;
      }
    }
    .ann-title {
      flex: 1;
      font-weight: 600;
      font-size: 11px;
      color: #000000;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .ann-edit-btn {
      opacity: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 2px;
      border: none;
      background: transparent;
      color: #333333;
      border-radius: 4px;
      cursor: pointer;
      transition: opacity 0.15s, color 0.15s;

      &:hover {
        color: #0d99ff;
        background: #e5e5e5;
      }
    }
    .ann-badge {
      flex-shrink: 0;
      font-size: 9px;
      font-weight: 600;
      padding: 1.5px 5px;
      border-radius: 4px;
      margin-left: 4px;
      letter-spacing: 0.2px;

      &.navigate {
        background: #ccfbf1;
        color: #0f766e;
        border: 1px solid #99f6e4;
      }
      &.modal {
        background: #ecfeff;
        color: #0e7490;
        border: 1px solid #cffafe;
      }
      &.toggle {
        background: #fffbeb;
        color: #b45309;
        border: 1px solid #fef3c7;
      }
    }
  }
  .ann-target-hint {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 4px;
    padding: 2px 6px;
    background: #fafafa;
    border-radius: 4px;
    font-size: 10px;
    color: #333333;

    .hint-dot {
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: #0d99ff;
    }
    .hint-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .ann-text {
    font-size: 11px;
    color: #000000;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;

    &.empty {
      color: #333333;
      font-style: italic;
    }
  }

  /* Inline editing form */
  .ann-edit-form {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .edit-row {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .edit-label {
        font-size: 10px;
        font-weight: 600;
        color: #333333;
      }
    }

    .ann-edit-title-input {
      width: 100%;
      border: 1px solid #0d99ff;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      padding: 3px 6px;
      font-family: inherit;
      color: #000000;
      outline: none;
      box-sizing: border-box;
      background: #ffffff;
      box-shadow: 0 0 0 2px rgba(13, 153, 255, 0.15);
    }

    .ann-edit-textarea {
      width: 100%;
      border: 1px solid #0d99ff;
      border-radius: 4px;
      font-size: 11px;
      line-height: 1.4;
      padding: 4px 6px;
      resize: vertical;
      font-family: inherit;
      color: #000000;
      outline: none;
      box-sizing: border-box;
      background: #ffffff;
      box-shadow: 0 0 0 2px rgba(13, 153, 255, 0.15);
    }

    .ann-edit-actions {
      display: flex;
      justify-content: flex-end;
      gap: 6px;
      margin-top: 2px;

      button {
        font-size: 10px;
        padding: 2px 8px;
        border-radius: 4px;
        cursor: pointer;
        font-weight: 500;
      }
      .btn-cancel {
        border: 1px solid #d4d4d4;
        background: #ffffff;
        color: #333333;
        &:hover {
          background: #f5f5f5;
        }
      }
      .btn-save {
        border: none;
        background: #0d99ff;
        color: #ffffff;
        &:hover {
          background: #0b7ed4;
        }
      }
    }
  }
}
</style>
