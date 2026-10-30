import type { Element, Page } from '../types'

export function backgroundReturnSetting(page?: Page | null): Element | undefined {
  return page?.elements?.find(el => {
    if (el.type !== 'background' || !el.style) return false
    try { return JSON.parse(el.style).wfPageAction === 'background-return' } catch { return false }
  })
}

export function backgroundReturnEnabled(page?: Page | null): boolean {
  const setting = backgroundReturnSetting(page)
  if (setting) return setting.interaction?.action === 'back'
  return !!page?.elements?.some(el => el.type === 'background' && ['back', 'close'].includes(el.interaction?.action || ''))
}

export function backgroundReturnTargets(page: Page) {
  const setting = backgroundReturnSetting(page)
  const elements = setting ? (setting.interaction?.action === 'back' ? [setting] : [])
    : (page.elements || []).filter(el => el.type === 'background' && ['back', 'close'].includes(el.interaction?.action || ''))
  return elements.map(el => ({ id: el.id, action: el.interaction!.action,
    x: setting ? 0 : el.x, y: setting ? 0 : el.y,
    w: setting ? (page.canvas_width || 375) : el.width, h: setting ? (page.canvas_height || 812) : el.height }))
}
