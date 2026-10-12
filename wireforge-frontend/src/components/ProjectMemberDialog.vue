<template>
  <el-dialog
    v-model="visible"
    title=""
    width="540px"
    :show-close="false"
    append-to-body
    destroy-on-close
    class="project-member-dialog"
  >
    <div class="relative overflow-hidden rounded-2xl bg-white dark:bg-[#202020] text-slate-800 dark:text-slate-100 select-none">
      <!-- Top Modal Header -->
      <div class="px-6 pt-5 pb-3.5 flex items-center justify-between border-b border-slate-100 dark:border-[#303030]">
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-sm font-bold text-slate-900 dark:text-white tracking-tight">项目成员与权限</h2>
            <span class="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 dark:bg-[#303030] text-slate-600 dark:text-slate-300">
              共 {{ members.length }} 人
            </span>
          </div>
          <p class="text-[11px] text-slate-400 dark:text-slate-400 mt-0.5">管理当前项目的协同人员及其编辑权限</p>
        </div>

        <button
          type="button"
          class="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-[#2a2a2a] rounded-lg transition-colors cursor-pointer"
          title="关闭窗口"
          @click="close"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Quick Add / Invite Member Bar (Streamlined & Clean) -->
      <div class="px-6 py-3.5 bg-slate-50/70 dark:bg-[#1a1a1a] border-b border-slate-100 dark:border-[#303030]">
        <div class="flex items-center gap-2">
          <!-- Member Input Field -->
          <div class="relative flex-1">
            <input
              v-model="newOpenId"
              type="text"
              placeholder="粘贴对方的飞书编号（ou_ 开头）"
              class="w-full px-3 py-1.5 text-xs bg-white dark:bg-[#252525] border border-slate-200 dark:border-[#3a3a3a] rounded-xl outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15 transition-all text-slate-800 dark:text-slate-100 placeholder:text-slate-400"
              @keydown.enter="handleAddMember"
            />
          </div>

          <!-- Role Dropdown -->
          <el-select v-model="newMemberRole" size="small" class="w-28 shrink-0">
            <el-option value="admin" label="管理员">
              <div class="flex items-center gap-1.5">
                <ShieldCheck class="w-3.5 h-3.5 text-amber-500" />
                <span class="text-xs font-semibold">管理员</span>
              </div>
            </el-option>
            <el-option value="editor" label="编辑者">
              <div class="flex items-center gap-1.5">
                <Edit3 class="w-3.5 h-3.5 text-blue-500" />
                <span class="text-xs">编辑者</span>
              </div>
            </el-option>
            <el-option value="viewer" label="只读评审">
              <div class="flex items-center gap-1.5">
                <Eye class="w-3.5 h-3.5 text-slate-400" />
                <span class="text-xs">只读评审</span>
              </div>
            </el-option>
          </el-select>

          <!-- Add Button -->
          <button
            type="button"
            class="px-3.5 py-1.5 text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white rounded-xl shadow-xs transition-colors cursor-pointer shrink-0 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1"
            :disabled="addingMember || !newOpenId.trim()"
            @click="handleAddMember"
          >
            <UserPlus class="w-3.5 h-3.5" />
            <span>{{ addingMember ? '添加中...' : '添加' }}</span>
          </button>
        </div>

        <!-- 粘贴编号后显示这个人是谁 -->
        <div v-if="inputHint || lookup" class="mt-2 min-h-[22px] flex items-center gap-2 text-xs">
          <span v-if="inputHint" class="text-amber-600 dark:text-amber-400">{{ inputHint }}</span>
          <span v-else-if="lookup?.state === 'loading'" class="text-slate-400">正在查找这个编号是谁…</span>
          <template v-else-if="lookup?.state === 'found'">
            <img v-if="lookup.avatar" :src="lookup.avatar" :alt="lookup.name" class="w-5 h-5 rounded-full object-cover border border-slate-200 dark:border-[#444]" />
            <div
              v-else
              class="w-5 h-5 rounded-full text-white text-[10px] font-bold flex items-center justify-center shrink-0"
              :class="getAvatarBg(lookup.name || '')"
            >
              {{ (lookup.name || '?').slice(0, 1).toUpperCase() }}
            </div>
            <span class="font-semibold text-slate-800 dark:text-slate-100">{{ lookup.name }}</span>
            <span class="text-slate-400">{{ lookupIsMember ? '已在项目里，点「添加」会改成所选权限' : '确认是这个人后点「添加」' }}</span>
          </template>
          <span v-else-if="lookup?.state === 'missing'" class="text-slate-500 dark:text-slate-400">
            查不到这个编号的名字（对方还没登录过 WireForge）。可以先添加，对方登录一次后会自动显示名字
          </span>
          <span v-else-if="lookup?.state === 'error'" class="text-rose-500">查询名字失败，可以直接添加</span>
        </div>
      </div>

      <!-- Search Filter Bar (Only shown when members list > 3) -->
      <div v-if="members.length > 3" class="px-6 pt-3 pb-1 flex items-center justify-between gap-3">
        <div class="relative flex-1">
          <Search class="w-3 h-3 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索成员姓名或 ID..."
            class="w-full pl-7 pr-3 py-1 text-xs bg-slate-50 dark:bg-[#1a1a1a] border border-slate-200 dark:border-[#383838] rounded-lg outline-none focus:border-blue-500 text-slate-800 dark:text-slate-100 placeholder:text-slate-400"
          />
        </div>
        <button
          type="button"
          class="p-1 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 rounded transition-colors cursor-pointer shrink-0"
          title="刷新成员列表"
          :disabled="loading"
          @click="loadMembers"
        >
          <RefreshCw class="w-3 h-3" :class="{ 'animate-spin': loading }" />
        </button>
      </div>

      <!-- Member List Area -->
      <div class="px-6 py-3 max-h-[340px] overflow-y-auto space-y-1.5 custom-scrollbar">
        <!-- Loading State -->
        <div v-if="loading" class="py-12 flex flex-col items-center justify-center text-slate-400">
          <div class="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mb-2"></div>
          <span class="text-xs">正在拉取项目成员...</span>
        </div>

        <!-- Members Rows -->
        <template v-else-if="filteredMembers.length > 0">
          <div
            v-for="m in filteredMembers"
            :key="m.userId"
            class="p-2.5 bg-white dark:bg-[#252525] hover:bg-slate-50/80 dark:hover:bg-[#2a2a2a] border border-slate-200/70 dark:border-[#333] rounded-xl flex items-center justify-between gap-3 transition-colors group"
          >
            <!-- Member Identity (Avatar + Name + ID) -->
            <div class="flex items-center gap-2.5 min-w-0 flex-1">
              <!-- Avatar -->
              <div class="relative shrink-0">
                <img
                  v-if="m.userAvatar"
                  :src="m.userAvatar"
                  :alt="memberName(m)"
                  class="w-8 h-8 rounded-full object-cover border border-slate-200 dark:border-[#444]"
                />
                <div
                  v-else
                  class="w-8 h-8 rounded-full text-white text-xs font-bold flex items-center justify-center shadow-2xs shrink-0"
                  :class="hasName(m) ? getAvatarBg(m.userName) : 'bg-slate-400'"
                >
                  {{ hasName(m) ? m.userName.slice(0, 1).toUpperCase() : '?' }}
                </div>
              </div>

              <!-- Name & ID -->
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-1.5">
                  <span
                    class="font-bold text-xs truncate"
                    :class="hasName(m) ? 'text-slate-900 dark:text-white' : 'text-slate-400'"
                    :title="hasName(m) ? m.userName : '对方登录一次 WireForge 后会显示名字'"
                  >
                    {{ memberName(m) }}
                  </span>
                  <span
                    v-if="isMe(m.userId)"
                    class="text-[9px] px-1.5 py-0.2 bg-blue-50 text-blue-600 dark:bg-blue-950/60 dark:text-blue-400 border border-blue-200/60 dark:border-blue-800/60 rounded font-medium shrink-0"
                  >
                    你
                  </span>
                </div>
                <div class="flex items-center gap-1.5 mt-0.5">
                  <span
                    class="text-[10px] text-slate-400 dark:text-slate-400 font-mono truncate max-w-[170px]"
                    :title="`飞书标识: ${m.userId}`"
                  >
                    {{ m.userId }}
                  </span>
                  <button
                    type="button"
                    class="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-opacity cursor-pointer"
                    title="复制完整 OpenID"
                    @click="copyOpenId(m.userId)"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>
            </div>

            <!-- Role Selector & Remove Action -->
            <div class="shrink-0 flex items-center gap-1.5">
              <el-select
                :model-value="m.role"
                size="small"
                class="w-28"
                :disabled="savingUserId === m.userId"
                @change="(val: UserRole) => handleRoleChange(m, val)"
              >
                <el-option value="admin" label="管理员">
                  <div class="flex items-center gap-1.5">
                    <ShieldCheck class="w-3.5 h-3.5 text-amber-500" />
                    <span class="font-semibold text-xs">管理员</span>
                  </div>
                </el-option>
                <el-option value="editor" label="编辑者">
                  <div class="flex items-center gap-1.5">
                    <Edit3 class="w-3.5 h-3.5 text-blue-500" />
                    <span class="text-xs">编辑者</span>
                  </div>
                </el-option>
                <el-option value="viewer" label="只读评审">
                  <div class="flex items-center gap-1.5">
                    <Eye class="w-3.5 h-3.5 text-slate-400" />
                    <span class="text-xs">只读评审</span>
                  </div>
                </el-option>
              </el-select>

              <button
                v-if="!isMe(m.userId)"
                type="button"
                class="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 rounded-lg transition-colors cursor-pointer"
                title="移除该成员"
                @click="handleRemoveMember(m)"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </template>

        <!-- Clean Empty State -->
        <div v-else class="py-12 flex flex-col items-center justify-center text-slate-400">
          <Users class="w-8 h-8 stroke-1 text-slate-300 dark:text-slate-600 mb-2" />
          <span class="text-xs">暂无匹配的成员</span>
          <p class="text-[11px] text-slate-400 mt-0.5">对方在头像菜单点「复制我的飞书编号」发给你，粘贴到上方即可添加</p>
        </div>
      </div>

      <!-- Refined Bottom Role Legend Bar -->
      <div class="px-6 py-2.5 bg-slate-50 dark:bg-[#1a1a1a] border-t border-slate-100 dark:border-[#303030] flex items-center justify-between text-[11px] text-slate-400">
        <div class="flex items-center gap-3">
          <span class="flex items-center gap-1" title="拥有画板编辑、交互连线、成员管理与审计日志权限">
            <ShieldCheck class="w-3 h-3 text-amber-500" />
            <span class="text-[10px]">管理员(全权)</span>
          </span>
          <span class="flex items-center gap-1" title="可自由绘制组件与交互连线">
            <Edit3 class="w-3 h-3 text-blue-500" />
            <span class="text-[10px]">编辑者(创作)</span>
          </span>
          <span class="flex items-center gap-1" title="仅查看与发表评论，画板锁定防误触">
            <Eye class="w-3 h-3 text-slate-400" />
            <span class="text-[10px]">只读评审(走查)</span>
          </span>
        </div>

        <button
          type="button"
          class="px-2.5 py-1 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-200/60 dark:hover:bg-[#303030] rounded-lg transition-colors cursor-pointer"
          @click="close"
        >
          完成
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Users, Search, RefreshCw, X, Copy, ShieldCheck, Edit3, Eye, UserPlus, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  currentUser,
  fetchProjectMembers,
  updateProjectMemberRole,
  addProjectMember,
  removeProjectMember,
  lookupFeishuUser,
  type ProjectMemberData,
  type UserRole,
} from '../services/auth'
import { recordAuditLog } from '../services/auditLog'

const props = defineProps<{
  modelValue: boolean
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'memberUpdated'): void
}>()

const visible = ref(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
    if (v) {
      loadMembers()
    }
  }
)
watch(visible, (v) => {
  emit('update:modelValue', v)
})

const loading = ref(false)
const savingUserId = ref<string | null>(null)
const searchQuery = ref('')
const members = ref<ProjectMemberData[]>([])

// 快捷添加成员状态
const newOpenId = ref('')
const newMemberRole = ref<UserRole>('editor')
const addingMember = ref(false)

const FEISHU_ID = /^ou_[A-Za-z0-9]+$/
type LookupState = { userId: string; state: 'loading' | 'found' | 'missing' | 'error'; name?: string; avatar?: string }
const lookup = ref<LookupState | null>(null)
let lookupTimer: ReturnType<typeof setTimeout> | undefined

const inputHint = computed(() => {
  const value = newOpenId.value.trim()
  return value && !value.startsWith('ou_') ? '飞书编号以 ou_ 开头，对方可在头像菜单里点「复制我的飞书编号」' : ''
})
const lookupIsMember = computed(() => !!lookup.value && members.value.some((m) => m.userId === lookup.value!.userId))

watch(newOpenId, (value) => {
  clearTimeout(lookupTimer)
  const userId = value.trim()
  if (!FEISHU_ID.test(userId)) {
    lookup.value = null
    return
  }
  lookup.value = { userId, state: 'loading' }
  lookupTimer = setTimeout(async () => {
    try {
      const r = await lookupFeishuUser(props.projectId, userId)
      if (lookup.value?.userId !== userId) return
      lookup.value = r.found && r.userName
        ? { userId, state: 'found', name: r.userName, avatar: r.userAvatar || '' }
        : { userId, state: 'missing' }
    } catch {
      if (lookup.value?.userId === userId) lookup.value = { userId, state: 'error' }
    }
  }, 300)
})

function hasName(m: ProjectMemberData): boolean {
  return !!m.userName && m.userName !== m.userId
}

function memberName(m: ProjectMemberData): string {
  return hasName(m) ? m.userName : '名字待确认'
}

function getAvatarBg(name: string): string {
  const bgs = [
    'bg-blue-600',
    'bg-indigo-600',
    'bg-violet-600',
    'bg-teal-600',
    'bg-emerald-600',
    'bg-amber-600',
    'bg-rose-600',
  ]
  let sum = 0
  for (let i = 0; i < (name || '').length; i++) {
    sum += name.charCodeAt(i)
  }
  return bgs[sum % bgs.length]
}

async function handleAddMember() {
  const openId = newOpenId.value.trim()
  if (!FEISHU_ID.test(openId)) {
    ElMessage.warning('请粘贴对方的飞书编号（ou_ 开头），对方可在头像菜单里点「复制我的飞书编号」')
    return
  }
  const existing = members.value.find((m) => m.userId === openId)
  const knownName = lookup.value?.userId === openId && lookup.value.state === 'found' ? lookup.value.name : undefined
  const name = knownName || (existing && hasName(existing) ? existing.userName : '')
  const label = name || openId

  addingMember.value = true
  try {
    const saved = await addProjectMember(props.projectId, openId, label, newMemberRole.value)
    const savedName = saved && saved.userName !== openId ? saved.userName : name
    const roleCn = newMemberRole.value === 'admin' ? '管理员' : newMemberRole.value === 'editor' ? '编辑者' : '只读评审'
    ElMessage.success(existing
      ? `已将「${savedName || openId}」的权限设为「${roleCn}」`
      : savedName
        ? `已添加「${savedName}」，权限：${roleCn}`
        : `已添加成员，权限：${roleCn}。对方登录一次后这里会显示名字`)
    recordAuditLog({
      projectId: props.projectId,
      actionType: 'member_change',
      changeType: 'add',
      actionLabel: '添加成员',
      pageName: savedName || openId,
      details: `将成员「${savedName || openId}」添加到项目，赋予「${roleCn}」权限`,
    })
    newOpenId.value = ''
    await loadMembers()
    emit('memberUpdated')
  } catch (err: any) {
    ElMessage.error(err.message || '添加成员失败')
  } finally {
    addingMember.value = false
  }
}

async function handleRemoveMember(member: ProjectMemberData) {
  if (isMe(member.userId)) {
    ElMessage.warning('不能移除您自己的管理员账号')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要将成员「${member.userName}」从当前项目中移除吗？`,
      '确认移除成员',
      {
        confirmButtonText: '确定移除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await removeProjectMember(props.projectId, member.userId)
    ElMessage.success(`已移除成员：${member.userName}`)
    recordAuditLog({
      projectId: props.projectId,
      actionType: 'member_change',
      changeType: 'delete',
      actionLabel: '移除成员',
      pageName: member.userName,
      details: `将成员「${member.userName}」从当前项目中移除`,
    })
    await loadMembers()
    emit('memberUpdated')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '移除成员失败')
    }
  }
}

const filteredMembers = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return members.value
  return members.value.filter(
    (m) =>
      m.userName.toLowerCase().includes(q) ||
      m.userId.toLowerCase().includes(q) ||
      (m.role && m.role.toLowerCase().includes(q))
  )
})

function isMe(userId: string): boolean {
  if (!currentUser.value) return false
  return currentUser.value.id === userId || currentUser.value.feishuOpenId === userId
}

function close() {
  visible.value = false
}

async function loadMembers() {
  if (!props.projectId) return
  loading.value = true
  try {
    members.value = await fetchProjectMembers(props.projectId)
  } catch (err: any) {
    ElMessage.error(err.message || '加载成员列表失败')
  } finally {
    loading.value = false
  }
}

async function handleRoleChange(member: ProjectMemberData, newRole: UserRole) {
  if (member.role === newRole) return

  // 防止管理员误操作取消自己的管理员权限
  if (isMe(member.userId) && newRole !== 'admin') {
    ElMessage.warning('不能取消您自己的管理员权限')
    return
  }

  savingUserId.value = member.userId
  try {
    await updateProjectMemberRole(props.projectId, member.userId, newRole)
    member.role = newRole
    const roleCn = newRole === 'admin' ? '管理员' : newRole === 'editor' ? '编辑者' : '只读评审'
    ElMessage.success(`已将「${member.userName}」权限设为：${roleCn}`)
    recordAuditLog({
      projectId: props.projectId,
      actionType: 'member_change',
      changeType: 'edit',
      actionLabel: '权限变更',
      pageName: member.userName,
      details: `将成员「${member.userName}」权限调整为「${roleCn}」`,
    })
    emit('memberUpdated')
  } catch (err: any) {
    ElMessage.error(err.message || '修改成员角色失败')
  } finally {
    savingUserId.value = null
  }
}

function copyOpenId(id: string) {
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(id).then(() => {
      ElMessage.success(`已复制飞书 OpenID: ${id}`)
    })
  } else {
    ElMessage.info(`OpenID: ${id}`)
  }
}

onMounted(() => {
  if (props.modelValue) {
    loadMembers()
  }
})
</script>

<style>
.project-member-dialog.el-dialog {
  padding: 0 !important;
  border-radius: 1rem !important;
  overflow: hidden !important;
  background: white !important;
  box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1) !important;
}
.dark .project-member-dialog.el-dialog {
  background: #202020 !important;
}
.project-member-dialog .el-dialog__header {
  display: none !important;
  height: 0 !important;
  padding: 0 !important;
  margin: 0 !important;
}
.project-member-dialog .el-dialog__body {
  padding: 0 !important;
}
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}
</style>
