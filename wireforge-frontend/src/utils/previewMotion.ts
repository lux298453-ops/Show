export type PreviewTransition = 'forward' | 'backward' | 'tab'

/** Keep prototype geometry intact; entry effects never transform authored content. */
export function animatePreviewContent(frame: HTMLIFrameElement | null | undefined, direction: PreviewTransition): () => void {
  const doc = frame?.contentDocument, win = doc?.defaultView
  if (!doc?.body || !win || win.matchMedia('(prefers-reduced-motion: reduce)').matches) return () => {}
  function visible(node: HTMLElement) {
    for (let current: HTMLElement | null = node; current; current = current.parentElement) {
      const style = win!.getComputedStyle(current)
      if (current.hidden || current.getAttribute('aria-hidden') === 'true' || style.display === 'none' ||
        style.visibility === 'hidden' || style.visibility === 'collapse' || style.opacity === '0') return false
    }
    return node.getBoundingClientRect().height > 0
  }
  const dialogs = Array.from(doc.querySelectorAll<HTMLElement>('[data-wf-preview-surface],[role="dialog"],[aria-modal="true"],dialog[open],[data-wf-role="modal"]'))
    .filter(visible)
  if (dialogs.length) {
    // Standalone dialog artboards should appear as a surface over their backdrop.
    const animations = dialogs.filter(node => !dialogs.some(parent => parent !== node && parent.contains(node))).map(node => {
      const surface = Array.from(node.querySelectorAll<HTMLElement>('[data-wf-dialog-content],.wf-modal-card,.modal-content,[role="dialog"],[aria-modal="true"],dialog[open]'))
        .find(visible) || node
      const opacity = Number(win.getComputedStyle(surface).opacity)
      return surface.animate([{ opacity: opacity * .8 }, { opacity }],
        { duration: 140, easing: 'ease-out' })
    })
    return () => animations.forEach(animation => animation.cancel())
  }
  if (direction === 'tab') return () => {}
  // A single fade on the host frame preserves the document's stacking and fixed
  // positioning. Animating individual content roots made separate cards drift
  // and temporarily changed the containing block of fixed descendants.
  const animation = frame!.animate([{ opacity: .92 }, { opacity: 1 }],
    { duration: 120, easing: 'ease-out' })
  return () => animation.cancel()
}
