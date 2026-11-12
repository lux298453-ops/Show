// Saved HTML, backend rendering and canvas previews share these exact assets.
import css from '../../../wireforge-backend/src/main/resources/prototype/controls.css?raw'
import runtime from '../../../wireforge-backend/src/main/resources/prototype/controls.js?raw'
import prototypeRuntime from '../../../wireforge-backend/src/main/resources/prototype/runtime.js?raw'
import chromeSelectionCss from '../../../wireforge-backend/src/main/resources/prototype/chrome-selection.css?raw'
import chromeSelectionRuntime from '../../../wireforge-backend/src/main/resources/prototype/chrome-selection.js?raw'
import chromeLayoutCss from '../../../wireforge-backend/src/main/resources/prototype/chrome-layout.css?raw'
import chromeLayoutRuntime from '../../../wireforge-backend/src/main/resources/prototype/chrome-layout.js?raw'

export function installPrototypeControls(doc: Document): void {
  doc.querySelectorAll('style[data-wf-controls-style],script[data-wf-controls-runtime]').forEach(node => node.remove())
  const style = doc.createElement('style')
  style.setAttribute('data-wf-controls-style', '1')
  style.textContent = css
  const script = doc.createElement('script')
  script.setAttribute('data-wf-controls-runtime', '1')
  script.textContent = runtime
  // Register ahead of legacy/generated handlers, keeping the original element roots and IDs.
  doc.head.prepend(style, script)
}

export function withPrototypeControls(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  installPrototypeControls(doc)
  return '<!doctype html>\n' + doc.documentElement.outerHTML
}

/** Reinstall from source, never trust a saved script's data-wf-* marker. */
export function installPrototypeRuntime(doc: Document): void {
  doc.querySelectorAll('style[data-wf-chrome-selection-style],style[data-wf-chrome-layout-style]').forEach(node => node.remove())
  const sharedChrome = doc.querySelector('[data-wf-shared-chrome=navigation]')
  if (sharedChrome) {
    const style = doc.createElement('style')
    style.setAttribute('data-wf-chrome-selection-style', 'true')
    style.textContent = chromeSelectionCss
    doc.head.appendChild(style)
    const layoutStyle = doc.createElement('style')
    layoutStyle.setAttribute('data-wf-chrome-layout-style', 'true')
    layoutStyle.textContent = chromeLayoutCss
    doc.head.appendChild(layoutStyle)
  }
  const script = doc.createElement('script')
  script.setAttribute('data-wf-prototype-runtime', 'true')
  script.textContent = (sharedChrome ? chromeLayoutRuntime + '\n' + chromeSelectionRuntime + '\n' : '') + prototypeRuntime
  doc.body.appendChild(script)
}
