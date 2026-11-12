import type { Page } from '../types'
import { findInteractionByDomUids, interactionDomUid } from './interactionHit'

// Main navigation and sibling tabs replace content in place. Linked cards and
// detail buttons outside these containers still open a child page.
const tabSelector = [
  '[data-wf-tab]', '[data-wf-role="tab"]', '[role="tab"]', '[data-wf-tab-segment]',
  '[role="tablist"]', '.wf-segs', '.wf-tab-underline', '.wf-tabs',
  '[data-wf-edit-role="navigation-item"]', '[data-nav-item]',
  '[data-wf-chrome="bottom-navigation"]', '[data-wf-navigation="bar"]',
  '[data-wf-role="navigation-bar"]', '[role="navigation"]', 'nav',
].join(',')
const cache = new Map<string, Set<string>>()
function tabKeys(page: Page): Set<string> {
  const html = page.html_content || ''
  const cached = cache.get(html)
  if (cached) return cached
  const doc = new DOMParser().parseFromString(html, 'text/html'), keys = new Set<string>()
  doc.querySelectorAll(tabSelector).forEach(tab => {
    for (const node of [tab, ...tab.querySelectorAll('[data-wf-uid],[data-wf-element-id]')]) {
      if (node.getAttribute('data-wf-uid')) keys.add(node.getAttribute('data-wf-uid')!)
      if (node.getAttribute('data-wf-element-id')) keys.add('id:' + node.getAttribute('data-wf-element-id'))
    }
  })
  if (cache.size >= 8) cache.delete(cache.keys().next().value!)
  cache.set(html, keys)
  return keys
}

export function isTabNavigation(page: Page | null | undefined, uids?: string[], elementId?: number): boolean {
  if (!page) return false
  const keys = tabKeys(page)
  return !!uids?.some(uid => keys.has(uid)) || (elementId != null && keys.has('id:' + elementId))
}

export function tabTargetIds(page: Page): number[] {
  return [...new Set((page.elements || []).filter(el =>
    el.interaction?.action === 'navigate' && el.interaction.target_page_id &&
    (!interactionDomUid(el) || findInteractionByDomUids(page, [interactionDomUid(el)])?.id === el.id) &&
    isTabNavigation(page, [interactionDomUid(el)], el.id),
  ).map(el => el.interaction!.target_page_id!))]
}
