/** Self-contained so the same implementation can run inside the canvas iframe. */
export function installNavigationEditing(doc: Document, pageId: number) {
  const bars = '.wf-canonical-nav,.wf-tabbar,.wf-ios-tab-bar,.wf-pose-dock,[data-wf-navigation="bar"]'
  const items = '.wf-nav-item,.wf-tabit,.wf-pose-option,[data-nav-item]'
  const partSelector = '[data-wf-edit-role="navigation-icon"],[data-wf-edit-role="navigation-label"]'
  function role(el: Element | null) { return el?.getAttribute('data-wf-edit-role') || '' }
  function item(node: Element | null) { return node?.closest<HTMLElement>('[data-wf-edit-role="navigation-item"]') || null }
  function part(node: Element | null) { return node?.closest<HTMLElement>(partSelector) || null }
  function isPart(node: Element | null) { return /navigation-(icon|label)/.test(role(node)) }
  function uid(el: Element, fallback: string) {
    if (!el.getAttribute('data-wf-uid')) el.setAttribute('data-wf-uid', fallback)
    return el.getAttribute('data-wf-uid')!
  }
  function mark(el: HTMLElement, kind: string, identity: string) {
    el.setAttribute('data-wf-edit-role', kind); uid(el, identity)
    if (kind === 'navigation' || kind === 'navigation-item') el.setAttribute('data-wf-component', '1')
  }
  function normalize() {
    doc.querySelectorAll<HTMLElement>(bars).forEach((bar, barIndex) => {
      mark(bar, 'navigation', 'wf-nav-p' + pageId + '-bar-' + barIndex)
      const ownerUid = bar.getAttribute('data-wf-uid')!
      Array.from(bar.querySelectorAll<HTMLElement>(items)).filter(n => n.closest(bars) === bar).forEach((entry, index) => {
        mark(entry, 'navigation-item', ownerUid + '-item-' + (entry.getAttribute('data-wf-element-id') || index))
        entry.setAttribute('data-nav-item', '1')
        const entryUid = entry.getAttribute('data-wf-uid')!
        let graphic = entry.querySelector<HTMLElement>('.wf-nav-graphic,.wf-tabic,.wf-pose-state,[data-wf-edit-role="navigation-icon"]')
        if (!graphic) {
          const image = entry.querySelector('svg,img')
          if (image) { graphic = doc.createElement('span'); image.parentNode!.insertBefore(graphic, image); graphic.appendChild(image) }
        }
        if (graphic) {
          mark(graphic, 'navigation-icon', entryUid + '-icon')
          graphic.classList.add('wf-vector-icon')
          graphic.setAttribute('data-wf-asset', 'icon')
          graphic.style.position ||= 'relative'
          graphic.style.flexShrink = '0'
          graphic.style.display = 'flex'; graphic.style.alignItems = 'center'; graphic.style.justifyContent = 'center'
          const image = graphic.querySelector<SVGElement | HTMLImageElement>('svg,img')
          if (image) {
            const w = parseFloat(graphic.style.width) || parseFloat(image.getAttribute('width') || '') || image.getBoundingClientRect().width || 24
            const h = parseFloat(graphic.style.height) || parseFloat(image.getAttribute('height') || '') || image.getBoundingClientRect().height || w
            graphic.style.width ||= w + 'px'; graphic.style.height ||= h + 'px'
            image.style.width = '100%'; image.style.height = '100%'; image.style.maxWidth = '100%'; image.style.maxHeight = '100%'
          }
        }
        const caption = entry.querySelector<HTMLElement>('.wf-nav-label,.wf-pose-label,[data-wf-edit-role="navigation-label"]')
          || Array.from(entry.children).find(n => n !== graphic && !graphic?.contains(n) && n.tagName === 'SPAN'
            && !n.classList.contains('wf-binding-alias') && !!n.textContent?.trim()) as HTMLElement | undefined
        if (caption) {
          mark(caption, 'navigation-label', entryUid + '-label'); caption.style.position ||= 'relative'
          caption.style.flexShrink = '0'; caption.style.whiteSpace = 'nowrap'
          if (caption.textContent?.trim()) entry.setAttribute('data-wf-nav-label', caption.textContent.trim())
        }
        // Old markup sometimes puts the same navigation on the icon. Keep a durable owner on the item.
        if (!entry.matches('[data-nav],[data-modal],[data-action]')) {
          const action = entry.querySelector('[data-nav],[data-modal]')
          if (action) for (const key of ['data-nav', 'data-modal']) if (action.hasAttribute(key)) entry.setAttribute(key, action.getAttribute(key)!)
        }
      })
    })
  }
  function describe(node: Element | null) {
    const kind = role(node), entry = item(node), name = entry?.getAttribute('data-wf-nav-label') || '导航项'
    return kind === 'navigation' ? '导航栏' : kind === 'navigation-item' ? '导航项 · ' + name
      : kind === 'navigation-icon' ? name + ' · 图标' : kind === 'navigation-label' ? name + ' · 文字' : ''
  }
  function target(node: Element | null, selected: Element | null) {
    const entry = item(node)
    if (entry) return isPart(selected) && item(selected) === entry ? (part(node) || entry) : entry
    return node?.closest<HTMLElement>('[data-wf-edit-role="navigation"]') || null
  }
  function preservePartBindings(node: HTMLElement, includeRoot = true) {
    const owner = item(node)
    if (!owner || !isPart(node)) return
    for (const source of [...(includeRoot ? [node] : []), ...Array.from(node.querySelectorAll('[data-wf-element-id],[data-wf-uid]'))]) {
      const id = source.getAttribute('data-wf-element-id'), sourceUid = source.getAttribute('data-wf-uid')
      if (!id && !sourceUid) continue
      if (id && id === owner.getAttribute('data-wf-element-id') && !sourceUid) continue
      const alias = doc.createElement('span'); alias.className = 'wf-binding-alias'
      alias.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;display:none'
      if (id && id !== owner.getAttribute('data-wf-element-id')) alias.setAttribute('data-wf-element-id', id)
      if (sourceUid) alias.setAttribute('data-wf-uid', sourceUid)
      owner.appendChild(alias)
    }
  }
  function replaceIcon(node: HTMLElement, src: string) {
    if (role(node) !== 'navigation-icon') return false
    preservePartBindings(node, false)
    node.setAttribute('data-wf-nav-icon-src', src)
    if (/^\/api\/assets\/icon-[a-z0-9_-]+$/i.test(src)) {
      // Built-in monochrome icons inherit the item's active color and remain recolorable.
      const mask = doc.createElement('span'); mask.setAttribute('data-wf-nav-mask', '1')
      mask.style.cssText = 'width:100%;height:100%;display:block;background:currentColor;pointer-events:none;mask-size:contain;mask-repeat:no-repeat;mask-position:center;-webkit-mask-size:contain;-webkit-mask-repeat:no-repeat;-webkit-mask-position:center'
      mask.style.setProperty('mask-image', 'url(' + JSON.stringify(src) + ')')
      mask.style.setProperty('-webkit-mask-image', 'url(' + JSON.stringify(src) + ')')
      node.replaceChildren(mask)
    } else {
      const image = doc.createElement('img'); image.src = src; image.alt = ''
      image.style.cssText = 'width:100%;height:100%;object-fit:contain;display:block;pointer-events:none'
      node.replaceChildren(image)
    }
    node.style.backgroundImage = ''; return true
  }
  function constrainBox(node: HTMLElement, left: number, top: number, width: number, height: number,
    origin: { left: number; top: number; boxL: number; boxT: number }) {
    const owner = item(node)
    if (!isPart(node) || !owner) return { left, top, width, height }
    const r = owner.getBoundingClientRect(), sx = doc.defaultView?.scrollX || 0, sy = doc.defaultView?.scrollY || 0
    if (!r.width || !r.height) return { left, top, width, height }
    width = Math.min(width, r.width); height = Math.min(height, r.height)
    const minL = origin.left + r.left + sx - origin.boxL, minT = origin.top + r.top + sy - origin.boxT
    return { left: Math.max(minL, Math.min(minL + r.width - width, left)),
      top: Math.max(minT, Math.min(minT + r.height - height, top)), width, height }
  }
  return { normalize, role, item, part, isPart, target, describe, preservePartBindings, replaceIcon, constrainBox }
}
