import type { Page } from '../types'

export interface MainNavigationItem { key: string; label: string; page?: Page }
interface Item { label: string; target?: number; active: boolean; declared: boolean }
interface Bar { page: Page; items: Item[] }

const normalize = (value: string) => value.trim().toLowerCase().replace(/\s+/g, '')
const pageName = (value: string) => normalize(value.replace(/\\/g, '/').split('/').pop() || '')
const pageOrder = (a: Page, b: Page) => ((a as Page & { sort_order?: number }).sort_order ?? a.id) - ((b as Page & { sort_order?: number }).sort_order ?? b.id) || a.id - b.id

function uniqueTarget(value: string, pages: Page[]): Page | undefined {
  if (!value) return undefined
  const exact = pages.filter(page => normalize(page.name) === normalize(value))
  if (exact.length) return exact.length === 1 ? exact[0] : undefined
  const renamed = pages.filter(page => pageName(page.name) === pageName(value))
  return renamed.length === 1 ? renamed[0] : undefined
}

function readBar(page: Page, pages: Page[]): Bar | undefined {
  const doc = new DOMParser().parseFromString(page.html_content || '', 'text/html')
  const nav = doc.querySelector('[data-wf-chrome="bottom-navigation"],.bottom-nav,.bottom-navigation,.wf-tabbar,.wf-canonical-nav')
  if (!nav) return undefined
  const selector = '[data-nav-item],[data-wf-chrome-part="item"]'
  let nodes = Array.from(nav.querySelectorAll(selector))
  if (!nodes.length) nodes = Array.from(nav.querySelectorAll('button,a,.nav-item,.wf-tabit,.tab-item'))
  nodes = nodes.filter(node => !nodes.some(parent => parent !== node && parent.contains(node)))
  const items = nodes.map(node => {
    const ids = [node, ...node.querySelectorAll('[data-wf-element-id]')].map(el => Number(el.getAttribute('data-wf-element-id'))).filter(Boolean)
    const links = (page.elements || []).filter(el => ids.includes(el.id) && el.interaction?.action === 'navigate')
      .map(el => el.interaction!.target_page_id).filter((id): id is number => !!id && pages.some(page => page.id === id))
    const targets = [...new Set(links)]
    const name = node.getAttribute('data-nav') || ''
    const target = targets.length === 1 ? targets[0] : targets.length > 1 ? undefined : uniqueTarget(name, pages)?.id
    return {
      label: (node.querySelector('[data-wf-chrome-part="label"]')?.textContent || node.getAttribute('aria-label') || node.textContent || '').trim(),
      target, declared: !!name || targets.length > 0,
      active: node.getAttribute('aria-current') === 'page' || node.getAttribute('aria-selected') === 'true'
        || ['active', 'selected', 'current', 'wf-nav-active', 'is-active'].some(className => node.classList.contains(className)),
    }
  })
  return items.length >= 2 ? { page, items } : undefined
}

function compatible(a: Bar, b: Bar): boolean {
  return a.items.length === b.items.length && a.items.filter((item, i) => normalize(item.label) === normalize(b.items[i].label)).length >= Math.max(2, Math.ceil(a.items.length / 2))
}

function parseMap(raw: unknown): { items?: { label?: string; target?: number }[] } | undefined {
  try {
    const value = typeof raw === 'string' ? JSON.parse(raw) : raw
    return value && typeof value === 'object' ? (value as { tab_bar?: { items?: { label?: string; target?: number }[] } }).tab_bar : undefined
  } catch { return undefined }
}

/** One entry per actual navigation item, in its order. Missing destinations stay visible. */
export function mainNavigation(pages: Page[], rawMap?: unknown): MainNavigationItem[] {
  const bars = pages.map(page => readBar(page, pages)).filter((bar): bar is Bar => !!bar)
  const reference = bars.sort((a, b) => bars.filter(bar => compatible(b, bar)).length - bars.filter(bar => compatible(a, bar)).length || pageOrder(a.page, b.page))[0]
  const mapped = parseMap(rawMap)?.items || []
  if (!reference) return mapped.map((item, index) => ({ key: `nav-${index}`, label: item.label || '', page: pages.find(page => page.id === item.target) }))
  const family = bars.filter(bar => compatible(reference, bar))
  return reference.items.map((item, slot) => {
    const votes = new Map<string, { score: number; pages: Page[] }>()
    const vote = (page: Page | undefined, weight: number) => {
      if (!page) return
      const key = pageName(page.name), group = votes.get(key) || { score: 0, pages: [] }
      group.score += weight
      if (!group.pages.some(candidate => candidate.id === page.id)) group.pages.push(page)
      votes.set(key, group)
    }
    for (const bar of family) {
      const source = bar.items[slot]
      const target = pages.find(page => page.id === source.target)
      vote(target, 2)
      if (source.active && (source.target === bar.page.id || !source.declared)) vote(bar.page, 3)
    }
    const ranked = [...votes.values()].sort((a, b) => b.score - a.score)
    let page = ranked[0] && (!ranked[1] || ranked[0].score > ranked[1].score) ? ranked[0].pages.sort(pageOrder)[0] : undefined
    if (!page && mapped.length === reference.items.length) page = pages.find(page => page.id === mapped[slot]?.target)
    page ||= uniqueTarget(item.label, pages)
    return { key: `nav-${slot}`, label: item.label, page }
  })
}
