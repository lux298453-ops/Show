import http from './http'
import type { Project, Prototype } from '../types'

export const projectApi = {
  list: () => http.get<any, Project[]>('/projects'),
  create: (name: string, description?: string) => http.post<any, Project>('/projects', { name, description }),
  remove: (id: number) => http.delete<any, void>(`/projects/${id}`),
  get: (id: number) => http.get<any, Project>(`/projects/${id}`),
  scan: (id: number) => http.post<any, unknown[]>(`/projects/${id}/scan`),
  analyze: (id: number) => http.post<any, { page_id: number; page_name: string; status: string; elements?: number; error?: string }[]>(`/projects/${id}/analyze`),
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
  saveHtml: (id: number, pageId: number, html: string) =>
    http.put<any, void>(`/projects/${id}/pages/${pageId}/html`, { html }),
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
  updatePageAnnotationOrders: (id: number, pageId: number, orderedAnnIds: number[]) =>
    http.put<any, number[]>(`/projects/${id}/pages/${pageId}/annotation-orders`, orderedAnnIds),
}