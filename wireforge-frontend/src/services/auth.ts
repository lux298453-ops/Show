import { ref, computed } from 'vue'
import http from '../api/http'

export type UserRole = 'admin' | 'editor' | 'viewer'

/** 登录的人是谁。能做什么一律问服务器：系统管理员看 systemAdmin，项目里看 projectRole。 */
export interface AuthUser {
  id: string
  name: string
  avatar: string
  department?: string
  feishuOpenId?: string
}

const STORAGE_KEY = 'wf_current_user'
const TOKEN_KEY = 'wf_token'

function toAuthUser(raw: any): AuthUser | null {
  const id = raw?.userId || raw?.id
  const name = raw?.userName || raw?.name
  if (!id || !name) return null
  return {
    id,
    name,
    avatar: raw.userAvatar || raw.avatar || '',
    department: raw.department,
    feishuOpenId: raw.openId || raw.feishuOpenId,
  }
}

function loadSavedUser(): AuthUser | null {
  try {
    if (!localStorage.getItem(TOKEN_KEY)) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return toAuthUser(JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null'))
  } catch {
    return null
  }
}

export const currentUser = ref<AuthUser | null>(loadSavedUser())
const systemAdmin = ref(false)
/** 当前打开项目里我的角色；null 表示还没问到服务器，此期间按只读处理 */
const projectRoleState = ref<{ projectId: number; role: UserRole } | null>(null)
let roleRequestSeq = 0

function storeUser(user: AuthUser | null) {
  currentUser.value = user ? { ...user } : null
  try {
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
      localStorage.setItem('wf_comment_author', user.name)
    } else {
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem('wf_comment_author')
    }
  } catch (err) {
    console.error('[Auth] Failed to update localStorage', err)
  }
}

function clearLogin() {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {}
  storeUser(null)
  systemAdmin.value = false
}

function isRole(value: unknown): value is UserRole {
  return value === 'admin' || value === 'editor' || value === 'viewer'
}

function applyMe(me: any) {
  if (!me?.authenticated) {
    if (currentUser.value || localStorage.getItem(TOKEN_KEY)) clearLogin()
    systemAdmin.value = false
    return
  }
  const user = toAuthUser(me)
  if (user) storeUser({ ...currentUser.value, ...user })
  systemAdmin.value = !!me.systemAdmin
}

/** 飞书回调拿到凭证后调用：存凭证，再向服务器确认身份 */
export async function completeLogin(token: string, user: unknown): Promise<AuthUser | null> {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {}
  storeUser(toAuthUser(user))
  await refreshSession()
  return currentUser.value
}

/** 另一个窗口完成了登录（同源共享 localStorage），重新读取并确认 */
export async function syncLoginFromStorage(): Promise<AuthUser | null> {
  storeUser(loadSavedUser())
  await refreshSession()
  return currentUser.value
}

/** 向服务器确认凭证是否有效、我是不是系统管理员。网络不通时保留本地状态。 */
export async function refreshSession(): Promise<void> {
  if (!localStorage.getItem(TOKEN_KEY)) {
    if (currentUser.value) storeUser(null)
    systemAdmin.value = false
    return
  }
  try {
    applyMe(await http.get<any, any>('/auth/me'))
  } catch (err) {
    console.warn('[Auth] Failed to verify session', err)
  }
}

/** 问服务器我在这个项目里的角色。拿到回答之前 isEditor 为 false，画布保持锁定。 */
export async function loadProjectRole(projectId: number): Promise<UserRole> {
  const seq = ++roleRequestSeq
  projectRoleState.value = null
  let role: UserRole = 'viewer'
  try {
    const me = await http.get<any, any>('/auth/me', { params: { projectId } })
    if (seq !== roleRequestSeq) return role
    applyMe(me)
    if (isRole(me?.projectRole)) role = me.projectRole
  } catch (err) {
    console.warn('[Auth] Failed to load project role', err)
  }
  if (seq === roleRequestSeq) projectRoleState.value = { projectId, role }
  return role
}

export function clearProjectRole() {
  roleRequestSeq++
  projectRoleState.value = null
}

export async function loginWithFeishu(code: string): Promise<AuthUser> {
  const result = await http.post<any, any>('/auth/feishu/callback', { code: code.trim() })
  const user = result?.token ? await completeLogin(result.token, result.user) : null
  if (!user) throw new Error('飞书登录失败')
  return user
}

export async function logout() {
  if (localStorage.getItem(TOKEN_KEY)) {
    await http.post('/auth/logout').catch(() => {})
  }
  clearLogin()
}

export const isAuthenticated = computed(() => !!currentUser.value)
/** 系统管理员：飞书用户编号在服务器的管理员名单里 */
export const isSystemAdmin = computed(() => !!currentUser.value && systemAdmin.value)
export const projectRole = computed<UserRole | null>(() => projectRoleState.value?.role ?? null)
export const projectRoleLoading = computed(() => projectRoleState.value === null)
/** 以下三个指当前打开的项目 */
export const isAdmin = computed(() => projectRole.value === 'admin')
export const isEditor = computed(() => projectRole.value === 'admin' || projectRole.value === 'editor')
export const isViewer = computed(() => !isEditor.value)

export function roleLabel(role?: UserRole | null): string {
  if (role === 'admin') return '管理员'
  if (role === 'editor') return '编辑者'
  return '只读评审'
}

export interface ProjectMemberData {
  id: number
  projectId: number
  userId: string
  userName: string
  userAvatar: string
  role: UserRole
  createdAt: string
}

export function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

export async function fetchProjectMembers(projectId: number): Promise<ProjectMemberData[]> {
  try {
    const res = await fetch(`/api/projects/${projectId}/members`, {
      headers: getAuthHeaders(),
    })
    if (res.ok) {
      const data = await res.json()
      if (data && data.code === 0 && Array.isArray(data.data)) {
        return data.data
      }
    }
  } catch (err) {
    console.error('Failed to fetch project members', err)
  }
  return []
}

export interface FeishuUserLookup {
  userId: string
  found: boolean
  userName?: string | null
  userAvatar?: string | null
}

/** 按飞书编号查这个人叫什么（登录过的人，或飞书通讯录能查到的人） */
export async function lookupFeishuUser(projectId: number, userId: string): Promise<FeishuUserLookup> {
  const res = await fetch(`/api/projects/${projectId}/members/lookup?userId=${encodeURIComponent(userId)}`, {
    headers: getAuthHeaders(),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok || data.code !== 0) {
    throw new Error(data.message || '查询失败')
  }
  return data.data
}

export async function updateProjectMemberRole(projectId: number, userId: string, role: UserRole): Promise<boolean> {
  const res = await fetch(`/api/projects/${projectId}/members/${userId}/role`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify({ role }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || '更新权限失败')
  }
  const data = await res.json()
  if (data.code !== 0) {
    throw new Error(data.message || '更新权限失败')
  }
  return true
}

export async function addProjectMember(
  projectId: number,
  userId: string,
  userName: string,
  role: UserRole
): Promise<ProjectMemberData> {
  const res = await fetch(`/api/projects/${projectId}/members`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ userId, userName, role }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || '添加成员失败')
  }
  const data = await res.json()
  if (data.code !== 0) {
    throw new Error(data.message || '添加成员失败')
  }
  return data.data
}

export async function removeProjectMember(projectId: number, userId: string): Promise<boolean> {
  const res = await fetch(`/api/projects/${projectId}/members/${userId}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || '移除成员失败')
  }
  const data = await res.json().catch(() => ({ code: 0 }))
  if (data.code !== 0) {
    throw new Error(data.message || '移除成员失败')
  }
  return true
}
