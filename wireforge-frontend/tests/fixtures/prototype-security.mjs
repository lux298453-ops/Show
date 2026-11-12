import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { webcrypto } from 'node:crypto'
import { transformSync } from 'esbuild'
import createDOMPurify from 'dompurify'
import { JSDOM } from 'jsdom'

export function createTestPrototypeSecurity() {
  const { window } = new JSDOM('<!doctype html><html><head></head><body></body></html>', {
    url: 'https://workbench.example.test', runScripts: 'outside-only',
  })
  const module = { exports: {} }
  const context = { module, exports: module.exports, require: id => id.endsWith('?raw')
    ? readFileSync('node_modules/dompurify/dist/purify.min.js', 'utf8') : createDOMPurify(window),
    DOMParser: window.DOMParser, crypto: webcrypto, window, document: window.document, HTMLIFrameElement: window.HTMLIFrameElement }
  vm.runInNewContext(transformSync(readFileSync('src/utils/prototypeSecurity.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code, context)
  return { ...module.exports, window }
}
