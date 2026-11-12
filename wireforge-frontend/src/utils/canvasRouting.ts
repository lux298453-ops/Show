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
export function installCanvasRouting(doc: Document, initial: ReturnType<typeof canvasBindings>) {
  let bindings = initial
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
    if (!binding) return
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
  return { update(next: typeof bindings) { bindings = next } }
}
