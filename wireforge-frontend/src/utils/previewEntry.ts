import type { Page } from '../types'
import type { MainNavigationItem } from './mainNavigation'
import { isOverlayPage } from './pageReferences'

/** Canvas presentation starts within main navigation; explicit page links may open any artboard. */
export function previewEntryPage(
  pages: Page[],
  navigation: MainNavigationItem[],
  options: { requestedPageId?: number | null; mainOnly?: boolean } = {},
): Page | undefined {
  const mainPages = navigation.map(item => pages.find(page => page.id === item.page?.id)).filter((page): page is Page => !!page)
  const requested = pages.find(page => page.id === options.requestedPageId)
  if (requested && !options.mainOnly) return requested
  if (requested && mainPages.some(page => page.id === requested.id)) return requested
  if (requested && !mainPages.length && !isOverlayPage(requested)) return requested
  return mainPages[0] || pages.find(page => !isOverlayPage(page)) || pages[0]
}
