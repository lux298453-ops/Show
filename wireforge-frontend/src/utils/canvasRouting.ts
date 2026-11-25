import type { Page } from '../types'
import { interactionDomUid } from './interactionHit'

export function canvasBindings(page: Page) {
  return (page.elements || []).filter(el => el.interaction).map(el => ({
    id: String(el.id), uid: interactionDomUid(el), action: el.interaction!.action,
    target: el.interaction!.target_page_id, source: el.interaction!.source,
    order: el.interaction!.id || 0,
  })).sort((a, b) => Number(b.source === 'user') - Number(a.source === 'user') || b.order - a.order)
}

/** Installed before generated scripts. Saved relations own routing; local tabs keep their own state. */
export function installCanvasRouting(doc: Document, initial: ReturnType<typeof canvasBindings>,
  initialBackgrounds: { id: number; action: string; x: number; y: number; w: number; h: number }[] = [],
  initialBackgroundDisabled = false) {
  let bindings = initial
  let backgrounds = initialBackgrounds
  let backgroundDisabled = initialBackgroundDisabled

  // A generated full-page wrapper can sit above the transparent return surface.
  // Handle its empty space here, before generated click handlers, without raising
  // a hit-test layer above cards, form controls or local modal dialogs.
  function blankBackground(node: globalThis.Element, event: MouseEvent) {
    if (backgroundDisabled || !backgrounds.length) return false
    const win = doc.defaultView
    if (!win) return false
    const body = doc.body.getBoundingClientRect()
    const x = event.clientX - body.left, y = event.clientY - body.top
    if (!backgrounds.some(area => ['back', 'close'].includes(area.action) &&
      x >= area.x && y >= area.y && x < area.x + area.w && y < area.y + area.h)) return false
    // Runtime-created local dialogs own their mask clicks. A composed page-level
    // dialog is marked by preparePreviewOverlay and uses the saved page action.
    const localModal = node.closest('.wf-modal')
    if (localModal && !localModal.hasAttribute('data-wf-preview-surface')) return false
    const width = doc.documentElement.clientWidth || body.width
    const height = doc.documentElement.clientHeight || body.height
    const transparent = (color: string) => !color || color === 'transparent' || /rgba\([^)]*,\s*0(?:\.0+)?\s*\)/.test(color)
    for (let n: globalThis.Element | null = node; n && n !== doc.body; n = n.parentElement) {
      if (n.matches('.wf-page-dismiss[data-wf-inject="backdrop-return"]')) break
      if (n.matches('button,a[href],input,select,textarea,label,summary,iframe,video,audio,canvas,img,svg,picture,hr,' +
        '[contenteditable]:not([contenteditable="false"]),[role="button"],[role="tab"],[role="switch"],[role="checkbox"],[role="radio"],[role="slider"],[role="menuitem"],' +
        '[data-nav],[data-nav-page-id],[data-action],[data-modal],[data-modal-target],[data-wf-control],' +
        '[data-wf-role="button"],[data-wf-role="input"],[data-wf-role="tab"],[data-wf-role="icon"]') ||
        (n as HTMLElement).onclick) return false
      if (n === node && Array.from(n.childNodes).some(child => child.nodeType === 3 && child.textContent?.trim())) return false
      const rect = n.getBoundingClientRect()
      const fullPage = rect.width >= width * .95 && rect.height >= height * .9
      if (!fullPage) {
        if (n.matches('[role="dialog"],[aria-modal="true"],[data-wf-role="modal"],[data-wf-role="drawer"],[data-wf-role="sheet"]')) return false
        const style = win.getComputedStyle(n)
        if (!transparent(style.backgroundColor) || style.backgroundImage && style.backgroundImage !== 'none' ||
          style.boxShadow && style.boxShadow !== 'none' ||
          ['Top', 'Right', 'Bottom', 'Left'].some(edge => parseFloat(style.getPropertyValue(`border-${edge.toLowerCase()}-width`)) > 0 &&
            !transparent(style.getPropertyValue(`border-${edge.toLowerCase()}-color`)))) return false
      }
    }
    return true
  }

  doc.addEventListener('click', event => {
    if (!doc.body?.classList.contains('wf-interactive')) return
    let node = event.target as globalThis.Element | null
    if (node?.nodeType !== 1) node = node?.parentElement || null
    const uids: string[] = []
    let binding: typeof bindings[number] | undefined
    for (let n = node; n && n !== doc.body; n = n.parentElement) {
      const uid = n.getAttribute('data-wf-uid') || ''
      if (uid) uids.push(uid)
      if (!binding) binding = bindings.find(b => b.uid ? b.uid === uid : b.id === n!.getAttribute('data-wf-element-id'))
    }
    if (!binding) {
      if (node && blankBackground(node, event)) {
        event.preventDefault()
        event.stopImmediatePropagation()
        doc.defaultView!.parent.postMessage({ type: 'wf-back' }, '*')
      }
      return
    }
    const action = binding.action
    const crossPage = action === 'navigate' || action === 'popup' || action === 'modal'
    if ((action === 'back' || action === 'close') && node?.closest('.wf-modal')) return
    if (!crossPage && action !== 'back' && action !== 'close' && action !== 'none') return
    if (crossPage && !binding.target) return
    if (action === 'none' && !node?.closest('[data-nav],[data-modal],[data-action]')) return
    event.preventDefault()
    event.stopImmediatePropagation()
    if (action === 'none') return
    const rect = doc.body.getBoundingClientRect()
    doc.defaultView!.parent.postMessage(crossPage ? {
      type: 'wf-nav', page: String(binding.target), uids,
      x: Math.round(event.clientX - rect.left), y: Math.round(event.clientY - rect.top),
    } : { type: 'wf-back' }, '*')
  }, true)
  return { update(next?: typeof bindings, nextBackgrounds?: typeof backgrounds, disabled?: boolean) {
    if (next) bindings = next
    if (nextBackgrounds) backgrounds = nextBackgrounds
    if (disabled != null) backgroundDisabled = disabled
  } }
}
