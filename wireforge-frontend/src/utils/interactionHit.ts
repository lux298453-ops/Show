import type { Element, Page } from '../types'

function hotspotBox(el: Element): { x: number; y: number; w: number; h: number } {
  const raw = el.interaction?.params
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      const hit = parsed?.hit
      if (hit && Number(hit.w) >= 4 && Number(hit.h) >= 4) {
        return { x: Number(hit.x) || 0, y: Number(hit.y) || 0, w: Number(hit.w) || 0, h: Number(hit.h) || 0 }
      }
    } catch {
      /* 旧数据没有点击区域，改用元素自己的位置 */
    }
  }
  return { x: el.x || 0, y: el.y || 0, w: el.width || 0, h: el.height || 0 }
}

export function interactionDomUid(el: Element): string {
  const raw = el.interaction?.params
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw)
    return parsed?.domUid ? String(parsed.domUid) : ''
  } catch {
    return ''
  }
}

/** 自己画上去的组件：优先认点击到的那个元素，不让底下更小的热区把跳转抢走。 */
export function findInteractionByDomUids(page: Page | null | undefined, uids?: string[]): Element | null {
  if (!page || !uids?.length) return null
  for (const uid of uids) {
    if (!uid) continue
    for (const el of page.elements || []) {
      if (interactionDomUid(el) !== uid) continue
      const target = el.interaction?.target_page_id
      if (!target) continue
      const action = el.interaction?.action
      if (action && action !== 'navigate') continue
      return el
    }
  }
  return null
}

/** 预览点击落在哪条已保存的连线上。取包住点击、面积最小的那一块。 */
export function hitInteractionElement(page: Page | null | undefined, x: number, y: number): Element | null {
  if (!page) return null
  let best: Element | null = null
  let bestArea = Infinity
  const slop = 12
  for (const el of page.elements || []) {
    const target = el.interaction?.target_page_id
    if (!target) continue
    const action = el.interaction?.action
    if (action && action !== 'navigate') continue
    const box = hotspotBox(el)
    if (box.w < 4 || box.h < 4) continue
    if (x < box.x - slop || x > box.x + box.w + slop || y < box.y - slop || y > box.y + box.h + slop) continue
    const area = box.w * box.h
    if (area < bestArea) {
      best = el
      bestArea = area
    }
  }
  return best
}

/** 先认组件自己的连线，对不上再按坐标找。 */
export function resolveNavigateElement(
  page: Page | null | undefined,
  x: number,
  y: number,
  uids?: string[],
): Element | null {
  return findInteractionByDomUids(page, uids) || hitInteractionElement(page, x, y)
}

