<template>
  <div
    ref="rootRef"
    class="page-canvas"
    :class="{ 'preview-mode': !showDesign && !showAnnotations }"
    :style="{ width: `${stageW}px`, height: `${stageH}px` }"
  >
    <!-- 设计稿原图（并列展示） -->
    <div
      v-if="showDesign"
      class="design-slot"
      :style="{
        left: `0px`,
        width: `${canvasW * scale}px`,
        height: `${canvasH * scale}px`,
      }"
    >
      <img
        v-if="page.background_image"
        :src="getFileUrl(page.background_image)"
        class="design-img"
        loading="lazy"
        decoding="async"
        draggable="false"
      />
      <div v-else class="design-empty">设计稿</div>
    </div>

    <!-- 线框画布（可交互原型） -->
    <div
      class="canvas-slot"
      :style="{
        left: `${wireX}px`,
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
        :style="{ width: `${canvasW * scale}px`, height: `${(iframeH || canvasH) * scale}px` }"
        :srcdoc="navRuntimeHtml"
        sandbox="allow-scripts"
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
                {{ ap.interactionType === 'navigate' ? '⚡ 跳转' : (ap.interactionType === 'modal' ? '⚡ 弹窗' : '⚡ 切换') }}
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
      title="🖼️ 素材库替换图片/头像"
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
  }>(),
  {
    showWireframe: true,
    showAnnotations: true,
    selectedElementId: null,
    hoveredElementId: null,
    hoveredAnnId: null,
    showDesign: true,
    boxW: 220,
    gap: 16,
    canvasScale: 1,
    editMode: false,
    interactive: false,
    lockedByOther: null,
  },
)

const emit = defineEmits<{
  (e: 'elementClick', el: Element): void
  (e: 'elementHover', el: Element, on: boolean): void
  (e: 'annHover', annId: number | null): void
  (e: 'annClick', annId: number): void
  (e: 'annSave', annId: number, text: string, title?: string): void
  (e: 'annOrderChange', pageId: number, order: number[]): void
  (e: 'navigate', pageName: string): void
  (e: 'back'): void
  (e: 'saveHtml', payload: { pageId: number; html: string }): void
  (e: 'lockedClick'): void
  (e: 'missClick'): void
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

function sendEditMode() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-edit', on: !!props.editMode }, '*')
}

function sendInteractiveMode() {
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-interactive', on: !!props.interactive }, '*')
}

function onFrameLoad() {
  sendEditMode()
  sendInteractiveMode()
}

watch(
  () => props.interactive,
  () => {
    sendInteractiveMode()
  },
)

function injectNavRuntime(html: string, initialInteractive = false): string {
  // 已注入过（用户保存过微调后的完整文档自带运行时）则不重复注入
  if (html.includes('data-wf-inject')) {
    return html
  }
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
    *{max-width:100%}
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
      outline: 2.5px solid #10b981 !important;
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
    /* Figma 经典青色热区脉冲发光与波纹动画 */
    .wf-hotspot-hint-pulse {
      position: relative !important;
      outline: 2.5px solid #06b6d4 !important;
      outline-offset: 2px !important;
      box-shadow: 0 0 0 4px rgba(6, 182, 212, 0.45), 0 0 22px rgba(6, 182, 212, 0.7) !important;
      background-color: rgba(6, 182, 212, 0.18) !important;
      animation: wfHotspotPulse 0.75s ease-out !important;
      border-radius: 8px !important;
      transition: all 0.2s ease !important;
    }
    @keyframes wfHotspotPulse {
      0% {
        box-shadow: 0 0 0 0 rgba(6, 182, 212, 0.8), 0 0 6px rgba(6, 182, 212, 0.5);
        background-color: rgba(6, 182, 212, 0.08);
      }
      35% {
        box-shadow: 0 0 0 6px rgba(6, 182, 212, 0.5), 0 0 26px rgba(6, 182, 212, 0.85);
        background-color: rgba(6, 182, 212, 0.28);
      }
      100% {
        box-shadow: 0 0 0 14px rgba(6, 182, 212, 0), 0 0 0 rgba(6, 182, 212, 0);
        background-color: transparent;
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
      if(ev.data.type === 'wf-trigger-hotspot-hints'){
        triggerHotspotHints();
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

    function triggerHotspotHints(){
      var hotspots = document.querySelectorAll('[data-nav], [data-modal], [data-action], .wf-btn, .wf-act, .wf-sw, .wf-ck, .wf-pill, .wf-tabit, .wf-segs span, .wf-tab-underline span, .wf-tabs [data-tab], button, a');
      for (var i = 0; i < hotspots.length; i++) {
        var h = hotspots[i];
        h.classList.remove('wf-hotspot-hint-pulse');
        void h.offsetWidth;
        h.classList.add('wf-hotspot-hint-pulse');
      }
      setTimeout(function(){
        for (var j = 0; j < hotspots.length; j++) {
          hotspots[j].classList.remove('wf-hotspot-hint-pulse');
        }
      }, 800);
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
          triggerHotspotHints();
          parent.postMessage({type:'wf-miss-click'},'*');
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
        parent.postMessage({type:'wf-nav',page:nav},'*');
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
  // 微调编辑器：右键/Alt+点击/Del 删除，Ctrl+Z 撤回，Ctrl+Y 重做，拖动移动，滚轮字号，自动保存
  const editor = `<script data-wf-inject>
  (function(){
    var EDIT=false,hovered=null,touched=[],st=null;
    var history=[], hIdx=-1, MAX_HIST=30;

    function pushSnapshot(){
      try{
        var html=document.body.innerHTML;
        if(hIdx>=0 && history[hIdx]===html)return;
        history=history.slice(0, hIdx+1);
        history.push(html);
        if(history.length>MAX_HIST)history.shift();
        hIdx=history.length-1;
      }catch(e){}
    }

    function showToast(msg){
      var t=document.getElementById('wf-edit-toast');
      if(!t){
        t=document.createElement('div');
        t.id='wf-edit-toast';
        t.style.cssText='position:fixed;top:14px;left:50%;transform:translateX(-50%);background:rgba(15,23,42,0.9);color:#fff;font-size:11px;font-weight:600;padding:6px 14px;border-radius:20px;z-index:99999;box-shadow:0 4px 12px rgba(0,0,0,0.25);pointer-events:none;transition:opacity 0.2s;opacity:0;font-family:sans-serif;';
        document.body.appendChild(t);
      }
      t.innerText=msg;
      t.style.opacity='1';
      clearTimeout(t._st);
      t._st=setTimeout(function(){t.style.opacity='0';},1800);
    }

    function undo(){
      if(hIdx>0){
        hIdx--;
        document.body.innerHTML=history[hIdx];
        clearHover();
        scheduleSave();
        showToast('已撤回操作 (Ctrl+Z)');
      } else {
        showToast('已是最初状态，无更多可撤回');
      }
    }

    function redo(){
      if(hIdx<history.length-1){
        hIdx++;
        document.body.innerHTML=history[hIdx];
        clearHover();
        scheduleSave();
        showToast('已重做 (Ctrl+Y)');
      }
    }

    function outline(el,on){if(!el)return;if(on){el.style.outline='2px dashed #ef4444';el.style.outlineOffset='-1px';touched.push(el);}else{el.style.outline='';el.style.outlineOffset='';}}
    function clearHover(){outline(hovered,false);hovered=null;}
    function cleanupStyles(){
      document.body.style.cursor='';
      for(var i=0;i<touched.length;i++){try{touched[i].style.outline='';touched[i].style.outlineOffset='';}catch(e){}}
      touched=[];
      var curEditing = document.querySelector('[data-wf-editing-text="true"]');
      if(curEditing){
        curEditing.contentEditable='false';
        curEditing.removeAttribute('data-wf-editing-text');
        curEditing.style.outline='';
        curEditing.style.outlineOffset='';
        curEditing.style.cursor='';
      }
    }
    function doExport(){
      try{
        cleanupStyles();
        var clone=document.documentElement.cloneNode(true);
        var bad=clone.querySelectorAll('[data-wf-inject],#wf-edit-toast');
        for(var i=0;i<bad.length;i++)bad[i].parentNode.removeChild(bad[i]);
        var targets=clone.querySelectorAll('.wf-current-asset-target');
        for(var ti=0;ti<targets.length;ti++)targets[ti].classList.remove('wf-current-asset-target');
        parent.postMessage({type:'wf-save',html:'<!DOCTYPE html>\\n'+clone.outerHTML},'*');
      }catch(e){}
    }
    function scheduleSave(){clearTimeout(st);st=setTimeout(doExport,500);}

    window.addEventListener('message',function(e){
      var d=e.data||{};
      if(d.type==='wf-edit'){
        EDIT=!!d.on;
        document.body.style.cursor=EDIT?'pointer':'';
        if(EDIT){pushSnapshot();showToast('微调模式已开启：双击改文案，点击换图片，拖动移动，拖入组件');}
        else{cleanupStyles();}
      }else if(d.type==='wf-undo'){undo();}
      else if(d.type==='wf-redo'){redo();}
      else if(d.type==='wf-export'){doExport();}
      else if(d.type==='wf-fit'){
        var th=d.h; if(!th)return;
        var hh=document.documentElement.scrollHeight||document.body.scrollHeight||0;
        if(hh>th*1.08){
          var r=th/hh;
          document.body.style.transformOrigin='top center';
          document.body.style.transform='scale('+r+')';
          parent.postMessage({type:'wf-size',h:th},'*');
        }
      }else if(d.type==='wf-replace-asset'){
        var cur=document.querySelector('.wf-current-asset-target');
        if(cur&&d.src){
          pushSnapshot();
          if(cur.tagName==='IMG'){
            cur.src=d.src;
          }else{
            cur.style.backgroundImage='url('+d.src+')';
            cur.style.backgroundSize='cover';
            cur.style.backgroundPosition='center';
          }
          cur.classList.remove('wf-current-asset-target');
          scheduleSave();
          showToast('素材图片替换成功并落库');
        }
      }else if(d.type==='wf-insert-html'){
        if(d.html){
          pushSnapshot();
          var tDiv=document.createElement('div');
          tDiv.innerHTML=d.html.trim();
          var newChild=tDiv.firstElementChild||tDiv;
          document.body.appendChild(newChild);
          scheduleSave();
          showToast('原子组件已插入页面并持久化落库');
        }
      }
    });

    document.addEventListener('mouseover',function(e){
      if(!EDIT)return;
      var activeText = document.querySelector('[data-wf-editing-text="true"]');
      if(activeText) return;
      if(hovered&&hovered!==e.target)outline(hovered,false);
      hovered=e.target;outline(hovered,true);
    },true);

    // 双击任意文本（h1-h6, p, span, button 等）激活 contenteditable 与蓝色虚线框，打字改文案，blur/enter 失焦自动保存落库
    document.addEventListener('dblclick',function(e){
      if(!EDIT)return;
      var t=e.target;
      if(!t||t===document.body||t===document.documentElement)return;
      if(t.tagName==='IMG'||t.tagName==='svg'||t.closest('svg'))return;

      e.preventDefault();
      e.stopPropagation();
      clearHover();

      t.contentEditable='true';
      t.setAttribute('data-wf-editing-text','true');
      t.style.outline='2px dashed #2563eb';
      t.style.outlineOffset='2px';
      t.style.cursor='text';
      t.focus();

      try{
        var rng=document.createRange();
        rng.selectNodeContents(t);
        var s=window.getSelection();
        s.removeAllRanges();
        s.addRange(rng);
      }catch(err){}

      showToast('正在编辑文案：直接打字，失焦或回车自动保存');

      function commitText(){
        if(t.getAttribute('data-wf-editing-text')!=='true')return;
        t.contentEditable='false';
        t.removeAttribute('data-wf-editing-text');
        t.style.outline='';
        t.style.outlineOffset='';
        t.style.cursor='';
        t.removeEventListener('blur',onTextBlur);
        t.removeEventListener('keydown',onTextKey);
        pushSnapshot();
        scheduleSave();
        showToast('文案已自动修改并落库保存');
      }

      function onTextBlur(){commitText();}
      function onTextKey(ke){
        if(ke.key==='Enter'&&!ke.shiftKey&&!['TEXTAREA','P'].includes(t.tagName)){
          ke.preventDefault();
          t.blur();
        }else if(ke.key==='Escape'){
          t.blur();
        }
      }

      t.addEventListener('blur',onTextBlur);
      t.addEventListener('keydown',onTextKey);
    },true);

    // 点击图片/头像唤起素材库替换
    document.addEventListener('click',function(e){
      if(!EDIT)return;
      var activeText = document.querySelector('[data-wf-editing-text="true"]');
      if(activeText) return;
      var t=e.target;
      if(!t)return;
      var isImg=t.tagName==='IMG';
      var isAvatar=t.classList&&(t.classList.contains('wf-avatar')||t.className.indexOf('avatar')!==-1);
      var hasBgImg=t.style&&t.style.backgroundImage&&t.style.backgroundImage!=='none'&&t.style.backgroundImage.indexOf('url(')!==-1;
      if(isImg||isAvatar||hasBgImg){
        e.preventDefault();
        e.stopPropagation();
        var prev=document.querySelectorAll('.wf-current-asset-target');
        for(var pi=0;pi<prev.length;pi++)prev[pi].classList.remove('wf-current-asset-target');
        t.classList.add('wf-current-asset-target');
        var src=isImg?t.src:(hasBgImg?t.style.backgroundImage:'');
        parent.postMessage({type:'wf-pick-asset',src:src},'*');
        showToast('已唤起素材库替换面板');
        return;
      }
    },true);

    // 接收从原子组件库拖入的 drop 事件，将方框或组件插入页面 HTML 并持久化
    document.addEventListener('dragover',function(e){
      if(!EDIT)return;
      e.preventDefault();
      if(e.dataTransfer)e.dataTransfer.dropEffect='copy';
    },true);

    document.addEventListener('drop',function(e){
      if(!EDIT)return;
      e.preventDefault();
      e.stopPropagation();
      var html='';
      if(e.dataTransfer){
        html=e.dataTransfer.getData('text/html')||e.dataTransfer.getData('text/plain')||'';
      }
      if(!html)return;
      pushSnapshot();
      var dropTarget=document.elementFromPoint(e.clientX,e.clientY);
      var container=dropTarget?dropTarget.closest('.wf-container,.wf-card,main,[class*="content"],body')||document.body:document.body;
      var temp=document.createElement('div');
      temp.innerHTML=html.trim();
      var newEl=temp.firstElementChild||temp;
      if(dropTarget&&dropTarget!==document.body&&dropTarget!==document.documentElement&&dropTarget.parentNode){
        dropTarget.parentNode.insertBefore(newEl,dropTarget.nextSibling);
      }else{
        container.appendChild(newEl);
      }
      scheduleSave();
      showToast('原子组件已插入页面并落库');
    },true);

    var drag=null;
    document.addEventListener('mousedown',function(e){
      if(!EDIT)return;
      var activeText = document.querySelector('[data-wf-editing-text="true"]');
      if(activeText || e.target.isContentEditable || e.target.getAttribute('data-wf-editing-text')==='true')return;
      window.focus();
      if(e.altKey||e.shiftKey){
        e.preventDefault();e.stopPropagation();
        var t=e.target;
        if(t&&t!==document.body){
          pushSnapshot();
          t.style.display='none';
          scheduleSave();
          clearHover();
          showToast('已删除元素 (按 Ctrl+Z 撤回)');
        }
        return;
      }
      e.preventDefault();e.stopPropagation();
      pushSnapshot();
      drag={el:e.target,sx:e.clientX,sy:e.clientY};
      if(hovered&&hovered!==e.target)outline(hovered,false);
      hovered=e.target;outline(hovered,true);
    },true);

    document.addEventListener('contextmenu',function(e){
      if(!EDIT)return;
      e.preventDefault();e.stopPropagation();
      var t=e.target;
      if(t&&t!==document.body){
        pushSnapshot();
        t.style.display='none';
        scheduleSave();
        clearHover();
        showToast('已删除元素 (按 Ctrl+Z 撤回)');
      }
    },true);

    document.addEventListener('mousemove',function(e){
      if(!drag)return;
      drag.el.style.transform='translate('+(e.clientX-drag.sx)+'px,'+(e.clientY-drag.sy)+'px)';
    },true);

    document.addEventListener('mouseup',function(){
      if(drag)scheduleSave();
      drag=null;
    },true);

    document.addEventListener('wheel',function(e){
      if(!EDIT)return;
      e.preventDefault();
      pushSnapshot();
      var fs=parseFloat(getComputedStyle(e.target).fontSize)||14;
      e.target.style.fontSize=Math.max(8,Math.min(48,fs+(e.deltaY<0?1:-1)))+'px';
      scheduleSave();
    },{passive:false,capture:true});

    document.addEventListener('keydown',function(e){
      if(!EDIT)return;
      var isZ = e.key==='z' || e.key==='Z';
      var isY = e.key==='y' || e.key==='Y';
      if((e.ctrlKey||e.metaKey) && isZ && !e.shiftKey){
        e.preventDefault(); undo(); return;
      }
      if((e.ctrlKey||e.metaKey) && (isY || (isZ && e.shiftKey))){
        e.preventDefault(); redo(); return;
      }
      if((e.key==='Delete'||e.key==='Backspace'||e.key==='x'||e.key==='X')&&hovered&&hovered!==document.body){
        e.preventDefault();
        pushSnapshot();
        hovered.style.display='none';
        scheduleSave();
        clearHover();
        showToast('已删除元素 (按 Ctrl+Z 撤回)');
      }else if(e.key==='Escape'){cleanupStyles();}
    },true);
  })();
  <\/script>`
  const payload = guard + runtime + sizer + editor
  if (/<\/body>/i.test(html)) {
    return html.replace(/<\/body>/i, `${payload}</body>`)
  }
  return html + payload
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
  if (!props.editMode) return
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
}

function onSlotDrop(e: DragEvent) {
  if (!props.editMode) return
  const html = e.dataTransfer?.getData('text/html') || e.dataTransfer?.getData('text/plain')
  if (html) {
    e.preventDefault()
    e.stopPropagation()
    htmlFrameRef.value?.contentWindow?.postMessage({
      type: 'wf-insert-html',
      html,
    }, '*')
  }
}

function onIframeMessage(e: MessageEvent) {
  // 关键：只处理当前组件 iframe 自己发出的消息。iframe 的 postMessage 会广播给
  // 父窗口所有监听器，若不校验来源，A 页微调保存会污染所有页面的 HTML（全变同一页）。
  if (e.source !== htmlFrameRef.value?.contentWindow) return
  const d = e.data as { type?: string; page?: string; html?: string; h?: number; src?: string } | null
  if (!d) return
  if (d.type === 'wf-nav' && d.page) {
    emit('navigate', d.page)
  } else if (d.type === 'wf-back') {
    emit('back')
  } else if (d.type === 'wf-pick-asset') {
    openAssetPicker()
  } else if (d.type === 'wf-miss-click') {
    emit('missClick')
  } else if (d.type === 'wf-save' && typeof d.html === 'string' && d.html.length > 50) {
    isInternalSaving = true
    clearTimeout(saveResetTimer)
    saveResetTimer = setTimeout(() => {
      isInternalSaving = false
    }, 1500)
    emit('saveHtml', { pageId: props.page.id, html: d.html })
  } else if (d.type === 'wf-size' && typeof d.h === 'number' && d.h > 0) {
    const target = props.page.canvas_height
    // 模板渲染的页面 body 高度恒等于画布高度；scrollHeight 被溢出/浮层内容撑大属于幻影高度，
    // 直接钳制到画布高度，避免 iframe 底部出现大片空白
    iframeH.value = target && target > 0 ? Math.min(Math.round(d.h), target) : Math.round(d.h)
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
  htmlFrameRef.value?.contentWindow?.postMessage({ type: 'wf-trigger-hotspot-hints' }, '*')
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
const { showDesign, boxW, gap } = props
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
const wireX = computed(() => (showDesign ? canvasW.value * scale.value + gap : 0))

// ===== 说明面板状态 =====
const panelOpen = ref(false)
// 选中的线框元素属于本页时自动展开面板
const selectedOnThisPage = computed(
  () => props.selectedElementId != null && props.page.elements.some((e) => e.id === props.selectedElementId),
)
const isPanelOpen = computed(() => panelOpen.value || selectedOnThisPage.value)

const panelX = computed(() => wireX.value + canvasW.value * scale.value + gap + 12)
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

const stageW = computed(() => {
  if (!showDesign && !props.showAnnotations) {
    return canvasW.value * scale.value
  }
  return panelX.value + boxW + 28
})
// 整页模式：高度随 iframe 内容自适应（避免固定高度把页面裁掉产生滚动）；预览模式去除额外的 8px 安全边距
const stageH = computed(() => {
  const baseH = (iframeH.value || canvasH.value) * scale.value
  if (!showDesign && !props.showAnnotations) {
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

// 暴露尺寸与热区提示，供父组件（无限画布）计算布局与调用
defineExpose({ stageW, stageH, triggerHotspots })
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
    stroke: #059669;
    stroke-width: 1.5;
  }
}

/* ===== 说明面板 ===== */
.ann-panel {
  position: absolute;
  top: 0;
  width: 240px;
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  background: #ffffff;
  border: 1px solid #e2e8f0;
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
    color: #1e293b;
    cursor: pointer;
    user-select: none;
    flex-shrink: 0;
    background: #f8fafc;
    border-bottom: 1px solid #f1f5f9;

    &:hover {
      background: #f1f5f9;
    }
    .panel-chevron {
      color: #64748b;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  margin-top: 8px;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: #a7f3d0;
    background: #f0fdf4;
    box-shadow: 0 2px 8px rgba(16, 185, 129, 0.12);

    .ann-edit-btn {
      opacity: 1;
    }
  }
  &.selected {
    border-color: #059669;
    background: #ecfdf5;
    box-shadow: 0 0 0 1px #059669, 0 2px 10px rgba(5, 150, 105, 0.15);
  }
  &.dragging {
    opacity: 0.4;
    border-style: dashed;
    border-color: #10b981;
  }
  &.drag-over {
    border-color: #059669;
    background: #ecfdf5;
    transform: translateY(-2px);
    box-shadow: 0 -3px 0 0 #059669;
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
      color: #94a3b8;
      cursor: grab;
      padding: 1px;
      border-radius: 4px;

      &:hover {
        color: #059669;
        background: #e2e8f0;
      }
      &:active {
        cursor: grabbing;
      }
    }
    .ann-title {
      flex: 1;
      font-weight: 600;
      font-size: 11px;
      color: #334155;
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
      color: #94a3b8;
      border-radius: 4px;
      cursor: pointer;
      transition: opacity 0.15s, color 0.15s;

      &:hover {
        color: #059669;
        background: #e2e8f0;
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
    background: #f8fafc;
    border-radius: 4px;
    font-size: 10px;
    color: #64748b;

    .hint-dot {
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: #10b981;
    }
    .hint-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .ann-text {
    font-size: 11px;
    color: #334155;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;

    &.empty {
      color: #94a3b8;
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
        color: #64748b;
      }
    }

    .ann-edit-title-input {
      width: 100%;
      border: 1px solid #059669;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      padding: 3px 6px;
      font-family: inherit;
      color: #0f172a;
      outline: none;
      box-sizing: border-box;
      background: #ffffff;
      box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.15);
    }

    .ann-edit-textarea {
      width: 100%;
      border: 1px solid #059669;
      border-radius: 4px;
      font-size: 11px;
      line-height: 1.4;
      padding: 4px 6px;
      resize: vertical;
      font-family: inherit;
      color: #0f172a;
      outline: none;
      box-sizing: border-box;
      background: #ffffff;
      box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.15);
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
        border: 1px solid #cbd5e1;
        background: #ffffff;
        color: #64748b;
        &:hover {
          background: #f1f5f9;
        }
      }
      .btn-save {
        border: none;
        background: #059669;
        color: #ffffff;
        &:hover {
          background: #047857;
        }
      }
    }
  }
}
</style>
