<template>
  <div class="project-home">
    <header class="home-header">
      <div class="home-header-inner">
        <RouterLink to="/" class="brand" aria-label="WireForge"><img src="/favicon.svg" alt="" /><span>WireForge</span></RouterLink>
        <div class="header-actions">
          <button class="home-button primary" @click="dialogVisible = true"><Plus :size="16" />{{ t('newProject') }}</button>
          <NavbarControls />
        </div>
      </div>
    </header>

    <main class="home-main">
      <section class="library-heading">
        <div><p class="eyebrow">{{ copy.workspace }}</p><h1>{{ copy.myProjects }}</h1><p class="heading-description">{{ copy.intro }}</p></div>
        <span v-if="!loading && !loadError" class="project-count">{{ t('projectsCount', { n: projects.length }) }}</span>
      </section>
      <div class="library-toolbar">
        <label class="project-search"><Search :size="17" /><input v-model="searchQuery" type="search" :placeholder="t('searchProjects')" :aria-label="t('searchProjects')" /></label>
        <div class="toolbar-right"><span class="sort-label">{{ copy.sort }}</span><div class="view-switch" role="group" :aria-label="copy.view">
          <button :class="{ active: viewMode === 'grid' }" :aria-label="t('gridView')" :aria-pressed="viewMode === 'grid'" :title="t('gridView')" @click="viewMode = 'grid'"><LayoutGrid :size="17" /></button>
          <button :class="{ active: viewMode === 'list' }" :aria-label="t('listView')" :aria-pressed="viewMode === 'list'" :title="t('listView')" @click="viewMode = 'list'"><List :size="17" /></button>
        </div></div>
      </div>

      <div v-if="loading" class="home-state" role="status"><span class="loading-spinner"></span><h2>{{ copy.loading }}</h2></div>
      <div v-else-if="loadError" class="home-state" role="alert"><AlertCircle :size="30" /><h2>{{ copy.loadFailed }}</h2><p>{{ copy.loadFailedDesc }}</p><button class="home-button primary" @click="load"><RotateCw :size="15" />{{ copy.retry }}</button></div>
      <div v-else-if="projects.length === 0" class="home-state"><FolderPlus :size="32" /><h2>{{ t('noProjects') }}</h2><p>{{ copy.emptyDesc }}</p><button class="home-button primary" @click="dialogVisible = true"><Plus :size="16" />{{ t('createFirstProject') }}</button></div>
      <div v-else-if="filteredProjects.length === 0" class="home-state"><Search :size="30" /><h2>{{ t('noMatches') }}</h2><p>{{ searchQuery }}</p><button class="home-button" @click="searchQuery = ''">{{ t('clearFilter') }}</button></div>

      <div v-else class="project-collection" :class="viewMode">
        <article v-for="p in filteredProjects" :key="p.id" class="project-card">
          <RouterLink :to="`/projects/${p.id}`" class="project-cover" :aria-label="`${t('openProject')} ${p.name}`">
            <img v-if="p.cover && !p.imageUnavailable" :src="getFileUrl(p.cover)" alt="" loading="lazy" @error="p.imageUnavailable = true" />
            <div v-else class="cover-placeholder"><Layers :size="28" /><span>{{ p.imageUnavailable || p.statsState === 'error' ? copy.previewUnavailable : p.statsState === 'loading' ? copy.loadingPreview : copy.noPreview }}</span></div>
          </RouterLink>
          <div class="project-content">
            <div class="project-title-row">
              <h2><RouterLink :to="`/projects/${p.id}`" :title="p.name">{{ p.name }}</RouterLink></h2>
              <el-dropdown trigger="click" @command="handleProjectCommand($event, p)">
                <button class="more-button" :aria-label="`${copy.more} ${p.name}`" :title="copy.more"><MoreHorizontal :size="19" /></button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item command="edit"><Pencil :size="14" class="menu-icon" />{{ t('editProjectInfo') }}</el-dropdown-item><el-dropdown-item command="delete" divided><Trash2 :size="14" class="menu-icon" />{{ t('delete') }}</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
            <p class="project-description" :class="{ muted: !p.description }" :title="p.description">{{ p.description || copy.noDescription }}</p>
            <div class="project-meta" aria-live="polite">
              <span v-if="p.statsState === 'loaded'" class="board-count"><Layers :size="14" />{{ p.pageCount }} {{ copy.boards }}<span v-if="p.analyzedCount" class="analyzed-count">· {{ p.analyzedCount }} {{ copy.analyzed }}</span></span>
              <span v-else-if="p.statsState === 'loading'">{{ copy.loadingStats }}</span>
              <span v-else class="stats-error">{{ copy.unknownStats }}<button :aria-label="`${copy.retry} ${p.name}`" @click="loadStats(p)">{{ copy.retry }}</button></span>
              <time v-if="validDate(p.createdAt)" :datetime="p.createdAt">{{ copy.created }} {{ formatDate(p.createdAt) }}</time>
              <span v-else>{{ copy.unknownDate }}</span>
            </div>
            <div class="project-footer">
              <RouterLink :to="`/projects/${p.id}`" class="details-link">{{ copy.details }}<ChevronRight :size="14" /></RouterLink>
              <RouterLink v-if="p.statsState === 'loaded' && p.pageCount" :to="`/projects/${p.id}/prototype`" class="edit-link">{{ copy.edit }}<ArrowUpRight :size="15" /></RouterLink>
              <RouterLink v-else :to="`/projects/${p.id}`" class="edit-link">{{ copy.open }}<ArrowUpRight :size="15" /></RouterLink>
            </div>
          </div>
        </article>
      </div>
    </main>

    <ProjectMetadataDialog v-model="metadataVisible" :project="editingProject" @saved="updateProjectMetadata" />
    <el-dialog v-model="dialogVisible" :title="t('newProject')" width="min(460px, calc(100vw - 32px))" align-center>
      <div class="project-form"><label><div>{{ t('projectName') }} <span>*</span></div><el-input v-model="form.name" :placeholder="t('projectNamePlaceholder')" size="large" clearable /></label><label>{{ t('projectDesc') }}<el-input v-model="form.description" type="textarea" :rows="3" :placeholder="t('projectDescPlaceholder')" /></label></div>
      <template #footer><div class="dialog-actions"><button class="home-button" @click="dialogVisible = false">{{ t('cancel') }}</button><button class="home-button primary" :disabled="creating" @click="create"><span v-if="creating" class="loading-spinner small"></span>{{ creating ? t('creating') : t('create') }}</button></div></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, FolderPlus, LayoutGrid, List, Trash2, Search, ChevronRight, Layers, MoreHorizontal, ArrowUpRight, AlertCircle, RotateCw, Pencil } from 'lucide-vue-next'
import NavbarControls from '../components/NavbarControls.vue'
import ProjectMetadataDialog from '../components/ProjectMetadataDialog.vue'
import { t, currentLang } from '../utils/i18n'
import { projectApi } from '../api/project'
import { getFileUrl } from '../api/http'
import type { Project } from '../types'

interface ProjectItem extends Project {
  pageCount?: number
  analyzedCount?: number
  cover?: string
  imageUnavailable?: boolean
  statsState: 'loading' | 'loaded' | 'error'
}
const words = {
  zh: { workspace: '工作空间', myProjects: '我的项目', intro: '从设计稿到交互原型，继续你的设计工作。', sort: '按创建时间排序', view: '项目显示方式', loading: '正在加载项目', loadFailed: '项目暂时无法加载', loadFailedDesc: '请检查连接后重试，现有项目不会受到影响。', retry: '重试', emptyDesc: '创建一个项目，导入设计稿并开始构建原型。', previewUnavailable: '封面暂时无法显示', loadingPreview: '正在加载封面', noPreview: '暂无画板封面', more: '更多项目操作', noDescription: '未填写项目说明', boards: '画板', analyzed: '已识别', loadingStats: '正在加载画板信息', unknownStats: '画板信息暂不可用', created: '创建于', unknownDate: '创建日期未知', details: '项目详情', edit: '编辑原型', open: '打开项目', required: '请输入项目名称', createFailed: '创建失败，请重试', deleteFailed: '删除失败，请重试', deleteConfirm: '确定删除项目「{name}」吗？其下所有画板、元素、标注将一并删除，且不可恢复。', deleteTitle: '删除项目', deleteAction: '确定删除' },
  en: { workspace: 'WORKSPACE', myProjects: 'My projects', intro: 'Continue your design work, from reference to interactive prototype.', sort: 'Newest created first', view: 'Project view', loading: 'Loading projects', loadFailed: 'Projects could not be loaded', loadFailedDesc: 'Check your connection and try again. Your existing projects are safe.', retry: 'Retry', emptyDesc: 'Create a project, import your designs and start building a prototype.', previewUnavailable: 'Cover unavailable', loadingPreview: 'Loading cover', noPreview: 'No artboard cover', more: 'More project actions', noDescription: 'No project description', boards: 'artboards', analyzed: 'analyzed', loadingStats: 'Loading artboard details', unknownStats: 'Artboard details unavailable', created: 'Created', unknownDate: 'Creation date unknown', details: 'Project details', edit: 'Edit prototype', open: 'Open project', required: 'Enter a project name', createFailed: 'Could not create the project. Try again.', deleteFailed: 'Could not delete the project. Try again.', deleteConfirm: 'Delete project “{name}”? All its artboards, elements and annotations will be permanently deleted.', deleteTitle: 'Delete project', deleteAction: 'Delete permanently' },
  ja: { workspace: 'ワークスペース', myProjects: 'マイプロジェクト', intro: 'デザインからインタラクティブなプロトタイプへ。作業を続けましょう。', sort: '作成日時の新しい順', view: 'プロジェクト表示', loading: 'プロジェクトを読み込み中', loadFailed: 'プロジェクトを読み込めません', loadFailedDesc: '接続を確認して再試行してください。既存のプロジェクトには影響しません。', retry: '再試行', emptyDesc: 'プロジェクトを作成し、デザインを取り込んでプロトタイプを構築しましょう。', previewUnavailable: 'カバーを表示できません', loadingPreview: 'カバーを読み込み中', noPreview: 'アートボードのカバーなし', more: 'プロジェクトのその他の操作', noDescription: 'プロジェクトの説明なし', boards: 'アートボード', analyzed: '解析済み', loadingStats: 'アートボード情報を読み込み中', unknownStats: 'アートボード情報を取得できません', created: '作成日', unknownDate: '作成日不明', details: 'プロジェクト詳細', edit: 'プロトタイプを編集', open: 'プロジェクトを開く', required: 'プロジェクト名を入力してください', createFailed: '作成できませんでした。再試行してください。', deleteFailed: '削除できませんでした。再試行してください。', deleteConfirm: 'プロジェクト「{name}」を削除しますか？すべてのアートボード、要素、注釈が削除され、復元できません。', deleteTitle: 'プロジェクトを削除', deleteAction: '完全に削除' },
}
const copy = computed(() => words[currentLang.value])
const router = useRouter()
const projects = ref<ProjectItem[]>([])
const loading = ref(true)
const loadError = ref(false)
const dialogVisible = ref(false)
const metadataVisible = ref(false)
const editingProject = ref<ProjectItem | null>(null)
const creating = ref(false)
const form = reactive({ name: '', description: '' })
const searchQuery = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
let loadGeneration = 0
const filteredProjects = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  return [...projects.value].filter(p => !q || p.name.toLowerCase().includes(q) || p.description?.toLowerCase().includes(q)).sort((a, b) => (dateValue(b.createdAt) - dateValue(a.createdAt)) || b.id - a.id)
})
function dateValue(value?: string) { const time = value ? Date.parse(value) : NaN; return Number.isFinite(time) ? time : 0 }
function validDate(value?: string) { return !!value && Number.isFinite(Date.parse(value)) }
function formatDate(value?: string) { return new Date(value!).toLocaleDateString({ zh: 'zh-CN', en: 'en-US', ja: 'ja-JP' }[currentLang.value], { year: 'numeric', month: 'short', day: 'numeric' }) }

async function loadStats(p: ProjectItem, generation = loadGeneration) {
  p.statsState = 'loading'
  try {
    const proto = await projectApi.prototype(p.id)
    if (generation !== loadGeneration || !projects.value.includes(p)) return
    if (!proto || !Array.isArray(proto.pages)) throw new Error('Invalid prototype response')
    p.pageCount = proto.pages.length
    p.analyzedCount = proto.pages.filter(page => page.analyzed === 1).length
    p.cover = p.coverImage || proto.pages.find(page => page.background_image)?.background_image
    p.imageUnavailable = false
    p.statsState = 'loaded'
  } catch {
    if (generation === loadGeneration && projects.value.includes(p)) p.statsState = 'error'
  }
}
async function load() {
  const generation = ++loadGeneration
  loading.value = true
  loadError.value = false
  try {
    const list = await projectApi.list()
    if (generation !== loadGeneration) return
    if (!Array.isArray(list)) throw new Error('Invalid project list')
    projects.value = list.map(p => ({ ...p, cover: p.coverImage, statsState: 'loading' }))
    loading.value = false
    // Covers and statistics load independently; one failure never hides the other projects.
    await Promise.allSettled(projects.value.map(p => loadStats(p, generation)))
  } catch {
    if (generation === loadGeneration) loadError.value = true
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
async function create() {
  if (creating.value) return
  if (!form.name.trim()) { ElMessage.warning(copy.value.required); return }
  creating.value = true
  try {
    const project = await projectApi.create(form.name, form.description)
    ElMessage.success(t('projectCreated'))
    dialogVisible.value = false
    router.push(`/projects/${project.id}`)
  } catch { ElMessage.error(copy.value.createFailed) }
  finally { creating.value = false }
}
function handleProjectCommand(command: string, p: ProjectItem) {
  if (command === 'edit') { editingProject.value = p; metadataVisible.value = true }
  else if (command === 'delete') void remove(p)
}
function updateProjectMetadata(saved: Project) {
  const p = projects.value.find(p => p.id === saved.id)
  if (p) { p.name = saved.name; p.description = saved.description }
}
async function remove(p: Project) {
  try {
    await ElMessageBox.confirm(copy.value.deleteConfirm.replace('{name}', p.name), copy.value.deleteTitle, { confirmButtonText: copy.value.deleteAction, cancelButtonText: t('cancel'), type: 'warning', confirmButtonClass: 'el-button--danger' })
  } catch { return }
  try {
    await projectApi.remove(p.id)
    ElMessage.success(t('projectDeleted'))
    await load()
  } catch { ElMessage.error(copy.value.deleteFailed) }
}
onMounted(load)
onUnmounted(() => { loadGeneration++ })
</script>

<style scoped>
.project-home { --home-bg: #f7f8fa; --home-surface: #fff; --home-text: #17202e; --home-muted: #667085; --home-line: #e5e8ed; --home-soft: #f0f2f5; --home-blue: #086bd6; min-height: 100vh; background: var(--home-bg); color: var(--home-text); font-size: 14px; }
:global(.dark .project-home) { --home-bg: #191b1f; --home-surface: #23262b; --home-text: #eef0f4; --home-muted: #a4adba; --home-line: #363c44; --home-soft: #2c3138; --home-blue: #68afff; }
.home-header { background: var(--home-surface); border-bottom: 1px solid var(--home-line); }
.home-header-inner { width: 100%; padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.brand, .header-actions { display: flex; align-items: center; gap: 14px; }
.brand { color: var(--home-text); font-size: 16px; font-weight: 650; gap: 9px; text-decoration: none; }.brand img { width: 28px; height: 28px; }
.home-main { max-width: 1280px; padding: 48px 32px 64px; margin: auto; }
.library-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 32px; }
.eyebrow { color: var(--home-muted); font-size: 12px; margin: 0 0 8px; letter-spacing: .06em; }
h1 { font-size: 28px; font-weight: 650; letter-spacing: -.025em; margin: 0; line-height: 1.35; }.heading-description { color: var(--home-muted); margin: 10px 0 0; line-height: 1.6; }.project-count { color: var(--home-muted); white-space: nowrap; }
.library-toolbar, .toolbar-right { display: flex; align-items: center; gap: 16px; }.library-toolbar { justify-content: space-between; margin-bottom: 24px; }.sort-label { color: var(--home-muted); font-size: 12px; }
.project-search { display: flex; align-items: center; gap: 9px; width: 300px; max-width: 100%; border: 1px solid var(--home-line); border-radius: 6px; background: var(--home-surface); padding: 9px 12px; color: var(--home-muted); }.project-search:focus-within { border-color: var(--home-blue); }.project-search input { min-width: 0; width: 100%; background: transparent; color: var(--home-text); outline: none; font-size: 14px; }.project-search input::placeholder { color: var(--home-muted); }
.view-switch { display: flex; padding: 3px; border: 1px solid var(--home-line); border-radius: 6px; background: var(--home-soft); }.view-switch button { padding: 6px; border-radius: 4px; color: var(--home-muted); cursor: pointer; }.view-switch .active { background: var(--home-surface); color: var(--home-text); }
.home-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 9px 14px; border: 1px solid var(--home-line, #e5e8ed); border-radius: 6px; background: var(--home-surface, var(--el-bg-color, #fff)); color: var(--home-text, var(--el-text-color-primary, #17202e)); font-size: 14px; font-weight: 550; cursor: pointer; }.home-button:hover { background: var(--home-soft); }.home-button.primary { background: #096dd9; color: #fff; border-color: #096dd9; }.home-button.primary:hover { background: #075cb7; }.home-button:disabled { opacity: .6; cursor: wait; }
.project-collection.grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }.project-card { min-width: 0; border: 1px solid var(--home-line); border-radius: 10px; background: var(--home-surface); transition: border-color .15s; }.project-card:hover { border-color: #a8b8cb; }
.project-cover { display: flex; align-items: center; justify-content: center; aspect-ratio: 16/10; background: var(--home-soft); border-bottom: 1px solid var(--home-line); border-radius: 9px 9px 0 0; overflow: hidden; padding: 16px; text-decoration: none; }.project-cover img { max-width: 100%; width: 100%; height: 100%; object-fit: contain; border-radius: 2px; }.cover-placeholder { display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--home-muted); font-size: 12px; }
.project-content { padding: 18px 20px 0; }.project-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.project-title-row h2 { min-width: 0; font-size: 16px; font-weight: 600; margin: 0; }.project-title-row h2 a { color: var(--home-text); text-decoration: none; display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.project-title-row h2 a:hover { color: var(--home-blue); }.more-button { display: flex; padding: 5px; border-radius: 5px; color: var(--home-muted); cursor: pointer; }.more-button:hover { background: var(--home-soft); }.menu-icon { margin-right: 8px; }
.project-description { font-size: 14px; color: var(--home-muted); line-height: 1.6; height: 45px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin: 10px 0 16px; }.project-description.muted { opacity: .85; }
.project-meta { display: flex; flex-direction: column; gap: 8px; font-size: 12px; color: var(--home-muted); min-height: 46px; }.board-count { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }.analyzed-count { color: var(--home-muted); }.stats-error { display: flex; gap: 10px; align-items: center; }.stats-error button { color: var(--home-blue); text-decoration: underline; cursor: pointer; }.project-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid var(--home-line); padding: 14px 0; margin-top: 16px; }.project-footer a { display: inline-flex; align-items: center; gap: 5px; text-decoration: none; font-size: 13px; }.details-link { color: var(--home-muted); }.edit-link { color: var(--home-blue); font-weight: 550; }.project-footer a:hover { text-decoration: underline; }
.grid .project-card { display: flex; flex-direction: column; }
.grid .project-cover { height: 176px; aspect-ratio: auto; padding: 12px; flex-shrink: 0; }
.grid .project-content { display: flex; flex-direction: column; flex: 1; padding: 14px 16px 0; }
.grid .project-description { height: auto; min-height: 21px; max-height: 42px; font-size: 13px; margin: 6px 0 12px; }
.grid .project-meta { gap: 6px; min-height: 40px; margin-top: auto; }
.grid .project-footer { padding: 11px 0; margin-top: 12px; }
.project-collection.list { display: flex; flex-direction: column; gap: 12px; }.list .project-card { display: flex; min-height: 180px; }.list .project-cover { width: 180px; flex-shrink: 0; aspect-ratio: auto; border-radius: 9px 0 0 9px; border-bottom: none; border-right: 1px solid var(--home-line); }.list .project-cover img { height: 140px; max-height: 140px; }.list .project-content { flex: 1; min-width: 0; }.list .project-description { height: auto; max-height: 45px; margin-bottom: 10px; }.list .project-meta { flex-direction: row; flex-wrap: wrap; gap: 12px; min-height: auto; }.list .project-footer { margin-top: 12px; }
.home-state { padding: 76px 24px; display: flex; align-items: center; flex-direction: column; text-align: center; color: var(--home-muted); }.home-state h2 { color: var(--home-text); font-size: 18px; font-weight: 600; margin: 20px 0 8px; }.home-state p { line-height: 1.7; max-width: 420px; margin: 0 0 24px; }.loading-spinner { display: inline-block; width: 26px; height: 26px; border-radius: 50%; border: 2px solid var(--home-line); border-top-color: var(--home-blue); animation: home-spin .8s linear infinite; }.loading-spinner.small { width: 14px; height: 14px; border-color: #ffffff66; border-top-color: #fff; }@keyframes home-spin { to { transform: rotate(360deg); } }
.project-form { display: grid; gap: 20px; padding: 8px 0; }.project-form label { display: grid; gap: 8px; font-size: 14px; }.project-form label span { color: #e44; }.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--home-blue); outline-offset: 3px; }
@media (max-width: 1100px) { .project-collection.grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 820px) { .project-collection.grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .home-header-inner { padding: 14px 16px; flex-wrap: wrap; }.header-actions { gap: 8px; }.home-main { padding: 28px 16px 40px; }.library-heading { align-items: flex-start; margin-bottom: 24px; }h1 { font-size: 25px; }.project-count { font-size: 12px; margin-top: 34px; }.heading-description { font-size: 13px; }.project-search { flex: 1; width: auto; }.sort-label { display: none; }.library-toolbar { gap: 10px; }.project-collection.grid { grid-template-columns: minmax(0, 1fr); gap: 18px; }.list .project-cover { width: 94px; padding: 8px; }.list .project-cover img { height: 110px; max-height: 110px; }.list .cover-placeholder span { display: none; }.list .project-content { padding: 14px 12px 0; }.list .project-footer { gap: 4px; }.list .project-footer a { font-size: 12px; }.home-button { padding: 8px 11px; } }
@media (prefers-reduced-motion: reduce) { .project-card { transition: none; } }
</style>
