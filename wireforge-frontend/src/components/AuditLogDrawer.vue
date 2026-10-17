<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="540px"
    :with-header="false"
    append-to-body
    destroy-on-close
    class="audit-log-drawer"
  >
    <div class="h-full flex flex-col bg-white dark:bg-[#202020] text-slate-800 dark:text-slate-100 select-none">
      <!-- Top Header -->
      <div class="px-5 py-4 border-b border-slate-100 dark:border-[#333] flex items-center justify-between bg-slate-50/70 dark:bg-[#252525]">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-xl bg-amber-500/10 dark:bg-amber-500/20 text-amber-600 dark:text-amber-400 flex items-center justify-center font-bold text-sm">
            <ScrollText class="w-4 h-4" />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="text-sm font-bold tracking-tight text-slate-900 dark:text-white">{{ drawerTitle }}</h2>
              <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-300/60 dark:border-amber-700/50">
                仅管理员可见
              </span>
            </div>
            <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">{{ drawerSubtitle }}</p>
          </div>
        </div>

        <div class="flex items-center gap-1.5">
          <button
            type="button"
            class="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-[#383838] rounded-lg transition-colors cursor-pointer"
            :title="loading ? '正在刷新...' : '刷新日志'"
            :disabled="loading"
            @click="handleRefresh"
          >
            <RefreshCw class="w-4 h-4" :class="{ 'animate-spin text-blue-500': loading }" />
          </button>
          <button
            type="button"
            class="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-[#383838] rounded-lg transition-colors cursor-pointer"
            title="关闭抽屉"
            @click="close"
          >
            <X class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Filter Controls Section -->
      <div class="p-4 border-b border-slate-100 dark:border-[#333] bg-white dark:bg-[#202020] space-y-2.5">
        <!-- Keyword Search Input -->
        <div class="flex items-center gap-2 px-3 py-1.5 bg-slate-50 dark:bg-[#1a1a1a] rounded-xl border border-slate-200/80 dark:border-[#383838] focus-within:border-blue-500 focus-within:bg-white dark:focus-within:bg-[#242424] focus-within:ring-2 focus-within:ring-blue-500/20 transition-all text-xs">
          <Search class="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <input
            v-model="keyword"
            type="text"
            :placeholder="isWorkspace ? '搜索操作人或项目名称...' : '搜索操作人、画板名称或变更描述...'"
            class="w-full bg-transparent border-none outline-none text-slate-800 dark:text-slate-100 placeholder:text-slate-400 text-xs"
          />
          <button
            v-if="keyword"
            class="text-[11px] text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
            @click="keyword = ''"
          >
            <X class="w-3 h-3" />
          </button>
        </div>

        <!-- Action Type Pills (Flex-wrap to ensure zero overflow or cutoff) -->
        <div class="flex flex-wrap items-center gap-1.5 pt-0.5">
          <button
            v-for="f in visibleFilters"
            :key="f.id"
            type="button"
            class="shrink-0 whitespace-nowrap py-1 px-2.5 rounded-lg text-[11px] font-medium transition-all cursor-pointer flex items-center gap-1.5"
            :class="
              selectedActionType === f.id
                ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 font-bold border border-blue-200 dark:border-blue-800 shadow-2xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-[#2a2a2a]'
            "
            @click="selectedActionType = f.id"
          >
            <component :is="f.icon" class="w-3.5 h-3.5 shrink-0" />
            <span class="whitespace-nowrap">{{ f.label }}</span>
            <span
              class="text-[10px] whitespace-nowrap px-1.5 py-0.2 rounded-full font-bold ml-0.5"
              :class="
                selectedActionType === f.id
                  ? 'bg-blue-200/70 text-blue-800 dark:bg-blue-900/60 dark:text-blue-200'
                  : 'bg-slate-200/60 dark:bg-[#333] text-slate-500'
              "
            >
              {{ getCountForType(f.id) }}
            </span>
          </button>
        </div>
      </div>

      <!-- Main Logs Timeline -->
      <div ref="timelineScrollRef" class="flex-1 overflow-y-auto p-5 custom-scrollbar">
        <!-- Loading State -->
        <div v-if="loading" class="py-12 flex flex-col items-center justify-center space-y-3">
          <div class="w-8 h-8 rounded-full border-2 border-blue-500 border-t-transparent animate-spin"></div>
          <p class="text-xs text-slate-400">{{ isWorkspace ? '正在拉取项目操作日志...' : '正在拉取并校验项目审计日志...' }}</p>
        </div>

        <!-- Empty State -->
        <div
          v-else-if="filteredLogs.length === 0"
          class="py-16 flex flex-col items-center justify-center text-center px-4"
        >
          <div class="w-12 h-12 rounded-2xl bg-slate-100 dark:bg-[#282828] text-slate-400 flex items-center justify-center mb-3">
            <Inbox class="w-6 h-6" />
          </div>
          <h3 class="text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
            {{ keyword ? '未检索到匹配的审计记录' : (isWorkspace ? '暂无项目操作' : '暂无项目操作日志') }}
          </h3>
          <p class="text-[11px] text-slate-400 max-w-xs leading-relaxed">
            {{ keyword ? '换个操作人或项目名称再试一次。' : (isWorkspace ? '创建、修改或删除项目后，会记在这里。已经删掉的项目也能看到是谁删的。' : '团队成员在画板上的创建、修改、交互连线及评审评论都将在此真实记录。') }}
          </p>
        </div>

        <!-- Timeline Items List -->
        <div v-else class="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-[2px] before:bg-slate-200 dark:before:bg-[#333]">
          <div
            v-for="item in filteredLogs"
            :key="item.id"
            class="relative group"
          >
            <!-- Timeline Dot / Avatar Marker -->
            <div class="absolute -left-6 top-0 -translate-x-1/2">
              <div
                class="w-5 h-5 rounded-full border-2 border-white dark:border-[#202020] flex items-center justify-center shadow-xs"
                :class="getChangeTypeDotClass(item.changeType)"
              >
                <div class="w-2 h-2 rounded-full bg-white"></div>
              </div>
            </div>

            <!-- Log Card -->
            <div class="p-3.5 rounded-xl border border-slate-200/80 dark:border-[#333] bg-white dark:bg-[#252525] hover:border-slate-300 dark:hover:border-[#444] shadow-xs transition-all">
              <!-- Top Row: Operator Info, Badge & Time -->
              <div class="flex items-center justify-between gap-2 mb-2">
                <div class="flex items-center gap-2 min-w-0">
                  <!-- Operator Avatar -->
                  <div class="relative shrink-0">
                    <img
                      v-if="item.userAvatar"
                      :src="item.userAvatar"
                      :alt="item.userName"
                      class="w-6 h-6 rounded-full object-cover border border-slate-200 dark:border-[#444]"
                      @error="onAvatarError($event)"
                    />
                    <div
                      v-else
                      class="w-6 h-6 rounded-full bg-blue-500 text-white text-[10px] font-bold flex items-center justify-center"
                    >
                      {{ item.userName.slice(0, 1) }}
                    </div>
                  </div>

                  <!-- Operator Name -->
                  <span class="text-xs font-bold text-slate-800 dark:text-slate-100 truncate">
                    {{ item.userName }}
                  </span>

                  <!-- Action Badge (绿色新增 / 蓝色修改 / 红色删除) -->
                  <span
                    class="text-[10px] font-semibold px-2 py-0.5 rounded-md shrink-0"
                    :class="getChangeBadgeClass(item.changeType)"
                  >
                    {{ item.actionLabel }}
                  </span>
                </div>

                <!-- Time: Relative with Absolute Tooltip -->
                <el-tooltip :content="formatAbsoluteTime(item.timestamp)" placement="top">
                  <span class="text-[10px] text-slate-400 dark:text-slate-400 font-mono shrink-0 cursor-default flex items-center gap-1">
                    <Clock class="w-3 h-3 text-slate-400" />
                    <span>{{ formatRelativeTime(item.timestamp) }}</span>
                  </span>
                </el-tooltip>
              </div>

              <!-- Page Name Badge (if applicable) -->
              <div v-if="item.pageName" class="mb-1.5 flex items-center gap-1 text-[11px] text-slate-600 dark:text-slate-300">
                <Layout class="w-3 h-3 text-slate-400" />
                <span class="font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-[#303030] px-1.5 py-0.5 rounded text-[10px]">
                  {{ item.pageName }}
                </span>
              </div>

              <!-- Change Details Description -->
              <p class="text-xs text-slate-600 dark:text-slate-300 leading-relaxed break-words">
                {{ item.details }}
              </p>

              <!-- Footer Metadata: Action category -->
              <div class="mt-2 pt-1.5 border-t border-slate-100 dark:border-[#303030] flex items-center justify-between text-[10px] text-slate-400 dark:text-slate-400">
                <span class="flex items-center gap-1">
                  <span class="w-1.5 h-1.5 rounded-full" :class="getActionTypeColor(item.actionType)"></span>
                  <span>{{ getActionTypeLabel(item.actionType) }}</span>
                </span>
                <span class="font-mono opacity-60">ID: {{ item.id.slice(-6) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Drawer Bottom Status Bar & Pagination -->
      <div class="px-5 py-3 border-t border-slate-100 dark:border-[#333] bg-slate-50/90 dark:bg-[#1a1a1a] flex items-center justify-between gap-3 text-[11px] text-slate-500 dark:text-slate-400">
        <div class="flex items-center gap-2 shrink-0">
          <ShieldAlert class="w-3.5 h-3.5 text-emerald-500 shrink-0" />
          <span>
            共 <strong class="text-slate-800 dark:text-slate-200">{{ totalLogs }}</strong> 条
            <span v-if="totalPages > 1" class="text-slate-400 dark:text-slate-500">（第 {{ currentPage }}/{{ totalPages }} 页）</span>
            <span v-if="hasActiveFilter" class="text-amber-600 dark:text-amber-400 ml-1 font-medium">（已筛选）</span>
          </span>
          <!-- 只要有筛选条件，就常驻提供重置筛选按钮，绝不会因翻页器而隐藏 -->
          <button
            v-if="hasActiveFilter"
            type="button"
            class="text-blue-600 dark:text-blue-400 hover:underline cursor-pointer flex items-center gap-0.5 ml-1 font-medium"
            title="清空搜索词与分类筛选，回到全部记录"
            @click="resetFilter"
          >
            <span>重置筛选</span>
          </button>
        </div>

        <!-- Pagination Controls (仅在存在多页时展示分页翻页器，不翻页时不加载后续数据) -->
        <div v-if="totalPages > 1" class="flex items-center gap-2">
          <el-pagination
            :current-page="currentPage"
            :page-size="pageSize"
            :total="totalLogs"
            layout="prev, pager, next"
            :pager-count="5"
            size="small"
            class="audit-pagination"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import {
  X,
  ScrollText,
  RefreshCw,
  Search,
  Inbox,
  Clock,
  Layout,
  Layers,
  Zap,
  MessageCircle,
  ShieldAlert,
  Users,
  FolderGit2,
} from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import {
  fetchAuditLogs,
  fetchProjectOperationLogs,
  auditLogsMap,
  type AuditLogItem,
  type AuditActionType,
  type AuditChangeType,
} from '../services/auditLog'

const props = withDefaults(defineProps<{
  modelValue: boolean
  projectId?: number
  scope?: 'project' | 'workspace'
}>(), {
  scope: 'project',
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
    if (v) {
      currentPage.value = 1
      loadLogs(1)
    }
  },
  { immediate: true }
)
watch(visible, (v) => {
  emit('update:modelValue', v)
})

const loading = ref(false)
const keyword = ref('')
const selectedActionType = ref<'all' | AuditActionType | AuditChangeType>('all')

// 分页状态管理：默认只加载第1页（最新50条），用户翻页时才触发对应分页的后端请求
const currentPage = ref(1)
const pageSize = ref(50)
const totalLogs = ref(0)
const totalPages = ref(1)
const pageRecords = ref<AuditLogItem[]>([])
const categoryCounts = ref<Record<string, number>>({})
const timelineScrollRef = ref<HTMLElement | null>(null)

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => !!keyword.value.trim() || selectedActionType.value !== 'all')

function resetFilter() {
  keyword.value = ''
  selectedActionType.value = 'all'
}

const isWorkspace = computed(() => props.scope === 'workspace')
const drawerTitle = computed(() => (isWorkspace.value ? '项目操作日志' : '项目操作审计日志'))
const drawerSubtitle = computed(() =>
  isWorkspace.value
    ? '记录谁创建、修改或删除了项目'
    : '记录团队全量画板变更、连线调整与评论讨论'
)

// 当前页的审计记录列表（由服务端全量搜索与分类过滤直接提供）
const filteredLogs = computed<AuditLogItem[]>(() => {
  return pageRecords.value
})

const actionFilters = [
  { id: 'all', label: '全部', icon: Layers },
  { id: 'page_edit', label: '画板', icon: Layout },
  { id: 'connection_change', label: '连线', icon: Zap },
  { id: 'comment_discuss', label: '评论', icon: MessageCircle },
  { id: 'member_change', label: '权限', icon: Users },
  { id: 'project_manage', label: '项目', icon: FolderGit2 },
] as const

const workspaceFilters = [
  { id: 'all', label: '全部', icon: Layers },
  { id: 'add', label: '创建', icon: FolderGit2 },
  { id: 'edit', label: '修改', icon: Layout },
  { id: 'delete', label: '删除', icon: ShieldAlert },
] as const

const visibleFilters = computed(() => (isWorkspace.value ? workspaceFilters : actionFilters))

// 关键词搜索服务端全量防抖联动
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadLogs(1)
  }, 300)
})

// 分类切换直接触发服务端全量检索
watch(selectedActionType, () => {
  currentPage.value = 1
  loadLogs(1)
})

async function loadLogs(page = 1) {
  loading.value = true
  try {
    currentPage.value = page
    const kw = keyword.value.trim()
    const at = selectedActionType.value
    if (isWorkspace.value) {
      const res = await fetchProjectOperationLogs(page, pageSize.value, kw, at)
      pageRecords.value = res.records
      totalLogs.value = res.total
      totalPages.value = res.pages
      if (res.counts && Object.keys(res.counts).length > 0) {
        categoryCounts.value = { ...categoryCounts.value, ...res.counts }
      }
    } else if (props.projectId != null) {
      const res = await fetchAuditLogs(props.projectId, page, pageSize.value, kw, at)
      pageRecords.value = res.records
      totalLogs.value = res.total
      totalPages.value = res.pages
      if (res.counts && Object.keys(res.counts).length > 0) {
        categoryCounts.value = { ...categoryCounts.value, ...res.counts }
      }
    }
    if (timelineScrollRef.value) {
      timelineScrollRef.value.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载操作日志失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(newPage: number) {
  if (newPage === currentPage.value) return
  loadLogs(newPage)
}

async function handleRefresh() {
  await loadLogs(currentPage.value)
  ElMessage.success('操作日志已刷新最新变动')
}

function close() {
  visible.value = false
}

// 分类徽标读取后端全局真实统计总数，绝不因当前页无对应类型而误显为 0
function getCountForType(type: string) {
  if (categoryCounts.value[type] !== undefined) {
    return categoryCounts.value[type]
  }
  if (type === 'all') return totalLogs.value
  return 0
}

function getChangeBadgeClass(changeType: AuditChangeType) {
  switch (changeType) {
    case 'add':
      // 绿色新增
      return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
    case 'edit':
      // 蓝色修改
      return 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-800'
    case 'delete':
      // 红色删除
      return 'bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border border-rose-200 dark:border-rose-800'
    default:
      return 'bg-slate-100 text-slate-700'
  }
}

function getChangeTypeDotClass(changeType: AuditChangeType) {
  switch (changeType) {
    case 'add':
      return 'bg-emerald-500'
    case 'edit':
      return 'bg-blue-500'
    case 'delete':
      return 'bg-rose-500'
    default:
      return 'bg-slate-400'
  }
}

function getActionTypeLabel(actionType: AuditActionType) {
  switch (actionType) {
    case 'page_edit':
      return '画板修改'
    case 'connection_change':
      return '连线变动'
    case 'comment_discuss':
      return '评论讨论'
    case 'member_change':
      return '权限变更'
    case 'project_manage':
      return '项目管理'
    default:
      return actionType
  }
}

function getActionTypeColor(actionType: AuditActionType) {
  switch (actionType) {
    case 'page_edit':
      return 'bg-emerald-500'
    case 'connection_change':
      return 'bg-blue-500'
    case 'comment_discuss':
      return 'bg-amber-500'
    case 'member_change':
      return 'bg-purple-500'
    case 'project_manage':
      return 'bg-indigo-500'
    default:
      return 'bg-slate-400'
  }
}

function formatRelativeTime(isoStr: string) {
  try {
    const d = new Date(isoStr)
    const now = new Date()
    const diffSec = Math.floor((now.getTime() - d.getTime()) / 1000)
    if (diffSec < 60) return '刚刚'
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin}分钟前`
    const diffH = Math.floor(diffMin / 60)
    if (diffH < 24) return `${diffH}小时前`
    const diffDays = Math.floor(diffH / 24)
    if (diffDays < 7) return `${diffDays}天前`
    return `${d.getMonth() + 1}月${d.getDate()}日`
  } catch {
    return isoStr
  }
}

function formatAbsoluteTime(isoStr: string) {
  try {
    const d = new Date(isoStr)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return isoStr
  }
}

function onAvatarError(e: Event) {
  const target = e.target as HTMLImageElement
  if (target) {
    target.style.display = 'none'
  }
}

onMounted(() => {
  if (props.modelValue) {
    currentPage.value = 1
    loadLogs(1)
  }
})
</script>

<style scoped>
.audit-log-drawer :deep(.el-drawer__body) {
  padding: 0;
}
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.audit-pagination :deep(.btn-prev),
.audit-pagination :deep(.btn-next),
.audit-pagination :deep(.el-pager li) {
  background: transparent !important;
  font-size: 11px;
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  border-radius: 6px;
}
.audit-pagination :deep(.el-pager li.is-active) {
  background: #2563eb !important;
  color: #fff !important;
  font-weight: 700;
}
</style>
