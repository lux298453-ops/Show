<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="min(480px, 100vw)"
    :with-header="false"
    append-to-body
    destroy-on-close
    class="audit-log-drawer"
  >
    <section class="audit-log-panel" :aria-label="drawerTitle">
      <header class="audit-log-header">
        <h2>{{ drawerTitle }}</h2>
        <div class="audit-log-header-actions">
          <button type="button" class="audit-log-icon-button" aria-label="刷新日志" title="刷新日志" :disabled="loading" @click="handleRefresh">
            <RefreshCw :size="16" :class="{ 'animate-spin': loading }" aria-hidden="true" />
          </button>
          <button type="button" class="audit-log-icon-button" aria-label="关闭操作日志" @click="close">
            <X :size="16" aria-hidden="true" />
          </button>
        </div>
      </header>

      <div class="audit-log-filters">
        <div class="audit-log-search">
          <Search :size="15" aria-hidden="true" />
          <input
            v-model="keyword"
            type="search"
            aria-label="搜索操作日志"
            :placeholder="isWorkspace ? '搜索操作人、项目或内容' : '搜索操作人、画板或内容'"
          />
          <button v-if="keyword" type="button" class="audit-log-icon-button" aria-label="清空搜索" @click="keyword = ''">
            <X :size="13" aria-hidden="true" />
          </button>
        </div>
        <nav class="audit-log-categories" aria-label="操作分类">
          <button
            v-for="filter in visibleFilters"
            :key="filter.id"
            type="button"
            class="audit-log-category"
            :class="{ 'is-active': selectedActionType === filter.id }"
            :aria-pressed="selectedActionType === filter.id"
            :title="filter.label + ' · ' + getCountForType(filter.id) + ' 条'"
            @click="selectedActionType = filter.id"
          >{{ filter.label }}</button>
        </nav>
      </div>

      <div ref="timelineScrollRef" class="audit-log-list" :aria-busy="loading">
        <div v-if="loading" class="audit-log-empty" role="status">
          <RefreshCw :size="18" class="animate-spin" aria-hidden="true" />
          <p>正在加载日志…</p>
        </div>
        <div v-else-if="filteredLogs.length === 0" class="audit-log-empty" role="status">
          <p class="audit-log-empty-title">{{ hasActiveFilter ? '没有匹配的记录' : '暂无操作记录' }}</p>
          <p>{{ hasActiveFilter ? '试试其他关键词或分类' : '新的操作会显示在这里' }}</p>
        </div>
        <template v-else>
          <details v-for="item in filteredLogs" :key="item.id" class="audit-log-record">
            <summary class="audit-log-summary" title="展开或收起日志详情">
              <div class="audit-log-avatar" aria-hidden="true">
                <span>{{ (item.userName || '?').slice(0, 1) }}</span>
                <img v-if="item.userAvatar" :src="item.userAvatar" alt="" @error="onAvatarError" />
              </div>
              <div class="audit-log-record-content">
                <div class="audit-log-meta">
                  <span class="audit-log-name" :title="item.userName">{{ item.userName }}</span>
                  <span class="audit-log-action" :class="'is-' + item.changeType">{{ item.actionLabel }}</span>
                  <time :datetime="item.timestamp" :title="formatAbsoluteTime(item.timestamp)">{{ formatRelativeTime(item.timestamp) }}</time>
                </div>
                <p v-if="item.pageName" class="audit-log-subject" :title="item.pageName">{{ item.pageName }}</p>
                <p class="audit-log-description">{{ item.details }}</p>
              </div>
              <ChevronDown :size="14" class="audit-log-chevron" aria-hidden="true" />
            </summary>
            <dl class="audit-log-details">
              <dt>操作类型</dt><dd>{{ getActionTypeLabel(item.actionType) }}</dd>
              <dt>操作时间</dt><dd>{{ formatAbsoluteTime(item.timestamp) }}</dd>
              <dt>记录编号</dt><dd class="audit-log-record-id">{{ item.id }}</dd>
            </dl>
          </details>
        </template>
      </div>

      <footer class="audit-log-footer">
        <div class="audit-log-status" aria-live="polite">
          <span>共 {{ totalLogs }} 条{{ hasActiveFilter ? '匹配记录' : '记录' }}</span>
          <button v-if="hasActiveFilter" type="button" class="audit-log-reset" @click="resetFilter">重置</button>
        </div>
        <el-pagination
          v-if="totalPages > 1"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="totalLogs"
          layout="prev, pager, next"
          :pager-count="5"
          size="small"
          class="audit-log-pagination"
          @current-change="handlePageChange"
        />
      </footer>
    </section>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { X, RefreshCw, Search, ChevronDown } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import {
  fetchAuditLogs,
  fetchProjectOperationLogs,
  type AuditLogItem,
  type AuditActionType,
  type AuditChangeType,
} from '../services/auditLog'

const props = withDefaults(defineProps<{
  modelValue: boolean
  projectId?: number
  scope?: 'project' | 'workspace'
}>(), { scope: 'project' })
const emit = defineEmits<{ (e: 'update:modelValue', val: boolean): void }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const loading = ref(false)
const keyword = ref('')
const selectedActionType = ref<'all' | AuditActionType | AuditChangeType>('all')
const currentPage = ref(1)
const pageSize = 50
const totalLogs = ref(0)
const totalPages = ref(1)
const filteredLogs = ref<AuditLogItem[]>([])
const categoryCounts = ref<Record<string, number>>({})
const timelineScrollRef = ref<HTMLElement | null>(null)
const hasActiveFilter = computed(() => !!keyword.value.trim() || selectedActionType.value !== 'all')
const isWorkspace = computed(() => props.scope === 'workspace')
const drawerTitle = computed(() => isWorkspace.value ? '项目操作日志' : '操作日志')
const actionFilters = [
  { id: 'all', label: '全部' },
  { id: 'page_edit', label: '画板' },
  { id: 'connection_change', label: '连线' },
  { id: 'comment_discuss', label: '评论' },
  { id: 'member_change', label: '权限' },
  { id: 'project_manage', label: '项目' },
] as const
const workspaceFilters = [
  { id: 'all', label: '全部' },
  { id: 'add', label: '创建' },
  { id: 'edit', label: '修改' },
  { id: 'delete', label: '删除' },
] as const
const visibleFilters = computed(() => isWorkspace.value ? workspaceFilters : actionFilters)
let searchDebounceTimer: ReturnType<typeof setTimeout> | undefined
let loadSequence = 0

function resetFilter() {
  keyword.value = ''
  selectedActionType.value = 'all'
}

async function loadLogs(page = 1): Promise<boolean> {
  if (!props.modelValue) return false
  const sequence = ++loadSequence
  loading.value = true
  currentPage.value = page
  try {
    const kw = keyword.value.trim()
    const type = selectedActionType.value
    const result = isWorkspace.value
      ? await fetchProjectOperationLogs(page, pageSize, kw, type)
      : props.projectId != null ? await fetchAuditLogs(props.projectId, page, pageSize, kw, type) : null
    if (sequence !== loadSequence) return false
    filteredLogs.value = result?.records || []
    totalLogs.value = result?.total || 0
    totalPages.value = result?.pages || 1
    categoryCounts.value = result?.counts || {}
    timelineScrollRef.value?.scrollTo({ top: 0 })
    return true
  } catch (err: unknown) {
    if (sequence === loadSequence) ElMessage.error(err instanceof Error ? err.message : '加载操作日志失败')
    return false
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function handlePageChange(page: number) {
  if (page !== currentPage.value) loadLogs(page)
}

async function handleRefresh() {
  clearTimeout(searchDebounceTimer)
  if (await loadLogs(currentPage.value)) ElMessage.success('日志已刷新')
}

function close() { visible.value = false }

function getCountForType(type: string): number {
  return categoryCounts.value[type] ?? (type === 'all' ? totalLogs.value : 0)
}

function getActionTypeLabel(type: AuditActionType): string {
  const labels: Record<AuditActionType, string> = {
    page_edit: '画板修改', connection_change: '连线变动', comment_discuss: '评论讨论',
    member_change: '权限变更', project_manage: '项目管理',
  }
  return labels[type] || type
}

function formatRelativeTime(iso: string): string {
  const date = new Date(iso)
  if (!Number.isFinite(date.getTime())) return iso
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000))
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return minutes + '分钟前'
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return hours + '小时前'
  const days = Math.floor(hours / 24)
  if (days < 7) return days + '天前'
  return (date.getMonth() + 1) + '月' + date.getDate() + '日'
}

function formatAbsoluteTime(iso: string): string {
  const date = new Date(iso)
  if (!Number.isFinite(date.getTime())) return iso
  const pad = (value: number) => String(value).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
    + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
}

function onAvatarError(event: Event) {
  (event.target as HTMLImageElement).style.display = 'none'
}

watch([keyword, selectedActionType], ([kw, type], [, previousType]) => {
  clearTimeout(searchDebounceTimer)
  if (!props.modelValue) return
  currentPage.value = 1
  if (type !== previousType || !kw.trim()) loadLogs(1)
  else searchDebounceTimer = setTimeout(() => loadLogs(1), 300)
})

watch(() => [props.modelValue, props.projectId, props.scope] as const, ([open]) => {
  clearTimeout(searchDebounceTimer)
  loadSequence++
  loading.value = false
  if (open) loadLogs(1)
}, { immediate: true })

onBeforeUnmount(() => { clearTimeout(searchDebounceTimer); loadSequence++ })
</script>

<style>
.audit-log-drawer.el-drawer {
  --audit-surface: var(--wf-surface, #ffffff);
  --audit-soft: var(--wf-bg, #f6f8fb);
  --audit-line: var(--wf-border, #e2e8f0);
  --audit-text: var(--wf-text-main, #1e293b);
  --audit-muted: var(--wf-text-muted, #64748b);
  max-width: 100vw;
  border-left: 1px solid var(--audit-line);
  background: var(--audit-surface);
  color: var(--audit-text);
}
.audit-log-drawer .el-drawer__body { padding: 0; overflow: hidden; min-height: 0; }
.audit-log-drawer .audit-log-panel { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.audit-log-drawer button, .audit-log-drawer input { font-family: inherit; }
.audit-log-drawer button { cursor: pointer; }
.audit-log-drawer button:disabled { cursor: not-allowed; opacity: .5; }
.audit-log-drawer button:focus-visible, .audit-log-drawer summary:focus-visible { outline: 2px solid var(--wf-primary); outline-offset: 2px; }
.audit-log-drawer .audit-log-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 21px 24px 17px; }
.audit-log-drawer .audit-log-header h2 { margin: 0; font-size: 16px; line-height: 1.5; font-weight: 600; letter-spacing: -.2px; }
.audit-log-drawer .audit-log-header-actions { display: flex; gap: 5px; }
.audit-log-drawer .audit-log-icon-button { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; padding: 6px; border: 0; border-radius: 6px; background: transparent; color: var(--audit-muted); }
.audit-log-drawer .audit-log-icon-button:hover { background: var(--audit-soft); color: var(--audit-text); }
.audit-log-drawer .audit-log-filters { padding: 0 24px; border-bottom: 1px solid var(--audit-line); }
.audit-log-drawer .audit-log-search { display: flex; align-items: center; gap: 8px; height: 36px; padding: 0 10px; border: 1px solid var(--audit-line); border-radius: 7px; color: var(--audit-muted); }
.audit-log-drawer .audit-log-search:focus-within { border-color: var(--wf-primary); }
.audit-log-drawer .audit-log-search > svg { flex-shrink: 0; }
.audit-log-drawer .audit-log-search input { flex: 1; width: 100%; min-width: 0; padding: 0; border: 0; outline: 0; background: transparent; color: var(--audit-text); font-size: 12px; }
.audit-log-drawer .audit-log-search input::placeholder { color: var(--audit-muted); }
.audit-log-drawer .audit-log-search input::-webkit-search-cancel-button { display: none; }
.audit-log-drawer .audit-log-search .audit-log-icon-button { padding: 3px; }
.audit-log-drawer .audit-log-categories { display: flex; gap: 20px; overflow-x: auto; padding-top: 6px; scrollbar-width: none; }
.audit-log-drawer .audit-log-categories::-webkit-scrollbar { display: none; }
.audit-log-drawer .audit-log-category { flex-shrink: 0; padding: 12px 0 11px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--audit-muted); font-size: 13px; line-height: 1.5; white-space: nowrap; }
.audit-log-drawer .audit-log-category:hover { color: var(--audit-text); }
.audit-log-drawer .audit-log-category.is-active { color: var(--wf-primary); border-bottom-color: var(--wf-primary); font-weight: 500; }
.audit-log-drawer .audit-log-list { flex: 1; min-height: 0; overflow-y: auto; padding: 0 24px; scrollbar-width: thin; scrollbar-color: var(--audit-line) transparent; }
.audit-log-drawer .audit-log-record { padding: 18px 0; border-bottom: 1px solid var(--audit-line); }
.audit-log-drawer .audit-log-record:last-child { border-bottom: 0; }
.audit-log-drawer .audit-log-summary { display: grid; grid-template-columns: 28px minmax(0, 1fr) 14px; gap: 10px; list-style: none; cursor: pointer; border-radius: 4px; }
.audit-log-drawer .audit-log-summary::-webkit-details-marker { display: none; }
.audit-log-drawer .audit-log-avatar { position: relative; display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; overflow: hidden; background: var(--audit-soft); color: var(--audit-muted); font-size: 11px; font-weight: 500; }
.audit-log-drawer .audit-log-avatar img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.audit-log-drawer .audit-log-record-content { min-width: 0; }
.audit-log-drawer .audit-log-meta { display: flex; align-items: baseline; gap: 8px; min-height: 22px; }
.audit-log-drawer .audit-log-name { min-width: 0; max-width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; }
.audit-log-drawer .audit-log-action { flex-shrink: 0; color: var(--audit-muted); font-size: 11px; white-space: nowrap; }
.audit-log-drawer .audit-log-action.is-delete { color: var(--el-color-danger); }
.audit-log-drawer .audit-log-meta time { flex-shrink: 0; margin-left: auto; color: var(--audit-muted); font-size: 11px; white-space: nowrap; }
.audit-log-drawer .audit-log-subject { margin: 3px 0 0; color: var(--audit-text); font-size: 13px; line-height: 1.6; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-log-drawer .audit-log-description { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; overflow-wrap: anywhere; margin: 4px 0 0; color: var(--audit-muted); font-size: 12px; line-height: 1.75; }
.audit-log-drawer .audit-log-chevron { margin-top: 4px; color: var(--audit-muted); transition: transform .15s; }
.audit-log-drawer .audit-log-record[open] .audit-log-chevron { transform: rotate(180deg); }
.audit-log-drawer .audit-log-record[open] .audit-log-description { display: block; -webkit-line-clamp: unset; }
.audit-log-drawer .audit-log-record[open] .audit-log-subject { white-space: normal; overflow-wrap: anywhere; }
.audit-log-drawer .audit-log-details { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 5px 14px; margin: 12px 0 0 38px; padding: 12px; border-radius: 7px; background: var(--audit-soft); font-size: 11px; line-height: 1.7; }
.audit-log-drawer .audit-log-details dt { color: var(--audit-muted); }
.audit-log-drawer .audit-log-details dd { margin: 0; overflow-wrap: anywhere; }
.audit-log-drawer .audit-log-record-id { font-family: ui-monospace, monospace; }
.audit-log-drawer .audit-log-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; min-height: 220px; padding: 24px 0; text-align: center; font-size: 12px; color: var(--audit-muted); }
.audit-log-drawer .audit-log-empty p { margin: 0; }
.audit-log-drawer .audit-log-empty .audit-log-empty-title { color: var(--audit-text); font-size: 13px; }
.audit-log-drawer .audit-log-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 14px; padding: 14px 24px; border-top: 1px solid var(--audit-line); }
.audit-log-drawer .audit-log-status { display: flex; align-items: center; gap: 12px; color: var(--audit-muted); font-size: 12px; }
.audit-log-drawer .audit-log-reset { padding: 0; border: 0; background: transparent; color: var(--wf-primary); font-size: 12px; }
.audit-log-drawer .audit-log-reset:hover { color: var(--wf-primary-hover); }
.audit-log-drawer .audit-log-pagination { margin-left: auto; }
.audit-log-drawer .audit-log-pagination .btn-prev, .audit-log-drawer .audit-log-pagination .btn-next, .audit-log-drawer .audit-log-pagination .el-pager li { background: transparent; font-size: 12px; min-width: 26px; height: 26px; line-height: 26px; border-radius: 6px; }
.audit-log-drawer .audit-log-pagination .el-pager li.is-active { color: var(--wf-primary); background: var(--el-color-primary-light-9); font-weight: 600; }
@media (max-width: 420px) {
  .audit-log-drawer .audit-log-header { padding: 18px 17px 15px; }
  .audit-log-drawer .audit-log-filters, .audit-log-drawer .audit-log-list { padding-inline: 17px; }
  .audit-log-drawer .audit-log-footer { padding: 14px 17px; }
  .audit-log-drawer .audit-log-meta { flex-wrap: wrap; gap: 3px 7px; }
  .audit-log-drawer .audit-log-meta time { font-size: 10px; }
}
@media (pointer: coarse) {
  .audit-log-drawer .audit-log-icon-button { min-width: 36px; min-height: 36px; }
  .audit-log-drawer .audit-log-search { height: 42px; }
  .audit-log-drawer .audit-log-search input { font-size: 16px; }
  .audit-log-drawer .audit-log-category { min-height: 44px; }
}
@media (prefers-reduced-motion: reduce) {
  .audit-log-drawer .audit-log-chevron { transition: none; }
}
</style>
