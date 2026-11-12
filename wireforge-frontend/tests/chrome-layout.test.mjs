import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { parseHTML } from 'linkedom'
import { transformSync } from 'esbuild'

const resource = '../wireforge-backend/src/main/resources/prototype/'
const runtime = readFileSync(resource + 'chrome-layout.js', 'utf8')
const item = (id, active = false) => `<button data-nav-item="1" data-wf-chrome-part="item" data-wf-uid="${id}" data-wf-element-id="${id}" data-nav="${id}" aria-current="${active ? 'page' : 'false'}"><svg data-wf-uid="${id}-icon"></svg><span data-wf-chrome-part="label">${id}</span><span class="badge">13</span></button>`
const run = document => vm.runInNewContext(runtime, { document })
const fixture = html => parseHTML(`<html><head></head><body><article id="content"><nav><button>本页筛选</button></nav></article>${html}</body></html>`).document

test('direct and nested navigation share equal-column layout without moving editable branches', () => {
  for (const nested of [false, true]) {
    const entries = [item('home'), item('shop', true), item('collection')]
    const doc = fixture(`<nav data-wf-shared-chrome="navigation">${nested ? `<div id="row"><div id="pair">${entries.slice(0, 2).map((html, i) => `<div id="slot-${i}">${html}</div>`).join('')}</div>${entries[2]}</div>` : entries.join('')}<span class="home-indicator" data-wf-uid="home-indicator"></span><span class="nav-divider"></span></nav>`)
    const before = [...doc.querySelectorAll('[data-wf-uid]')].map(node => ({ node, parent: node.parentNode, html: node.outerHTML }))
    const content = doc.querySelector('#content').outerHTML
    const selected = doc.querySelector('[aria-current=page]')
    let clicked = 0
    selected.addEventListener('click', () => clicked++)
    run(doc)
    assert.equal(doc.querySelector('nav[data-wf-shared-chrome]').style.getPropertyValue('--wf-chrome-columns'), '3')
    if (nested) {
      assert.equal(doc.querySelector('#row').style.getPropertyValue('--wf-chrome-columns'), '3')
      assert.equal(doc.querySelector('#pair').style.getPropertyValue('--wf-chrome-columns'), '2')
      assert.equal(doc.querySelector('#slot-0').style.getPropertyValue('--wf-chrome-columns'), '1')
    }
    assert.equal(doc.querySelectorAll('[data-wf-chrome-layout=indicator]').length, 1)
    assert.equal(doc.querySelector('.nav-divider').getAttribute('data-wf-chrome-layout'), 'decoration')
    for (const { node, parent, html } of before) {
      assert.equal(node.parentNode, parent)
      if (node.matches('[data-nav-item]')) assert.equal(node.outerHTML, html)
    }
    selected.click()
    assert.equal(clicked, 1)
    assert.equal(doc.querySelector('#content').outerHTML, content)
    const once = doc.documentElement.outerHTML
    run(doc)
    assert.equal(doc.documentElement.outerHTML, once)
  }
})

test('missing home indicators are added once and saved grouping attributes are refreshed', () => {
  const doc = fixture(`<nav data-wf-shared-chrome="navigation"><div data-wf-chrome-layout="group" style="--wf-chrome-columns:99">${item('home')}${item('shop', true)}</div></nav>`)
  run(doc)
  assert.equal(doc.querySelector('[data-wf-chrome-layout=group]').style.getPropertyValue('--wf-chrome-columns'), '2')
  assert.equal(doc.querySelector('[data-wf-chrome-layout=indicator]').getAttribute('aria-hidden'), 'true')
  run(doc)
  assert.equal(doc.querySelectorAll('[data-wf-chrome-layout=indicator]').length, 1)
  assert.equal(doc.querySelector('[aria-current=page]').getAttribute('data-wf-uid'), 'shop')
})

test('unshared local tabs and single-item bars retain their authored layout', () => {
  const doc = fixture(`<nav class="tabs">${item('local-a')}${item('local-b', true)}</nav><nav data-wf-shared-chrome="navigation">${item('only')}</nav>`)
  const before = doc.documentElement.outerHTML
  run(doc)
  assert.equal(doc.documentElement.outerHTML, before)
})

test('host runtime installs the same shared geometry on saved pages and replaces obsolete injected CSS', () => {
  const doc = fixture(`<nav data-wf-shared-chrome="navigation">${item('home')}${item('shop', true)}</nav>`)
  const module = { exports: {} }
  const code = transformSync(readFileSync('src/utils/prototypeControls.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code
  vm.runInNewContext(code, { module, exports: module.exports, require: name => readFileSync(name.replace('?raw', '').replace('../../../wireforge-backend/', '../wireforge-backend/'), 'utf8') })
  const { installPrototypeRuntime } = module.exports
  const obsolete = doc.createElement('style')
  obsolete.setAttribute('data-wf-chrome-layout-style', 'true')
  obsolete.textContent = 'old geometry'
  doc.head.appendChild(obsolete)
  installPrototypeRuntime(doc)
  assert.equal(doc.querySelectorAll('[data-wf-chrome-layout-style]').length, 1)
  assert.equal(doc.querySelector('[data-wf-chrome-layout-style]').textContent, readFileSync(resource + 'chrome-layout.css', 'utf8'))
  assert.ok(doc.querySelector('[data-wf-prototype-runtime]').textContent.includes(runtime))
})
