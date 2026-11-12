import type { Page } from '../types'
import type { MainNavigationItem } from './mainNavigation'

/** Canvas presentation starts within main navigation; explicit page links may open any artboard. */
export function previewEntryPage(
  pages: Page[],
  navigation: MainNavigationItem[],
  options: { requestedPageId?: number | null; mainOnly?: boolean } = {},
): Page | undefined {
  const mainPages = navigation.map(item => pages.find(page => page.id === item.page?.id)).filter((page): page is Page => !!page)
  const requested = pages.find(page => page.id === options.requestedPageId)
  if (requested && (!options.mainOnly || !mainPages.length || mainPages.some(page => page.id === requested.id))) return requested
  return mainPages[0] || pages[0]
}
