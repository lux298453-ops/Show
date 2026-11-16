<template>
  <el-dialog
    v-model="visible"
    title="项目成员"
    width="min(500px, calc(100vw - 32px))"
    :show-close="false"
    append-to-body
    destroy-on-close
    class="project-member-dialog"
  >
    <div class="member-panel">
      <header class="member-header">
        <div class="member-title">
          <h2>项目成员</h2>
          <span class="member-count">{{ members.length }} 人</span>
        </div>
        <button type="button" class="member-icon-button" aria-label="关闭成员弹窗" @click="close">
          <X :size="16" aria-hidden="true" />
        </button>
      </header>

      <form v-if="inviteVisible" :id="inviteFormId" class="member-invite" @submit.prevent="handleAddMember">
        <div class="member-invite-fields">
          <input
            ref="inviteInput"
            v-model="newOpenId"
            type="text"
            aria-label="飞书编号"
            placeholder="粘贴飞书编号"
            autocomplete="off"
            class="member-input"
            :disabled="addingMember"
          />
          <el-select v-model="newMemberRole" aria-label="新成员权限" class="member-invite-role" :disabled="addingMember">
            <el-option v-for="option in roleOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <button type="submit" class="member-primary-button" :disabled="addingMember || !newOpenId.trim()">
            {{ addingMember ? '添加中…' : '添加' }}
          </button>
        </div>
        <div v-if="inputHint || lookup" class="member-lookup" aria-live="polite">
          <span v-if="inputHint" class="member-warning">{{ inputHint }}</span>
          <span v-else-if="lookup?.state === 'loading'">正在查找成员…</span>
          <template v-else-if="lookup?.state === 'found'">
            <img v-if="lookup.avatar" :src="lookup.avatar" alt="" class="member-lookup-avatar" />
            <span class="member-lookup-name">{{ lookup.name }}</span>
            <span>{{ lookupIsMember ? '已在项目中，添加将更新权限' : '确认后点击添加' }}</span>
          </template>
          <span v-else-if="lookup?.state === 'missing'">暂未查到姓名，可先添加；对方登录后显示。</span>
          <span v-else-if="lookup?.state === 'error'" class="member-warning">查询失败，可以直接添加。</span>
        </div>
      </form>

      <div v-if="members.length > 3 || searchQuery" class="member-search">
        <div class="member-search-field">
          <Search :size="14" aria-hidden="true" />
          <input v-model="searchQuery" type="search" aria-label="搜索成员姓名或飞书编号" placeholder="搜索成员" class="member-input" />
        </div>
        <button type="button" class="member-icon-button" aria-label="刷新成员列表" :disabled="loading" @click="loadMembers">
          <RefreshCw :size="15" :class="{ 'animate-spin': loading }" aria-hidden="true" />
        </button>
      </div>

      <div class="member-list" :aria-busy="loading">
        <div v-if="loading" class="member-empty" role="status">正在加载成员…</div>
        <template v-else-if="filteredMembers.length">
          <div v-for="member in filteredMembers" :key="member.userId" class="member-row">
            <img v-if="member.userAvatar" :src="member.userAvatar" alt="" class="member-avatar" />
            <div v-else class="member-avatar member-avatar-fallback" :class="{ 'is-self': isMe(member.userId) }" aria-hidden="true">
              {{ hasName(member) ? member.userName.slice(0, 1).toUpperCase() : '?' }}
            </div>
            <div class="member-identity">
              <div class="member-name-line">
                <span class="member-name" :title="hasName(member) ? member.userName : '对方登录后显示姓名'">{{ memberName(member) }}</span>
                <span v-if="isMe(member.userId)" class="member-you">你</span>
              </div>
              <div v-if="expandedMemberIds.has(member.userId)" class="member-id-line">
                <span class="member-id" :title="member.userId">{{ member.userId }}</span>
                <button type="button" class="member-icon-button member-copy" :aria-label="`复制${memberName(member)}的飞书编号`" @click="copyOpenId(member.userId)">
                  <Copy :size="13" aria-hidden="true" />
                </button>
              </div>
            </div>
            <el-dropdown
              trigger="click"
              placement="bottom-end"
              popper-class="wf-member-role-menu"
              :disabled="!!savingUserId || addingMember"
              @command="(command: string) => handleMemberCommand(member, command)"
            >
              <button
                type="button"
                class="member-role-button"
                :aria-label="`${memberName(member)}，${roleLabel(member.role)}，打开成员操作`"
                aria-haspopup="menu"
                :disabled="!!savingUserId || addingMember"
              >
                {{ savingUserId === member.userId ? '保存中…' : roleLabel(member.role) }}
                <ChevronDown :size="13" aria-hidden="true" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="option in roleOptions"
                    :key="option.value"
                    :command="option.value"
                    :disabled="isMe(member.userId) && option.value !== 'admin'"
                    :class="{ 'is-current-role': member.role === option.value }"
                  >
                    <span class="member-role-description"><span>{{ option.label }}</span><small>{{ option.description }}</small></span>
                    <Check v-if="member.role === option.value" :size="14" aria-hidden="true" />
                  </el-dropdown-item>
                  <el-dropdown-item command="details" divided>{{ expandedMemberIds.has(member.userId) ? '收起飞书编号' : '查看飞书编号' }}</el-dropdown-item>
                  <el-dropdown-item v-if="!isMe(member.userId)" command="remove" class="member-remove">移除成员</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
        <div v-else class="member-empty">{{ searchQuery ? '暂无匹配的成员' : '暂无项目成员' }}</div>
      </div>

      <footer class="member-footer">
        <button
          type="button"
          class="member-add-button"
          :aria-expanded="inviteVisible"
          :aria-controls="inviteFormId"
          :disabled="addingMember"
          @click="toggleInvite"
        >
          <Plus :size="15" aria-hidden="true" />
          {{ inviteVisible ? '收起添加' : '添加成员' }}
        </button>
        <button type="button" class="member-primary-button member-done" @click="close">完成</button>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount, useId } from 'vue'
import { Search, RefreshCw, X, Copy, Plus, ChevronDown, Check } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  currentUser,
  fetchProjectMembers,
  updateProjectMemberRole,
  addProjectMember,
  removeProjectMember,
  lookupFeishuUser,
  roleLabel,
  type ProjectMemberData,
  type UserRole,
} from '../services/auth'
import { recordAuditLog } from '../services/auditLog'

const props = defineProps<{ modelValue: boolean; projectId: number }>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'memberUpdated'): void
}>()

const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const loading = ref(false)
const savingUserId = ref<string | null>(null)
const searchQuery = ref('')
const members = ref<ProjectMemberData[]>([])
const expandedMemberIds = ref(new Set<string>())
const inviteVisible = ref(false)
const inviteInput = ref<HTMLInputElement | null>(null)
const inviteFormId = `member-invite-${useId()}`
const newOpenId = ref('')
const newMemberRole = ref<UserRole>('editor')
const addingMember = ref(false)
const roleOptions: { value: UserRole; label: string; description: string }[] = [
  { value: 'admin', label: '管理员', description: '管理成员、编辑与评审' },
  { value: 'editor', label: '编辑者', description: '编辑画板与交互' },
  { value: 'viewer', label: '只读评审', description: '查看与评论' },
]

const FEISHU_ID = /^ou_[A-Za-z0-9]+$/
type LookupState = { userId: string; state: 'loading' | 'found' | 'missing' | 'error'; name?: string; avatar?: string }
const lookup = ref<LookupState | null>(null)
let lookupTimer: ReturnType<typeof setTimeout> | undefined
let lookupSequence = 0
let memberLoadSequence = 0

const inputHint = computed(() => {
  const value = newOpenId.value.trim()
  return value && !value.startsWith('ou_') ? '飞书编号以 ou_ 开头，可让对方从头像菜单复制。' : ''
})
const lookupIsMember = computed(() => !!lookup.value && members.value.some(member => member.userId === lookup.value!.userId))

function cancelLookup() {
  clearTimeout(lookupTimer)
  lookupSequence++
  lookup.value = null
}

watch(newOpenId, value => {
  cancelLookup()
  const userId = value.trim()
  if (!inviteVisible.value || !props.modelValue || !FEISHU_ID.test(userId)) return
  const sequence = lookupSequence
  const projectId = props.projectId
  lookup.value = { userId, state: 'loading' }
  lookupTimer = setTimeout(async () => {
    try {
      const result = await lookupFeishuUser(projectId, userId)
      if (sequence !== lookupSequence) return
      lookup.value = result.found && result.userName
        ? { userId, state: 'found', name: result.userName, avatar: result.userAvatar || '' }
        : { userId, state: 'missing' }
    } catch {
      if (sequence === lookupSequence) lookup.value = { userId, state: 'error' }
    }
  }, 300)
})

async function toggleInvite() {
  inviteVisible.value = !inviteVisible.value
  if (inviteVisible.value) {
    await nextTick()
    inviteInput.value?.focus()
  } else {
    newOpenId.value = ''
    cancelLookup()
  }
}

function hasName(member: ProjectMemberData): boolean {
  return !!member.userName && member.userName !== member.userId
}

function memberName(member: ProjectMemberData): string {
  return hasName(member) ? member.userName : '名字待确认'
}

async function handleAddMember() {
  if (addingMember.value) return
  const openId = newOpenId.value.trim()
  if (!FEISHU_ID.test(openId)) {
    ElMessage.warning('请粘贴 ou_ 开头的飞书编号，对方可在头像菜单里复制。')
    return
  }
  const existing = members.value.find(member => member.userId === openId)
  const role = newMemberRole.value
  if (isMe(openId) && role !== 'admin') {
    ElMessage.warning('不能取消您自己的管理员权限')
    return
  }
  const projectId = props.projectId
  const knownName = lookup.value?.userId === openId && lookup.value.state === 'found' ? lookup.value.name : undefined
  const name = knownName || (existing && hasName(existing) ? existing.userName : '')
  addingMember.value = true
  try {
    const saved = await addProjectMember(projectId, openId, name || openId, role)
    const savedName = saved && saved.userName !== openId ? saved.userName : name
    const roleCn = roleLabel(role)
    ElMessage.success(existing
      ? `已将「${savedName || openId}」的权限设为「${roleCn}」`
      : savedName ? `已添加「${savedName}」，权限：${roleCn}` : `已添加成员，权限：${roleCn}。对方登录后显示姓名。`)
    recordAuditLog({ projectId, actionType: 'member_change', changeType: 'add', actionLabel: '添加成员',
      pageName: savedName || openId, details: `将成员「${savedName || openId}」添加到项目，赋予「${roleCn}」权限` })
    if (projectId === props.projectId) {
      newOpenId.value = ''
      cancelLookup()
      inviteVisible.value = false
      searchQuery.value = ''
      await loadMembers()
      emit('memberUpdated')
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '添加成员失败')
  } finally { addingMember.value = false }
}

async function handleMemberCommand(member: ProjectMemberData, command: string) {
  if (savingUserId.value || addingMember.value) return
  if (command === 'details') {
    if (expandedMemberIds.value.has(member.userId)) expandedMemberIds.value.delete(member.userId)
    else expandedMemberIds.value.add(member.userId)
  } else if (command === 'remove') {
    await handleRemoveMember(member)
  } else if (command === 'admin' || command === 'editor' || command === 'viewer') {
    await handleRoleChange(member, command)
  }
}

async function handleRemoveMember(member: ProjectMemberData) {
  if (isMe(member.userId)) { ElMessage.warning('不能移除您自己的管理员账号'); return }
  const projectId = props.projectId
  try {
    await ElMessageBox.confirm(`确定要将成员「${memberName(member)}」从当前项目中移除吗？`, '确认移除成员', {
      confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning',
    })
    if (projectId !== props.projectId || !props.modelValue) return
    savingUserId.value = member.userId
    await removeProjectMember(projectId, member.userId)
    ElMessage.success(`已移除成员：${memberName(member)}`)
    recordAuditLog({ projectId, actionType: 'member_change', changeType: 'delete', actionLabel: '移除成员',
      pageName: memberName(member), details: `将成员「${memberName(member)}」从当前项目中移除` })
    if (projectId === props.projectId) {
      expandedMemberIds.value.delete(member.userId)
      await loadMembers()
      emit('memberUpdated')
    }
  } catch (err: unknown) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err instanceof Error ? err.message : '移除成员失败')
  } finally { savingUserId.value = null }
}

const filteredMembers = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return members.value
  return members.value.filter(member => (member.userName || '').toLowerCase().includes(query)
    || member.userId.toLowerCase().includes(query) || roleLabel(member.role).includes(query) || member.role.includes(query))
})

function isMe(userId: string): boolean {
  return !!currentUser.value && (currentUser.value.id === userId || currentUser.value.feishuOpenId === userId)
}

function close() { visible.value = false }

async function loadMembers() {
  if (!props.projectId || !props.modelValue) return
  const sequence = ++memberLoadSequence
  loading.value = true
  try {
    const result = await fetchProjectMembers(props.projectId)
    if (sequence === memberLoadSequence) members.value = result
  } catch (err: unknown) {
    if (sequence === memberLoadSequence) ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
  } finally {
    if (sequence === memberLoadSequence) loading.value = false
  }
}

async function handleRoleChange(member: ProjectMemberData, newRole: UserRole) {
  if (member.role === newRole) return
  if (isMe(member.userId) && newRole !== 'admin') { ElMessage.warning('不能取消您自己的管理员权限'); return }
  const projectId = props.projectId
  savingUserId.value = member.userId
  try {
    await updateProjectMemberRole(projectId, member.userId, newRole)
    member.role = newRole
    const roleCn = roleLabel(newRole)
    ElMessage.success(`已将「${memberName(member)}」权限设为：${roleCn}`)
    recordAuditLog({ projectId, actionType: 'member_change', changeType: 'edit', actionLabel: '权限变更',
      pageName: memberName(member), details: `将成员「${memberName(member)}」权限调整为「${roleCn}」` })
    if (projectId === props.projectId) emit('memberUpdated')
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '修改成员角色失败')
  } finally { savingUserId.value = null }
}

async function copyOpenId(id: string) {
  try {
    if (!navigator.clipboard?.writeText) { ElMessage.info(`飞书编号：${id}`); return }
    await navigator.clipboard.writeText(id)
    ElMessage.success('飞书编号已复制')
  } catch { ElMessage.error('复制失败，请手动复制编号') }
}

watch(() => [props.modelValue, props.projectId] as const, ([open]) => {
  memberLoadSequence++
  cancelLookup()
  inviteVisible.value = false
  newOpenId.value = ''
  newMemberRole.value = 'editor'
  expandedMemberIds.value = new Set()
  searchQuery.value = ''
  members.value = []
  if (open) loadMembers()
}, { immediate: true })

onBeforeUnmount(() => { memberLoadSequence++; cancelLookup() })
</script>

<style>
.project-member-dialog.el-dialog {
  --member-surface: var(--wf-surface, #ffffff);
  --member-soft: var(--wf-bg, #f6f8fb);
  --member-line: var(--wf-border, #e2e8f0);
  --member-text: var(--wf-text-main, #1e293b);
  --member-muted: var(--wf-text-muted, #64748b);
  padding: 0;
  border: 1px solid var(--member-line);
  border-radius: 14px !important;
  background: var(--member-surface);
  color: var(--member-text);
  box-shadow: 0 12px 44px rgb(15 23 42 / 9%) !important;
}
.project-member-dialog .el-dialog__header { display: none; }
.project-member-dialog .el-dialog__body { padding: 0 !important; }
.project-member-dialog .member-panel { color: var(--member-text); }
.project-member-dialog .member-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 24px 15px; }
.project-member-dialog .member-title { display: flex; align-items: baseline; gap: 9px; min-width: 0; }
.project-member-dialog .member-title h2 { margin: 0; font-size: 16px; line-height: 1.5; font-weight: 600; letter-spacing: -.2px; }
.project-member-dialog .member-count { font-size: 13px; color: var(--member-muted); }
.project-member-dialog button, .project-member-dialog input { font-family: inherit; }
.project-member-dialog button { cursor: pointer; }
.project-member-dialog button:disabled { cursor: not-allowed; opacity: .5; }
.project-member-dialog button:focus-visible, .project-member-dialog .member-input:focus-visible { outline: 2px solid var(--wf-primary); outline-offset: 2px; }
.project-member-dialog .member-icon-button { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; padding: 6px; border: 0; border-radius: 6px; background: transparent; color: var(--member-muted); }
.project-member-dialog .member-icon-button:hover { background: var(--member-soft); color: var(--member-text); }
.project-member-dialog .member-invite { padding: 0 24px 16px; }
.project-member-dialog .member-invite-fields { display: grid; grid-template-columns: minmax(0, 1fr) 96px auto; gap: 8px; align-items: center; }
.project-member-dialog .member-input { min-width: 0; width: 100%; height: 34px; padding: 7px 10px; border: 1px solid var(--member-line); border-radius: 7px; background: var(--member-surface); color: var(--member-text); font-size: 12px; }
.project-member-dialog .member-input::placeholder { color: var(--member-muted); }
.project-member-dialog .member-input:disabled { opacity: .5; }
.project-member-dialog .member-invite-role { min-width: 0; }
.project-member-dialog .member-invite-role .el-select__wrapper { min-height: 34px; font-size: 12px; border-radius: 7px; }
.project-member-dialog .member-lookup { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 8px; color: var(--member-muted); font-size: 12px; line-height: 1.6; }
.project-member-dialog .member-lookup-name { color: var(--member-text); font-weight: 500; }
.project-member-dialog .member-lookup-avatar { width: 20px; height: 20px; object-fit: cover; border-radius: 50%; }
.project-member-dialog .member-warning { color: var(--el-color-warning-dark-2, #b88230); }
.project-member-dialog .member-search { display: flex; align-items: center; gap: 8px; padding: 0 24px 8px; }
.project-member-dialog .member-search-field { position: relative; min-width: 0; flex: 1; }
.project-member-dialog .member-search-field > svg { position: absolute; top: 10px; left: 10px; color: var(--member-muted); pointer-events: none; }
.project-member-dialog .member-search-field input { padding-left: 31px; }
.project-member-dialog .member-list { padding: 0 24px; max-height: 340px; overflow-y: auto; scrollbar-width: thin; scrollbar-color: var(--member-line) transparent; }
.project-member-dialog .member-row { display: flex; align-items: center; gap: 12px; padding: 16px 0; border-bottom: 1px solid var(--member-line); }
.project-member-dialog .member-row:last-child { border-bottom: 0; }
.project-member-dialog .member-avatar { width: 34px; height: 34px; flex: 0 0 34px; border-radius: 50%; object-fit: cover; }
.project-member-dialog .member-avatar-fallback { display: grid; place-items: center; background: var(--member-soft); color: var(--member-muted); font-size: 13px; font-weight: 500; }
.project-member-dialog .member-avatar-fallback.is-self { color: var(--wf-primary); background: var(--el-color-primary-light-9); }
.project-member-dialog .member-identity { flex: 1; min-width: 0; }
.project-member-dialog .member-name-line { display: flex; align-items: baseline; gap: 7px; }
.project-member-dialog .member-name { min-width: 0; font-size: 14px; line-height: 1.5; font-weight: 500; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.project-member-dialog .member-you { flex-shrink: 0; color: var(--member-muted); font-size: 12px; }
.project-member-dialog .member-id-line { display: flex; align-items: center; gap: 4px; margin-top: 3px; }
.project-member-dialog .member-id { min-width: 0; color: var(--member-muted); font-size: 11px; font-family: ui-monospace, monospace; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.project-member-dialog .member-copy { padding: 3px; }
.project-member-dialog .member-role-button { display: inline-flex; align-items: center; gap: 8px; padding: 6px 4px 6px 8px; border: 0; border-radius: 6px; background: transparent; color: var(--member-muted); font-size: 13px; line-height: 1.5; white-space: nowrap; }
.project-member-dialog .member-role-button:hover { background: var(--member-soft); color: var(--member-text); }
.project-member-dialog .member-empty { display: grid; min-height: 160px; align-items: center; justify-items: center; color: var(--member-muted); font-size: 13px; }
.project-member-dialog .member-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 24px 20px; }
.project-member-dialog .member-add-button { display: inline-flex; align-items: center; gap: 5px; padding: 6px 0; border: 0; background: transparent; color: var(--wf-primary); font-size: 13px; font-weight: 500; }
.project-member-dialog .member-add-button:hover { color: var(--wf-primary-hover); }
.project-member-dialog .member-primary-button { padding: 7px 14px; border: 0; border-radius: 7px; background: var(--wf-primary); color: #ffffff; font-size: 13px; line-height: 1.5; font-weight: 500; white-space: nowrap; }
.project-member-dialog .member-primary-button:hover { background: var(--wf-primary-hover); }
.project-member-dialog .member-done { margin-left: auto; padding-inline: 19px; }
.wf-member-role-menu.el-dropdown__popper { border-radius: 10px; overflow: hidden; }
.wf-member-role-menu .el-dropdown-menu { padding: 5px; }
.wf-member-role-menu .el-dropdown-menu__item { display: flex; justify-content: space-between; gap: 16px; min-width: 208px; padding: 8px 10px; border-radius: 6px; font-size: 13px; line-height: 1.5; }
.wf-member-role-menu .member-role-description { display: grid; gap: 1px; }
.wf-member-role-menu .member-role-description small { font-size: 11px; color: var(--el-text-color-secondary); }
.wf-member-role-menu .is-current-role { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.wf-member-role-menu .el-dropdown-menu__item.member-remove { color: var(--el-color-danger); }
.wf-member-role-menu .el-dropdown-menu__item.member-remove:not(.is-disabled):hover { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.dark .project-member-dialog.el-dialog { box-shadow: 0 12px 44px rgb(0 0 0 / 26%) !important; }
@media (max-width: 420px) {
  .project-member-dialog .member-header { padding: 17px 17px 12px; }
  .project-member-dialog .member-list { padding-inline: 17px; }
  .project-member-dialog .member-invite { padding: 0 17px 14px; }
  .project-member-dialog .member-invite-fields { grid-template-columns: minmax(0, 1fr) 92px; }
  .project-member-dialog .member-invite-fields > input { grid-column: 1 / -1; }
  .project-member-dialog .member-invite-fields > button { justify-self: end; }
  .project-member-dialog .member-search { padding-inline: 17px; }
  .project-member-dialog .member-footer { padding: 14px 17px 17px; }
}
@media (pointer: coarse) {
  .project-member-dialog .member-icon-button, .project-member-dialog .member-role-button, .project-member-dialog .member-primary-button, .project-member-dialog .member-add-button { min-height: 44px; }
  .project-member-dialog .member-icon-button { min-width: 44px; }
  .project-member-dialog .member-input { min-height: 44px; font-size: 16px; }
  .project-member-dialog .member-invite-role .el-select__wrapper { min-height: 44px; font-size: 16px; }
  .wf-member-role-menu .el-dropdown-menu__item { min-height: 44px; }
}
</style>
