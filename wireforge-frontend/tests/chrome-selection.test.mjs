import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { parseHTML } from 'linkedom'

const runtime = readFileSync('../wireforge-backend/src/main/resources/prototype/chrome-selection.js', 'utf8')
function fixture(original = '.nav-item.active{background:linear-gradient(#879bfa,#88d4ff);border-radius:9px;color:#fff}', variables = '--wf-chrome-nav-bg:#fff;--wf-chrome-active:#fff;') {
  return parseHTML(`<html><head><style>${original}</style><style data-wf-chrome-style>:root{${variables}}</style></head><body>
    <article id="content"><button data-wf-tab="filters" class="active">全部</button></article>
    <nav data-wf-shared-chrome="navigation">
      <button class="nav-item" data-wf-chrome-part="item" data-wf-uid="home" data-nav="家园" aria-current="false"><svg><path d="M2 2"/></svg><span>家园</span></button>
      <button class="nav-item" data-wf-chrome-part="item" data-wf-uid="shop" data-wf-element-id="72" data-nav="商城" aria-current="page"><svg><path d="M4 4"/></svg><span>商城</span></button>
    </nav></body></html>`).document
}
function run(document) { vm.runInNewContext(runtime, { document }) }

test('old white selected tab recovers the original gradient without rebuilding editable nodes', () => {
  const d = fixture(), nav = d.querySelector('nav'), active = d.querySelector('[aria-current=page]')
  const content = d.querySelector('#content').outerHTML, original = active.outerHTML
  run(d)
  assert.equal(nav.style.getPropertyValue('--wf-chrome-active-bg'), 'linear-gradient(#879bfa,#88d4ff)')
  assert.equal(nav.style.getPropertyValue('--wf-chrome-active-radius'), '9px')
  assert.equal(d.querySelector('[aria-current=page]'), active)
  assert.equal(active.outerHTML, original)
  assert.equal(d.querySelector('#content').outerHTML, content)
  const once = d.documentElement.outerHTML; run(d)
  assert.equal(d.documentElement.outerHTML, once)
})

test('a missing legacy background gets a readable foreground on light and dark bars', () => {
  for (const [bg, ink, fallback] of [['#fff', '#fff', '#365ac7'], ['#121212', '#151515', '#a8c7fa']]) {
    const d = fixture('', `--wf-chrome-nav-bg:${bg};--wf-chrome-active:${ink};`)
    run(d)
    assert.equal(d.querySelector('nav').style.getPropertyValue('--wf-chrome-active'), fallback)
    assert.equal(d.querySelectorAll('[aria-current=page]').length, 1)
  }
})

test('a decorative selected pseudo element supplies its surface without restoring pseudo markup', () => {
  const d = fixture('.nav-item{background:transparent}.nav-item.active{color:#fff}.nav-item.active:before{background:linear-gradient(#879bfa,#88d4ff);border-radius:9px}')
  run(d)
  assert.equal(d.querySelector('nav').style.getPropertyValue('--wf-chrome-active-bg'), 'linear-gradient(#879bfa,#88d4ff)')
  assert.equal(d.querySelector('nav').style.getPropertyValue('--wf-chrome-active-radius'), '9px')
  assert.equal(d.querySelector('[aria-current=page]').className, 'nav-item')
})

test('valid unfilled selections and v3 project surfaces keep their own styling', () => {
  const outline = fixture('', '--wf-chrome-nav-bg:#fff;--wf-chrome-active:#ec681d;')
  run(outline)
  assert.equal(outline.querySelector('nav').style.getPropertyValue('--wf-chrome-active'), '')
  const modern = fixture(undefined, '--wf-chrome-nav-bg:#fff;--wf-chrome-active:#fff;--wf-chrome-active-bg:#4563c2;')
  const before = modern.documentElement.outerHTML; run(modern)
  assert.equal(modern.documentElement.outerHTML, before)
})

test('pages without project chrome are left unchanged and inactive items stay inactive', () => {
  const d = fixture(), inactive = d.querySelector('[aria-current=false]'), before = inactive.outerHTML
  run(d); assert.equal(inactive.outerHTML, before)
  const plain = parseHTML('<html><head></head><body><button class="active">本页标签</button></body></html>').document
  const html = plain.documentElement.outerHTML; run(plain)
  assert.equal(plain.documentElement.outerHTML, html)
})
