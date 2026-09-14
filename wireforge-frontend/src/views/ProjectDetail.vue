<template>
  <div class="min-h-screen bg-[#f8fafc] flex flex-col text-slate-800 selection:bg-emerald-500 selection:text-white">
    <!-- ===== Minimalist Figma-Style Top Navigation ===== -->
    <header class="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200/80 px-6 py-2.5 transition-all shadow-2xs">
      <div class="max-w-7xl mx-auto flex items-center justify-between gap-4">
        <!-- Breadcrumbs & Project Identity -->
        <div class="flex items-center gap-2.5 min-w-0">
          <button
            class="wf-tap p-1.5 rounded-lg text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors cursor-pointer"
            title="返回项目列表"
            @click="router.push('/')"
          >
            <ArrowLeft class="w-4 h-4" />
          </button>

          <div class="flex items-center gap-1.5 text-xs text-slate-400">
            <span class="hover:text-slate-600 cursor-pointer transition-colors" @click="router.push('/')">我的项目</span>
            <span>/</span>
          </div>

          <h1 class="text-sm font-bold text-slate-900 tracking-tight truncate max-w-xs sm:max-w-md">
            {{ project?.name || '项目详情' }}
          </h1>

          <span class="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 tabular-nums border border-slate-200/60 hidden sm:inline-flex">
            {{ pages.length }} 个画框
          </span>
        </div>

        <!-- Top Right Action Controls -->
        <div class="flex items-center gap-2 shrink-0">
          <!-- Secondary: Scan local directory -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 active:bg-slate-100 border border-slate-200/80 rounded-lg shadow-2xs transition-all disabled:opacity-50 cursor-pointer"
            :disabled="scanning"
            title="扫描本地目录是否有新增的设计稿图片"
            @click="scan"
          >
            <RefreshCw class="w-3.5 h-3.5 text-slate-500" :class="{ 'animate-spin': scanning }" />
            <span class="hidden sm:inline">{{ scanning ? '扫描中...' : '扫描设计稿' }}</span>
          </button>

          <!-- Secondary/AI: AI Analyze Wireframe -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-emerald-800 bg-emerald-50 hover:bg-emerald-100/80 active:bg-emerald-100 border border-emerald-200/80 rounded-lg transition-all disabled:opacity-50 cursor-pointer shadow-2xs"
            :disabled="analyzing || pages.length === 0"
            title="通过视觉大模型识别图层并生成线框组件"
            @click="analyze"
          >
            <Sparkles class="w-3.5 h-3.5 text-emerald-600" :class="{ 'animate-spin': analyzing }" />
            <span>{{ analyzing ? 'AI 解析中...' : 'AI 生成原型' }}</span>
          </button>

          <!-- Primary CTA: Open Interactive Canvas -->
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 rounded-lg shadow-xs shadow-emerald-600/20 border border-emerald-500/90 transition-all disabled:opacity-50 cursor-pointer"
            :disabled="pages.length === 0"
            @click="router.push(`/projects/${id}/prototype`)"
          >
            <Play class="w-3.5 h-3.5 fill-white" />
            <span>进入原型画布</span>
          </button>
        </div>
      </div>
    </header>

    <!-- ===== Secondary Toolbar (Search, Filter, View Mode, Path Hint) ===== -->
    <div class="border-b border-slate-200/70 bg-white/70 px-6 py-2.5">
      <div class="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs">
        <!-- Left: Quiet Directory Path Indicator & Quick Copy -->
        <div class="flex items-center gap-2 text-slate-500 min-w-0">
          <span class="text-slate-400 font-medium shrink-0 flex items-center gap-1">
            <Folder class="w-3.5 h-3.5 text-slate-400" />
            设计稿路径:
          </span>
          <div
            class="group inline-flex items-center gap-1.5 px-2 py-1 rounded bg-slate-100/90 hover:bg-slate-200/70 border border-slate-200/60 font-mono text-[11px] text-slate-600 truncate max-w-sm sm:max-w-md cursor-pointer transition-colors"
            title="点击复制设计稿路径（将图片放入此目录后点击扫描即可导入）"
            @click="copyDirectoryPath"
          >
            <span class="truncate">{{ designsDir }}</span>
            <button class="shrink-0 text-slate-400 group-hover:text-slate-700 ml-0.5">
              <CheckCheck v-if="copied" class="w-3 h-3 text-emerald-600" />
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
              placeholder="搜索画框名称..."
              class="w-full pl-8 pr-2.5 py-1 text-xs bg-white border border-slate-200 rounded-lg text-slate-800 placeholder-slate-400 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500/30 transition-all"
            />
          </div>

          <!-- Status Segmented Filter -->
          <div class="flex items-center bg-slate-100/90 p-0.5 rounded-lg border border-slate-200/70 text-[11px]">
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'all' ? 'bg-white text-slate-900 shadow-2xs font-semibold' : 'text-slate-500 hover:text-slate-800'"
              @click="statusFilter = 'all'"
            >
              全部 ({{ pages.length }})
            </button>
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'analyzed' ? 'bg-white text-emerald-700 shadow-2xs font-semibold' : 'text-slate-500 hover:text-slate-800'"
              @click="statusFilter = 'analyzed'"
            >
              已识别 ({{ analyzedCount }})
            </button>
            <button
              class="px-2.5 py-1 rounded-md font-medium transition-all cursor-pointer"
              :class="statusFilter === 'pending' ? 'bg-white text-amber-700 shadow-2xs font-semibold' : 'text-slate-500 hover:text-slate-800'"
              @click="statusFilter = 'pending'"
            >
              待分析 ({{ pendingCount }})
            </button>
          </div>

          <!-- View Mode Switcher -->
          <div class="flex items-center bg-slate-100/90 p-0.5 rounded-lg border border-slate-200/70">
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'grid' ? 'bg-white text-slate-900 shadow-2xs' : 'text-slate-400 hover:text-slate-700'"
              title="网格视图"
              @click="viewMode = 'grid'"
            >
              <LayoutGrid class="w-3.5 h-3.5" />
            </button>
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'list' ? 'bg-white text-slate-900 shadow-2xs' : 'text-slate-400 hover:text-slate-700'"
              title="列表视图"
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
        <p class="mt-3 text-xs text-slate-400 font-medium tracking-wide">正在读取画框与原型数据...</p>
      </div>

      <!-- Empty State (No Pages Found) -->
      <div
        v-else-if="pages.length === 0"
        class="bg-white border border-dashed border-slate-300/80 rounded-2xl p-12 text-center max-w-md mx-auto my-12"
      >
        <div class="w-11 h-11 rounded-xl bg-slate-50 text-slate-400 flex items-center justify-center mx-auto mb-3 border border-slate-200/70">
          <Folder class="w-5 h-5" />
        </div>
        <h3 class="text-sm font-bold text-slate-800">尚未发现设计稿画框</h3>
        <p class="text-xs text-slate-400 mt-1 mb-5 leading-relaxed max-w-xs mx-auto">
          请将设计稿图片 (PNG / JPG) 放入项目对应的本地目录，点击下方按钮开始载入。
        </p>
        <button
          class="wf-tap inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow-sm transition-all cursor-pointer"
          :disabled="scanning"
          @click="scan"
        >
          <RefreshCw class="w-3.5 h-3.5" :class="{ 'animate-spin': scanning }" />
          <span>扫描设计稿目录</span>
        </button>
      </div>

      <!-- Filter No Results -->
      <div
        v-else-if="filteredPages.length === 0"
        class="py-20 text-center text-slate-400 text-xs"
      >
        <p>未找到匹配「{{ searchQuery }}」的页面画框</p>
        <button
          class="mt-2 text-emerald-600 hover:underline font-medium cursor-pointer"
          @click="searchQuery = ''; statusFilter = 'all'"
        >
          清除筛选条件
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
          class="group relative flex flex-col bg-white rounded-xl border border-slate-200/80 hover:border-emerald-500/80 hover:shadow-sm transition-all cursor-pointer p-2 overflow-hidden"
          title="点击进入画布编辑"
          @click="router.push(`/projects/${id}/prototype?page=${p.id}`)"
        >
          <!-- Thumbnail Area (Phone Frame 9:16 Aspect) -->
          <div class="relative w-full aspect-[9/15] bg-slate-50 rounded-lg overflow-hidden flex items-center justify-center border border-slate-100">
            <img
              v-if="p.background_image"
              :src="getFileUrl(p.background_image)"
              class="w-full h-full object-contain group-hover:scale-[1.01] transition-transform duration-200"
              :alt="p.name"
              loading="lazy"
            />
            <div v-else class="text-[10px] text-slate-300 font-medium flex flex-col items-center gap-1">
              <ImageIcon class="w-5 h-5 text-slate-200" />
              <span>无预览</span>
            </div>

            <!-- Crisp Status Tag -->
            <div class="absolute top-1.5 right-1.5">
              <span
                v-if="p.analyzed === 1"
                class="inline-flex items-center gap-1 text-[9px] font-semibold px-1.5 py-0.5 rounded-md bg-emerald-600/90 text-white shadow-xs backdrop-blur-xs"
              >
                <Check class="w-2.5 h-2.5" />
                已识别
              </span>
              <span
                v-else
                class="inline-flex items-center text-[9px] font-semibold px-1.5 py-0.5 rounded-md bg-amber-500/90 text-white shadow-xs backdrop-blur-xs"
              >
                待分析
              </span>
            </div>
          </div>

          <!-- Clean Card Footer -->
          <div class="pt-2 px-0.5 flex items-center justify-between">
            <span class="text-xs font-medium text-slate-800 group-hover:text-emerald-700 transition-colors truncate">
              {{ p.name }}
            </span>
          </div>
        </div>
      </div>

      <!-- ===== Mode 2: Minimalist List / Table View ===== -->
      <div
        v-else-if="viewMode === 'list'"
        class="bg-white border border-slate-200/80 rounded-xl overflow-hidden shadow-2xs"
      >
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr class="bg-slate-50 border-b border-slate-200/80 text-slate-500 font-medium">
              <th class="py-2.5 px-4 w-12">预览</th>
              <th class="py-2.5 px-4">页面画框名称</th>
              <th class="py-2.5 px-4 w-32">识别状态</th>
              <th class="py-2.5 px-4 w-28 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr
              v-for="p in filteredPages"
              :key="p.id"
              class="hover:bg-slate-50/80 transition-colors cursor-pointer group"
              @click="router.push(`/projects/${id}/prototype?page=${p.id}`)"
            >
              <!-- Thumbnail -->
              <td class="py-2 px-4">
                <div class="w-7 h-11 bg-slate-100 rounded border border-slate-200 overflow-hidden flex items-center justify-center shrink-0">
                  <img v-if="p.background_image" :src="getFileUrl(p.background_image)" class="w-full h-full object-cover" />
                  <ImageIcon v-else class="w-3.5 h-3.5 text-slate-300" />
                </div>
              </td>
              <!-- Name -->
              <td class="py-2 px-4 font-semibold text-slate-900 group-hover:text-emerald-600 transition-colors">
                {{ p.name }}
              </td>
              <!-- Status -->
              <td class="py-2 px-4">
                <span
                  v-if="p.analyzed === 1"
                  class="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full"
                >
                  <Check class="w-2.5 h-2.5" />
                  已识别
                </span>
                <span
                  v-else
                  class="inline-flex items-center text-[10px] font-semibold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full"
                >
                  待分析
                </span>
              </td>
              <!-- Action -->
              <td class="py-2 px-4 text-right">
                <span class="text-xs font-semibold text-emerald-600 group-hover:translate-x-0.5 transition-transform inline-flex items-center gap-0.5">
                  打开画布
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
import { computed, onMounted, ref } from 'vue'
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
}

const project = ref<any>(null)
const pages = ref<PageCard[]>([])
const loading = ref(true)
const scanning = ref(false)
const analyzing = ref(false)
const DEFAULT_DESIGNS_DIR = 'D:/idea/Project/html版本/html不是很好版/designs'
const designsDir = ref(DEFAULT_DESIGNS_DIR)

// Interactive toolbar state
const searchQuery = ref('')
const statusFilter = ref<'all' | 'analyzed' | 'pending'>('all')
const viewMode = ref<'grid' | 'list'>('grid')
const copied = ref(false)

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

async function load() {
  loading.value = true
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
    loading.value = false
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

async function analyze() {
  analyzing.value = true
  try {
    const results = await projectApi.analyze(id)
    if (results.length === 0) {
      ElMessage.info('所有页面都已有线稿，无需生成')
      return
    }
    const ok = results.filter((r) => r.status === 'ok').length
    const fail = results.filter((r) => r.status === 'error')
    ElMessage.success(`分析完成：成功 ${ok} 页`)
    fail.forEach((r) => ElMessage.warning(`页面 [${r.page_name}] 失败：${r.error}`))
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

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
})
</script>