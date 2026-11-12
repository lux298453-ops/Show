import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { DOMParser, parseHTML } from 'linkedom'

function load(name, deps = {}) {
  const module = { exports: {} }, context = { module, exports: module.exports, DOMParser, require: id => deps[id] }
  vm.runInNewContext(transformSync(readFileSync(`src/utils/${name}.ts`, 'utf8'), { loader: 'ts', format: 'cjs' }).code, context)
  return module.exports
}
const hit = load('interactionHit')
const { canvasBindings, installCanvasRouting } = load('canvasRouting', { './interactionHit': hit })
const { isTabNavigation, tabTargetIds } = load('previewTabs', { './interactionHit': hit })
const page = {
  html_content: '<div role="tablist"><button data-wf-role="tab" data-wf-tab="mode" data-wf-uid="collection" data-wf-element-id="10"><span data-wf-uid="label">收集</span></button></div><button data-wf-uid="detail" data-nav="详情">详情</button>',
  elements: [
    { id: 10 }, // Older noninteractive anchor on the same node must not shadow the new manual relation.
    { id: 20, interaction: { id: 2, action: 'navigate', target_page_id: 7, source: 'user', params: '{"domUid":"collection"}' } },
    { id: 21, interaction: { id: 1, action: 'navigate', target_page_id: 8, source: 'ai', params: '{"domUid":"collection"}' } },
    { id: 30, interaction: { id: 3, action: 'navigate', target_page_id: 9, params: '{"domUid":"detail"}' } },
  ],
}
function setup() {
  const { document: d } = parseHTML(`<html><body class="wf-interactive">${page.html_content}</body></html>`)
  const listeners = [], messages = []
  d.addEventListener = (_, fn) => listeners.push(fn)
  d.defaultView.parent = { postMessage: message => messages.push(message) }
  d.body.getBoundingClientRect = () => ({ left: 0, top: 0 })
  const routing = installCanvasRouting(d, canvasBindings(page))
  let localClicks = 0
  listeners.push(() => localClicks++)
  function click(selector) {
    let stopped = false
    const event = { target: d.querySelector(selector), clientX: 50, clientY: 60, preventDefault() {}, stopImmediatePropagation() { stopped = true } }
    for (const fn of listeners) { fn(event); if (stopped) break }
  }
  return { d, messages, routing, click, get localClicks() { return localClicks } }
}
test('manual UID relation wins over stale element ID and local tab capture, with one navigation', () => {
  const s = setup(); s.click('[data-wf-uid=label]')
  assert.equal(s.messages.length, 1)
  assert.equal(s.messages[0].page, '7')
  assert.deepEqual([...s.messages[0].uids], ['label', 'collection'])
  assert.equal(s.localClicks, 0)
})
test('binding refresh works without reloading; ordinary in-page tabs and edit mode remain local', () => {
  const s = setup()
  s.routing.update([{ id: '20', uid: 'collection', action: 'navigate', target: 12 }])
  s.click('[data-wf-uid=collection]'); assert.equal(s.messages[0].page, '12')
  s.routing.update([]); s.click('[data-wf-uid=collection]'); assert.equal(s.localClicks, 1)
  s.d.body.classList.remove('wf-interactive'); s.click('[data-wf-uid=detail]')
  assert.equal(s.messages.length, 1)
})
test('peer tabs are identified by DOM semantics, while detail buttons retain page navigation', () => {
  assert.equal(isTabNavigation(page, ['label']), true)
  assert.equal(isTabNavigation(page, undefined, 10), true)
  assert.equal(isTabNavigation(page, ['detail']), false)
  assert.equal(isTabNavigation(page, ['missing']), false)
  assert.deepEqual([...tabTargetIds(page)], [7])
  assert.equal(hit.findInteractionByDomUids({ ...page, elements: [...page.elements].reverse() }, ['collection']).id, 20)
})
test('a local modal close keeps the local handler instead of popping the page history', () => {
  const s = setup(), modal = s.d.createElement('div')
  modal.className = 'wf-modal'
  modal.innerHTML = '<button data-wf-uid="modal-close" data-action="close">关闭</button>'
  s.d.body.appendChild(modal)
  s.routing.update([{ id: '40', uid: 'modal-close', action: 'back' }])
  s.click('[data-wf-uid=modal-close]')
  assert.equal(s.messages.length, 0)
  assert.equal(s.localClicks, 1)
})

for (const action of ['popup', 'modal']) test(`a saved ${action} destination opens its actual page once`, () => {
  const s = setup()
  s.routing.update([{ id: '40', uid: 'detail', action, target: 12 }])
  s.click('[data-wf-uid=detail]')
  assert.equal(s.messages.length, 1)
  assert.equal(s.messages[0].type, 'wf-nav')
  assert.equal(s.messages[0].page, '12')
  assert.equal(s.localClicks, 0)
  const element = { id: 40, x: 10, y: 20, width: 100, height: 40,
    interaction: { action, target_page_id: 12, params: '{"dom_uid":"detail"}' } }
  const source = { html_content: page.html_content, elements: [element] }
  assert.equal(hit.findInteractionByDomUids(source, ['detail']), element)
  assert.equal(hit.hitInteractionElement(source, 50, 35), element)
})

test('modal actions without a page and nonrouting actions remain local', () => {
  const s = setup()
  for (const binding of [
    { id: '40', uid: 'detail', action: 'modal' },
    { id: '40', uid: 'detail', action: 'popup' },
    { id: '40', uid: 'detail', action: 'input_focus', target: 12 },
  ]) {
    s.routing.update([binding])
    s.click('[data-wf-uid=detail]')
  }
  assert.equal(s.messages.length, 0)
  assert.equal(s.localClicks, 3)
  const element = { id: 40, x: 10, y: 20, width: 100, height: 40,
    interaction: { action: 'input_focus', target_page_id: 12, params: '{"dom_uid":"detail"}' } }
  const source = { html_content: page.html_content, elements: [element] }
  assert.equal(hit.findInteractionByDomUids(source, ['detail']), null)
  assert.equal(hit.hitInteractionElement(source, 50, 35), null)
})

test('main navigation icons and labels are peer switches, including legacy navigation bars', () => {
  for (const container of ['nav', 'div role="navigation"', 'div data-wf-navigation="bar"', 'div data-wf-role="navigation-bar"', 'div role="tablist"']) {
    const p = { html_content: `<${container}><button data-wf-uid="nav" data-wf-element-id="8"><svg data-wf-uid="icon"></svg><span data-wf-uid="text">商城</span></button></${container.split(' ')[0]}><main><button data-wf-uid="detail">详情</button></main>` }
    assert.equal(isTabNavigation(p, ['icon']), true, container)
    assert.equal(isTabNavigation(p, ['text']), true, container)
    assert.equal(isTabNavigation(p, undefined, 8), true, container)
    assert.equal(isTabNavigation(p, ['detail']), false, container)
  }
})
