<template>
  <div class="min-h-screen bg-[#f8fafc] flex flex-col text-slate-800 selection:bg-emerald-500 selection:text-white">
    <!-- ===== Global Figma-Style Header ===== -->
    <header class="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200/80 px-6 py-2.5 transition-all shadow-2xs">
      <div class="max-w-7xl mx-auto flex items-center justify-between gap-4">
        <!-- Logo and Brand -->
        <div class="flex items-center gap-2.5">
          <div class="w-7 h-7 rounded-lg bg-emerald-600 flex items-center justify-center text-white font-bold text-xs tracking-wider shadow-xs">
            W
          </div>
          <span class="text-sm font-bold text-slate-900 tracking-tight">WireForge</span>
        </div>

        <!-- Header Actions & Engine Status -->
        <div class="flex items-center gap-3">
          <div class="hidden sm:flex items-center gap-1.5 text-xs text-slate-500 bg-slate-100/80 px-2.5 py-1 rounded-md border border-slate-200/60">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            <span>本地引擎 :8091 就绪</span>
          </div>

          <button
            class="wf-tap inline-flex items-center justify-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 rounded-lg shadow-xs shadow-emerald-600/20 border border-emerald-500/90 transition-all cursor-pointer"
            @click="dialogVisible = true"
          >
            <Plus class="w-3.5 h-3.5" />
            <span>新建项目</span>
          </button>
        </div>
      </div>
    </header>

    <!-- ===== Secondary Toolbar (Title, Search, View Switcher) ===== -->
    <div class="border-b border-slate-200/70 bg-white/70 px-6 py-3">
      <div class="max-w-7xl mx-auto flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
        <!-- Title and Count -->
        <div class="flex items-center gap-2.5">
          <h1 class="text-sm font-bold text-slate-900 tracking-tight">原型项目库</h1>
          <span class="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 tabular-nums border border-slate-200/60">
            {{ projects.length }} 个项目
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
              placeholder="搜索项目名称..."
              class="w-full pl-8 pr-2.5 py-1 text-xs bg-white border border-slate-200 rounded-lg text-slate-800 placeholder-slate-400 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500/30 transition-all"
            />
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
        class="bg-white border border-dashed border-slate-300/80 rounded-2xl p-12 text-center max-w-md mx-auto my-12"
      >
        <div class="w-11 h-11 rounded-xl bg-slate-50 text-slate-400 flex items-center justify-center mx-auto mb-3 border border-slate-200/70">
          <FolderPlus class="w-5 h-5" />
        </div>
        <h3 class="text-sm font-bold text-slate-800">暂无原型项目</h3>
        <p class="text-xs text-slate-400 mt-1 mb-5 leading-relaxed max-w-xs mx-auto">
          创建新项目并载入设计稿图片，WireForge 将自动识别界面组件并生成可交互线框原型。
        </p>
        <button
          class="wf-tap inline-flex items-center justify-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow-sm transition-all cursor-pointer"
          @click="dialogVisible = true"
        >
          <Plus class="w-3.5 h-3.5" />
          <span>立即创建第一个项目</span>
        </button>
      </div>

      <!-- Filter No Results -->
      <div
        v-else-if="filteredProjects.length === 0"
        class="py-20 text-center text-slate-400 text-xs"
      >
        <p>未找到匹配「{{ searchQuery }}」的项目</p>
        <button
          class="mt-2 text-emerald-600 hover:underline font-medium cursor-pointer"
          @click="searchQuery = ''"
        >
          清除搜索条件
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
          class="group relative bg-white rounded-2xl border border-slate-200/85 hover:border-emerald-500/80 hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 cursor-pointer p-5 flex flex-col justify-between"
          @click="router.push(`/projects/${p.id}`)"
        >
          <div>
            <!-- Card Header: Icon, Title & Delete -->
            <div class="flex items-start justify-between gap-3 mb-3">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-50 to-teal-50 border border-emerald-200/60 text-emerald-700 flex items-center justify-center font-bold text-sm shrink-0 shadow-2xs group-hover:border-emerald-300 transition-colors">
                  <Layers class="w-5 h-5 text-emerald-600" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-1.5">
                    <h3 class="text-sm font-bold text-slate-900 group-hover:text-emerald-700 transition-colors truncate">
                      {{ p.name }}
                    </h3>
                  </div>
                  <span class="text-[10px] font-mono text-slate-400 block tabular-nums">ID #{{ p.id }}</span>
                </div>
              </div>

              <!-- Delete Action Button -->
              <button
                class="wf-tap opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 focus:opacity-100 cursor-pointer shrink-0"
                title="删除项目"
                @click.stop="remove(p)"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Card Description -->
            <p v-if="p.description" class="text-xs text-slate-500 line-clamp-2 leading-relaxed mb-3 mt-1">
              {{ p.description }}
            </p>
            <div v-else class="h-2"></div>

            <!-- Project Tags & Stats Pill Row -->
            <div class="flex items-center flex-wrap gap-1.5 mb-2 text-[11px] tabular-nums">
              <span
                class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border font-medium transition-colors"
                :class="p.pageCount ? 'bg-emerald-50/80 text-emerald-700 border-emerald-200/70' : 'bg-slate-50 text-slate-400 border-slate-200/60'"
              >
                <Layers class="w-3 h-3" />
                <span>{{ p.pageCount != null ? `${p.pageCount} 个画框` : '读取中...' }}</span>
              </span>

              <span
                v-if="p.analyzedCount != null && p.analyzedCount > 0"
                class="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-slate-50 text-slate-600 border border-slate-200/60 font-medium"
              >
                <Check class="w-3 h-3 text-emerald-600" />
                <span>{{ p.analyzedCount }} 已就绪</span>
              </span>
            </div>
          </div>

          <!-- Card Footer Meta -->
          <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400 mt-2">
            <span class="flex items-center gap-1 tabular-nums">
              <Calendar class="w-3 h-3 text-slate-400" />
              {{ new Date(p.createdAt || '').toLocaleDateString() }}
            </span>
            <span class="text-xs font-semibold text-slate-500 group-hover:text-emerald-600 transition-colors flex items-center gap-0.5">
              进入工作区
              <ChevronRight class="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
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
              <th class="py-2.5 px-4 w-12">#</th>
              <th class="py-2.5 px-4">项目名称</th>
              <th class="py-2.5 px-4 w-32">包含画框</th>
              <th class="py-2.5 px-4">项目描述</th>
              <th class="py-2.5 px-4 w-36">创建时间</th>
              <th class="py-2.5 px-4 w-28 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr
              v-for="p in filteredProjects"
              :key="p.id"
              class="hover:bg-slate-50/80 transition-colors cursor-pointer group"
              @click="router.push(`/projects/${p.id}`)"
            >
              <!-- ID -->
              <td class="py-2.5 px-4 text-slate-400 tabular-nums">
                {{ p.id }}
              </td>
              <!-- Name -->
              <td class="py-2.5 px-4 font-semibold text-slate-900 group-hover:text-emerald-600 transition-colors">
                <div class="flex items-center gap-2">
                  <div class="w-6 h-6 rounded bg-emerald-50 border border-emerald-200/60 text-emerald-700 flex items-center justify-center shrink-0">
                    <Layers class="w-3.5 h-3.5" />
                  </div>
                  <span>{{ p.name }}</span>
                </div>
              </td>
              <!-- Page Count -->
              <td class="py-2.5 px-4 tabular-nums">
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/70">
                  {{ p.pageCount != null ? `${p.pageCount} 画框` : '—' }}
                </span>
              </td>
              <!-- Description -->
              <td class="py-2.5 px-4 text-slate-500 truncate max-w-xs">
                {{ p.description || '—' }}
              </td>
              <!-- Date -->
              <td class="py-2.5 px-4 text-slate-400 tabular-nums">
                {{ new Date(p.createdAt || '').toLocaleDateString() }}
              </td>
              <!-- Action -->
              <td class="py-2.5 px-4 text-right">
                <div class="inline-flex items-center gap-2">
                  <span class="text-xs font-semibold text-emerald-600 group-hover:translate-x-0.5 transition-transform inline-flex items-center gap-0.5">
                    打开
                    <ChevronRight class="w-3 h-3" />
                  </span>
                  <button
                    class="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                    title="删除"
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
      title="新建原型项目"
      width="460px"
      align-center
      class="rounded-2xl"
    >
      <div class="space-y-4 pt-2">
        <div>
          <label class="block text-xs font-bold text-slate-700 mb-1.5">项目名称 <span class="text-red-500">*</span></label>
          <el-input
            v-model="form.name"
            placeholder="例如：电商大促移动端原型"
            size="large"
            clearable
          />
        </div>
        <div>
          <label class="block text-xs font-bold text-slate-700 mb-1.5">项目描述</label>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="填写关于此项目的简要说明或业务目标（可选）"
          />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2.5">
          <button
            class="wf-tap px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
            @click="dialogVisible = false"
          >
            取消
          </button>
          <button
            class="wf-tap inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 rounded-xl shadow-sm shadow-emerald-500/25 transition-all disabled:opacity-60 cursor-pointer"
            :disabled="creating"
            @click="create"
          >
            <div v-if="creating" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            {{ creating ? '创建中...' : '立即创建' }}
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