import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { JSDOM } from 'jsdom'

const module = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasRouting.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code,
  { module, exports: module.exports, require: () => ({ interactionDomUid: () => '' }) })
const { installCanvasRouting } = module.exports
const background = { id: 9, action: 'back', x: 0, y: 0, w: 375, h: 833 }

function setup(t, extra = '', bindings = []) {
  const dom = new JSDOM(`<!doctype html><html><body class="wf-interactive">
    <div data-wf-inject="backdrop-return" class="wf-page-dismiss" data-wf-element-id="9" data-action="back" style="position:absolute;inset:0;z-index:1"></div>
    <main><div id="mask" role="dialog" data-wf-preview-surface style="position:absolute;inset:0;z-index:10;background:rgba(0,0,0,.8)">
      <section id="layout" data-wf-role="card" style="padding-top:250px">
        <h2 id="title">获得奖励</h2>
        <article data-wf-role="card" data-small style="background:#fff"><div id="card-padding"></div><img id="art" src="/test.png"></article>
        <button id="button">确认</button><input id="input"><div role="switch" id="switch"></div>
        <div id="painted" data-small style="background:#fff;border-radius:16px"></div>
        <div id="outlined" data-small style="border:1px solid #123"></div>
        ${extra}
      </section>
    </div></main>
  </body></html>`)
  t.after(() => dom.window.close())
  const { window: win } = dom, doc = win.document, messages = [], generated = []
  Object.defineProperties(doc.documentElement, { clientWidth: { value: 375 }, clientHeight: { value: 833 } })
  for (const node of doc.querySelectorAll('*')) node.getBoundingClientRect = () => ({
    left: 0, top: 0, right: 375, bottom: node.hasAttribute('data-small') ? 300 : 833,
    width: 375, height: node.hasAttribute('data-small') ? 300 : 833,
  })
  win.postMessage = data => messages.push(data)
  const routing = installCanvasRouting(doc, bindings, [background])
  doc.addEventListener('click', event => generated.push(event.target.id), true)
  const click = (id, x = 20, y = 150) => doc.getElementById(id).dispatchEvent(new win.MouseEvent('click', {
    bubbles: true, cancelable: true, clientX: x, clientY: y,
  }))
  return { doc, routing, messages, generated, click }
}

test('blank clicks on a full-screen generated mask and padded layout use the saved return action', t => {
  const s = setup(t)
  s.click('mask'); s.click('layout')
  assert.deepEqual(s.messages.map(message => message.type), ['wf-back', 'wf-back'])
  assert.equal(s.generated.length, 0, 'a generated handler cannot swallow or duplicate the saved blank-area action')
})

test('card padding, text, images, standard controls and visible panels keep their own behavior', t => {
  const s = setup(t)
  for (const id of ['title', 'card-padding', 'art', 'button', 'input', 'switch', 'painted', 'outlined']) s.click(id)
  assert.equal(s.messages.length, 0)
  assert.equal(s.generated.length, 8)
})

test('updated and disabled background settings apply without replacing the iframe', t => {
  const s = setup(t)
  s.routing.update(undefined, [background], true); s.click('layout')
  s.routing.update(undefined, [], false); s.click('layout')
  assert.equal(s.messages.length, 0)
  s.routing.update(undefined, [background], false); s.click('layout')
  assert.equal(s.messages.length, 1)
  s.routing.update(undefined, [], false); s.click('layout')
  assert.equal(s.messages.length, 1)
})

test('native local modal masks and partial dialog panels are not treated as page background', t => {
  const s = setup(t, '<div class="wf-modal wf-show"><div id="local-mask"></div></div><section data-small role="dialog"><div id="dialog-padding"></div></section>')
  s.click('local-mask'); s.click('dialog-padding')
  assert.equal(s.messages.length, 0)
  assert.equal(s.generated.length, 2)
})

test('saved element navigation wins and coordinates outside the configured background do not return', t => {
  const s = setup(t, '<button id="jump" data-wf-element-id="50">跳转</button>', [{ id: '50', uid: '', action: 'navigate', target: 300 }])
  s.click('jump'); s.click('layout', 400, 100); s.click('layout', 20, 900)
  assert.deepEqual(s.messages.map(message => [message.type, message.page]), [['wf-nav', '300']])
  s.doc.body.classList.remove('wf-interactive'); s.click('layout')
  assert.equal(s.messages.length, 1, 'editing must never trigger a page return')
})
