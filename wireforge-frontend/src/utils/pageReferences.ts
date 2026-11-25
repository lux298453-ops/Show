import type { Page } from '../types'

const normalize = (value: string) => value.trim().replace(/\\/g, '/').replace(/\s+/g, ' ').toLowerCase()
const basename = (value: string) => value.replace(/\\/g, '/').split('/').pop() || ''
const references = (page: Page) => [page.name, page.source_name || '', ...(page.name_aliases || [])].filter(Boolean)

/** Names and aliases are project-scoped and must identify exactly one destination. */
export function pageReferenceCandidates(value: string, pages: Page[]): Page[] {
  const key = normalize(value)
  if (!key) return []
  const aliases = pages.filter(page => references(page).some(alias => normalize(alias) === key))
  if (aliases.length) return aliases
  if (key.includes('/')) return []
  return pages.filter(page => references(page).some(alias => normalize(basename(alias)) === key))
}

export function resolvePageReference(value: string, pages: Page[]): Page | undefined {
  if (value.startsWith('wf-page-id:')) return pages.find(page => String(page.id) === value.slice(11))
  const matches = pageReferenceCandidates(value, pages)
  if (matches.length) return matches.length === 1 ? matches[0] : undefined
  // Retain the old explicit numeric-ID entry point, after checking actual numeric filenames.
  return pages.find(page => String(page.id) === value.trim())
}

export function isOverlayPage(page: Page): boolean {
  return page.page_type === 'modal' || page.page_type === 'drawer'
}
