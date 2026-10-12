import { ref } from 'vue'
import { getAuthHeaders, currentUser } from './auth'
import { apiUrl } from '../api/http'

export type AuditActionType = 'page_edit' | 'connection_change' | 'comment_discuss' | 'member_change' | 'project_manage'
export type AuditChangeType = 'add' | 'edit' | 'delete'

export interface AuditLogItem {
  id: string
  projectId: number
  userId: string
  userName: string
  userAvatar?: string
  actionType: AuditActionType
  changeType: AuditChangeType
  actionLabel: string
  pageName?: string
  details: string
  timestamp: string // ISO string
}

export type RecordAuditLogParam = Omit<AuditLogItem, 'id' | 'timestamp' | 'userId' | 'userName'> & {
  userId?: string
  userName?: string
  timestamp?: string
}

function mapBackendLogToItem(r: any): AuditLogItem {
  let actionType: AuditActionType = 'page_edit'
  let changeType: AuditChangeType = 'edit'
  let actionLabel = '画板修改'

  const type = (r.actionType || '').toUpperCase()
  if (type.includes('MEMBER')) {
    actionType = 'member_change'
    if (type.includes('ADD')) { changeType = 'add'; actionLabel = '添加成员' }
    else if (type.includes('DELETE') || type.includes('REMOVE')) { changeType = 'delete'; actionLabel = '移除成员' }
    else { changeType = 'edit'; actionLabel = '权限变更' }
  } else if (type.includes('INTERACTION') || type.includes('WIRE')) {
    actionType = 'connection_change'
    if (type.includes('ADD')) { changeType = 'add'; actionLabel = '新建连线' }
    else if (type.includes('DELETE')) { changeType = 'delete'; actionLabel = '删除连线' }
    else { changeType = 'edit'; actionLabel = '更新连线' }
  } else if (type.includes('COMMENT')) {
    actionType = 'comment_discuss'
    if (type.includes('RESOLVE')) { changeType = 'edit'; actionLabel = '解决评论' }
    else if (type.includes('DELETE')) { changeType = 'delete'; actionLabel = '删除评论' }
    else { changeType = 'add'; actionLabel = '发表评论' }
  } else if (type.includes('PROJECT')) {
    actionType = 'project_manage'
    if (type.includes('DELETE')) { changeType = 'delete'; actionLabel = '删除项目' }
    else if (type.includes('UPDATE') || type.includes('EDIT')) { changeType = 'edit'; actionLabel = '修改项目' }
    else { changeType = 'add'; actionLabel = '创建项目' }
  } else if (type.includes('PAGE_CREATE') || type.includes('PAGE_ADD') || type.includes('PAGE_SCAN')) {
    actionType = 'page_edit'
    changeType = 'add'
    actionLabel = '新建画板'
  } else if (type.includes('PAGE_DELETE')) {
    actionType = 'page_edit'
    changeType = 'delete'
    actionLabel = '删除画板'
  } else {
    actionType = 'page_edit'
    changeType = 'edit'
    actionLabel = '画板修改'
  }

  let rawUserName = r.userName || '未知用户'
  try {
    if (rawUserName.includes('%')) {
      rawUserName = decodeURIComponent(rawUserName)
    }
  } catch (e) {}

  return {
    id: `log_${r.id || Date.now()}`,
    projectId: Number(r.projectId),
    userId: r.userId || 'system',
    userName: rawUserName,
    userAvatar: r.userAvatar || '',
    actionType,
    changeType,
    actionLabel,
    pageName: r.targetName || '',
    details: r.actionDesc || '',
    timestamp: r.createdAt ? new Date(r.createdAt.replace(' ', 'T')).toISOString() : new Date().toISOString(),
  }
}

export const auditLogsMap = ref<Record<number, AuditLogItem[]>>({})

// 彻底清除旧版本遗留的模拟数据缓存
try {
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const k = localStorage.key(i)
    if (k && k.startsWith('wf_audit_logs_')) {
      localStorage.removeItem(k)
    }
  }
} catch (e) {}

export function loadAuditLogs(projectId: number): AuditLogItem[] {
  return auditLogsMap.value[projectId] || []
}

export function recordAuditLog(item: RecordAuditLogParam): AuditLogItem {
  const userId = item.userId || currentUser.value?.id || 'unknown'
  const userName = item.userName || currentUser.value?.name || '未登录用户'
  const userAvatar = item.userAvatar !== undefined ? item.userAvatar : (currentUser.value?.avatar || '')

  const fullItem: AuditLogItem = {
    ...item,
    userId,
    userName,
    userAvatar,
    id: `log_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
    timestamp: item.timestamp || new Date().toISOString(),
  }

  const currentList = auditLogsMap.value[item.projectId] || []
  const updated = [fullItem, ...currentList]
  auditLogsMap.value[item.projectId] = updated

  // 异步上报落库至后端 operation_log，操作人由服务器按登录凭证认定
  if (!localStorage.getItem('wf_token')) return fullItem
  try {
    let actionType = 'PAGE_EDIT'
    if (item.actionType === 'member_change') actionType = 'MEMBER_ROLE_CHANGE'
    else if (item.actionType === 'connection_change') actionType = 'INTERACTION_ADD'
    else if (item.actionType === 'comment_discuss') actionType = 'COMMENT_ADD'
    else if (item.actionType === 'project_manage') actionType = 'PROJECT_CREATE'
    else if (item.changeType === 'add') actionType = 'PAGE_CREATE'
    else if (item.changeType === 'delete') actionType = 'PAGE_DELETE'

    fetch(apiUrl(`/projects/${item.projectId}/logs`), {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        actionType,
        actionDesc: item.details,
        targetName: item.pageName || item.actionLabel,
      }),
    })
      .then(async (res) => {
        if (res.ok) {
          const ret = await res.json()
          if (ret && ret.code === 0 && ret.data?.id) {
            fullItem.id = `log_${ret.data.id}`
          }
        }
      })
      .catch((err) => console.warn('[AuditLog] Failed to sync log to server', err))
  } catch (e) {}

  return fullItem
}

export async function fetchProjectOperationLogs(): Promise<AuditLogItem[]> {
  const res = await fetch(apiUrl('/logs/projects?page=1&pageSize=100'), {
    headers: getAuthHeaders(),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data || data.code !== 0) {
    throw new Error(data?.message || '加载项目操作日志失败')
  }
  const records = data.data?.records
  return Array.isArray(records) ? records.map(mapBackendLogToItem) : []
}

export async function fetchAuditLogs(projectId: number): Promise<AuditLogItem[]> {
  try {
    const res = await fetch(apiUrl(`/projects/${projectId}/logs?page=1&pageSize=100`), {
      headers: getAuthHeaders(),
    })
    if (res.ok) {
      const data = await res.json()
      if (data && data.code === 0 && data.data && Array.isArray(data.data.records)) {
        const remoteList = data.data.records.map(mapBackendLogToItem)
        auditLogsMap.value[projectId] = remoteList
        return remoteList
      }
    }
  } catch (err) {
    console.warn('[AuditLog] Failed to fetch remote audit logs', err)
  }
  return auditLogsMap.value[projectId] || []
}

