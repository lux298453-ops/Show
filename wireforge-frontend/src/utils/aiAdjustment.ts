/** AI adjustments operate on source HTML, never on a preview iframe's injected runtime. */
export interface AiElementTarget {
  id: string
  tag: string
  label: string
  html: string
  path: number[]
  layoutContext: string
}

const identityAttributes = ['data-wf-select-id', 'data-wf-element-id', 'data-wf-id', 'data-wf-uid', 'id']
const injectedSelector = '[data-wf-inject],#wf-inspector-style'

function sourceChildren(node: Element) {
  return Array.from(node.children).filter(child => !child.matches(injectedSelector))
}

function pathOf(node: Element): number[] {
  const path: number[] = []
  while (node.parentElement && node.tagName.toLowerCase() !== 'body') {
    path.unshift(sourceChildren(node.parentElement).indexOf(node))
    node = node.parentElement
  }
  return path
}

function atPath(doc: Document, path: number[]): Element | null {
  let node: Element | null = doc.body
  for (const index of path) node = node ? sourceChildren(node)[index] || null : null
  return node
}

function byIdentity(doc: Document, node: Element): Element | null {
  for (const attribute of identityAttributes) {
    const value = node.getAttribute(attribute)
    if (!value) continue
    // Compare values directly: generated IDs can contain quotes or CSS metacharacters.
    const matches = Array.from(doc.querySelectorAll(`[${attribute}]`)).filter(el => el.getAttribute(attribute) === value)
    if (matches.length === 1) return matches[0]
  }
  return null
}

export function captureAiElement(sourceHtml: string, clicked: Element): AiElementTarget | null {
  const doc = new DOMParser().parseFromString(sourceHtml, 'text/html')
  const identityMatch = byIdentity(doc, clicked)
  const target = identityMatch || atPath(doc, pathOf(clicked))
  if (!target || target === doc.body || target.tagName !== clicked.tagName) return null
  // A runtime-created control must not silently target an unrelated source node.
  if (!identityMatch && target.textContent?.trim() !== clicked.textContent?.trim()) return null
  const path = pathOf(target)
  const id = identityAttributes.map(attr => target.getAttribute(attr)).find(Boolean) || `wf-source-${path.join('-')}`
  const label = (target.getAttribute('aria-label') || target.getAttribute('alt') || target.textContent || target.tagName).trim().slice(0, 40)
  const parent = target.parentElement?.cloneNode(true) as Element | undefined
  parent?.querySelectorAll('script').forEach(el => el.remove())
  const styles = Array.from(doc.querySelectorAll('style')).map(el => el.textContent || '').join('\n').slice(0, 10000)
  return { id, tag: target.tagName.toLowerCase(), label, html: target.outerHTML, path,
    layoutContext: `父容器（只读参考）：\n${parent?.outerHTML.slice(0, 4000) || ''}\n页面样式（只读参考）：\n${styles}` }
}

export function patchAiElement(sourceHtml: string, target: AiElementTarget, action: 'REPLACE' | 'DELETE', snippet: string): string {
  const doc = new DOMParser().parseFromString(sourceHtml, 'text/html')
  const originalTemplate = doc.createElement('template')
  originalTemplate.innerHTML = target.html
  const original = originalTemplate.content.firstElementChild
  if (!original) throw new Error('未找到原始元素，请重新选中')
  const node = byIdentity(doc, original) || atPath(doc, target.path)
  if (!node || node.outerHTML !== original.outerHTML) throw new Error('目标元素已变化，请重新选中后调整')
  if (action === 'DELETE') node.remove()
  else {
    const template = doc.createElement('template')
    template.innerHTML = snippet.trim()
    if (template.content.children.length !== 1 || Array.from(template.content.childNodes).some(n => n.nodeType === 3 && n.textContent?.trim())) {
      throw new Error('AI 返回的元素结构无效，未修改画板')
    }
    const replacement = template.content.firstElementChild!
    if (replacement.matches('script,style,link,html,head,body')) throw new Error('AI 返回了无效的元素类型')
    for (const attribute of identityAttributes) {
      if (node.hasAttribute(attribute)) replacement.setAttribute(attribute, node.getAttribute(attribute)!)
    }
    node.replaceWith(replacement)
  }
  // Restore deletion from a full source snapshot, including its parent and sibling position.
  return `${doc.doctype ? '<!DOCTYPE html>\n' : ''}${doc.documentElement.outerHTML}`
}

export function isTextEditingTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  return !!el && (el.isContentEditable || !!el.closest?.('input,textarea,select,[contenteditable="true"],[contenteditable=""]'))
}
