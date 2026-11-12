import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { parseHTML } from 'linkedom'

const runtime = readFileSync('../wireforge-backend/src/main/resources/prototype/runtime.js', 'utf8')
const controls = readFileSync('../wireforge-backend/src/main/resources/prototype/controls.js', 'utf8')
const markup = `<button data-wf-uid="buy" data-modal="detail">购买</button>
<div id="wf-modal-detail" class="wf-modal"><button data-action="close">关闭</button><button data-wf-feedback="已确认" data-wf-close-modal="true">确认</button></div>
<button id="tab-a" data-wf-tab="products" aria-controls="panel-a" class="active" aria-pressed="true">全部</button>
<button id="tab-b" data-wf-tab="products" aria-controls="panel-b" aria-pressed="false">典藏</button>
<div id="panel-a">全部内容</div><div id="panel-b" hidden>典藏内容</div>
<input data-wf-search="products"><div id="products"><article data-wf-search-text="幽筱">幽筱</article><article data-wf-search-text="苍然">苍然</article></div>
<button data-nav="商品详情">详情</button><div id="wf-prototype-feedback" hidden></div>`

function setup(body = markup, hosted = false, interactive = false) {
  const { document } = parseHTML(`<html><head>${hosted ? '<script data-wf-inject></script>' : ''}</head><body>${body}</body></html>`)
  if (interactive) document.body.classList.add('wf-interactive')
  const handlers = {}, messages = [], observers = [], window = { document }
  document.addEventListener = (name, handler) => (handlers[name] ??= []).push(handler)
  const parent = hosted ? { postMessage: data => messages.push(data) } : window
  const context = { window, document, parent, setTimeout: () => 1, clearTimeout() {}, MutationObserver: class {
    constructor(callback) { observers.push(callback) }
    observe() {}
  } }
  vm.runInNewContext(controls, context)
  vm.runInNewContext(runtime, context)
  function emit(name, target, extra = {}) {
    let stopped = false
    const event = { target, preventDefault() {}, stopImmediatePropagation() { stopped = true }, ...extra }
    for (const handler of handlers[name] || []) { handler(event); if (stopped) break }
  }
  return { document, handlers, emit, messages, added: node => observers.forEach(callback => callback([{ addedNodes: [node] }])) }
}

test('static modal opens, closes and confirms with feedback without rebuilding its buttons', () => {
  const { document: d, emit } = setup()
  const buy = d.querySelector('[data-wf-uid=buy]'), modal = d.querySelector('.wf-modal')
  emit('click', buy); assert.equal(modal.classList.contains('wf-show'), true)
  emit('click', d.querySelector('[data-action=close]')); assert.equal(modal.classList.contains('wf-show'), false)
  emit('click', buy); emit('click', d.querySelector('[data-wf-feedback]'))
  assert.equal(modal.classList.contains('wf-show'), false)
  assert.equal(d.querySelector('#wf-prototype-feedback').textContent, '已确认')
  assert.equal(d.querySelector('[data-wf-uid=buy]'), buy)
})

test('tabs and search change static node state and keep node identity', () => {
  const { document: d, emit } = setup(), card = d.querySelector('article')
  emit('click', d.querySelector('#tab-b'))
  assert.equal(d.querySelector('#tab-a').getAttribute('aria-pressed'), 'false')
  assert.equal(d.querySelector('#tab-b').getAttribute('aria-selected'), 'true')
  assert.equal(d.querySelector('#panel-a').hidden, true)
  assert.equal(d.querySelector('#panel-b').hidden, false)
  const input = d.querySelector('input'); input.value = '幽筱'; emit('input', input)
  assert.equal(card.hidden, false); assert.equal(card.nextElementSibling.hidden, true)
  assert.equal(d.querySelector('article'), card)
})

test('canvas edit mode suppresses all prototype state changes', () => {
  const { document: d, emit } = setup(markup, true)
  const before = d.body.innerHTML
  emit('click', d.querySelector('[data-modal]')); emit('click', d.querySelector('#tab-b'))
  const input = d.querySelector('input'); input.value = '不存在'; const withValue = d.body.innerHTML; emit('input', input)
  assert.equal(d.querySelector('.wf-modal').classList.contains('wf-show'), false)
  assert.equal(d.querySelector('#tab-b').getAttribute('aria-pressed'), 'false')
  assert.equal(d.body.innerHTML, withValue)
  assert.ok(before.includes('data-wf-uid="buy"'))
})

test('host preview handles navigation and modal exactly once while micro-interactions still work', () => {
  const { document: d, emit, handlers, messages } = setup(markup, true, true)
  let hostCalls = 0
  handlers.click.push(event => {
    hostCalls++
    const target = event.target
    if (target.hasAttribute('data-modal')) d.querySelector('.wf-modal').classList.toggle('wf-show')
  })
  emit('click', d.querySelector('[data-modal]'))
  assert.equal(d.querySelector('.wf-modal').classList.contains('wf-show'), true)
  emit('click', d.querySelector('[data-nav]'))
  assert.equal(hostCalls, 2); assert.equal(messages.length, 0)
  emit('click', d.querySelector('#tab-b'))
  assert.equal(hostCalls, 2); assert.equal(d.querySelector('#tab-b').getAttribute('aria-pressed'), 'true')
})

test('saving and reloading an edited label does not restore the initial content', () => {
  const first = setup(), button = first.document.querySelector('[data-wf-uid=buy]')
  button.textContent = '购买·已编辑'
  const reloaded = setup(first.document.body.innerHTML)
  assert.equal(reloaded.document.querySelector('[data-wf-uid=buy]').textContent, '购买·已编辑')
})

test('legacy generated OFF switches migrate, keep their IDs and toggle exactly once', () => {
  const { document: d, emit, handlers } = setup('<button class="switch" data-wf-toggle="pendant" data-wf-uid="my-toggle" aria-pressed="false" aria-label="挂件开关"></button>', true, true)
  const el = d.querySelector('button')
  let oldHandlerCalls = 0; handlers.click.push(() => oldHandlerCalls++)
  assert.equal(el.getAttribute('data-wf-control'), 'switch')
  assert.equal(el.getAttribute('aria-checked'), 'false')
  assert.equal(el.style.width, '44px'); assert.equal(el.style.height, '25px')
  emit('click', el); assert.equal(el.getAttribute('aria-checked'), 'true')
  emit('click', el); assert.equal(el.getAttribute('aria-checked'), 'false')
  assert.equal(oldHandlerCalls, 0)
  assert.equal(el.getAttribute('data-wf-uid'), 'my-toggle')
  assert.equal(d.querySelector('button'), el)
})

test('switch geometry edits and state persist across serialization; favorites stay icon buttons', () => {
  const first = setup('<button data-wf-control="switch" aria-checked="true" aria-label="开关"></button><button data-wf-toggle="favorite" aria-pressed="false">收藏</button>')
  const el = first.document.querySelector('[data-wf-control]')
  el.style.width = '60px'; el.style.height = '30px'; el.style.left = '20px'
  const { document: d, emit } = setup(first.document.body.innerHTML)
  const saved = d.querySelector('[data-wf-control]')
  assert.equal(saved.style.width, '60px'); assert.equal(saved.style.height, '30px'); assert.equal(saved.style.left, '20px')
  assert.equal(saved.getAttribute('aria-checked'), 'true')
  const favorite = d.querySelector('[data-wf-toggle]')
  assert.equal(favorite.hasAttribute('data-wf-control'), false)
  emit('click', favorite); assert.equal(favorite.getAttribute('aria-pressed'), 'true')
})

test('shared switch/checkbox/radio controls respect editing, disabled state and radio groups', () => {
  const html = '<button data-wf-control="switch" aria-checked="false"></button><button data-wf-control="checkbox" aria-checked="false"></button><button data-wf-control="switch" disabled aria-checked="true"></button><button id="r1" data-wf-control="radio" data-wf-radio-group="one" aria-checked="true"></button><button id="r2" data-wf-control="radio" data-wf-radio-group="one" aria-checked="false"></button><button id="other" data-wf-control="radio" data-wf-radio-group="two" aria-checked="true"></button>'
  const editing = setup(html, true)
  const before = editing.document.body.innerHTML
  emitAll(editing); assert.equal(editing.document.body.innerHTML, before)
  const preview = setup(html, true, true), d = preview.document
  preview.emit('click', d.querySelector('[data-wf-control="checkbox"]'))
  assert.equal(d.querySelector('[data-wf-control="checkbox"]').getAttribute('aria-checked'), 'true')
  preview.emit('click', d.querySelector('[disabled]'))
  assert.equal(d.querySelector('[disabled]').getAttribute('aria-checked'), 'true')
  preview.emit('click', d.querySelector('#r2'))
  assert.equal(d.querySelector('#r1').getAttribute('aria-checked'), 'false')
  assert.equal(d.querySelector('#r2').getAttribute('aria-checked'), 'true')
  assert.equal(d.querySelector('#other').getAttribute('aria-checked'), 'true')
  function emitAll(ctx) { for (const el of ctx.document.querySelectorAll('button')) ctx.emit('click', el) }
})

test('a legacy switch inserted after load receives the shared control without losing its node identity', () => {
  const ctx = setup('<p>已有页面</p>', true, true), el = ctx.document.createElement('span')
  el.className = 'wf-sw'; el.setAttribute('data-state', 'off'); el.setAttribute('data-wf-uid', 'inserted-switch')
  ctx.document.body.appendChild(el); ctx.added(el)
  assert.equal(el.getAttribute('data-wf-control'), 'switch')
  ctx.emit('click', el)
  assert.equal(el.getAttribute('aria-checked'), 'true')
  assert.equal(el.getAttribute('data-wf-uid'), 'inserted-switch')
})
