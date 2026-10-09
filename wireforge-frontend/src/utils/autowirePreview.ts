import type { Element as PageElement, Page } from '../types'
import type { NavigationInfo } from '../types/autowire'

export interface PreviewRect { x: number; y: number; width: number; height: number }
export interface PreviewHighlight { rect: PreviewRect; basis: 'node' | 'navigation' | 'element' }

/** A review preview is inert HTML, never the editor or the prototype runtime. */
export function createReadonlyPreviewHtml(html: string, resolveFileUrl: (url: string) => string = url => url): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script,iframe,frame,frameset,object,embed,applet,portal,base,template,meta[http-equiv],link:not([rel="stylesheet"]),[data-wf-inject],#wf-edit-toast,#wf-transform-box,#wf-marquee-box').forEach(node => node.remove())

  const resourceUrl = (value: string): string => {
    const clean = value.trim()
    if (/^(javascript|vbscript|file):/i.test(clean) || /[\u0000-\u001f]/.test(clean)) return ''
    if (/^data:/i.test(clean) && !/^data:image\/(?:png|jpe?g|gif|webp|avif|svg\+xml)[;,]/i.test(clean)) return ''
    // Uploads are hosted by the API origin, not necessarily the Pages origin.
    if (/^\/?uploads\//i.test(clean)) return resolveFileUrl(clean.startsWith('/') ? clean : `/${clean}`)
    return clean
  }
  const rewriteCss = (css: string) => css.replace(/url\(\s*(['"]?)(.*?)\1\s*\)/gi, (_match, _quote, url: string) => {
    const safe = resourceUrl(url)
    return `url("${safe.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}")`
  })
  doc.querySelectorAll('*').forEach(node => {
    for (const attribute of Array.from(node.attributes)) {
      const name = attribute.name.toLowerCase()
      if (name.startsWith('on') || ['contenteditable', 'autofocus', 'formaction', 'formtarget', 'action', 'target', 'srcdoc', 'ping'].includes(name)) {
        node.removeAttribute(attribute.name)
      } else if (name === 'src' || name === 'poster' || name === 'background' || ((name === 'href' || name === 'xlink:href') && ['image', 'use', 'link'].includes(node.localName.toLowerCase()))) {
        const safe = resourceUrl(attribute.value)
        if (safe) node.setAttribute(attribute.name, safe)
        else node.removeAttribute(attribute.name)
      } else if (name === 'href' || name === 'xlink:href' || name === 'srcset') {
        // Static src remains available; srcset may contain non-image navigation URLs.
        node.removeAttribute(attribute.name)
      } else if (name === 'style') node.setAttribute('style', rewriteCss(attribute.value))
    }
    if (node.matches('input,textarea,select,button,a,[tabindex]')) node.setAttribute('tabindex', '-1')
    node.classList.remove('wf-edit-mode', 'wf-multi-selected', 'wf-spotlight-target')
  })
  doc.querySelectorAll('style').forEach(node => { node.textContent = rewriteCss(node.textContent || '') })
  doc.body.setAttribute('inert', '')
  const policy = doc.createElement('meta')
  policy.httpEquiv = 'Content-Security-Policy'
  policy.content = "default-src 'none'; script-src 'none'; style-src 'unsafe-inline' http: https:; img-src http: https: data: blob:; font-src http: https: data:; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'"
  doc.head.prepend(policy)
  const guard = doc.createElement('style')
  guard.dataset.wfReadonlyPreview = 'true'
  guard.textContent = 'html{overflow:hidden!important}body{overflow-x:hidden!important}.wf-layer-hidden{visibility:hidden!important}body *{caret-color:transparent!important}a,button,input,textarea,select,[contenteditable],[data-nav],[data-modal],[data-action]{pointer-events:none!important}#wf-edit-toast,#wf-transform-box,#wf-marquee-box,.wf-nav-hotspot{display:none!important}'
  doc.head.appendChild(guard)
  return '<!doctype html>\n' + doc.documentElement.outerHTML
}

export function previewDimensions(page?: Page): { width: number; height: number } {
  const positive = (value: number | undefined, fallback: number) => Number.isFinite(value) && value! > 0 ? Math.min(value!, 100000) : fallback
  return { width: positive(page?.canvas_width, 375), height: positive(page?.canvas_height, 812) }
}

function validRect(rect: PreviewRect | undefined, width: number, height: number): PreviewRect | null {
  if (!rect || ![rect.x, rect.y, rect.width, rect.height].every(Number.isFinite) || rect.width <= 0 || rect.height <= 0) return null
  const right = Math.min(width, rect.x + rect.width), bottom = Math.min(height, rect.y + rect.height)
  const x = Math.max(0, rect.x), y = Math.max(0, rect.y)
  return right > x && bottom > y ? { x, y, width: right - x, height: bottom - y } : null
}

function elementDomUid(element?: PageElement): string {
  if (!element) return ''
  try {
    const params = JSON.parse(element.interaction?.params || '{}')
    const uid = params.domUid || params.dom_uid || params.props_html_uid
    if (typeof uid === 'string') return uid
  } catch { /* Older interactions may store free text rather than JSON. */ }
  // Some imported snapshots expose the persisted HTML identifier directly.
  const uid = (element as PageElement & { props_html_uid?: string }).props_html_uid
  return typeof uid === 'string' ? uid : ''
}

function exactNode(doc: Document, attribute: string, value: string): globalThis.Element | null {
  const matches = Array.from(doc.querySelectorAll(`[${attribute}]`)).filter(node => !node.classList.contains('wf-nav-hotspot') && node.getAttribute(attribute) === value)
  return matches.length === 1 ? matches[0]! : null
}

/** Exact DOM identity or persisted geometry only. Labels are never used to guess a click area. */
export function locatePreviewHighlight(page: Page, elementId?: number, navigation?: NavigationInfo | null, doc?: Document, contentHeight?: number): PreviewHighlight | null {
  const { width, height: pageHeight } = previewDimensions(page)
  const height = Math.max(pageHeight, contentHeight || 0)
  const element = page.elements.find(entry => entry.id === elementId)
  if (doc && elementId != null) {
    const id = String(elementId)
    const uid = elementDomUid(element)
    const node = (navigation ? exactNode(doc, 'data-wf-nav-owner', id) : null) || exactNode(doc, 'data-wf-element-id', id) || (uid ? exactNode(doc, 'data-wf-uid', uid) : null)
    if (node) {
      const bounds = node.getBoundingClientRect()
      const rect = validRect({ x: bounds.left + (doc.defaultView?.scrollX || 0), y: bounds.top + (doc.defaultView?.scrollY || 0), width: bounds.width, height: bounds.height }, width, height)
      // An explicitly hidden node is not an actionable visible area.
      const style = doc.defaultView?.getComputedStyle(node)
      if (!rect || style?.visibility === 'hidden' || style?.display === 'none') return null
      return { rect, basis: 'node' }
    }
  }
  if (navigation) {
    const rect = validRect(navigation, width, height)
    if (rect) return { rect, basis: 'navigation' }
  }
  if (element) {
    const rect = validRect(element, width, height)
    if (rect) return { rect, basis: 'element' }
  }
  return null
}
