export interface PreviewOverlay {
  needsBackdrop: boolean
  restore: () => void
}

/** Isolate an already visible dialog/drawer in its original document, retaining its
 * layout, artwork, handlers and edited geometry. Only the preview is changed. */
export function preparePreviewOverlay(frame: HTMLIFrameElement | null | undefined): PreviewOverlay | null {
  const doc = frame?.contentDocument, win = doc?.defaultView
  if (!frame || !doc?.body || !win) return null
  const visible = (node: HTMLElement) => {
    for (let parent: HTMLElement | null = node; parent; parent = parent.parentElement) {
      const style = win.getComputedStyle(parent)
      if (parent.hidden || parent.getAttribute('aria-hidden') === 'true' || style.display === 'none' ||
        style.visibility === 'hidden' || style.visibility === 'collapse' || style.opacity === '0') return false
    }
    const rect = node.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0
  }
  const dialogs = Array.from(doc.querySelectorAll<HTMLElement>('[role="dialog"],[aria-modal="true"],dialog[open],[data-wf-role="modal"]'))
    .filter(node => !node.closest('.wf-autowire-modal') && visible(node))
  const drawers = Array.from(doc.querySelectorAll<HTMLElement>('[data-wf-role="sheet"],[data-wf-role="drawer"],.sheet,.bottom-sheet,.drawer,[data-wf-id$="-sheet"],[data-wf-id$="-drawer"]'))
    .filter(node => {
      if (node.closest('.wf-autowire-modal') || !visible(node)) return false
      if (node.matches('[data-wf-role="sheet"],[data-wf-role="drawer"]')) return true
      // Older generated artboards call a bottom drawer a "card". A class/name
      // alone is not enough: require an edge panel, dismissal and a background.
      const rect = node.getBoundingClientRect(), width = frame.clientWidth, height = frame.clientHeight
      const bottomSheet = rect.width >= width * .8 && rect.height >= height * .2 && rect.height < height * .94 &&
        rect.top > height * .04 && Math.abs(rect.bottom - height) <= Math.max(16, height * .04)
      const sideDrawer = rect.height >= height * .8 && rect.width >= width * .2 && rect.width < width * .94 &&
        (Math.abs(rect.left) <= 16 || Math.abs(rect.right - width) <= 16)
      const dismiss = node.querySelector('[data-action="back"],[data-action="close"],[data-dismiss],button[aria-label="收起"],button[aria-label="关闭"],button[aria-label="Close"]')
      const background = Array.from(node.parentElement?.children || []).some(sibling => sibling !== node &&
        sibling.matches('.backdrop,.background,[data-wf-role="backdrop"],[data-wf-id$="-background"]') && visible(sibling as HTMLElement))
      return (bottomSheet || sideDrawer) && !!dismiss && background
    })
  const candidates = [...new Set([...dialogs, ...drawers])]
  const roots = candidates.filter(node => !candidates.some(parent => parent !== node && parent.contains(node)))
  if (!roots.length) return null

  const undo: (() => void)[] = [], shells = new Set<HTMLElement>([doc.documentElement, doc.body])
  const styled = new Set<HTMLElement>()
  const setStyle = (node: HTMLElement, name: string, value: string) => {
    if (!styled.has(node)) {
      // Restoring a shorthand alone can lose pre-existing background longhands.
      const old = node.getAttribute('style')
      undo.push(() => { if (old == null) node.removeAttribute('style'); else node.setAttribute('style', old) })
      styled.add(node)
    }
    node.style.setProperty(name, value, 'important')
  }
  const mark = (node: HTMLElement, name: string) => {
    const old = node.getAttribute(name)
    undo.push(() => { if (old == null) node.removeAttribute(name); else node.setAttribute(name, old) })
    node.setAttribute(name, '')
  }
  for (const root of roots) {
    mark(root, 'data-wf-preview-surface')
    for (let parent = root.parentElement; parent; parent = parent.parentElement) shells.add(parent)
  }
  for (const shell of shells) {
    mark(shell, 'data-wf-preview-shell')
    for (const name of ['background', 'box-shadow']) setStyle(shell, name, name === 'background' ? 'transparent' : 'none')
    setStyle(shell, 'border-color', 'transparent')
    for (const child of Array.from(shell.children) as HTMLElement[]) {
      // This transparent runtime surface owns the saved blank-area back action.
      // Keep it on every opening, including cached documents with no new runtime sync.
      if (shells.has(child) || roots.includes(child) || child.matches('script,style,link,meta,.wf-page-dismiss[data-wf-inject="backdrop-return"]')) continue
      setStyle(child, 'visibility', 'hidden')
      setStyle(child, 'pointer-events', 'none')
    }
  }
  const style = doc.createElement('style')
  style.setAttribute('data-wf-inject', 'preview-overlay')
  style.textContent = '[data-wf-preview-shell]::before,[data-wf-preview-shell]::after{visibility:hidden!important}'
  doc.head.appendChild(style)
  undo.push(() => style.remove())
  setStyle(frame, 'background', 'transparent')
  const needsBackdrop = !roots.some(root => {
    const rect = root.getBoundingClientRect(), css = win.getComputedStyle(root)
    const background = css.backgroundColor || 'transparent'
    return rect.width >= frame.clientWidth * .9 && rect.height >= frame.clientHeight * .9 &&
      ((background !== 'transparent' && background !== 'rgba(0, 0, 0, 0)') || !!(css.backgroundImage && css.backgroundImage !== 'none'))
  })
  let restored = false
  return { needsBackdrop, restore() {
    if (restored) return
    restored = true
    undo.reverse().forEach(fn => fn())
  } }
}
