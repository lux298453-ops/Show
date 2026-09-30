<template>
  <div class="min-h-screen bg-[#f8fafc] dark:bg-[#1e1e1e] flex flex-col text-slate-800 dark:text-slate-100 selection:bg-emerald-500 selection:text-white transition-colors duration-200">
    <!-- ===== Minimalist Figma-Style Top Navigation (Full-width, no edge margins) ===== -->
    <header class="sticky top-0 z-30 bg-white/95 dark:bg-[#2c2c2c] backdrop-blur-md border-b border-slate-200/80 dark:border-[#383838] px-6 py-2.5 transition-all shadow-2xs dark:shadow-md dark:shadow-black/50">
      <div class="w-full flex items-center justify-between gap-4">
        <!-- Breadcrumbs & Project Identity -->
        <div class="flex items-center gap-2.5 min-w-0">
          <button
            class="wf-tap p-1.5 rounded-lg text-slate-400 dark:text-slate-300 hover:text-slate-800 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-[#383838] transition-colors cursor-pointer"
            :title="t('backToProjects')"
            @click="router.push('/')"
          >
            <ArrowLeft class="w-4 h-4" />
          </button>

          <img
            src="/favicon.svg"
            alt="WireForge"
            class="w-6 h-6 rounded-md object-contain cursor-pointer hover:scale-105 transition-transform shrink-0"
            @click="router.push('/')"
            :title="t('brand')"
          />

          <div class="flex items-center gap-1.5 text-xs text-slate-400 dark:text-slate-500">
            <span class="hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer transition-colors" @click="router.push('/')">{{ t('myProjects') }}</span>
            <span>/</span>
          </div>

          <h1 class="text-sm font-bold text-slate-900 dark:text-white tracking-tight truncate max-w-xs sm:max-w-md">
            {{ project?.name || t('projectDetail') }}
          </h1>

          <span class="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 dark:bg-[#383838] text-slate-600 dark:text-slate-200 tabular-nums border border-slate-200/60 dark:border-[#484848] hidden sm:inline-flex">
            {{ pages.length }} {{ t('frames') }}
          </span>
        </div>

        <!-- Top Right Action Controls -->
        <div class="flex items-center gap-2.5 shrink-0">
          <!-- Secondary: Scan local directory -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 bg-white dark:bg-[#383838] hover:bg-slate-50 dark:hover:bg-[#444444] active:bg-slate-100 dark:active:bg-[#333333] border border-slate-200/80 dark:border-[#484848] rounded-lg shadow-2xs transition-all disabled:opacity-50 cursor-pointer"
            :disabled="scanning"
            :title="t('scanDesigns')"
            @click="scan"
          >
            <RefreshCw class="w-3.5 h-3.5 text-slate-500 dark:text-slate-300" :class="{ 'animate-spin': scanning }" />
            <span class="hidden sm:inline">{{ scanning ? t('scanning') : t('scanDesigns') }}</span>
          </button>

          <!-- Secondary/AI: AI Analyze Wireframe -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-emerald-800 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/70 hover:bg-emerald-100/80 dark:hover:bg-emerald-900/60 active:bg-emerald-100 border border-emerald-200/80 dark:border-emerald-600/50 rounded-lg transition-all disabled:opacity-50 cursor-pointer shadow-2xs"
            :disabled="analyzing || pages.length === 0"
            :title="t('aiGenerate')"
            @click="analyze"
          >
            <Sparkles class="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" :class="{ 'animate-spin': analyzing }" />
            <span>{{ analyzing ? (analyzeProgressText || t('aiGenerating')) : t('aiGenerate') }}</span>
          </button>

          <!-- Primary CTA: Open Interactive Canvas -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white dark:text-[#38bdf8] bg-[#0d99ff] hover:bg-[#0b87e0] active:bg-[#0972bd] dark:bg-[#0d99ff]/20 dark:hover:bg-[#0d99ff]/35 dark:active:bg-[#0d99ff]/50 rounded-lg shadow-xs shadow-[#0d99ff]/20 dark:shadow-[0_0_14px_rgba(13,153,255,0.25)] border border-[#0d99ff]/90 dark:border-[#0d99ff]/50 transition-all disabled:opacity-50 cursor-pointer"
            :disabled="entering"
            :title="t('enterCanvas')"
            @click="enterCanvas"
          >
            <Play class="w-3.5 h-3.5 fill-current" />
            <span>{{ t('enterCanvas') }}</span>
          </button>

          <!-- Language & Theme Switcher (1:1 with Project List) -->
          <NavbarControls />
        </div>
      </div>
    </header>

    <!-- ===== Secondary Toolbar (Search, Filter, View Mode, Path Hint) ===== -->
    <div class="border-b border-slate-200/70 dark:border-[#333333] bg-white/70 dark:bg-[#252525] px-6 py-2.5 transition-colors">
      <div class="w-full flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs">
        <!-- Left: Quiet Directory Path Indicator & Quick Copy -->
        <div class="flex items-center gap-2 text-slate-500 dark:text-[#a1a1a1] min-w-0">
          <span class="text-slate-400 dark:text-[#a1a1a1] font-medium shrink-0 flex items-center gap-1">
            <Folder class="w-3.5 h-3.5 text-slate-400 dark:text-[#a1a1a1]" />
            {{ t('designsPath') }}:
          </span>
          <div
            class="group inline-flex items-center gap-1.5 px-2 py-1 rounded bg-slate-100/90 dark:bg-[#1e1e1e] hover:bg-slate-200/70 dark:hover:bg-[#2c2c2c] border border-slate-200/60 dark:border-[#383838] font-mono text-[11px] text-slate-600 dark:text-slate-300 truncate max-w-sm sm:max-w-md cursor-pointer transition-colors"
            :title="t('copyPath')"
            @click="copyDirectoryPath"
          >
            <span class="truncate">{{ designsDir }}</span>
            <button class="shrink-0 text-slate-400 group-hover:text-slate-700 dark:group-hover:text-white ml-0.5">
              <CheckCheck v-if="copied" class="w-3 h-3 text-emerald-600 dark:text-[#0d99ff]" />
              <Copy v-else class="w-3 h-3" />
            </button>
          </div>
        </div>

        <!-- Right: Search, Status Filter & View Toggle -->
        <div class="flex items-center flex-wrap gap-2.5 shrink-0">
          <!-- Search Box -->
          <div class="relative w-44 sm:w-52">
            <Search class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              v-model="searchQuery"
              type="text"
              :placeholder="t('searchFrames')"
              class="w-full pl-8 pr-2.5 py-1 text-xs bg-white dark:bg-[#1e1e1e] border border-slate-200 dark:border-[#444444] rounded-lg text-slate-800 dark:text-slate-100 placeholder-slate-400 dark:placeholder-[#757575] focus:outline-none focus:border-[#0d99ff] focus:ring-1 focus:ring-[#0d99ff]/30 transition-all"
            />
          </div>

          <!-- Status Segmented Filter -->
          <div class="flex items-center bg-slate-100/90 dark:bg-[#1a1a1a] p-0.5 rounded-lg border border-slate-200/70 dark:border-[#383838] text-[11px]">
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'all' ? 'bg-white dark:bg-[#383838] text-slate-900 dark:text-white shadow-2xs font-semibold' : 'text-slate-500 dark:text-[#a1a1a1] hover:text-slate-800 dark:hover:text-white'"
              @click="statusFilter = 'all'"
            >
              {{ t('all') }} ({{ pages.length }})
            </button>
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'analyzed' ? 'bg-white dark:bg-[#383838] text-emerald-700 dark:text-emerald-400 shadow-2xs font-semibold' : 'text-slate-500 dark:text-[#a1a1a1] hover:text-slate-800 dark:hover:text-white'"
              @click="statusFilter = 'analyzed'"
            >
              {{ t('analyzed') }} ({{ analyzedCount }})
            </button>
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'pending' ? 'bg-white dark:bg-[#383838] text-amber-700 dark:text-amber-400 shadow-2xs font-semibold' : 'text-slate-500 dark:text-[#a1a1a1] hover:text-slate-800 dark:hover:text-white'"
              @click="statusFilter = 'pending'"
            >
              {{ t('pending') }} ({{ pendingCount }})
            </button>
          </div>

          <!-- View Mode Switcher -->
          <div class="flex items-center bg-slate-100/90 dark:bg-[#1a1a1a] p-0.5 rounded-lg border border-slate-200/70 dark:border-[#383838]">
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'grid' ? 'bg-white dark:bg-[#383838] text-slate-900 dark:text-white shadow-2xs' : 'text-slate-400 hover:text-slate-700 dark:hover:text-white'"
              :title="t('gridView')"
              @click="viewMode = 'grid'"
            >
              <LayoutGrid class="w-3.5 h-3.5" />
            </button>
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'list' ? 'bg-white dark:bg-[#383838] text-slate-900 dark:text-white shadow-2xs' : 'text-slate-400 hover:text-slate-700 dark:hover:text-white'"
              :title="t('listView')"
              @click="viewMode = 'list'"
            >
              <List class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Main Frame Workspace ===== -->
    <main class="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
      <!-- Loading State -->
      <div v-if="loading" class="flex flex-col items-center justify-center py-24">
        <div class="w-8 h-8 border-2 border-emerald-200 border-t-emerald-600 rounded-full animate-spin"></div>
        <p class="mt-3 text-xs text-slate-400 dark:text-[#a1a1a1] font-medium tracking-wide">{{ t('readingData') }}</p>
      </div>

      <!-- Empty State (No Pages Found) -->
      <div
        v-else-if="pages.length === 0"
        class="bg-white dark:bg-[#2d2d2d] border border-dashed border-slate-300/80 dark:border-[#444444] rounded-2xl p-12 text-center max-w-md mx-auto my-12 shadow-sm dark:shadow-xl dark:shadow-black/50"
      >
        <div class="w-11 h-11 rounded-xl bg-slate-50 dark:bg-[#232323] text-slate-400 dark:text-[#a1a1a1] flex items-center justify-center mx-auto mb-3 border border-slate-200/70 dark:border-[#383838]">
          <Folder class="w-5 h-5" />
        </div>
        <h3 class="text-sm font-bold text-slate-800 dark:text-white">{{ t('noFrames') }}</h3>
        <p class="text-xs text-slate-400 dark:text-[#a1a1a1] mt-1 mb-5 leading-relaxed max-w-xs mx-auto">
          {{ t('noFramesDesc') }}
        </p>
        <div class="flex items-center justify-center gap-2">
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white dark:text-[#38bdf8] bg-[#0d99ff] hover:bg-[#0b87e0] active:bg-[#0972bd] dark:bg-[#0d99ff]/20 dark:hover:bg-[#0d99ff]/35 dark:border-[#0d99ff]/50 border border-[#0d99ff]/90 rounded-lg shadow-sm transition-all cursor-pointer disabled:opacity-50"
            :disabled="entering"
            @click="enterCanvas"
          >
            <Play class="w-3.5 h-3.5 fill-current" />
            <span>{{ entering ? '正在进入...' : t('directDraw') }}</span>
          </button>
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-slate-700 dark:text-slate-200 bg-white dark:bg-[#383838] hover:bg-slate-50 dark:hover:bg-[#444444] border border-slate-200 dark:border-[#484848] rounded-lg transition-all cursor-pointer disabled:opacity-50"
            :disabled="scanning"
            @click="scan"
          >
            <RefreshCw class="w-3.5 h-3.5 text-slate-500 dark:text-slate-300" :class="{ 'animate-spin': scanning }" />
            <span>{{ t('scanDesigns') }}</span>
          </button>
        </div>
      </div>

      <!-- Filter No Results -->
      <div
        v-else-if="filteredPages.length === 0"
        class="py-20 text-center text-slate-400 dark:text-[#a1a1a1] text-xs"
      >
        <p>{{ t('noMatchingFrames', { query: searchQuery }) }}</p>
        <button
          class="mt-2 text-emerald-600 dark:text-[#0d99ff] hover:underline font-medium cursor-pointer"
          @click="searchQuery = ''; statusFilter = 'all'"
        >
          {{ t('clearFramesFilter') }}
        </button>
      </div>

      <!-- ===== Mode 1: Figma-Style Minimalist Grid View ===== -->
      <div
        v-else-if="viewMode === 'grid'"
        class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
      >
        <div
          v-for="p in filteredPages"
          :key="p.id"
          class="group relative flex flex-col bg-white dark:bg-[#2d2d2d] rounded-xl border border-slate-200/80 dark:border-[#444444] hover:border-emerald-500/80 dark:hover:border-[#0d99ff] hover:shadow-md dark:shadow-xl dark:shadow-black/50 dark:hover:bg-[#353535] transition-all cursor-pointer p-2 overflow-hidden"
          title="点击进入画布编辑"
          @click="router.push(`/projects/${id}/prototype?page=${p.id}`)"
        >
          <!-- Thumbnail Area (Phone Frame 9:16 Aspect) -->
          <div class="relative w-full aspect-[9/15] bg-slate-50 dark:bg-[#1a1a1a] rounded-lg overflow-hidden flex items-center justify-center border border-slate-100 dark:border-[#383838]">
            <img
              v-if="p.background_image"
              :src="getFileUrl(p.background_image)"
              class="w-full h-full object-contain group-hover:scale-[1.01] transition-transform duration-200"
              :alt="p.name"
              loading="lazy"
            />
            <div
              v-else-if="pagePreviewHtml(p)"
              class="absolute inset-0 overflow-hidden bg-white dark:bg-[#1a1a1a]"
              :ref="(el) => bindHtmlThumb(el as HTMLElement | null)"
            >
              <iframe
                class="absolute top-0 left-0 origin-top-left pointer-events-none border-0 bg-white"
                :data-w="p.canvas_width || 375"
                :data-h="p.canvas_height || 812"
                :style="{ width: (p.canvas_width || 375) + 'px', height: (p.canvas_height || 812) + 'px' }"
                :srcdoc="p.html_content || ''"
                sandbox=""
                tabindex="-1"
                title=""
              />
            </div>
            <div v-else class="text-[10px] text-slate-300 dark:text-slate-500 font-medium flex flex-col items-center gap-1">
              <ImageIcon class="w-5 h-5 text-slate-200 dark:text-slate-600" />
              <span>{{ t('noPreview') }}</span>
            </div>

            <!-- Crisp Status Tag -->
            <div class="absolute top-1.5 right-1.5">
              <span
                v-if="p.analyzed === 1"
                class="inline-flex items-center gap-1 text-[9px] font-semibold px-1.5 py-0.5 rounded-md bg-emerald-600/90 dark:bg-emerald-700 text-white shadow-xs backdrop-blur-xs"
              >
                <Check class="w-2.5 h-2.5" />
                {{ t('analyzed') }}
              </span>
              <span
                v-else
                class="inline-flex items-center text-[9px] font-semibold px-1.5 py-0.5 rounded-md bg-amber-500/90 dark:bg-amber-600 text-white shadow-xs backdrop-blur-xs"
              >
                {{ t('pending') }}
              </span>
            </div>
          </div>

          <!-- Clean Card Footer -->
          <div class="pt-2 px-0.5 flex items-center justify-between">
            <span class="text-xs font-semibold text-slate-800 dark:text-slate-100 group-hover:text-emerald-700 dark:group-hover:text-[#38bdf8] transition-colors truncate">
              {{ p.name }}
            </span>
          </div>
        </div>
      </div>

      <!-- ===== Mode 2: Minimalist List / Table View ===== -->
      <div
        v-else-if="viewMode === 'list'"
        class="bg-white dark:bg-[#2d2d2d] border border-slate-200/80 dark:border-[#383838] rounded-xl overflow-hidden shadow-2xs dark:shadow-xl dark:shadow-black/50"
      >
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr class="bg-slate-50 dark:bg-[#262626] border-b border-slate-200/80 dark:border-[#383838] text-slate-500 dark:text-[#a1a1a1] font-medium">
              <th class="py-2.5 px-4 w-12">{{ t('preview') }}</th>
              <th class="py-2.5 px-4">{{ t('frameName') }}</th>
              <th class="py-2.5 px-4 w-32">{{ t('status') }}</th>
              <th class="py-2.5 px-4 w-28 text-right">{{ t('actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-[#383838]">
            <tr
              v-for="p in filteredPages"
              :key="p.id"
              class="hover:bg-slate-50/80 dark:hover:bg-[#353535] transition-colors cursor-pointer group"
              @click="router.push(`/projects/${id}/prototype?page=${p.id}`)"
            >
              <!-- Thumbnail -->
              <td class="py-2 px-4">
                <div class="w-7 h-11 bg-slate-100 dark:bg-[#1a1a1a] rounded border border-slate-200 dark:border-[#383838] overflow-hidden flex items-center justify-center shrink-0 relative">
                  <img v-if="p.background_image" :src="getFileUrl(p.background_image)" class="w-full h-full object-cover" />
                  <div
                    v-else-if="pagePreviewHtml(p)"
                    class="absolute inset-0 overflow-hidden bg-white dark:bg-[#1a1a1a]"
                    :ref="(el) => bindHtmlThumb(el as HTMLElement | null)"
                  >
                    <iframe
                      class="absolute top-0 left-0 origin-top-left pointer-events-none border-0 bg-white"
                      :data-w="p.canvas_width || 375"
                      :data-h="p.canvas_height || 812"
                      :style="{ width: (p.canvas_width || 375) + 'px', height: (p.canvas_height || 812) + 'px' }"
                      :srcdoc="p.html_content || ''"
                      sandbox=""
                      tabindex="-1"
                      title=""
                    />
                  </div>
                  <ImageIcon v-else class="w-3.5 h-3.5 text-slate-300 dark:text-slate-600" />
                </div>
              </td>
              <!-- Name -->
              <td class="py-2 px-4 font-semibold text-slate-900 dark:text-white group-hover:text-emerald-600 dark:group-hover:text-[#38bdf8] transition-colors">
                {{ p.name }}
              </td>
              <!-- Status -->
              <td class="py-2 px-4">
                <span
                  v-if="p.analyzed === 1"
                  class="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/70 border border-emerald-200 dark:border-emerald-600/50 px-2 py-0.5 rounded-full"
                >
                  <Check class="w-2.5 h-2.5" />
                  {{ t('analyzed') }}
                </span>
                <span
                  v-else
                  class="inline-flex items-center text-[10px] font-semibold text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/70 border border-amber-200 dark:border-amber-600/50 px-2 py-0.5 rounded-full"
                >
                  {{ t('pending') }}
                </span>
              </td>
              <!-- Action -->
              <td class="py-2 px-4 text-right">
                <span class="text-xs font-semibold text-emerald-600 dark:text-[#0d99ff] group-hover:translate-x-0.5 group-hover:text-emerald-500 dark:group-hover:text-[#38bdf8] transition-all inline-flex items-center gap-0.5">
                  {{ t('openCanvas') }}
                  <ChevronRight class="w-3 h-3" />
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  RefreshCw,
  Sparkles,
  Play,
  Folder,
  Check,
  Image as ImageIcon,
  Copy,
  CheckCheck,
  Search,
  LayoutGrid,
  List,
  ChevronRight,
} from 'lucide-vue-next'
import { projectApi } from '../api/project'
import { getFileUrl } from '../api/http'
import NavbarControls from '../components/NavbarControls.vue'
import { t } from '../utils/i18n'
import type { Page } from '../types'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

interface PageCard {
  id: number
  name: string
  background_image: string
  analyzed: number
  elements: number
  annotations: number
  html_content?: string | null
  canvas_width?: number
  canvas_height?: number
}

const project = ref<any>(null)
const pages = ref<PageCard[]>([])
const loading = ref(true)
const scanning = ref(false)
const analyzing = ref(false)
const analyzeProgressText = ref('')
const entering = ref(false)
const DEFAULT_DESIGNS_DIR = 'D:/idea/Project/html版本/html不是很好版/designs'
const designsDir = ref(DEFAULT_DESIGNS_DIR)

// Interactive toolbar state
const searchQuery = ref('')
const statusFilter = ref<'all' | 'analyzed' | 'pending'>('all')
const viewMode = ref<'grid' | 'list'>('grid')
const copied = ref(false)
const thumbObservers = new Map<HTMLElement, ResizeObserver>()

function pagePreviewHtml(p: PageCard) {
  const html = p.html_content || ''
  const body = html.match(/<body[^>]*>([\s\S]*)<\/body>/i)?.[1] ?? html
  const visible = body.replace(/<script[\s\S]*?<\/script>/gi, '').replace(/<style[\s\S]*?<\/style>/gi, '')
  return /<[a-z][\s\S]*>/i.test(visible)
}

function bindHtmlThumb(el: HTMLElement | null) {
  if (!el) return
  const frame = el.querySelector('iframe') as HTMLIFrameElement | null
  if (!frame) return
  const apply = () => {
    const pageW = Number(frame.dataset.w) || 375
    const pageH = Number(frame.dataset.h) || 812
    const s = Math.min(el.clientWidth / pageW, el.clientHeight / pageH)
    if (!s || !Number.isFinite(s)) return
    frame.style.transform = `scale(${s})`
  }
  apply()
  thumbObservers.get(el)?.disconnect()
  const ro = new ResizeObserver(apply)
  ro.observe(el)
  thumbObservers.set(el, ro)
}

const analyzedCount = computed(() => pages.value.filter((p) => p.analyzed === 1).length)
const pendingCount = computed(() => pages.value.filter((p) => p.analyzed === 0).length)

const filteredPages = computed(() => {
  return pages.value.filter((p) => {
    // 1. Status Filter
    if (statusFilter.value === 'analyzed' && p.analyzed !== 1) return false
    if (statusFilter.value === 'pending' && p.analyzed !== 0) return false

    // 2. Search Keyword Filter
    if (searchQuery.value.trim()) {
      const q = searchQuery.value.trim().toLowerCase()
      if (!p.name.toLowerCase().includes(q)) return false
    }

    return true
  })
})

async function copyDirectoryPath() {
  if (!designsDir.value) return
  try {
    await navigator.clipboard.writeText(designsDir.value)
    copied.value = true
    ElMessage.success('已复制设计稿路径到剪贴板')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.info('复制路径: ' + designsDir.value)
  }
}

async function enterCanvas() {
  if (entering.value) return
  if (pages.value.length === 0) {
    entering.value = true
    try {
      await projectApi.createPage(id, { name: '画板 1', width: 375, height: 812, x: 56, y: 64 })
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '创建空白画板失败')
      entering.value = false
      return
    }
    entering.value = false
  }
  router.push(`/projects/${id}/prototype`)
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const proto = await projectApi.prototype(id)
    project.value = proto.project
    pages.value = proto.pages.map((p) => ({
      ...p,
      elements: p.elements.length,
      annotations: p.annotations.length,
    }))
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    if (!silent) loading.value = false
  }
}

async function scan() {
  scanning.value = true
  try {
    const created = await projectApi.scan(id)
    ElMessage.success(created.length > 0 ? `扫描完成，新增 ${created.length} 个页面` : '没有新的设计稿')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '扫描失败')
  } finally {
    scanning.value = false
  }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollAnalysisStatus() {
  try {
    const status = await projectApi.getAnalysisStatus(id)
    if (status.analyzing) {
      sessionStorage.setItem('wf_analyzing_proj_' + id, '1')
      analyzing.value = true
      analyzeProgressText.value = status.step || `正在分析 (${status.current}/${status.total})...`
      startPolling()
    } else {
      const wasTracking = analyzing.value || sessionStorage.getItem('wf_analyzing_proj_' + id) === '1'
      sessionStorage.removeItem('wf_analyzing_proj_' + id)
      stopPolling()
      analyzing.value = false
      analyzeProgressText.value = ''

      if (wasTracking) {
        if (status.lastError) {
          ElMessage.error(`分析中断：${status.lastError}`)
        } else if (status.okCount > 0) {
          ElMessage.success(`分析完成：成功 ${status.okCount} 页${status.failCount > 0 ? `，失败 ${status.failCount} 页` : ''}`)
        } else if (status.step && status.step.includes('完成')) {
          ElMessage.success(status.step)
        }
        await load(true)
      }
    }
  } catch (err: any) {
    console.error('查询项目分析状态异常:', err)
    if (analyzing.value) {
      sessionStorage.removeItem('wf_analyzing_proj_' + id)
      stopPolling()
      analyzing.value = false
      analyzeProgressText.value = ''
      ElMessage.error('无法连接到后端分析服务，已恢复按钮状态')
    }
  }
}

function startPolling() {
  if (!pollTimer) {
    pollTimer = setInterval(pollAnalysisStatus, 1200)
  }
}

async function analyze() {
  if (analyzing.value) return
  const pending = pages.value.filter((p: any) => !p.analyzed || p.elements === 0)
  if (pending.length === 0) {
    ElMessage.info('所有页面都已有原型线稿，无需生成')
    return
  }

  analyzing.value = true
  analyzeProgressText.value = '准备启动 AI 分析...'
  sessionStorage.setItem('wf_analyzing_proj_' + id, '1')
  try {
    const status = await projectApi.startAnalyze(id)
    if (status.step) {
      analyzeProgressText.value = status.step
    }
    startPolling()
  } catch (err: any) {
    sessionStorage.removeItem('wf_analyzing_proj_' + id)
    analyzing.value = false
    analyzeProgressText.value = ''
    ElMessage.error(err?.response?.data?.message || err?.message || '启动分析失败')
  }
}

onBeforeUnmount(() => {
  stopPolling()
  thumbObservers.forEach((ro) => ro.disconnect())
  thumbObservers.clear()
})

onMounted(async () => {
  try {
    const resp = await fetch('/api/projects/' + id)
    const body = await resp.json()
    const dir = body?.data?.designsDir || body?.designsDir
    if (dir) {
      designsDir.value = dir
    }
  } catch {
    /* ignore */
  }
  await load()
  // 页面加载或刷新时，第一件事向后端核对真实分析状态：只要后端还在跑，立即进入持续 Loading 态并恢复进度显示与轮询！
  await pollAnalysisStatus()
})
</script>