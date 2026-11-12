import DOMPurify from 'dompurify'
import type { Config } from 'dompurify'
import purifierSource from 'dompurify/purify.min.js?raw'

const sanitizerConfig: Config = {
  USE_PROFILES: { html: true, svg: true, svgFilters: true },
  ADD_TAGS: ['use'],
  FORBID_TAGS: ['script', 'iframe', 'frame', 'frameset', 'object', 'embed', 'applet', 'portal', 'base', 'meta', 'link', 'template', 'foreignObject'],
  FORBID_ATTR: ['nonce', 'srcdoc', 'is', 'autofocus', 'action', 'formaction', 'formtarget', 'target', 'ping'],
  ALLOW_DATA_ATTR: true,
}

/** Self-contained: the same rule runs in the host and the trusted frame runtime. */
function restrictPrototypeLinks(root: ParentNode): void {
  root.querySelectorAll('[href],[xlink\\:href]').forEach(node => {
    for (const attr of ['href', 'xlink:href']) {
      if (node.hasAttribute(attr) && !node.getAttribute(attr)!.trim().startsWith('#')
          && node.localName !== 'image') node.removeAttribute(attr)
    }
  })
}

/** Self-contained and installed before any prototype markup is parsed. */
function installPrototypeNavigationGuard(doc: Document): void {
  const stopNavigation = (event: Event) => {
    const target = event.target as Element | null
    if (target?.closest?.('a,area')) event.preventDefault()
  }
  // Keep data-nav handlers running; block only the browser's native navigation.
  doc.addEventListener('click', stopNavigation, true)
  doc.addEventListener('auxclick', stopNavigation, true)
  doc.addEventListener('submit', event => event.preventDefault(), true)
}

/** Component samples render in the host, so stylesheets must not escape into the workbench. */
export function sanitizeComponentPreview(html: string): string {
  const fragment = DOMPurify.sanitize(html, {
    ...sanitizerConfig,
    FORBID_TAGS: [...sanitizerConfig.FORBID_TAGS!, 'style'],
    RETURN_DOM_FRAGMENT: true,
  })
  restrictPrototypeLinks(fragment)
  const container = document.createElement('div')
  container.appendChild(fragment)
  return container.innerHTML
}

/** Model output and saved HTML are data, never executable application code.
 * Keep DOM access for the editor, and install trusted runtimes only after this boundary. */
export function createPrototypeDocument(html: string): Document {
  const source = new DOMParser().parseFromString(html, 'text/html')
  const data: { id: string; text: string }[] = []
  source.querySelectorAll('script[type="application/json"][id]').forEach(node => {
    try {
      const value = JSON.parse(node.textContent || '')
      data.push({ id: node.id, text: JSON.stringify(value).replace(/</g, '\\u003c') })
    } catch { /* Invalid imported JSON is not a runtime. */ }
  })
  const clean = DOMPurify.sanitize(source.documentElement.outerHTML, {
    ...sanitizerConfig,
    WHOLE_DOCUMENT: true,
  })
  const doc = new DOMParser().parseFromString(clean, 'text/html')
  // Do not let an anchor navigate this same-origin frame to an untrusted HTML upload.
  restrictPrototypeLinks(doc)
  for (const item of data) {
    const script = doc.createElement('script')
    script.type = 'application/json'
    script.id = item.id
    script.textContent = item.text
    doc.head.appendChild(script)
  }
  return doc
}

/** Called after application-owned scripts have been installed. Never call on raw HTML. */
export function securePrototypeDocument(doc: Document): void {
  const runtime = doc.createElement('script')
  runtime.setAttribute('data-wf-inject', 'security-runtime')
  runtime.setAttribute('data-wf-security-runtime', 'true')
  // The bundled purifier is application-owned code. Keep it in the frame so edits
  // are safe before load, without sharing a parent callback or waiting for images.
  runtime.textContent = `${purifierSource}\n;(function(){
    var purifier = window.DOMPurify;
    var config = ${JSON.stringify(sanitizerConfig)};
    var restrictLinks = ${restrictPrototypeLinks.toString()};
    Object.defineProperty(window, '__wfSanitizeFragment', { value: function(html){
      var fragment = purifier.sanitize(String(html || ''), Object.assign({}, config, { RETURN_DOM_FRAGMENT: true }));
      restrictLinks(fragment);
      return fragment;
    }});
    (${installPrototypeNavigationGuard.toString()})(document);
  })();`
  doc.head.prepend(runtime)
  const nonce = Array.from(crypto.getRandomValues(new Uint8Array(24)), n => n.toString(16).padStart(2, '0')).join('')
  doc.querySelectorAll('script:not([type="application/json"])').forEach(script => script.setAttribute('nonce', nonce))
  const policy = doc.createElement('meta')
  policy.httpEquiv = 'Content-Security-Policy'
  policy.setAttribute('data-wf-security', 'true')
  policy.setAttribute('data-wf-inject', 'security')
  policy.content = `default-src 'none'; script-src 'nonce-${nonce}'; script-src-attr 'none'; style-src 'unsafe-inline'; img-src http: https: data: blob:; font-src http: https: data:; media-src http: https: data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'`
  doc.head.prepend(policy)
}

/** Only the currently mounted, application-managed frame may issue editor commands. */
export function isPrototypeFrameMessage(event: MessageEvent): boolean {
  if (event.origin !== window.location.origin || !event.source) return false
  try {
    const frame = (event.source as Window).frameElement
    return frame instanceof HTMLIFrameElement && frame.ownerDocument === document
      && frame.title === 'prototype-html' && frame.contentWindow === event.source
  } catch { return false }
}
