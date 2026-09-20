<template>
  <div
    class="pure-preview-container relative w-full h-full overflow-hidden select-none bg-[#090d16] text-slate-100 flex flex-col items-center justify-center font-sans"
    @click="onBackgroundClick"
  >
    <!-- ===== Ambient Studio Lighting Background (Figma Presentation Style) ===== -->
    <div class="absolute inset-0 pointer-events-none overflow-hidden">
      <!-- Deep subtle radial spotlight -->
      <div
        class="absolute -top-1/4 left-1/2 -translate-x-1/2 w-[1100px] h-[800px] rounded-full bg-gradient-to-b from-slate-800/25 via-emerald-950/10 to-transparent blur-3xl"
      ></div>
      <div
        class="absolute -bottom-1/3 left-1/2 -translate-x-1/2 w-[900px] h-[600px] rounded-full bg-gradient-to-t from-cyan-950/15 via-slate-900/10 to-transparent blur-3xl"
      ></div>
      <!-- Subtle Presentation Watermark / Ambient Grid (Ultra Faint) -->
      <div
        class="absolute inset-0 opacity-[0.03] bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:24px_24px]"
      ></div>
    </div>

    <!-- ===== Top Floating Action Island (轻量浮动操作栏) ===== -->
    <header
      class="fixed top-4 left-1/2 -translate-x-1/2 z-50 flex items-center gap-2.5 px-4 py-2 rounded-2xl bg-slate-900/85 backdrop-blur-2xl border border-white/12 shadow-[0_12px_45px_rgba(0,0,0,0.65)] text-white text-xs font-medium transition-all duration-300 hover:border-white/20"
      @click.stop
    >
      <!-- Left: Project Name & Badge -->
      <div class="flex items-center gap-2 pr-1">
        <div class="w-6 h-6 rounded-lg bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
          <Layers class="w-3.5 h-3.5" />
        </div>
        <div class="flex flex-col">
          <span class="font-bold text-xs tracking-tight text-white max-w-[150px] truncate" :title="proto?.project.name">
            {{ proto?.project.name || 'WireForge 原型' }}
          </span>
          <span class="text-[9px] text-slate-400 font-normal leading-none">纯净全屏演示</span>
        </div>
      </div>

      <div class="h-4 w-[1px] bg-white/15 mx-0.5"></div>

      <!-- Center: Page Selector Dropdown -->
      <div class="relative">
        <el-select
          v-model="currentPageId"
          size="small"
          class="preview-page-select !w-36 sm:!w-44"
          placeholder="选择页面"
          @change="onSelectPageChange"
        >
          <el-option
            v-for="(p, idx) in pages"
            :key="p.id"
            :label="`${idx + 1}. ${p.name}`"
            :value="p.id"
          />
        </el-select>
      </div>

      <div class="h-4 w-[1px] bg-white/15 mx-0.5"></div>

      <!-- Feature Toggles & Actions -->
      <div class="flex items-center gap-1.5">
        <!-- 💡 点击热区提示切换按钮 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-semibold transition-all cursor-pointer"
          :class="hotspotHintsEnabled 
            ? 'bg-cyan-500/20 border-cyan-500/60 text-cyan-300 shadow-[0_0_15px_rgba(6,182,212,0.35)]' 
            : 'bg-white/5 border-white/10 text-slate-400 hover:bg-white/10 hover:text-slate-200'"
          title="点击空白未命中区域时，青色波纹脉冲高亮所有交互热区 (快捷键 H)"
          @click="toggleHotspotHints"
        >
          <Lightbulb class="w-3.5 h-3.5" :class="{ 'fill-cyan-400 text-cyan-400': hotspotHintsEnabled }" />
          <span class="hidden sm:inline">热区提示</span>
          <span v-if="hotspotHintsEnabled" class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-ping"></span>
        </button>

        <!-- 📱 手机外壳切换按钮 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-semibold transition-all cursor-pointer"
          :class="showDeviceFrame 
            ? 'bg-emerald-500/20 border-emerald-500/60 text-emerald-300 shadow-[0_0_15px_rgba(16,185,129,0.35)]' 
            : 'bg-white/5 border-white/10 text-slate-400 hover:bg-white/10 hover:text-slate-200'"
          title="切换 iPhone 16 Pro 钛金属机身外壳与灵动岛 (快捷键 D)"
          @click="showDeviceFrame = !showDeviceFrame"
        >
          <Smartphone class="w-3.5 h-3.5" />
          <span class="hidden sm:inline">手机外壳</span>
        </button>

        <!-- ↺ 重置原型 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 hover:text-white transition-all cursor-pointer"
          title="回到初始第一页并重置历史跳转堆栈 (快捷键 R)"
          @click="resetPrototype"
        >
          <RotateCcw class="w-3.5 h-3.5" />
          <span class="hidden sm:inline">重置</span>
        </button>

        <!-- 🔗 复制分享链接 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 hover:text-white transition-all cursor-pointer"
          title="复制当前页面独立演示链接到剪贴板"
          @click="copyShareLink"
        >
          <Link class="w-3.5 h-3.5" />
          <span class="hidden sm:inline">复制链接</span>
        </button>

        <!-- ✕ 退出全屏 / 返回画板 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 border border-white/20 text-white font-semibold shadow-sm transition-all cursor-pointer"
          title="退出全屏并返回原型画板 (Esc)"
          @click="exitPreview"
        >
          <X class="w-3.5 h-3.5" />
          <span>退出 (Esc)</span>
        </button>
      </div>
    </header>

    <!-- ===== Main Viewport Stage Container ===== -->
    <main
      ref="viewportContainerRef"
      class="flex-1 w-full h-full flex items-center justify-center p-4 sm:p-8 overflow-hidden relative"
      @click="onViewportAreaClick"
    >
      <!-- Click Ripple Effect Layer -->
      <div
        v-for="ripple in activeRipples"
        :key="ripple.id"
        class="click-ripple pointer-events-none fixed z-50 rounded-full border-2 border-cyan-400/80 bg-cyan-400/20"
        :style="{
          left: `${ripple.x}px`,
          top: `${ripple.y}px`,
        }"
      ></div>

      <!-- Centered Mobile Scaling Wrapper -->
      <div
        class="viewport-scaler transition-transform duration-200 ease-out origin-center flex items-center justify-center"
        :style="{
          transform: `scale(${phoneScale})`,
        }"
      >
        <!-- ===== High-Precision iPhone 16 Pro Chassis ===== -->
        <div
          class="phone-chassis relative transition-all duration-300 select-none"
          :class="[
            showDeviceFrame
              ? 'p-[14px] bg-[#1a1f2c] rounded-[56px] shadow-[0_0_0_1px_rgba(255,255,255,0.18),0_0_0_3px_#272e3f,0_0_0_4px_rgba(255,255,255,0.06),0_25px_80px_-15px_rgba(0,0,0,0.9),0_0_50px_rgba(6,182,212,0.1)]'
              : 'p-0 rounded-[32px] shadow-[0_20px_60px_-10px_rgba(0,0,0,0.85)]'
          ]"
          @click="onChassisClick"
        >
          <!-- iPhone Side Hardware Buttons (Pure Hardware Aesthetics) -->
          <template v-if="showDeviceFrame">
            <!-- Left: Action Button -->
            <div class="absolute -left-[5px] top-[108px] w-[5px] h-[28px] bg-slate-700/80 rounded-l-[3px] shadow-sm"></div>
            <!-- Left: Volume Up -->
            <div class="absolute -left-[5px] top-[152px] w-[5px] h-[52px] bg-slate-700/80 rounded-l-[3px] shadow-sm"></div>
            <!-- Left: Volume Down -->
            <div class="absolute -left-[5px] top-[214px] w-[5px] h-[52px] bg-slate-700/80 rounded-l-[3px] shadow-sm"></div>
            <!-- Right: Power Button -->
            <div class="absolute -right-[5px] top-[168px] w-[5px] h-[68px] bg-slate-700/80 rounded-r-[3px] shadow-sm"></div>
            <!-- Right: Camera Control Touch Strip -->
            <div class="absolute -right-[4px] bottom-[160px] w-[4px] h-[48px] bg-slate-600/60 rounded-r-[2px] shadow-inner"></div>
          </template>

          <!-- ===== Screen Glass Viewport ===== -->
          <div
            class="phone-screen relative w-[375px] bg-black overflow-hidden flex flex-col transition-all duration-300"
            :class="[
              showDeviceFrame ? 'rounded-[44px]' : 'rounded-[28px]',
              isPulseActive ? 'ring-2 ring-cyan-400/60 shadow-[0_0_25px_rgba(6,182,212,0.3)]' : ''
            ]"
            :style="{ height: `${screenHeight}px` }"
            @click="onScreenClick"
          >
            <!-- iOS Status Bar & Dynamic Island (When Device Frame is ON) -->
            <div
              v-if="showDeviceFrame"
              class="ios-status-bar absolute top-0 left-0 right-0 h-12 z-40 flex items-center justify-between px-7 text-white pointer-events-none select-none"
            >
              <!-- Left: Real-time Time -->
              <span class="text-[14px] font-semibold tracking-tight tabular-nums pl-1 drop-shadow-sm">
                {{ currentTime }}
              </span>

              <!-- Center: Dynamic Island (灵动岛) -->
              <div
                class="dynamic-island absolute top-2.5 left-1/2 -translate-x-1/2 w-[122px] h-[34px] rounded-full bg-black flex items-center justify-between px-3 shadow-md pointer-events-auto cursor-pointer hover:scale-105 transition-transform"
                title="iPhone 16 Pro 灵动岛"
                @click.stop="triggerHotspotHints"
              >
                <!-- Front Camera Lens reflection -->
                <div class="w-3 h-3 rounded-full bg-slate-950 border border-slate-800/80 flex items-center justify-center relative overflow-hidden">
                  <div class="w-1.5 h-1.5 rounded-full bg-blue-950/90 flex items-center justify-center">
                    <div class="w-0.5 h-0.5 rounded-full bg-cyan-400/60"></div>
                  </div>
                </div>
                <!-- FaceID Dot / Ambient Sensor -->
                <div class="w-2 h-2 rounded-full bg-slate-950/80 border border-slate-900"></div>
              </div>

              <!-- Right: Cellular 5G + WiFi + Battery -->
              <div class="flex items-center gap-1.5 pr-1 drop-shadow-sm">
                <!-- Cellular Bars -->
                <svg class="w-4 h-3 text-white" viewBox="0 0 17 12" fill="currentColor">
                  <rect x="0" y="8" width="3" height="4" rx="0.5" />
                  <rect x="4.5" y="5.5" width="3" height="6.5" rx="0.5" />
                  <rect x="9" y="3" width="3" height="9" rx="0.5" />
                  <rect x="13.5" y="0" width="3" height="12" rx="0.5" />
                </svg>

                <!-- Wi-Fi -->
                <svg class="w-3.5 h-3 text-white" viewBox="0 0 16 12" fill="currentColor">
                  <path d="M8 10a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm-4.24-2.83a6 6 0 0 1 8.48 0 .8.8 0 1 1-1.13 1.13 4.4 4.4 0 0 0-6.22 0 .8.8 0 1 1-1.13-1.13zm-2.83-2.83a10 10 0 0 1 14.14 0 .8.8 0 0 1-1.13 1.13 8.4 8.4 0 0 0-11.88 0 .8.8 0 0 1-1.13-1.13z" />
                </svg>

                <!-- Battery -->
                <div class="w-5 h-2.5 rounded-[4px] border border-white/90 p-[1px] flex items-center relative ml-0.5">
                  <div class="h-full w-[85%] bg-white rounded-[2px]"></div>
                  <div class="absolute -right-[3px] top-[2.5px] w-[2px] h-[3.5px] bg-white/90 rounded-r-[1px]"></div>
                </div>
              </div>
            </div>

            <!-- Floating Back Navigation Button (When history exists) -->
            <transition name="fade-fast">
              <button
                v-if="historyStack.length > 0"
                class="wf-tap absolute top-14 left-3.5 z-40 w-8 h-8 rounded-full bg-black/50 hover:bg-black/75 backdrop-blur-md text-white flex items-center justify-center transition-all cursor-pointer shadow-lg border border-white/20"
                title="返回上一页"
                @click.stop="handleBack"
              >
                <ArrowLeft class="w-4 h-4" />
              </button>
            </transition>

            <!-- Hotspot Hint Floating Indicator (当发光触发时右上角微标) -->
            <transition name="fade-fast">
              <div
                v-if="isPulseActive"
                class="absolute top-14 right-3.5 z-40 px-2.5 py-1 rounded-full bg-cyan-950/80 backdrop-blur-md text-cyan-300 text-[10px] font-bold border border-cyan-500/50 shadow-[0_0_15px_rgba(6,182,212,0.4)] flex items-center gap-1.5 pointer-events-none"
              >
                <span class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-ping"></span>
                <span>可交互热区</span>
              </div>
            </transition>

            <!-- ===== Prototype Content Stage with Native Push Transitions ===== -->
            <div
              class="relative flex-1 w-full overflow-hidden bg-white"
              :class="{ 'pt-11': showDeviceFrame }"
            >
              <transition :name="pageTransitionName">
                <div
                  :key="currentPageId ?? 0"
                  class="page-transition-wrapper absolute inset-0 w-full h-full overflow-x-hidden overflow-y-auto custom-scrollbar"
                >
                  <PageCanvas
                    v-if="currentPage"
                    ref="pageCanvasRef"
                    :page="currentPage"
                    :all-pages="pages"
                    :show-wireframe="true"
                    :show-design="false"
                    :show-annotations="false"
                    :edit-mode="false"
                    :interactive="true"
                    :box-w="0"
                    :gap="0"
                    @navigate="handleNavigate"
                    @back="handleBack"
                    @element-click="handleElementClick"
                    @miss-click="onScreenMissClick"
                  />
                </div>
              </transition>

              <!-- Wireframe Mode Hotspot Overlay Pulse (当没有 HTML 纯线框模式下的交互热区提示) -->
              <div
                v-if="isPulseActive && !currentPage?.html_content"
                class="absolute inset-0 pointer-events-none z-30"
              >
                <div
                  v-for="el in interactiveElements"
                  :key="el.id"
                  class="absolute rounded-lg border-2 border-cyan-400 bg-cyan-400/20 shadow-[0_0_15px_rgba(6,182,212,0.7)] animate-pulse"
                  :style="{
                    left: `${el.x}px`,
                    top: `${el.y}px`,
                    width: `${el.width}px`,
                    height: `${el.height}px`,
                  }"
                ></div>
              </div>
            </div>

            <!-- iOS Home Indicator Gesture Bar -->
            <div
              v-if="showDeviceFrame"
              class="absolute bottom-2 left-1/2 -translate-x-1/2 w-36 h-1 rounded-full bg-slate-400/80 shadow-sm z-40 pointer-events-none"
            ></div>

            <!-- ===== Native Modal / Bottom Sheet Layer (高斯模糊半透明暗色遮罩) ===== -->
            <transition name="modal-fade">
              <div
                v-if="activeModal"
                class="absolute inset-0 z-50 flex items-center justify-center p-5 bg-black/60 backdrop-blur-md transition-all duration-300 select-none"
                :class="{ 'items-end !p-0': activeModal.type === 'sheet' }"
                @click.self="closeModal"
              >
                <!-- Center Dialog Modal -->
                <div
                  v-if="activeModal.type === 'dialog'"
                  class="w-full max-w-[310px] bg-slate-900/95 border border-white/15 rounded-3xl p-5 text-white shadow-2xl animate-scale-up"
                  @click.stop
                >
                  <div class="flex items-center justify-between mb-3">
                    <h4 class="font-bold text-sm tracking-tight text-white flex items-center gap-2">
                      <span class="w-2 h-2 rounded-full bg-cyan-400"></span>
                      <span>{{ activeModal.title || '操作提示' }}</span>
                    </h4>
                    <button
                      class="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                      @click="closeModal"
                    >
                      <X class="w-4 h-4" />
                    </button>
                  </div>
                  <p class="text-xs text-slate-300 leading-relaxed mb-5">
                    {{ activeModal.content || '此处为原型弹层交互效果展示，点击遮罩或按钮即可关闭。' }}
                  </p>
                  <div class="flex items-center justify-end gap-2.5">
                    <button
                      class="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/15 text-slate-300 transition-colors cursor-pointer"
                      @click="closeModal"
                    >
                      {{ activeModal.cancelText || '取消' }}
                    </button>
                    <button
                      class="px-4 py-1.5 rounded-xl text-xs font-bold bg-cyan-500 hover:bg-cyan-400 text-slate-950 shadow-md shadow-cyan-500/25 transition-all cursor-pointer"
                      @click="closeModal"
                    >
                      {{ activeModal.confirmText || '确定' }}
                    </button>
                  </div>
                </div>

                <!-- Bottom Sheet Modal -->
                <div
                  v-else-if="activeModal.type === 'sheet'"
                  class="w-full bg-slate-900/95 border-t border-white/15 rounded-t-[32px] p-5 pb-8 text-white shadow-2xl animate-slide-up"
                  @click.stop
                >
                  <!-- Grabber Handle -->
                  <div class="w-12 h-1 rounded-full bg-white/30 mx-auto mb-4"></div>

                  <div class="flex items-center justify-between mb-3">
                    <h4 class="font-bold text-sm text-white">
                      {{ activeModal.title || '操作菜单' }}
                    </h4>
                    <button
                      class="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                      @click="closeModal"
                    >
                      <X class="w-4 h-4" />
                    </button>
                  </div>

                  <p class="text-xs text-slate-300 leading-relaxed mb-6">
                    {{ activeModal.content || '此处为底部半屏抽屉 (Bottom Sheet) 弹层，可容纳选项列表或操作按钮。' }}
                  </p>

                  <div class="space-y-2">
                    <button
                      class="w-full py-2.5 rounded-xl text-xs font-bold bg-cyan-500 hover:bg-cyan-400 text-slate-950 shadow-md shadow-cyan-500/25 transition-all cursor-pointer"
                      @click="closeModal"
                    >
                      {{ activeModal.confirmText || '确认操作' }}
                    </button>
                    <button
                      class="w-full py-2.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/15 text-slate-300 transition-colors cursor-pointer"
                      @click="closeModal"
                    >
                      {{ activeModal.cancelText || '取消' }}
                    </button>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </main>

    <!-- ===== Global Toast Notification ===== -->
    <transition name="toast-fade">
      <div
        v-if="toastMsg"
        class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-2xl bg-slate-900/90 text-white text-xs font-semibold border border-white/15 shadow-2xl backdrop-blur-xl flex items-center gap-2 pointer-events-none"
      >
        <span>{{ toastMsg }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Layers,
  Lightbulb,
  Smartphone,
  RotateCcw,
  Link,
  X,
  ArrowLeft,
} from 'lucide-vue-next'
import { projectApi } from '../api/project'
import type { Element, Page, Prototype } from '../types'
import PageCanvas from '../components/PageCanvas.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const proto = ref<Prototype | null>(null)
const pages = computed<Page[]>(() => proto.value?.pages || [])

const currentPageId = ref<number | null>(null)
const currentPage = computed<Page | null>(() => {
  if (!pages.value.length) return null
  return pages.value.find((p) => p.id === currentPageId.value) || pages.value[0] || null
})

// 视口标准高度：兼容页面实际高度（基准 812px）
const screenHeight = computed(() => {
  if (!currentPage.value) return 812
  return currentPage.value.canvas_height || 812
})

// 历史导航栈与原生 Push 转场
const historyStack = ref<number[]>([])
const transitionDirection = ref<'forward' | 'backward'>('forward')
const pageTransitionName = computed(() =>
  transitionDirection.value === 'forward' ? 'push-forward' : 'push-backward',
)

// 浮动功能开关
const hotspotHintsEnabled = ref(true)
const showDeviceFrame = ref(true)
const isPulseActive = ref(false)
let pulseTimer: any = null

// 实时时间
const currentTime = ref('')
let clockTimer: any = null
function updateClock() {
  const now = new Date()
  const h = String(now.getHours()).padStart(2, '0')
  const m = String(now.getMinutes()).padStart(2, '0')
  currentTime.value = `${h}:${m}`
}

// 手机壳全视口自适应等比缩放
const viewportContainerRef = ref<HTMLElement | null>(null)
const phoneScale = ref(1)

function updatePhoneScale() {
  const container = viewportContainerRef.value
  if (!container) return
  const PAD_X = 40
  const PAD_Y = 100 // 为顶部浮动栏留足操作空间
  const availW = Math.max(100, container.clientWidth - PAD_X)
  const availH = Math.max(100, container.clientHeight - PAD_Y)

  const frameW = showDeviceFrame.value ? 403 : 375
  const frameH = (screenHeight.value || 812) + (showDeviceFrame.value ? 28 : 0)

  const scaleW = availW / frameW
  const scaleH = availH / frameH
  phoneScale.value = Math.min(1.05, Math.max(0.4, Math.min(scaleW, scaleH)))
}

// 弹层状态 (Modal / Bottom Sheet)
interface ActiveModalState {
  title: string
  content: string
  type: 'dialog' | 'sheet'
  confirmText?: string
  cancelText?: string
}
const activeModal = ref<ActiveModalState | null>(null)

function closeModal() {
  activeModal.value = null
}

// Toast
const toastMsg = ref('')
let toastTimer: any = null
function showToast(msg: string) {
  toastMsg.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMsg.value = ''
  }, 2200)
}

// 点击水波纹效果 (Click Ripples)
const activeRipples = ref<{ id: number; x: number; y: number }[]>([])
let rippleIdCounter = 0
function spawnRipple(x: number, y: number) {
  const rId = ++rippleIdCounter
  activeRipples.value.push({ id: rId, x, y })
  setTimeout(() => {
    activeRipples.value = activeRipples.value.filter((r) => r.id !== rId)
  }, 600)
}

// 核心功能：Figma 经典 Hotspot Hinting
const pageCanvasRef = ref<any>(null)

function triggerHotspotHints() {
  isPulseActive.value = false
  if (pulseTimer) clearTimeout(pulseTimer)
  nextTick(() => {
    isPulseActive.value = true
    pageCanvasRef.value?.triggerHotspots()
    pulseTimer = setTimeout(() => {
      isPulseActive.value = false
    }, 850)
  })
}

function toggleHotspotHints() {
  hotspotHintsEnabled.value = !hotspotHintsEnabled.value
  if (hotspotHintsEnabled.value) {
    triggerHotspotHints()
    showToast('💡 已开启热区提示：点击空白处将高亮可交互区域')
  } else {
    isPulseActive.value = false
    showToast('已关闭热区提示')
  }
}

// 屏幕未命中交互区域点击处理
function onScreenMissClick() {
  if (hotspotHintsEnabled.value) {
    triggerHotspotHints()
  }
}

function onScreenClick(e: MouseEvent) {
  spawnRipple(e.clientX, e.clientY)
}

function onChassisClick(e: MouseEvent) {
  spawnRipple(e.clientX, e.clientY)
  if (hotspotHintsEnabled.value) {
    triggerHotspotHints()
  }
}

function onViewportAreaClick(e: MouseEvent) {
  spawnRipple(e.clientX, e.clientY)
  if (hotspotHintsEnabled.value) {
    triggerHotspotHints()
  }
}

function onBackgroundClick() {
  if (hotspotHintsEnabled.value) {
    triggerHotspotHints()
  }
}

// 页面导航与转场
function navigateTo(targetPageId: number) {
  if (targetPageId === currentPageId.value) return
  if (currentPageId.value != null) {
    historyStack.value.push(currentPageId.value)
  }
  transitionDirection.value = 'forward'
  currentPageId.value = targetPageId
  activeModal.value = null
  nextTick(updatePhoneScale)
}

function handleNavigate(pageName: string) {
  const clean = pageName.trim().toLowerCase()
  const target = pages.value.find(
    (p) => p.name.trim().toLowerCase() === clean || String(p.id) === clean,
  )
  if (target) {
    navigateTo(target.id)
    showToast(`🔗 前往: ${target.name}`)
  } else {
    // 尝试包含匹配
    const fuzzy = pages.value.find(
      (p) => p.name.toLowerCase().includes(clean) || clean.includes(p.name.toLowerCase()),
    )
    if (fuzzy) {
      navigateTo(fuzzy.id)
      showToast(`🔗 前往: ${fuzzy.name}`)
    }
  }
}

function handleBack() {
  if (activeModal.value) {
    closeModal()
    return
  }
  if (historyStack.value.length > 0) {
    const prevId = historyStack.value.pop()!
    transitionDirection.value = 'backward'
    currentPageId.value = prevId
    nextTick(updatePhoneScale)
  } else if (pages.value.length > 0 && currentPageId.value !== pages.value[0].id) {
    transitionDirection.value = 'backward'
    currentPageId.value = pages.value[0].id
    nextTick(updatePhoneScale)
  } else {
    showToast('已是首页')
  }
}

function onSelectPageChange(pageId: number) {
  navigateTo(pageId)
}

function resetPrototype() {
  historyStack.value = []
  transitionDirection.value = 'backward'
  if (pages.value.length > 0) {
    currentPageId.value = pages.value[0].id
  }
  activeModal.value = null
  nextTick(updatePhoneScale)
  showToast('↺ 原型已重置至首页')
}

function copyShareLink() {
  const url = `${window.location.origin}/projects/${id}/preview?page=${currentPageId.value ?? ''}`
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard
      .writeText(url)
      .then(() => showToast('🔗 已复制纯原型演示链接到剪贴板'))
      .catch(() => showToast(`请复制链接: ${url}`))
  } else {
    const ta = document.createElement('textarea')
    ta.value = url
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    showToast('🔗 已复制纯原型演示链接到剪贴板')
  }
}

function exitPreview() {
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {})
  }
  router.push({
    path: `/projects/${id}/prototype`,
    query: { page: currentPageId.value ? String(currentPageId.value) : undefined },
  })
}

// 元素交互处理
function handleElementClick(el: Element) {
  const ix = el.interaction
  if (!ix) {
    if (hotspotHintsEnabled.value) triggerHotspotHints()
    return
  }

  if (ix.action === 'navigate' && ix.target_page_id) {
    navigateTo(ix.target_page_id)
  } else if (ix.action === 'modal') {
    const params = ix.params || ''
    const isSheet =
      params.includes('sheet') ||
      params.includes('抽屉') ||
      el.label.includes('选择') ||
      el.label.includes('菜单') ||
      el.label.includes('底部')
    activeModal.value = {
      title: el.label || '操作提示',
      content:
        params ||
        `您点击了「${el.label || '弹窗组件'}」，此处为原生高保真模态弹层展示。支持高斯模糊暗色半透明遮罩与顺滑入场动效。`,
      type: isSheet ? 'sheet' : 'dialog',
      confirmText: '确定',
      cancelText: '取消',
    }
  } else if (ix.action === 'input_focus') {
    showToast(`⌨️ 聚焦输入框: ${el.label || '输入组件'}`)
  } else {
    if (hotspotHintsEnabled.value) triggerHotspotHints()
  }
}

// Wireframe 模式下可交互元素列表
const interactiveElements = computed(() => {
  if (!currentPage.value) return []
  return currentPage.value.elements.filter(
    (e) =>
      e.interaction?.action === 'navigate' ||
      e.interaction?.action === 'modal' ||
      ['button', 'switch', 'checkbox', 'tab'].includes(e.type),
  )
})

// 快捷键支持
function onGlobalKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (activeModal.value) {
      closeModal()
      return
    }
    exitPreview()
  } else if (e.key === 'h' || e.key === 'H') {
    toggleHotspotHints()
  } else if (e.key === 'd' || e.key === 'D') {
    showDeviceFrame.value = !showDeviceFrame.value
    showToast(showDeviceFrame.value ? '已开启手机外壳' : '已切换无框纯屏模式')
    nextTick(updatePhoneScale)
  } else if (e.key === 'r' || e.key === 'R') {
    resetPrototype()
  } else if (e.key === 'Backspace') {
    handleBack()
  }
}

// 加载项目数据
async function loadData() {
  try {
    const data = await projectApi.prototype(id)
    proto.value = data
    const queryPage = route.query.page ? Number(route.query.page) : null
    if (queryPage && data.pages.some((p) => p.id === queryPage)) {
      currentPageId.value = queryPage
    } else if (data.pages.length > 0) {
      currentPageId.value = data.pages[0].id
    }
    nextTick(updatePhoneScale)
  } catch (e: any) {
    showToast(`❌ 加载失败: ${e.message || '网络错误'}`)
  }
}

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 10000)
  window.addEventListener('resize', updatePhoneScale)
  window.addEventListener('keydown', onGlobalKeyDown)
  loadData()
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pulseTimer) clearTimeout(pulseTimer)
  if (toastTimer) clearTimeout(toastTimer)
  window.removeEventListener('resize', updatePhoneScale)
  window.removeEventListener('keydown', onGlobalKeyDown)
})
</script>

<style scoped>
/* ===== iOS Native Push Transitions ===== */
.push-forward-enter-active,
.push-forward-leave-active,
.push-backward-enter-active,
.push-backward-leave-active {
  transition: transform 0.36s cubic-bezier(0.32, 0.72, 0, 1), opacity 0.36s ease;
  will-change: transform, opacity;
}

.push-forward-enter-from {
  transform: translate3d(100%, 0, 0);
  opacity: 0.95;
}
.push-forward-leave-to {
  transform: translate3d(-25%, 0, 0);
  opacity: 0.8;
}

.push-backward-enter-from {
  transform: translate3d(-25%, 0, 0);
  opacity: 0.8;
}
.push-backward-leave-to {
  transform: translate3d(100%, 0, 0);
  opacity: 0.95;
}

/* ===== Modal Animation ===== */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s cubic-bezier(0.32, 0.72, 0, 1);
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@keyframes scaleUp {
  0% {
    transform: scale(0.92);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
.animate-scale-up {
  animation: scaleUp 0.28s cubic-bezier(0.32, 0.72, 0, 1) forwards;
}

@keyframes slideUp {
  0% {
    transform: translateY(100%);
  }
  100% {
    transform: translateY(0);
  }
}
.animate-slide-up {
  animation: slideUp 0.32s cubic-bezier(0.32, 0.72, 0, 1) forwards;
}

/* Click Ripple Animation */
.click-ripple {
  width: 40px;
  height: 40px;
  margin-left: -20px;
  margin-top: -20px;
  animation: rippleSpread 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes rippleSpread {
  0% {
    transform: scale(0.2);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.6);
    opacity: 0;
  }
}

/* Toast Fade */
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.2s ease;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}

.fade-fast-enter-active,
.fade-fast-leave-active {
  transition: opacity 0.15s ease;
}
.fade-fast-enter-from,
.fade-fast-leave-to {
  opacity: 0;
}

/* Custom Select Styling */
:deep(.preview-page-select .el-select__wrapper) {
  background-color: rgba(255, 255, 255, 0.08) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  box-shadow: none !important;
  border-radius: 10px !important;
  color: #ffffff !important;
}
:deep(.preview-page-select .el-select__placeholder) {
  color: #cbd5e1 !important;
  font-size: 11px !important;
}
:deep(.preview-page-select .el-select__selected-item) {
  color: #ffffff !important;
  font-size: 11px !important;
  font-weight: 600 !important;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.3);
  border-radius: 9999px;
}
</style>
