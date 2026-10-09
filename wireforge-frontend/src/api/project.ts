import http from './http'
import type { Project, Prototype, Page, Annotation, CommentThread, CommentReply } from '../types'
import type { AutowirePlan, AutowireApplyRequest, AutowireApplyResult, AutowirePreviewRequest } from '../types/autowire'

export interface AnalysisStatus {
  projectId: number
  analyzing: boolean
  current: number
  total: number
  currentPageName?: string
  step?: string
  okCount: number
  failCount: number
  lastError?: string | null
  finished: boolean
  startTime?: number
  updateTime?: number
}

export const projectApi = {
  list: () => http.get<any, Project[]>('/projects'),
  create: (name: string, description?: string) => http.post<any, Project>('/projects', { name, description }),
  update: (id: number, body: { name: string; description: string }) => http.put<any, Project>(`/projects/${id}`, body),
  remove: (id: number) => http.delete<any, void>(`/projects/${id}`),
  get: (id: number) => http.get<any, Project>(`/projects/${id}`),
  scan: (id: number) => http.post<any, unknown[]>(`/projects/${id}/scan`),
  analyze: (id: number) => http.post<any, AnalysisStatus>(`/projects/${id}/analyze`),
  startAnalyze: (id: number) => http.post<any, AnalysisStatus>(`/projects/${id}/analyze`),
  getAnalysisStatus: (id: number) => http.get<any, AnalysisStatus>(`/projects/${id}/analysis-status`),
  reanalyzePage: (id: number, pageId: number) =>
    http.post<any, { page_id: number; page_name: string; status: string; elements?: number; error?: string }[]>(
      `/projects/${id}/pages/${pageId}/reanalyze`,
    ),
  reanalyzeAll: (id: number) =>
    http.post<any, { page_id: number; page_name: string; status: string; elements?: number; error?: string }[]>(
      `/projects/${id}/reanalyze-all`,
    ),
  prototype: (id: number) => http.get<any, Prototype>(`/projects/${id}/prototype`),
  /** 仅重新生成某页的整页 HTML（Stitch 式直出），返回新 HTML */
  regenerateHtml: (id: number, pageId: number) =>
    http.post<any, string>(`/projects/${id}/pages/${pageId}/regenerate-html`),
  /** 保存用户微调后的完整整页 HTML */
  saveHtml: (id: number, pageId: number, html: string, clientId?: string) =>
    http.put<any, void>(`/projects/${id}/pages/${pageId}/html`, { html, clientId }),
  createAnnotation: (id: number, pageId: number, body: { elementId: number | null; title: string; text: string }) =>
    http.post<any, Annotation>(`/projects/${id}/pages/${pageId}/annotations`, body),
  deleteAnnotation: (id: number, annId: number) => http.delete<any, void>(`/projects/${id}/annotations/${annId}`),
  updateAnnotation: (
    id: number,
    annId: number,
    body: {
      text?: string
      title?: string
      positionX?: number
      positionY?: number
      boxX?: number
      boxY?: number
      anchorX?: number
      anchorY?: number
      elbowX?: number
    },
  ) => http.put<any, Record<string, unknown>>(`/projects/${id}/annotations/${annId}`, body),
  updatePagePosition: (id: number, pageId: number, body: { canvasX: number; canvasY: number }) =>
    http.put<any, Record<string, unknown>>(`/projects/${id}/pages/${pageId}/position`, body),
  updatePageSize: (id: number, pageId: number, body: { width: number; height: number }) =>
    http.put<any, Record<string, unknown>>(`/projects/${id}/pages/${pageId}/size`, body),
  updatePageAnnotationOrders: (id: number, pageId: number, orderedAnnIds: number[]) =>
    http.put<any, number[]>(`/projects/${id}/pages/${pageId}/annotation-orders`, orderedAnnIds),
  lockPageEditing: (
    id: number,
    pageId: number,
    body: { clientId: string; userName?: string; active: boolean },
  ) => http.post<any, { editing: boolean; conflict: boolean; editor?: string }>(`/projects/${id}/pages/${pageId}/edit-lock`, body),
  getPageEditingStatus: (id: number, pageId: number, clientId: string) =>
    http.get<any, { editing: boolean; conflict: boolean; editor?: string }>(`/projects/${id}/pages/${pageId}/edit-status`, {
      params: { clientId },
    }),
  getAllPageEditingStatuses: (id: number, clientId: string) =>
    http.get<any, Record<number, { editing: boolean; conflict: boolean; editor?: string }>>(`/projects/${id}/edit-statuses`, {
      params: { clientId },
    }),
  /** 保存或更新交互连线 */
  saveInteraction: (
    id: number,
    body: {
      elementId?: number
      pageId?: number | null
      targetPageId?: number | null
      triggerType?: string
      actionType?: string
      params?: string | null
    },
  ) => http.post<any, any>(`/projects/${id}/interactions`, body),
  /** Compatibility entry: now read-only, never silently applies inferred relations. */
  autowireInteractionsWithAi: (id: number) =>
    http.post<any, AutowirePlan>(`/projects/${id}/autowire-ai`),
  previewAutowire: (id: number, body?: AutowirePreviewRequest, signal?: AbortSignal) => http.post<any, AutowirePlan>(`/projects/${id}/autowire/preview`, body, { signal }),
  applyAutowire: (id: number, body: AutowireApplyRequest) =>
    http.post<any, AutowireApplyResult>(`/projects/${id}/autowire/apply`, body),
  autowireStatus: (id: number, applicationId: string) =>
    http.get<any, AutowireApplyResult>(`/projects/${id}/autowire/applications/${applicationId}`),
  retryAutowireRender: (id: number, applicationId: string) =>
    http.post<any, AutowireApplyResult>(`/projects/${id}/autowire/applications/${applicationId}/retry-render`),
  /** 删除交互连线 */
  deleteInteraction: (id: number, interactionId: number) =>
    http.delete<any, void>(`/projects/${id}/interactions/${interactionId}`),
  /** 为页面注册新增元素 */
  createElement: (id: number, pageId: number, body: Record<string, any>) =>
    http.post<any, any>(`/projects/${id}/pages/${pageId}/elements`, body),
  /** 新建空白画板/框架 (Figma Frame 工具支持) */
  createPage: (
    id: number,
    data: {
      name?: string
      width?: number
      height?: number
      x?: number
      y?: number
      htmlContent?: string
    },
  ) => http.post<any, Page>(`/projects/${id}/pages`, data),
  /** 删除画板/页面 */
  deletePage: (id: number, pageId: number) => http.delete<any, void>(`/projects/${id}/pages/${pageId}`),
  /** 获取素材库列表 */
  getAssets: () => http.get<any, any[]>('/assets/list'),
  /** 上传一张图片进素材库，category 为 avatars/products/icons/backgrounds/effects/uploads */
  uploadAsset: (file: File, category: string) => {
    const form = new FormData()
    form.append('file', file)
    form.append('category', category)
    return http.post<any, any>('/assets/upload', form)
  },

  // ==========================================
  // Figma 评论系统
  // ==========================================
  listComments: (id: number) => http.get<any, CommentThread[]>(`/projects/${id}/comments`),
  createComment: (id: number, body: { pageId: number; x: number; y: number; author: string; content: string }) =>
    http.post<any, CommentThread>(`/projects/${id}/comments`, body),
  addReply: (id: number, threadId: number, body: { author: string; content: string }) =>
    http.post<any, CommentReply>(`/projects/${id}/comments/${threadId}/replies`, body),
  toggleResolveComment: (id: number, threadId: number, body?: { resolved?: boolean }) =>
    http.put<any, CommentThread>(`/projects/${id}/comments/${threadId}/resolve`, body || {}),
  deleteCommentThread: (id: number, threadId: number) =>
    http.delete<any, void>(`/projects/${id}/comments/${threadId}`),
}
