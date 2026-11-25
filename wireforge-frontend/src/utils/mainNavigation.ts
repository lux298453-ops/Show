import type { Page } from '../types'
import { interactionDomUid } from './interactionHit'
import { isOverlayPage, pageReferenceCandidates, resolvePageReference } from './pageReferences'

export interface MainNavigationItem { key: string; label: string; page?: Page }
const targetSources = ['link', 'id', 'reference'] as const
interface Target { source: typeof targetSources[number]; id?: number }
interface Item { label: string; active: boolean; target?: Target }
interface Bar { page: Page; items: Item[]; marked: boolean }

const markedBar = '[data-wf-chrome="bottom-navigation"],[data-wf-navigation="bar"],.wf-canonical-nav'
const legacyBar = '.bottom-nav,.bottom-navigation,.wf-tabbar,.wf-ios-tab-bar,nav:has([data-nav-item])'
const markedItem = '[data-nav-item],[data-wf-chrome-part="item"]'
const legacyItem = 'button,a,.nav-item,.wf-nav-item,.wf-tabit,.tab-item'
const activeClasses = ['active', 'selected', 'on', 'current', 'wf-nav-active', 'is-active']

const normalize = (value: string) => value.trim().toLowerCase().replace(/\s+/g, '')
const pageOrder = (a: Page, b: Page) => ((a as Page & { sort_order?: number }).sort_order ?? a.id) - ((b as Page & { sort_order?: number }).sort_order ?? b.id) || a.id - b.id

function pageId(value: unknown): number | undefined {
  if (typeof value !== 'number' && typeof value !== 'string') return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

function findOnly<T>(values: Iterable<T>): T | undefined {
  const distinct = new Set(values)
  return distinct.size === 1 ? distinct.values().next().value : undefined
}

function readBar(page: Page, pages: Page[]): Bar | undefined {
  if (!page.html_content || isOverlayPage(page)) return undefined
  const doc = new DOMParser().parseFromString(page.html_content, 'text/html')
  const links = (page.elements || []).flatMap(element => {
    const target = pageId(element.interaction?.target_page_id)
    if (element.interaction?.action !== 'navigate' || !pages.some(candidate => candidate.id === target)) return []
    return [{ id: element.id, uid: interactionDomUid(element), target }]
  })

  function readItem(node: Element): Item {
    const members = [node, ...node.querySelectorAll('[data-wf-element-id],[data-wf-uid],[data-nav-page-id],[data-nav]')]
    const ids = new Set(members.map(member => pageId(member.getAttribute('data-wf-element-id'))))
    const uids = new Set(members.map(member => member.getAttribute('data-wf-uid')))
    const linked = links.filter(link => ids.has(link.id) || (link.uid && uids.has(link.uid)))
    const declaredIds = members.map(member => pageId(member.getAttribute('data-nav-page-id'))).filter(id => id !== undefined)
    const declaredNames = members.map(member => member.getAttribute('data-nav')?.trim()).filter((name): name is string => !!name)

    let target: Target | undefined
    if (linked.length) {
      target = { source: 'link', id: findOnly(linked.map(link => link.target)) }
    } else if (declaredIds.length) {
      const id = findOnly(declaredIds)
      if (id === undefined || pages.some(candidate => candidate.id === id) || !declaredNames.length) target = { source: 'id', id }
    }
    if (!target && declaredNames.length) {
      target = { source: 'reference', id: findOnly(declaredNames.map(name => resolvePageReference(name, pages)?.id)) }
    }

    const current = node.getAttribute('aria-current')
    const selected = node.getAttribute('aria-selected')
    const active = current !== null || selected !== null
      ? current === 'page' || current === 'true' || selected === 'true'
      : activeClasses.some(className => node.classList.contains(className))
    const label = node.querySelector('[data-wf-chrome-part="label"]')?.textContent?.trim()
      || node.textContent?.trim() || node.getAttribute('aria-label')?.trim() || ''
    return { label, target, active }
  }

  for (const marked of [true, false]) {
    for (const nav of doc.querySelectorAll(marked ? markedBar : legacyBar)) {
      if (nav.closest('[hidden],[aria-hidden="true"]')) continue
      const markedNodes = Array.from(nav.querySelectorAll(markedItem))
      const nodes = markedNodes.length ? markedNodes : Array.from(nav.querySelectorAll(legacyItem))
      const items = nodes.filter(node => !node.closest('[hidden],[aria-hidden="true"]')
        && !nodes.some(parent => parent !== node && parent.contains(node)))
      if (items.length >= (marked ? 1 : 2)) return { page, items: items.map(readItem), marked }
    }
  }
}

function sameItem(a: Item, b: Item): boolean {
  return (!!a.label && normalize(a.label) === normalize(b.label))
    || (a.target?.id !== undefined && a.target.id === b.target?.id)
}

function containsItems(bar: Bar, subset: Bar): boolean {
  let from = 0
  for (const item of subset.items) {
    const index = bar.items.findIndex((candidate, at) => at >= from && sameItem(item, candidate))
    if (index < 0) return false
    from = index + 1
  }
  return true
}

function compatible(a: Bar, b: Bar): boolean {
  if (a === b) return true
  const shared = Math.min(a.items.length, b.items.length)
  if (a.items.length !== b.items.length) return shared >= 2 && (a.items.length > b.items.length ? containsItems(a, b) : containsItems(b, a))
  const matched = a.items.filter((item, index) => sameItem(item, b.items[index])).length
  return matched >= Math.min(shared, Math.max(2, Math.ceil(shared / 2)))
}

function referenceBar(bars: Bar[]): Bar | undefined {
  const preferred = bars.some(bar => bar.marked) ? bars.filter(bar => bar.marked) : bars
  // An older copy missing a slot cannot outvote a complete version of the same bar.
  const candidates = preferred.filter(bar => !preferred.some(other => other.items.length > bar.items.length && compatible(bar, other)))
  const ranked = candidates.map(bar => ({
    bar,
    peers: preferred.filter(other => compatible(bar, other)).length,
    sameOrder: preferred.filter(other => other.items.length === bar.items.length && containsItems(bar, other)).length,
  }))
  return ranked.sort((a, b) => b.peers - a.peers || b.sameOrder - a.sameOrder || pageOrder(a.bar.page, b.bar.page))[0]?.bar
}

function onlyMatch<T>(matches: T[]): T | undefined {
  return matches.length === 1 ? matches[0] : undefined
}

function peerItem(item: Item, reference: Bar, peer: Bar): Item | undefined {
  if (reference === peer) return item
  const label = normalize(item.label)
  const sameLabel = (candidate: Item) => !!label && normalize(candidate.label) === label
  const byLabel = onlyMatch(reference.items.filter(sameLabel)) && onlyMatch(peer.items.filter(sameLabel))
  if (byLabel) return byLabel
  const id = item.target?.id
  if (id === undefined) return undefined
  const sameTarget = (candidate: Item) => candidate.target?.id === id
  if (onlyMatch(reference.items.filter(sameTarget))) return onlyMatch(peer.items.filter(sameTarget))
}

function readAppMap(raw: unknown): { label: string; target?: number }[] {
  try {
    const value = typeof raw === 'string' ? JSON.parse(raw) : raw
    const items = (value as { tab_bar?: { items?: unknown } } | null)?.tab_bar?.items
    if (!Array.isArray(items)) return []
    return items.flatMap(item => {
      const label = typeof item?.label === 'string' ? item.label.trim() : ''
      const target = pageId(item?.target)
      return label || target !== undefined ? [{ label, target }] : []
    })
  } catch { return [] }
}

/** One entry per actual navigation item, in its order. Missing destinations stay visible. */
export function mainNavigation(pages: Page[], rawMap?: unknown): MainNavigationItem[] {
  const destinations = pages.filter(page => !isOverlayPage(page))
  const byId = new Map(destinations.map(page => [page.id, page]))
  const bars = destinations.map(page => readBar(page, pages)).filter((bar): bar is Bar => !!bar)
  const reference = referenceBar(bars)
  const mapped = readAppMap(rawMap)
  if (!reference) return mapped.map((item, index) => ({ key: `nav-${index}`, label: item.label, page: item.target === undefined ? undefined : byId.get(item.target) }))
  const family = bars.filter(bar => compatible(reference, bar))

  const resolveItem = (item: Item): Page | undefined => {
    const targets: Target[] = []
    const activePages: number[] = []
    for (const bar of family) {
      const source = peerItem(item, reference, bar)
      if (source?.target) targets.push(source.target)
      else if (source?.active) activePages.push(bar.page.id)
    }
    for (const evidence of targetSources) {
      const ids = targets.filter(target => target.source === evidence).map(target => target.id)
      if (!ids.length) continue
      const id = findOnly(ids)
      return id === undefined ? undefined : byId.get(id)
    }

    const nameMatches = pageReferenceCandidates(item.label, destinations)
    if (nameMatches.length) return onlyMatch(nameMatches)
    if (activePages.length) {
      const active = findOnly(activePages)
      return active === undefined ? undefined : byId.get(active)
    }
    const label = normalize(item.label)
    if (!label || !onlyMatch(reference.items.filter(candidate => normalize(candidate.label) === label))) return undefined
    const fallback = onlyMatch(mapped.filter(candidate => normalize(candidate.label) === label))
    return fallback?.target === undefined ? undefined : byId.get(fallback.target)
  }

  return reference.items.map((item, slot) => ({ key: `nav-${slot}`, label: item.label, page: resolveItem(item) }))
}

export function activeMainNavigationKey(page: Page | null, navigation: MainNavigationItem[], pages: Page[]): string | undefined {
  if (!page || !navigation.length) return undefined
  const direct = navigation.filter(item => item.page?.id === page.id)
  if (direct.length === 1) return direct[0].key

  const selected = readBar(page, pages)?.items.filter(item => item.active) || []
  const active = onlyMatch(selected)
  if (!active) return undefined
  let candidates = direct.length ? direct : navigation.filter(item => item.page)
  if (active.target) {
    const id = active.target.id
    if (id === undefined) return undefined
    candidates = candidates.filter(item => item.page?.id === id)
    if (candidates.length === 1) return candidates[0].key
  }
  const label = normalize(active.label)
  if (label) return onlyMatch(candidates.filter(item => normalize(item.label) === label))?.key
}
