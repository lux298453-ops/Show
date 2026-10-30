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
      :class="chromeVisible ? 'opacity-100' : 'opacity-0 -translate-y-3 pointer-events-none'"
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
          <span class="text-[9px] text-slate-300 font-normal leading-none">纯净全屏演示</span>
        </div>
      </div>

      <div class="h-4 w-[1px] bg-white/15 mx-0.5"></div>

      <!-- Center: Page Selector Dropdown with Segmented Control -->
      <div ref="pageDropdownRef" class="relative" @click.stop>
        <!-- Single entry pill trigger button -->
        <button
          type="button"
          class="wf-pill px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/15 hover:border-white/25 text-white flex items-center justify-between gap-2 min-w-[170px] max-w-[210px] cursor-pointer shadow-xs transition-all select-none"
          title="切换画板"
          @click="pageDropdownOpen = !pageDropdownOpen"
        >
          <span class="truncate font-semibold text-xs text-white">
            {{ currentPageIndex >= 0 ? `${currentPageIndex + 1}. ${currentPage?.name}` : '选择画板' }}
          </span>
          <ChevronDown
            class="w-3.5 h-3.5 text-slate-300 transition-transform duration-200 shrink-0"
            :class="{ 'rotate-180': pageDropdownOpen }"
          />
        </button>

        <!-- Segmented Dropdown Popover -->
        <Transition name="dropdown-fade">
          <div
            v-if="pageDropdownOpen"
            class="absolute top-full mt-2 left-1/2 -translate-x-1/2 w-72 rounded-2xl bg-slate-900/95 backdrop-blur-2xl border border-white/15 shadow-[0_20px_50px_rgba(0,0,0,0.85)] p-2.5 flex flex-col gap-2 z-50 select-none text-white text-xs"
          >
            <!-- Segmented Control Pills -->
            <div class="bg-black/50 border border-white/10 rounded-xl p-1 flex gap-1">
              <button
                type="button"
                class="flex-1 py-1 px-2 rounded-lg text-center font-semibold text-[11px] transition-all duration-150 cursor-pointer"
                :class="activeSegmentTab === 'main' ? 'bg-[#0D99FF]/20 text-[#38bdf8] border border-[#0D99FF]/40 shadow-xs font-bold' : 'text-slate-400 hover:text-white border border-transparent'"
                @click="activeSegmentTab = 'main'"
              >
                主流程 ({{ mainFlowPages.length }})
              </button>
              <button
                type="button"
                class="flex-1 py-1 px-2 rounded-lg text-center font-semibold text-[11px] transition-all duration-150 cursor-pointer"
                :class="activeSegmentTab === 'all' ? 'bg-[#0D99FF]/20 text-[#38bdf8] border border-[#0D99FF]/40 shadow-xs font-bold' : 'text-slate-400 hover:text-white border border-transparent'"
                @click="activeSegmentTab = 'all'"
              >
                全部画板 ({{ pages.length }})
              </button>
            </div>

            <!-- View A: Main Flow (Default: 4 Tabs of Bottom Navigation) -->
            <div v-if="activeSegmentTab === 'main'" class="space-y-1 max-h-[190px] overflow-y-auto custom-scrollbar pr-0.5">
              <div
                v-for="(p, idx) in mainFlowPages"
                :key="p.id"
                class="px-2.5 py-1.5 rounded-lg flex items-center justify-between cursor-pointer transition-colors"
                :class="isPageActive(p.id) ? 'bg-white/15 text-[#38bdf8] font-bold' : 'hover:bg-white/10 text-slate-200'"
                @click="selectPageFromDropdown(p.id)"
              >
                <span class="truncate">{{ idx + 1 }}. {{ p.name }}</span>
                <Check v-if="isPageActive(p.id)" class="w-3.5 h-3.5 text-[#38bdf8] shrink-0" />
              </div>
              <div v-if="mainFlowPages.length === 0" class="py-3 text-center text-slate-400 text-xs">
                暂无主流程页面
              </div>
            </div>

            <!-- View B: All Artboards (Inspection View) -->
            <div v-else class="space-y-2">
              <div class="relative">
                <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  v-model="searchQuery"
                  type="text"
                  placeholder="搜索画板名称、简拼..."
                  class="w-full bg-white/5 border border-white/10 rounded-xl pl-8 pr-7 py-1.5 text-xs text-white placeholder-slate-400 outline-none focus:border-[#0D99FF] transition-colors"
                  @click.stop
                />
                <button
                  v-if="searchQuery"
                  type="button"
                  class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white p-0.5 rounded cursor-pointer"
                  @click.stop="searchQuery = ''"
                >
                  <X class="w-3 h-3" />
                </button>
              </div>

              <div class="space-y-1 max-h-[220px] overflow-y-auto custom-scrollbar pr-0.5">
                <div
                  v-for="(p, idx) in filteredAllPages"
                  :key="p.id"
                  class="px-2.5 py-1.5 rounded-lg flex items-center justify-between cursor-pointer transition-colors"
                  :class="p.id === currentPageId ? 'bg-white/15 text-[#38bdf8] font-bold' : 'hover:bg-white/10 text-slate-200'"
                  @click="selectPageFromDropdown(p.id)"
                >
                  <span class="truncate">{{ idx + 1 }}. {{ p.name }}</span>
                  <Check v-if="p.id === currentPageId" class="w-3.5 h-3.5 text-[#38bdf8] shrink-0" />
                </div>
                <div v-if="filteredAllPages.length === 0" class="py-4 text-center text-slate-400 text-xs">
                  未找到匹配画板
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <div class="h-4 w-[1px] bg-white/15 mx-0.5"></div>

      <!-- Feature Toggles & Actions -->
      <div class="flex items-center gap-1.5">
        <!-- 📱 手机外壳切换按钮 -->
        <button
          class="wf-pill inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-semibold transition-all cursor-pointer"
          :class="showDeviceFrame 
            ? 'bg-emerald-500/20 border-emerald-500/60 text-emerald-300 shadow-[0_0_15px_rgba(16,185,129,0.35)]' 
            : 'bg-white/5 border-white/10 text-slate-300 hover:bg-white/10 hover:text-slate-200'"
          title="手机画板默认套外壳。网页画板按自己的宽高显示，也可手动套上外壳 (快捷键 D)"
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
      class="preview-scroll flex-1 w-full min-h-0"
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

      <!-- 画框按真实尺寸摆放：放得下就居中，超出预览窗口的部分可以滚动 -->
      <div class="preview-center">
        <!-- ===== High-Precision iPhone 16 Pro Chassis ===== -->
        <div
          class="phone-chassis relative transition-all duration-300 select-none"
          :class="[
            showDeviceFrame
              ? 'p-[14px] bg-[#1a1f2c] rounded-[56px] shadow-[0_0_0_1px_rgba(255,255,255,0.18),0_0_0_3px_#272e3f,0_0_0_4px_rgba(255,255,255,0.06),0_25px_80px_-15px_rgba(0,0,0,0.9),0_0_50px_rgba(6,182,212,0.1)]'
              : 'p-0 rounded-lg shadow-[0_20px_60px_-10px_rgba(0,0,0,0.85)]'
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
            class="phone-screen relative bg-black overflow-hidden flex flex-col transition-all duration-300"
            :class="[
              showDeviceFrame ? 'rounded-[44px]' : 'rounded-lg'
            ]"
            :style="{ width: `${screenWidth}px`, height: `${screenHeight}px` }"
            @click="onScreenClick"
          >
            <!-- Floating Back Navigation Button (When history exists) -->
            <transition name="fade-fast">
              <button
                v-if="historyStack.length > 0"
                class="wf-tap absolute top-3.5 left-3.5 z-40 w-8 h-8 rounded-full bg-black/50 hover:bg-black/75 backdrop-blur-md text-white flex items-center justify-center transition-all cursor-pointer shadow-lg border border-white/20"
                title="返回上一页"
                @click.stop="handleBack"
              >
                <ArrowLeft class="w-4 h-4" />
              </button>
            </transition>

            <!-- ===== Prototype Content Stage with Native Push Transitions ===== -->
            <div
              class="relative flex-1 w-full overflow-hidden bg-white"
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
                      class="p-1 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
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
                      class="p-1 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
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
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Layers,
  Smartphone,
  RotateCcw,
  Link,
  X,
  ArrowLeft,
  ChevronDown,
  Search,
  Check,
} from 'lucide-vue-next'
import { projectApi } from '../api/project'
import type { Element, Page, Prototype } from '../types'
import PageCanvas from '../components/PageCanvas.vue'
import { findInteractionByDomUids, hitInteractionElement, findElementAt, resolveNavigateElement } from '../utils/interactionHit'
import { useProjectTitle } from '../utils/pageTitle'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const proto = ref<Prototype | null>(null)
useProjectTitle(() => proto.value?.project.name)
const pages = computed<Page[]>(() => proto.value?.pages || [])

const currentPageId = ref<number | null>(null)
const currentPage = computed<Page | null>(() => {
  if (!pages.value.length) return null
  return pages.value.find((p) => p.id === currentPageId.value) || pages.value[0] || null
})

const currentPageIndex = computed(() => {
  if (!currentPageId.value) return -1
  return pages.value.findIndex((p) => p.id === currentPageId.value)
})

// 分段切换下拉框状态与数据源
const pageDropdownOpen = ref(false)
const activeSegmentTab = ref<'main' | 'all'>('main')
const searchQuery = ref('')
const pageDropdownRef = ref<HTMLElement | null>(null)

// 智能分析原型画板底栏 4 个 Tab 对应的原型页
function detectBottomNavPages(pagesList: Page[]): Page[] {
  if (!pagesList || !pagesList.length) return []
  const pagesById = new Map<number, Page>(pagesList.map((p) => [p.id, p]))

  const slotVotes = new Map<number, Map<number, number>>()
  const slotLabels = new Map<number, Map<string, number>>()

  for (const p of pagesList) {
    const h = p.canvas_height || 812
    const w = p.canvas_width || 375
    const bottomThreshold = h - 110
    const bottomEls = (p.elements || []).filter((e) => e.y >= bottomThreshold && e.height <= 65)

    if (
      bottomEls.length < 3 &&
      !bottomEls.some((e) => e.label && (e.label.includes('底部') || e.label.includes('导航')))
    ) {
      continue
    }

    const navItems = bottomEls.filter(
      (e) =>
        e.type === 'icon' ||
        e.type === 'button' ||
        (e.interaction && e.interaction.action === 'navigate') ||
        (e.label && ['家园', '商城', '收集', '我的', '首页', '发现', '消息', '设置'].some((k) => e.label.includes(k)))
    )

    for (const it of navItems) {
      const cx = it.x + it.width / 2
      const normX = Math.max(0, Math.min(0.999, cx / w))
      // 默认将底栏按屏幕横向分为 4 个槽位
      const slot = Math.floor(normX * 4)

      if (!slotVotes.has(slot)) slotVotes.set(slot, new Map())
      if (!slotLabels.has(slot)) slotLabels.set(slot, new Map())

      const votes = slotVotes.get(slot)!
      const labels = slotLabels.get(slot)!

      const lbl = (it.label || '').trim()
      if (lbl) {
        labels.set(lbl, (labels.get(lbl) || 0) + 1)
      }

      if (it.interaction && it.interaction.action === 'navigate' && it.interaction.target_page_id) {
        const tgt = it.interaction.target_page_id
        votes.set(tgt, (votes.get(tgt) || 0) + 2)
      } else if (it.type === 'icon' && !it.interaction) {
        // 自指/当前激活项
        votes.set(p.id, (votes.get(p.id) || 0) + 1)
      }
    }
  }

  const result: Page[] = []
  const usedIds = new Set<number>()

  for (let slot = 0; slot < 4; slot++) {
    const votes = slotVotes.get(slot)
    const labels = slotLabels.get(slot)
    let bestPageId: number | null = null
    let maxVote = -1

    if (votes) {
      for (const [pid, count] of votes.entries()) {
        if (count > maxVote && pagesById.has(pid) && !usedIds.has(pid)) {
          maxVote = count
          bestPageId = pid
        }
      }
    }

    if (!bestPageId && labels) {
      let topLabel = ''
      let maxLblCount = -1
      for (const [lbl, c] of labels.entries()) {
        if (c > maxLblCount) {
          maxLblCount = c
          topLabel = lbl
        }
      }
      if (topLabel) {
        const matched = pagesList.find(
          (p) => !usedIds.has(p.id) && (p.name.includes(topLabel) || topLabel.includes(p.name))
        )
        if (matched) bestPageId = matched.id
      }
    }

    if (bestPageId && pagesById.has(bestPageId)) {
      usedIds.add(bestPageId)
      result.push(pagesById.get(bestPageId)!)
    }
  }

  return result
}

// 视图 A：主流程页面（严格映射底栏 4 个 Tab 对应原型图，支持 appMap 与智能底栏识别）
const mainFlowPages = computed<Page[]>(() => {
  if (!pages.value.length) return []

  // 1. 尝试从 proto.value.project.appMap 解析
  let appMapObj: any = null
  const rawMap = proto.value?.project?.appMap
  if (rawMap) {
    if (typeof rawMap === 'string') {
      try {
        appMapObj = JSON.parse(rawMap)
      } catch (err) {
        console.warn('[PurePreview] Failed to parse project.appMap', err)
      }
    } else if (typeof rawMap === 'object') {
      appMapObj = rawMap
    }
  }

  const tabBar = appMapObj?.tab_bar
  if (Array.isArray(tabBar?.items) && tabBar.items.length > 0) {
    const list: Page[] = []
    const visited = new Set<number>()
    for (const item of tabBar.items) {
      if (typeof item?.target === 'number' && !visited.has(item.target)) {
        const p = pages.value.find((pg) => pg.id === item.target)
        if (p) {
          list.push(p)
          visited.add(p.id)
        }
      }
    }
    if (list.length >= 2) return list
  }

  if (Array.isArray(tabBar?.pages) && tabBar.pages.length > 0) {
    const list: Page[] = []
    const visited = new Set<number>()
    for (const pid of tabBar.pages) {
      if (typeof pid === 'number' && !visited.has(pid)) {
        const p = pages.value.find((pg) => pg.id === pid)
        if (p) {
          list.push(p)
          visited.add(p.id)
        }
      }
    }
    if (list.length >= 2) return list
  }

  // 2. 智能底栏分析：自动从画板底层元素提取导航栏各 Tab 对应的原型图
  const detected = detectBottomNavPages(pages.value)
  if (detected.length >= 2) {
    return detected
  }

  // 3. 终极兜底策略：未配置 tab_bar 且未识别到底栏时取前 4 个画板
  return pages.value.slice(0, Math.min(4, pages.value.length))
})

// 判断下拉列表中某项是否处于激活态（包含同 Tab 子状态关联）
function isPageActive(pageId: number): boolean {
  if (pageId === currentPageId.value) return true
  // 关联子页判断：若当前停留在 2322 (扭蛋抽奖页)，而该 Tab 项是 2346 (收集_次态)，则视为处于该 Tab
  if (currentPageId.value === 2322 && pageId === 2346) return true
  return false
}

// 拼音首字母提取算法，支持中文字符声母模糊匹配
function getPinyinInitials(str: string): string {
  return str
    .split('')
    .map((char) => {
      const code = char.charCodeAt(0)
      if (code >= 0x4e00 && code <= 0x9fa5) {
        if (char.localeCompare('吖', 'zh') >= 0 && char.localeCompare('八', 'zh') < 0) return 'a'
        if (char.localeCompare('八', 'zh') >= 0 && char.localeCompare('嚓', 'zh') < 0) return 'b'
        if (char.localeCompare('嚓', 'zh') >= 0 && char.localeCompare('咑', 'zh') < 0) return 'c'
        if (char.localeCompare('咑', 'zh') >= 0 && char.localeCompare('妸', 'zh') < 0) return 'd'
        if (char.localeCompare('妸', 'zh') >= 0 && char.localeCompare('发', 'zh') < 0) return 'e'
        if (char.localeCompare('发', 'zh') >= 0 && char.localeCompare('旮', 'zh') < 0) return 'f'
        if (char.localeCompare('旮', 'zh') >= 0 && char.localeCompare('铪', 'zh') < 0) return 'g'
        if (char.localeCompare('铪', 'zh') >= 0 && char.localeCompare('讥', 'zh') < 0) return 'h'
        if (char.localeCompare('讥', 'zh') >= 0 && char.localeCompare('咔', 'zh') < 0) return 'j'
        if (char.localeCompare('咔', 'zh') >= 0 && char.localeCompare('垃', 'zh') < 0) return 'k'
        if (char.localeCompare('垃', 'zh') >= 0 && char.localeCompare('呒', 'zh') < 0) return 'l'
        if (char.localeCompare('呒', 'zh') >= 0 && char.localeCompare('嗱', 'zh') < 0) return 'm'
        if (char.localeCompare('嗱', 'zh') >= 0 && char.localeCompare('噢', 'zh') < 0) return 'n'
        if (char.localeCompare('噢', 'zh') >= 0 && char.localeCompare('妑', 'zh') < 0) return 'o'
        if (char.localeCompare('妑', 'zh') >= 0 && char.localeCompare('七', 'zh') < 0) return 'p'
        if (char.localeCompare('七', 'zh') >= 0 && char.localeCompare('亽', 'zh') < 0) return 'q'
        if (char.localeCompare('亽', 'zh') >= 0 && char.localeCompare('仨', 'zh') < 0) return 'r'
        if (char.localeCompare('仨', 'zh') >= 0 && char.localeCompare('他', 'zh') < 0) return 's'
        if (char.localeCompare('他', 'zh') >= 0 && char.localeCompare('哇', 'zh') < 0) return 't'
        if (char.localeCompare('哇', 'zh') >= 0 && char.localeCompare('夕', 'zh') < 0) return 'w'
        if (char.localeCompare('夕', 'zh') >= 0 && char.localeCompare('丫', 'zh') < 0) return 'x'
        if (char.localeCompare('丫', 'zh') >= 0 && char.localeCompare('帀', 'zh') < 0) return 'y'
        if (char.localeCompare('帀', 'zh') >= 0 && char.localeCompare('咗', 'zh') <= 0) return 'z'
      }
      return char.toLowerCase()
    })
    .join('')
}

// 视图 B：全部画板（支持名称与拼音快速模糊搜索过滤）
const filteredAllPages = computed<Page[]>(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return pages.value
  return pages.value.filter((p) => {
    const name = p.name.toLowerCase()
    if (name.includes(q)) return true
    const initials = getPinyinInitials(p.name)
    if (initials.includes(q)) return true
    return false
  })
})

function selectPageFromDropdown(pageId: number) {
  navigateTo(pageId)
  pageDropdownOpen.value = false
  searchQuery.value = ''
}

function handleWindowClick(e: MouseEvent) {
  if (!pageDropdownOpen.value) return
  const target = e.target as HTMLElement | null
  if (pageDropdownRef.value && !pageDropdownRef.value.contains(target)) {
    pageDropdownOpen.value = false
  }
}

/** 竖屏、宽度不超过 480 的画板按手机展示。更宽或横过来的画板按自己的宽高展示。 */
function isPhoneArtboard(page: Page | null) {
  const w = page?.canvas_width || 375
  const h = page?.canvas_height || 812
  return w <= 480 && h > w
}

// 换页后按这一页自己的尺寸决定要不要套手机壳；当前页仍可用按钮临时改
const frameOverride = ref<boolean | null>(null)
const showDeviceFrame = computed({
  get() {
    if (frameOverride.value != null) return frameOverride.value
    return isPhoneArtboard(currentPage.value)
  },
  set(on: boolean) {
    frameOverride.value = on
  },
})

const screenWidth = computed(() => (showDeviceFrame.value ? 375 : (currentPage.value?.canvas_width || 375)))
const screenHeight = computed(() => currentPage.value?.canvas_height || 812)

// 历史导航栈与原生 Push 转场
const historyStack = ref<number[]>([])
const transitionDirection = ref<'forward' | 'backward'>('forward')
const pageTransitionName = computed(() =>
  transitionDirection.value === 'forward' ? 'push-forward' : 'push-backward',
)

// 手机壳全视口自适应等比缩放
const viewportContainerRef = ref<HTMLElement | null>(null)
const phoneScale = ref(1)

function updatePhoneScale() {
  phoneScale.value = 1
}

watch(currentPageId, () => {
  frameOverride.value = null
  nextTick(updatePhoneScale)
})
watch([showDeviceFrame, screenWidth, screenHeight], () => nextTick(updatePhoneScale))

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


// 核心功能：PageCanvas ref（保留供导航使用）
const pageCanvasRef = ref<any>(null)

// 屏幕未命中交互区域点击处理（热区显示已关闭，仅保留接口不报错）
function onScreenMissClick(pos?: { x: number; y: number; uids?: string[] }) {
  if (!pos || !currentPage.value) return
  const hit = resolveNavigateElement(currentPage.value, pos.x, pos.y, pos.uids)
  const targetId = hit?.interaction?.target_page_id
  if (targetId) navigateTo(targetId)
}

function onScreenClick(e: MouseEvent) {
  spawnRipple(e.clientX, e.clientY)
}

function onChassisClick(e: MouseEvent) {
  spawnRipple(e.clientX, e.clientY)
}

const chromeVisible = ref(true)

function onViewportAreaClick(e: MouseEvent) {
  pageDropdownOpen.value = false
  const target = e.target as HTMLElement | null
  if (target?.closest('.phone-chassis')) chromeVisible.value = false
  else chromeVisible.value = true
  spawnRipple(e.clientX, e.clientY)
}

function onBackgroundClick() {
  pageDropdownOpen.value = false
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

function handleNavigate(pageName: string, uids?: string[], pos?: { x?: number; y?: number; uid?: string }) {
  // 1. 优先通过 DOM UID 精准匹配已绑定的交互连线
  const byUid = findInteractionByDomUids(currentPage.value, uids)
  const uidTarget = byUid?.interaction?.target_page_id
  if (uidTarget) {
    navigateTo(uidTarget)
    const named = pages.value.find((p) => p.id === uidTarget)
    if (named) showToast(`前往: ${named.name}`)
    return
  }

  // 2. 尝试通过点击物理坐标命中热区交互规则
  if (pos?.x != null && pos?.y != null) {
    const hit = hitInteractionElement(currentPage.value, pos.x, pos.y)
    const hitTarget = hit?.interaction?.target_page_id
    if (hitTarget) {
      navigateTo(hitTarget)
      const named = pages.value.find((p) => p.id === hitTarget)
      if (named) showToast(`前往: ${named.name}`)
      return
    }

    // 3. 关键判定：检查点击位置是否命中了某个具体组件（若该组件无交互规则，说明未连接或已被用户删除）
    const matchedEl = findElementAt(currentPage.value, pos.x, pos.y, uids)
    if (matchedEl) {
      console.log(`[PurePreview] 拦截跳转：组件「${matchedEl.label || matchedEl.id}」未绑定交互连线`)
      return
    }
  }

  // 4. 如果点击元素带有 UID，检查对应组件是否有交互
  if (uids?.length) {
    const matchedEl = findElementAt(currentPage.value, null, null, uids)
    if (matchedEl && !matchedEl.interaction?.target_page_id) {
      console.log(`[PurePreview] 拦截跳转：组件「${matchedEl.label || matchedEl.id}」无交互连线`)
      return
    }
  }

  // 5. 如果当前项目存在交互连线系统，禁止非绑定的任意元素根据文本跳转
  const projectHasInteractions = pages.value.some((p) => (p.elements || []).some((e) => e.interaction?.target_page_id))
  if (projectHasInteractions) {
    return
  }

  // 6. 纯静态原型无元素时的兜底匹配
  const clean = pageName.trim().toLowerCase()
  const target = pages.value.find(
    (p) => p.name.trim().toLowerCase() === clean || String(p.id) === clean,
  ) || pages.value.find(
    (p) => p.name.toLowerCase().includes(clean) || clean.includes(p.name.toLowerCase()),
  )
  if (target) {
    navigateTo(target.id)
    showToast(`前往: ${target.name}`)
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
  showToast('原型已重置至首页')
}

function copyShareLink() {
  const url = `${window.location.origin}/projects/${id}/preview?page=${currentPageId.value ?? ''}`
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard
      .writeText(url)
      .then(() => showToast('已复制纯原型演示链接到剪贴板'))
      .catch(() => showToast(`请复制链接: ${url}`))
  } else {
    const ta = document.createElement('textarea')
    ta.value = url
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    showToast('已复制纯原型演示链接到剪贴板')
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

function handleElementClick(el: Element) {
  const ix = el.interaction
  if (!ix) {
    return
  }

  if (ix.action === 'back' || ix.action === 'close') {
    handleBack()
  } else if (ix.action === 'navigate' && ix.target_page_id) {
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
  }
}

// 快捷键支持
function onGlobalKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (pageDropdownOpen.value) {
      pageDropdownOpen.value = false
      return
    }
    if (activeModal.value) {
      closeModal()
      return
    }
    exitPreview()
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
    showToast(`加载失败: ${e.message || '网络错误'}`)
  }
}

function onPreviewWheelMessage(ev: MessageEvent) {
  if (ev.data?.type === 'wf-preview-page-click') {
    chromeVisible.value = false
    return
  }
  if (ev.data?.type !== 'wf-preview-wheel') return
  const el = viewportContainerRef.value
  if (!el) return
  el.scrollLeft += Number(ev.data.dx) || 0
  el.scrollTop += Number(ev.data.dy) || 0
}

onMounted(() => {
  window.addEventListener('resize', updatePhoneScale)
  window.addEventListener('keydown', onGlobalKeyDown)
  window.addEventListener('message', onPreviewWheelMessage)
  window.addEventListener('click', handleWindowClick)
  loadData()
})

onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer)
  window.removeEventListener('resize', updatePhoneScale)
  window.removeEventListener('keydown', onGlobalKeyDown)
  window.removeEventListener('message', onPreviewWheelMessage)
  window.removeEventListener('click', handleWindowClick)
})
</script>

<style scoped>
.preview-scroll {
  overflow: auto;
  height: 100%;
}
.preview-center {
  display: flex;
  align-items: safe center;
  justify-content: safe center;
  box-sizing: border-box;
  min-width: 100%;
  min-height: 100%;
  width: max-content;
  height: max-content;
  padding: 64px 32px 36px;
}
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

/* Dropdown Animation */
.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: opacity 0.15s cubic-bezier(0.16, 1, 0.3, 1), transform 0.15s cubic-bezier(0.16, 1, 0.3, 1);
}
.dropdown-fade-enter-from,
.dropdown-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -6px);
}
.dropdown-fade-enter-to,
.dropdown-fade-leave-from {
  opacity: 1;
  transform: translate(-50%, 0);
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
