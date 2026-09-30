<template>
  <div class="min-h-screen bg-[#f8fafc] dark:bg-[#1e1e1e] flex flex-col text-slate-800 dark:text-slate-100 selection:bg-emerald-500 selection:text-white transition-colors duration-200">
    <!-- ===== Global Figma-Style Header ===== -->
    <header class="sticky top-0 z-30 bg-white/95 dark:bg-[#2c2c2c] backdrop-blur-md border-b border-slate-200/80 dark:border-[#383838] px-6 py-2.5 transition-all shadow-2xs dark:shadow-md dark:shadow-black/50">
      <div class="w-full flex items-center justify-between gap-4">
        <!-- Logo and Brand -->
        <div class="flex items-center gap-2.5 select-none">
          <img
            src="/favicon.svg"
            alt="WireForge Logo"
            class="w-7 h-7 rounded-lg object-contain shadow-2xs hover:scale-105 transition-transform shrink-0"
          />
          <span class="text-sm font-bold text-slate-900 dark:text-white tracking-tight">WireForge</span>
        </div>

        <!-- Header Actions & Engine Status -->
        <div class="flex items-center gap-3">
          <div class="hidden sm:flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-200 bg-slate-100/80 dark:bg-[#383838] px-2.5 py-1 rounded-md border border-slate-200/60 dark:border-[#484848]">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            <span>{{ t('engineReady') }}</span>
          </div>

          <button
            class="wf-tap inline-flex items-center justify-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white dark:text-[#38bdf8] bg-[#0d99ff] hover:bg-[#0b87e0] active:bg-[#0972bd] dark:bg-[#0d99ff]/20 dark:hover:bg-[#0d99ff]/35 dark:active:bg-[#0d99ff]/50 rounded-lg shadow-xs shadow-[#0d99ff]/20 dark:shadow-[0_0_14px_rgba(13,153,255,0.25)] border border-[#0d99ff]/90 dark:border-[#0d99ff]/50 transition-all cursor-pointer"
            @click="dialogVisible = true"
          >
            <Plus class="w-3.5 h-3.5" />
            <span>{{ t('newProject') }}</span>
          </button>

          <!-- Language & Appearance Controls (1:1 with reference images) -->
          <NavbarControls />
        </div>
      </div>
    </header>

    <!-- ===== Secondary Toolbar (Title, Search, View Switcher) ===== -->
    <div class="border-b border-slate-200/70 dark:border-[#333333] bg-white/70 dark:bg-[#252525] px-6 py-3 transition-colors">
      <div class="w-full flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
        <!-- Title and Count -->
        <div class="flex items-center gap-2.5">
          <h1 class="text-sm font-bold text-slate-900 dark:text-white tracking-tight">{{ t('projectLibrary') }}</h1>
          <span class="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 dark:bg-[#383838] text-slate-600 dark:text-slate-200 tabular-nums border border-slate-200/60 dark:border-[#484848]">
            {{ t('projectsCount', { n: projects.length }) }}
          </span>
        </div>

        <!-- Search & View Mode Switcher -->
        <div class="flex items-center gap-2.5">
          <!-- Search Box -->
          <div class="relative w-48 sm:w-56">
            <Search class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              v-model="searchQuery"
              type="text"
              :placeholder="t('searchProjects')"
              class="w-full pl-8 pr-2.5 py-1 text-xs bg-white dark:bg-[#1e1e1e] border border-slate-200 dark:border-[#444444] rounded-lg text-slate-800 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-400 focus:outline-none focus:border-[#0d99ff] focus:ring-1 focus:ring-[#0d99ff]/30 transition-all"
            />
          </div>

          <!-- View Mode Switcher -->
          <div class="flex items-center bg-slate-100/90 dark:bg-[#1a1a1a] p-0.5 rounded-lg border border-slate-200/70 dark:border-[#383838]">
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'grid' ? 'bg-white dark:bg-[#383838] text-slate-900 dark:text-white shadow-2xs' : 'text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'"
              :title="t('gridView')"
              @click="viewMode = 'grid'"
            >
              <LayoutGrid class="w-3.5 h-3.5" />
            </button>
            <button
              class="p-1 rounded-md transition-all cursor-pointer"
              :class="viewMode === 'list' ? 'bg-white dark:bg-[#383838] text-slate-900 dark:text-white shadow-2xs' : 'text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'"
              :title="t('listView')"
              @click="viewMode = 'list'"
            >
              <List class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Main Content Area ===== -->
    <main class="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
      <!-- Loading State -->
      <div v-if="loading" class="flex flex-col items-center justify-center py-24">
        <div class="w-8 h-8 border-2 border-emerald-200 border-t-emerald-600 rounded-full animate-spin"></div>
        <p class="mt-3 text-xs text-slate-400 font-medium tracking-wide">正在载入项目列表...</p>
      </div>

      <!-- Empty State -->
      <div
        v-else-if="projects.length === 0"
        class="bg-white dark:bg-[#2d2d2d] border border-dashed border-slate-300/80 dark:border-[#444444] rounded-2xl p-12 text-center max-w-md mx-auto my-12 shadow-sm dark:shadow-xl dark:shadow-black/50"
      >
        <div class="w-11 h-11 rounded-xl bg-slate-50 dark:bg-[#232323] text-slate-400 flex items-center justify-center mx-auto mb-3 border border-slate-200/70 dark:border-[#383838]">
          <FolderPlus class="w-5 h-5" />
        </div>
        <h3 class="text-sm font-bold text-slate-800 dark:text-slate-100">{{ t('noProjects') }}</h3>
        <p class="text-xs text-slate-400 dark:text-[#a1a1a1] mt-1 mb-5 leading-relaxed max-w-xs mx-auto">
          {{ t('noProjectsDesc') }}
        </p>
        <button
          class="wf-tap inline-flex items-center justify-center gap-1.5 px-4 py-2 text-xs font-semibold text-white dark:text-[#38bdf8] bg-[#0d99ff] hover:bg-[#0b87e0] active:bg-[#0972bd] dark:bg-[#0d99ff]/20 dark:hover:bg-[#0d99ff]/35 dark:border-[#0d99ff]/50 border border-[#0d99ff]/90 rounded-lg shadow-sm transition-all cursor-pointer"
          @click="dialogVisible = true"
        >
          <Plus class="w-3.5 h-3.5" />
          <span>{{ t('createFirstProject') }}</span>
        </button>
      </div>

      <!-- Filter No Results -->
      <div
        v-else-if="filteredProjects.length === 0"
        class="py-20 text-center text-slate-400 dark:text-[#a1a1a1] text-xs"
      >
        <p>{{ t('noMatches') }}「{{ searchQuery }}」</p>
        <button
          class="mt-2 text-emerald-600 dark:text-[#0d99ff] hover:underline font-medium cursor-pointer"
          @click="searchQuery = ''"
        >
          {{ t('clearFilter') }}
        </button>
      </div>

      <!-- ===== Mode 1: Figma-Style Polished Project Grid ===== -->
      <div
        v-else-if="viewMode === 'grid'"
        class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-4 gap-5"
      >
        <div
          v-for="p in filteredProjects"
          :key="p.id"
          class="group relative bg-white dark:bg-[#2d2d2d] rounded-2xl border border-slate-200/85 dark:border-[#444444] hover:border-emerald-500/80 dark:hover:border-[#0d99ff] hover:shadow-md dark:shadow-xl dark:shadow-black/50 dark:hover:shadow-2xl dark:hover:bg-[#353535] hover:-translate-y-1 transition-all duration-200 cursor-pointer p-5 flex flex-col justify-between"
          @click="router.push(`/projects/${p.id}`)"
        >
          <div>
            <!-- Card Header: Icon, Title & Delete -->
            <div class="flex items-start justify-between gap-3 mb-3">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-50 to-teal-50 dark:from-[#383838] dark:to-[#333333] border border-emerald-200/60 dark:border-[#484848] text-emerald-700 dark:text-[#0d99ff] flex items-center justify-center font-bold text-sm shrink-0 shadow-2xs group-hover:border-emerald-300 dark:group-hover:border-[#0d99ff]/50 transition-colors">
                  <Layers class="w-5 h-5 text-emerald-600 dark:text-[#0d99ff]" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-1.5">
                    <h3 class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-emerald-700 dark:group-hover:text-[#38bdf8] transition-colors truncate">
                      {{ p.name }}
                    </h3>
                  </div>
                  <span class="text-[10px] font-mono text-slate-400 dark:text-[#a1a1a1] block tabular-nums">ID #{{ p.id }}</span>
                </div>
              </div>

              <!-- Delete Action Button -->
              <button
                class="wf-tap opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/50 focus:opacity-100 cursor-pointer shrink-0"
                :title="t('delete')"
                @click.stop="remove(p)"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Card Description -->
            <p v-if="p.description" class="text-xs text-slate-500 dark:text-[#cbd5e1] line-clamp-2 leading-relaxed mb-3 mt-1">
              {{ p.description }}
            </p>
            <div v-else class="h-2"></div>

            <!-- Project Tags & Stats Pill Row -->
            <div class="flex items-center flex-wrap gap-1.5 mb-2 text-[11px] tabular-nums">
              <span
                class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border font-medium transition-colors"
                :class="p.pageCount ? 'bg-emerald-50/80 dark:bg-emerald-950/70 text-emerald-700 dark:text-emerald-300 border-emerald-200/70 dark:border-emerald-600/50' : 'bg-slate-50 dark:bg-[#232323] text-slate-400 dark:text-[#a1a1a1] border-slate-200/60 dark:border-[#444444]'"
              >
                <Layers class="w-3 h-3" />
                <span>{{ p.pageCount != null ? `${p.pageCount} ${t('frames')}` : '...' }}</span>
              </span>

              <span
                v-if="p.analyzedCount != null && p.analyzedCount > 0"
                class="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-slate-50 dark:bg-[#232323] text-slate-600 dark:text-slate-200 border border-slate-200/60 dark:border-[#444444] font-medium"
              >
                <Check class="w-3 h-3 text-emerald-600 dark:text-emerald-400" />
                <span>{{ p.analyzedCount }} {{ t('ready') }}</span>
              </span>
            </div>
          </div>

          <!-- Card Footer Meta -->
          <div class="pt-3 border-t border-slate-100 dark:border-[#383838] flex items-center justify-between text-[11px] text-slate-400 dark:text-[#a1a1a1] mt-2">
            <span class="flex items-center gap-1 tabular-nums">
              <Calendar class="w-3 h-3 text-slate-400 dark:text-[#a1a1a1]" />
              {{ new Date(p.createdAt || '').toLocaleDateString() }}
            </span>
            <span class="text-xs font-semibold text-slate-500 dark:text-[#0d99ff] group-hover:text-emerald-600 dark:group-hover:text-[#38bdf8] transition-colors flex items-center gap-0.5">
              {{ t('enterWorkspace') }}
              <ChevronRight class="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
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
              <th class="py-2.5 px-4 w-12">#</th>
              <th class="py-2.5 px-4">{{ t('projectName') }}</th>
              <th class="py-2.5 px-4 w-32">{{ t('frames') }}</th>
              <th class="py-2.5 px-4">{{ t('projectDesc') }}</th>
              <th class="py-2.5 px-4 w-36">{{ t('createTime') }}</th>
              <th class="py-2.5 px-4 w-28 text-right">{{ t('actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-[#383838]">
            <tr
              v-for="p in filteredProjects"
              :key="p.id"
              class="hover:bg-slate-50/80 dark:hover:bg-[#353535] transition-colors cursor-pointer group"
              @click="router.push(`/projects/${p.id}`)"
            >
              <!-- ID -->
              <td class="py-2.5 px-4 text-slate-400 dark:text-[#a1a1a1] tabular-nums">
                {{ p.id }}
              </td>
              <!-- Name -->
              <td class="py-2.5 px-4 font-semibold text-slate-900 dark:text-white group-hover:text-emerald-600 dark:group-hover:text-[#38bdf8] transition-colors">
                <div class="flex items-center gap-2">
                  <div class="w-6 h-6 rounded bg-emerald-50 dark:bg-[#383838] border border-emerald-200/60 dark:border-[#484848] text-emerald-700 dark:text-[#0d99ff] flex items-center justify-center shrink-0">
                    <Layers class="w-3.5 h-3.5" />
                  </div>
                  <span>{{ p.name }}</span>
                </div>
              </td>
              <!-- Page Count -->
              <td class="py-2.5 px-4 tabular-nums">
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/70 text-emerald-700 dark:text-emerald-300 border border-emerald-200/70 dark:border-emerald-600/50">
                  {{ p.pageCount != null ? `${p.pageCount} ${t('frames')}` : '—' }}
                </span>
              </td>
              <!-- Description -->
              <td class="py-2.5 px-4 text-slate-500 dark:text-[#cbd5e1] truncate max-w-xs">
                {{ p.description || '—' }}
              </td>
              <!-- Date -->
              <td class="py-2.5 px-4 text-slate-400 dark:text-[#94a3b8] tabular-nums">
                {{ new Date(p.createdAt || '').toLocaleDateString() }}
              </td>
              <!-- Action -->
              <td class="py-2.5 px-4 text-right">
                <div class="inline-flex items-center gap-2">
                  <span class="text-xs font-semibold text-emerald-600 dark:text-emerald-400 group-hover:translate-x-0.5 transition-transform inline-flex items-center gap-0.5">
                    {{ t('open') }}
                    <ChevronRight class="w-3 h-3" />
                  </span>
                  <button
                    class="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/50 transition-colors cursor-pointer"
                    :title="t('delete')"
                    @click.stop="remove(p)"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <!-- ===== New Project Dialog ===== -->
    <el-dialog
      v-model="dialogVisible"
      :title="t('newProject')"
      width="460px"
      align-center
      class="rounded-2xl"
    >
      <div class="space-y-4 pt-2">
        <div>
          <label class="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">{{ t('projectName') }} <span class="text-red-500">*</span></label>
          <el-input
            v-model="form.name"
            :placeholder="t('projectNamePlaceholder')"
            size="large"
            clearable
          />
        </div>
        <div>
          <label class="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">{{ t('projectDesc') }}</label>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :placeholder="t('projectDescPlaceholder')"
          />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2.5">
          <button
            class="wf-tap px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-[#383838] dark:border dark:border-[#484848] rounded-xl transition-all cursor-pointer"
            @click="dialogVisible = false"
          >
            {{ t('cancel') }}
          </button>
          <button
            class="wf-tap inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white dark:text-[#38bdf8] bg-[#0d99ff] hover:bg-[#0b87e0] active:bg-[#0972bd] dark:bg-[#0d99ff]/20 dark:hover:bg-[#0d99ff]/35 dark:active:bg-[#0d99ff]/50 dark:border-[#0d99ff]/50 border border-[#0d99ff]/90 rounded-xl shadow-sm dark:shadow-[0_0_14px_rgba(13,153,255,0.25)] transition-all disabled:opacity-60 cursor-pointer"
            :disabled="creating"
            @click="create"
          >
            <div v-if="creating" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            {{ creating ? t('creating') : t('create') }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  FolderPlus,
  LayoutGrid,
  List,
  Trash2,
  Calendar,
  Search,
  ChevronRight,
  Layers,
  Check,
} from 'lucide-vue-next'
import NavbarControls from '../components/NavbarControls.vue'
import { t } from '../utils/i18n'
import { projectApi } from '../api/project'
import type { Project } from '../types'

interface ProjectItem extends Project {
  pageCount?: number
  analyzedCount?: number
}

const router = useRouter()
const projects = ref<ProjectItem[]>([])
const loading = ref(true)
const dialogVisible = ref(false)
const creating = ref(false)
const form = reactive({ name: '', description: '' })

const searchQuery = ref('')
const viewMode = ref<'grid' | 'list'>('grid')

const filteredProjects = computed(() => {
  if (!searchQuery.value.trim()) return projects.value
  const q = searchQuery.value.trim().toLowerCase()
  return projects.value.filter((p) => p.name.toLowerCase().includes(q) || (p.description && p.description.toLowerCase().includes(q)))
})

async function load() {
  loading.value = true
  try {
    const list: ProjectItem[] = await projectApi.list()
    projects.value = list

    // Asynchronously load page statistics for each project
    Promise.allSettled(
      list.map(async (p) => {
        try {
          const proto = await projectApi.prototype(p.id)
          const target = projects.value.find((item) => item.id === p.id)
          if (target && proto?.pages) {
            target.pageCount = proto.pages.length
            target.analyzedCount = proto.pages.filter((pg: any) => pg.analyzed === 1).length
          }
        } catch {
          /* ignore */
        }
      })
    )
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  creating.value = true
  try {
    const project = await projectApi.create(form.name, form.description)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    router.push(`/projects/${project.id}`)
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function remove(p: Project) {
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${p.name}」吗？其下所有页面、元素、标注将一并删除，且不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return // 用户取消
  }
  try {
    await projectApi.remove(p.id)
    ElMessage.success('项目已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>