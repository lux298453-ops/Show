import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createTestPrototypeSecurity } from './fixtures/prototype-security.mjs'

test('saved and generated HTML cannot supply executable scripts, handlers, frames or navigation', t => {
  const security = createTestPrototypeSecurity(); t.after(() => security.window.close())
  const doc = security.createPrototypeDocument(`<html><head><base href="https://evil.example"><meta http-equiv="refresh" content="0;url=/files/evil.html"></head><body>
    <script nonce="forged" data-wf-prototype-runtime>parent.localStorage.clear()</script>
    <img src="/api/assets/pet.png" onerror="parent.localStorage.clear()">
    <svg><a xlink:href="javascript:alert(1)"><text>bad link</text></a><use href="#shape"/></svg>
    <iframe srcdoc="&lt;script&gt;alert(1)&lt;/script&gt;"></iframe><object data="/files/evil.html"></object>
    <a id="upload-link" href="/files/evil.html">上传</a><form action="/api/projects/7"><button formaction="https://evil.example">购买</button></form>
    <article data-wf-uid="card-pet" data-nav="商城" style="left:20px;border-radius:16px"><b>宠物</b></article>
    <style>:root{--primary:#864cff}.card{display:grid}</style>
  </body></html>`)
  assert.equal(doc.querySelector('script,iframe,object,base,meta,link'), null)
  assert.equal(doc.querySelector('[onerror],[action],[formaction],[nonce]'), null)
  assert.equal(doc.querySelector('#upload-link').hasAttribute('href'), false)
  assert.equal(doc.querySelector('use').getAttribute('href'), '#shape')
  assert.equal(doc.querySelector('img').getAttribute('src'), '/api/assets/pet.png')
  assert.equal(doc.querySelector('[data-wf-uid="card-pet"]').getAttribute('data-nav'), '商城')
  assert.match(doc.querySelector('style').textContent, /--primary/)
})

test('only valid JSON data survives, while trusted runtimes get an unpredictable nonce and a restrictive policy', t => {
  const security = createTestPrototypeSecurity(); t.after(() => security.window.close())
  const doc = security.createPrototypeDocument('<html><body><script type="application/json" id="map">{"html":"<b>宠物</b>"}</script><script type="application/json" id="invalid">not json</script></body></html>')
  assert.equal(doc.querySelector('#invalid'), null)
  assert.deepEqual(JSON.parse(doc.querySelector('#map').textContent), { html: '<b>宠物</b>' })
  const script = doc.createElement('script'); script.textContent = 'window.trusted=true'; doc.body.appendChild(script)
  security.securePrototypeDocument(doc)
  const policy = doc.head.firstElementChild
  assert.equal(policy.httpEquiv, 'Content-Security-Policy')
  assert.match(policy.content, /connect-src 'none'/)
  assert.match(policy.content, /frame-src 'none'/)
  assert.match(policy.content, /script-src-attr 'none'/)
  assert.ok(script.nonce.length >= 32)
  assert.ok(policy.content.includes(`script-src 'nonce-${script.nonce}'`))
  const other = security.createPrototypeDocument('<html><body><p>other</p></body></html>')
  security.securePrototypeDocument(other)
  assert.notEqual(other.querySelector('meta').content, policy.content)
})

test('editor commands reject foreign origins, stale windows and unrelated same-origin frames', t => {
  const s = createTestPrototypeSecurity(); t.after(() => s.window.close())
  const frame = s.window.document.createElement('iframe'); frame.title = 'prototype-html'; s.window.document.body.appendChild(frame)
  const event = { origin: s.window.location.origin, source: frame.contentWindow }
  assert.equal(s.isPrototypeFrameMessage(event), true)
  assert.equal(s.isPrototypeFrameMessage({ ...event, origin: 'https://evil.example' }), false)
  assert.equal(s.isPrototypeFrameMessage({ ...event, source: s.window }), false)
  frame.title = 'other'; assert.equal(s.isPrototypeFrameMessage(event), false)
  frame.remove(); assert.equal(s.isPrototypeFrameMessage(event), false)
})

test('the bundled runtime sanitizes dynamically imported DOM while keeping editable controls and SVG', t => {
  const s = createTestPrototypeSecurity(); t.after(() => s.window.close())
  const doc = s.createPrototypeDocument('<html><body></body></html>')
  s.securePrototypeDocument(doc)
  s.window.eval(doc.querySelector('[data-wf-security-runtime]').textContent)
  const fragment = s.window.__wfSanitizeFragment(`<article data-wf-uid="new-card" style="left:20px">
    <meta http-equiv="refresh" content="0;url=/files/evil.html"><base href="/files/">
    <script nonce="forged">parent.localStorage.clear()</script><iframe src="/files/evil.html"></iframe>
    <a href="/files/evil.html" data-nav="收集页">收集</a>
    <img src="/api/assets/pet.png" onerror="parent.localStorage.clear()">
    <svg><defs><path id="shape" d="M0 0h20"/></defs><use href="#shape"/>
      <use href="/files/evil.svg#shape"/><foreignObject><div>bad</div></foreignObject></svg>
    <button data-modal="detail" data-wf-control="switch" aria-checked="false">开关</button>
  </article>`)
  assert.equal(fragment.querySelector('meta,base,script,iframe,foreignObject,[nonce],[onerror]'), null)
  assert.equal(fragment.querySelector('a').hasAttribute('href'), false)
  assert.equal(fragment.querySelector('a').getAttribute('data-nav'), '收集页')
  assert.equal(fragment.querySelector('img').getAttribute('src'), '/api/assets/pet.png')
  assert.equal(fragment.querySelector('use').getAttribute('href'), '#shape')
  assert.equal(fragment.querySelectorAll('use')[1].hasAttribute('href'), false)
  assert.equal(fragment.querySelector('button').getAttribute('data-wf-control'), 'switch')
  assert.equal(fragment.querySelector('article').getAttribute('data-wf-uid'), 'new-card')
  assert.equal(fragment.querySelector('article').style.left, '20px')
  s.window.document.body.appendChild(fragment)
  assert.ok(s.window.document.querySelector('[data-wf-uid="new-card"]'))
})

test('native link and form navigation is cancelled without stopping prototype data-nav handlers', t => {
  const s = createTestPrototypeSecurity(); t.after(() => s.window.close())
  const doc = s.createPrototypeDocument('<html><body></body></html>')
  s.securePrototypeDocument(doc)
  s.window.eval(doc.querySelector('[data-wf-security-runtime]').textContent)
  const link = s.window.document.createElement('a')
  link.href = '/files/evil.html'; link.setAttribute('data-nav', '背包')
  const icon = s.window.document.createElement('span'); link.appendChild(icon)
  s.window.document.body.appendChild(link)
  let prototypeClicks = 0
  link.addEventListener('click', () => prototypeClicks++)
  const click = new s.window.MouseEvent('click', { bubbles: true, cancelable: true })
  assert.equal(icon.dispatchEvent(click), false)
  assert.equal(click.defaultPrevented, true)
  assert.equal(prototypeClicks, 1)
  const auxclick = new s.window.MouseEvent('auxclick', { bubbles: true, cancelable: true, button: 1 })
  assert.equal(icon.dispatchEvent(auxclick), false)
  const form = s.window.document.createElement('form'); form.action = '/files/evil.html'
  s.window.document.body.appendChild(form)
  const submit = new s.window.Event('submit', { bubbles: true, cancelable: true })
  assert.equal(form.dispatchEvent(submit), false)
})

test('component thumbnails remove executable markup and global styles before rendering in the host', t => {
  const s = createTestPrototypeSecurity(); t.after(() => s.window.close())
  const html = s.sanitizeComponentPreview(`<div style="width:90px;transform:scale(.2)">
    <style>body{display:none}</style><script>localStorage.clear()</script>
    <img src="/api/assets/pet.png" onerror="localStorage.clear()"><iframe srcdoc="bad"></iframe>
    <meta http-equiv="refresh" content="0;url=/files/evil.html"><a href="/files/evil.html">宠物</a>
    <svg viewBox="0 0 24 24"><use href="#pet"/></svg>
  </div>`)
  const container = s.window.document.createElement('div'); container.innerHTML = html
  assert.equal(container.querySelector('style,script,iframe,meta,[onerror]'), null)
  assert.equal(container.querySelector('a').hasAttribute('href'), false)
  assert.equal(container.querySelector('img').getAttribute('src'), '/api/assets/pet.png')
  assert.equal(container.firstElementChild.style.width, '90px')
  assert.equal(container.querySelector('use').getAttribute('href'), '#pet')
})
