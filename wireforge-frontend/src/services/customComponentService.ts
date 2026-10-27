import { ref } from 'vue'
import http from '../api/http'
import type { PaletteItem } from '../data/componentLibrary'

export interface UserComponent {
  id: number
  name: string
  html: string
  width: number
  height: number
  updatedAt?: string
}

export const myComponents = ref<UserComponent[]>([])

export async function loadMyComponents() {
  try {
    const list = await http.get<unknown, UserComponent[]>('/components/mine')
    myComponents.value = Array.isArray(list) ? list : []
  } catch {
    myComponents.value = []
  }
}

export async function createMyComponent(payload: { name: string; html: string; width: number; height: number }) {
  const saved = await http.post<unknown, UserComponent>('/components', payload)
  await loadMyComponents()
  return saved
}

export async function renameMyComponent(id: number, name: string) {
  await http.put(`/components/${id}`, { name })
  await loadMyComponents()
}

export async function deleteMyComponent(id: number) {
  await http.delete(`/components/${id}`)
  await loadMyComponents()
}

export function asPaletteItem(item: UserComponent): PaletteItem {
  const width = Math.max(1, Number(item.width) || 1)
  const height = Math.max(1, Number(item.height) || 1)
  const scale = Math.min(1, 100 / width, 56 / height)
  const previewWidth = Math.max(8, Math.round(width * scale))
  const previewHeight = Math.max(8, Math.round(height * scale))
  return {
    id: `mine-${item.id}`,
    name: item.name,
    category: 'display',
    tag: '我的',
    description: '我的组件',
    html: item.html,
    width,
    height,
    customId: item.id,
    previewHtml: `<div style="width:${previewWidth}px;height:${previewHeight}px;overflow:hidden"><div style="width:${width}px;height:${height}px;transform:scale(${scale});transform-origin:top left">${item.html}</div></div>`,
  }
}
