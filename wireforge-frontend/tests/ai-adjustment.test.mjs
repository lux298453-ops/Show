import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { DOMParser } from 'linkedom'
import * as vue from 'vue'

function load(path, imports, globals = {}) {
  const module = { exports: {} }
  vm.runInNewContext(transformSync(readFileSync(path, 'utf8'), { loader: 'ts', format: 'cjs' }).code,
    { module, exports: module.exports, require: name => imports[name], DOMParser, crypto, AbortController,
      setTimeout, clearTimeout, console, Error, ...globals })
  return module.exports
}
const utils = load('src/utils/aiAdjustment.ts', {})
const html = text => `<!DOCTYPE html><html><head></head><body><main>${text}</main></body></html>`
const base = html('<button id="a">A</button><button id="b">B</button>')

function setup(t, storage = new Map(), pageHtml = base) {
  const props = vue.reactive({ projectId: 7, currentPage: { id: 1, name: 'A', html_content: pageHtml }, isOpen: true, selectedElement: null })
  const requests = [], saves = [], events = [], cleanups = []
  const api = {
    aiAdjust: (...args) => new Promise((resolve, reject) => requests.push({ args, resolve, reject })),
    aiAdjustElement: (...args) => new Promise((resolve, reject) => requests.push({ args, resolve, reject })),
    saveHtml: async (...args) => saves.push(args),
  }
  const { useAiAdjustment } = load('src/composables/useAiAdjustment.ts', {
    vue: { ...vue, onMounted() {}, onBeforeUnmount: fn => cleanups.push(fn) }, '../api/project': { projectApi: api }, '../utils/aiAdjustment': utils,
  }, { window: { removeEventListener() {} }, sessionStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value) } })
  const scope = vue.effectScope()
  const draft = new Map()
  const session = scope.run(() => useAiAdjustment(props, {
    preview: (id, html) => { draft.set(id, html); events.push(['preview', id]) },
    revert: id => draft.delete(id),
    commit: (id, html) => { events.push(['commit', id]); draft.delete(id); if (props.currentPage.id === id) props.currentPage.html_content = html },
    toast() {},
  }))
  const dispose = () => { cleanups.splice(0).forEach(fn => fn()); scope.stop() }
  t.after(dispose)
  return { props, api, requests, saves, session, events, draft, dispose, storage,
    async choose(id) { props.currentPage = { id, name: id === 1 ? 'A' : 'B', html_content: id === 1 ? pageHtml : html('B original') }; await vue.nextTick() },
    async generate(value, instruction = '修改') {
      session.inputPrompt.value = instruction
      const promise = session.handleSend()
      requests.at(-1).resolve({ pageId: props.currentPage.id, modifiedHtml: value, summary: instruction, diffList: [], changed: true })
      await promise
      return session.messages.value.at(-1)
    },
  }
}

test('a response and save keep the original page when switching before either completes', async t => {
  const s = setup(t)
  s.session.inputPrompt.value = '修改 A'
  const pending = s.session.handleSend()
  await s.choose(2)
  s.requests[0].resolve({ pageId: 1, modifiedHtml: html('changed A'), changed: true })
  await pending
  assert.equal(s.session.messages.value.length, 0)
  assert.equal(s.draft.get(1), html('changed A'))
  await s.choose(1)
  const msg = s.session.messages.value.at(-1)
  let finish
  s.api.saveHtml = (...args) => { s.saves.push(args); return new Promise(resolve => { finish = resolve }) }
  const saving = s.session.handleCommit(msg)
  await s.choose(2)
  finish(); await saving
  assert.equal(s.saves[0][1], 1)
  assert.equal(s.saves[0][4], base, 'save includes the original persisted baseline')
  assert.deepEqual(s.events.at(-1), ['commit', 1])
})

test('applying an older result saves precisely that result and does not relabel another version', async t => {
  const s = setup(t), first = await s.generate(html('one')), second = await s.generate(html('two'))
  await s.session.handleCommit(first)
  assert.equal(s.saves[0][2], first.modifiedHtml)
  assert.equal(first.isCommitted, true)
  assert.equal(second.isCommitted, false)
})

test('element selection changes cannot redirect a pending patch and deletion restores its position', async t => {
  const s = setup(t)
  const doc = new DOMParser().parseFromString(base, 'text/html')
  s.props.selectedElement = utils.captureAiElement(base, doc.getElementById('a'))
  s.session.inputPrompt.value = '删除 A'
  const pending = s.session.handleSend()
  s.props.selectedElement = utils.captureAiElement(base, doc.getElementById('b'))
  s.requests[0].resolve({ elementId: 'a', action: 'DELETE', changed: true, summary: '删除 A', modifiedSnippet: '' })
  await pending
  const message = s.session.messages.value.at(-1)
  const changed = new DOMParser().parseFromString(s.draft.get(1), 'text/html')
  assert.equal(changed.getElementById('a'), null)
  assert.equal(changed.getElementById('b').textContent, 'B')
  s.session.handleRevert(message)
  assert.equal(s.draft.has(1), false)
  assert.equal(message.isReverted, true)
  assert.equal(message.originalHtml, base)
})

test('switching pages and refreshing preserve drafts, conversations, versions and input', async t => {
  const s = setup(t)
  await s.generate(html('draft'))
  s.session.inputPrompt.value = '未发送的想法'
  await s.choose(2); await s.choose(1)
  assert.equal(s.session.messages.value.length, 2)
  assert.equal(s.session.historySnapshots.value.length, 1)
  s.dispose()
  const restored = setup(t, s.storage)
  assert.equal(restored.draft.get(1), html('draft'))
  assert.equal(restored.session.messages.value.length, 2)
  assert.equal(restored.session.inputPrompt.value, '未发送的想法')
})

test('restored stale drafts cannot overwrite a newer server version', async t => {
  const s = setup(t); await s.generate(html('draft')); s.dispose()
  const restored = setup(t, s.storage, html('new server'))
  await restored.session.handleCommit(restored.session.messages.value.at(-1))
  assert.equal(restored.saves.length, 0)
  restored.session.handleDiscardDraft()
  await restored.generate(html('new edit'))
  await restored.session.handleCommit(restored.session.messages.value.at(-1))
  assert.equal(restored.saves[0][4], html('new server'))
})

test('Ctrl+Z belongs to the text editor; outside it undo targets the current preview', async t => {
  const s = setup(t); const msg = await s.generate(html('draft'))
  const doc = new DOMParser().parseFromString('<html><body><textarea></textarea><div contenteditable="true"><span></span></div></body></html>', 'text/html')
  let prevented = 0
  const event = target => ({ key: 'z', ctrlKey: true, target, preventDefault() { prevented++ } })
  s.session.onGlobalKeyDown(event(doc.querySelector('textarea')))
  s.session.onGlobalKeyDown(event(doc.querySelector('span')))
  assert.equal(prevented, 0)
  assert.equal(msg.isReverted, false)
  s.session.onGlobalKeyDown(event(doc.body))
  assert.equal(prevented, 1)
  assert.equal(msg.isReverted, true)
})

test('cancel and retry ignore a late result from the cancelled transport', async t => {
  const s = setup(t)
  s.session.inputPrompt.value = '修改'
  const first = s.session.handleSend()
  s.session.handleCancel()
  assert.equal(s.session.loading.value, false)
  assert.equal(s.session.canRetry.value, true)
  const retry = s.session.handleRetry()
  assert.ok(s.requests[0].args[2].requestId)
  assert.equal(s.requests[1].args[2].requestId, s.requests[0].args[2].requestId, 'retry retains the image generation request ID')
  s.requests[0].resolve({ pageId: 1, modifiedHtml: html('cancelled'), changed: true })
  await first
  assert.equal(s.session.loading.value, true)
  s.requests[1].resolve({ pageId: 1, modifiedHtml: html('retried'), changed: true })
  await retry
  assert.equal(s.draft.get(1), html('retried'))
  assert.equal(s.session.messages.value.length, 2)
})

test('generated image versions are previewed, persisted, applied and undone with their own URLs', async t => {
  const oldHtml = html('<img id="pet" src="/api/assets/original.png"><button id="b">B</button>')
  const nextHtml = oldHtml.replace('/api/assets/original.png', '/api/assets/artwork/7/generated.png')
  const s = setup(t, new Map(), oldHtml)
  s.session.inputPrompt.value = '重新生成宠物图片'
  const pending = s.session.handleSend()
  const image = { name: '宠物', url: '/api/assets/artwork/7/generated.png', kind: 'character' }
  s.requests[0].resolve({ pageId: 1, modifiedHtml: nextHtml, summary: '已生成宠物', changed: true, generatedImages: [image] })
  await pending
  const message = s.session.messages.value.at(-1)
  assert.equal(message.generatedImages[0].url, image.url)
  assert.equal(s.saves.length, 0, 'generation only creates a preview')
  assert.equal(s.draft.get(1), nextHtml)
  await s.session.handleCommit(message)
  assert.equal(s.saves[0][2], nextHtml)
  await s.session.handleRevertCommitted(message)
  assert.equal(s.saves[1][2], oldHtml)
  s.dispose()
  const restored = setup(t, s.storage, oldHtml)
  assert.equal(restored.session.messages.value.at(-1).generatedImages[0].url, image.url)
})

test('a failed image request keeps its ID after refreshing; a newly sent request gets another', async t => {
  const s = setup(t)
  s.session.inputPrompt.value = '重新生成背景图'
  const failed = s.session.handleSend()
  const requestId = s.requests[0].args[2].requestId
  s.requests[0].reject(new Error('图片生成未完成，原画板已保留'))
  await failed
  assert.equal(s.draft.size, 0)
  s.dispose()
  const restored = setup(t, s.storage)
  const retry = restored.session.handleRetry()
  assert.equal(restored.requests[0].args[2].requestId, requestId)
  restored.requests[0].resolve({ pageId: 1, modifiedHtml: base, changed: false })
  await retry
  restored.session.inputPrompt.value = '重新生成背景图'
  const next = restored.session.handleSend()
  assert.notEqual(restored.requests[1].args[2].requestId, requestId)
  restored.requests[1].resolve({ pageId: 1, modifiedHtml: base, changed: false })
  await next
})

test('selected image results keep the original page and element when selection changes', async t => {
  const source = html('<img id="a" src="old.png"><button id="b">B</button>')
  const s = setup(t, new Map(), source)
  const doc = new DOMParser().parseFromString(source, 'text/html')
  s.props.selectedElement = utils.captureAiElement(source, doc.getElementById('a'))
  s.session.inputPrompt.value = '重新生成角色图片'
  const pending = s.session.handleSend()
  const requestId = s.requests[0].args[2].requestId
  await s.choose(2)
  s.requests[0].resolve({ elementId: 'a', action: 'REPLACE', changed: true, summary: '角色已生成',
    modifiedSnippet: '<img id="a" src="new.png">', generatedImages: [{ name: '角色', url: 'new.png', kind: 'character' }] })
  await pending
  assert.ok(requestId)
  assert.equal(s.draft.has(2), false)
  assert.match(s.draft.get(1), /new.png/)
  assert.match(s.draft.get(1), /<button id="b">B<\/button>/)
  await s.choose(1)
  assert.equal(s.session.messages.value.at(-1).generatedImages[0].name, '角色')
})

test('failure retains its request, retry uses the same baseline and bounded conversation context', async t => {
  const s = setup(t); await s.generate(html('first'), '把按钮改蓝')
  s.session.inputPrompt.value = '按刚才的风格继续'
  const failed = s.session.handleSend()
  s.requests[1].reject(new Error('service unavailable')); await failed
  assert.match(s.session.errorMessage.value, /service unavailable/)
  const retry = s.session.handleRetry()
  assert.match(s.requests[2].args[2].conversationContext, /把按钮改蓝/)
  assert.equal(s.requests[2].args[2].contextHtml, html('first'))
  s.requests[2].resolve({ pageId: 1, modifiedHtml: html('first'), changed: false })
  await retry
  assert.equal(s.session.messages.value.at(-1).unchanged, true)
  assert.equal(s.session.historySnapshots.value.length, 1)
})

test('save failures retain recoverable drafts and do not claim applied status', async t => {
  const s = setup(t); const msg = await s.generate(html('draft'))
  s.api.saveHtml = async () => { throw new Error('版本冲突') }
  await s.session.handleCommit(msg)
  assert.equal(msg.isCommitted, false)
  assert.equal(s.draft.get(1), html('draft'))
  assert.match(s.session.errorMessage.value, /版本冲突/)
})

test('undo after applying an older version returns to the actual previous preview and continues through earlier steps', async t => {
  const s = setup(t), first = await s.generate(html('one')), second = await s.generate(html('two'))
  await s.session.handleCommit(second)
  await s.session.handleCommit(first)
  await s.session.handleRevertCommitted(first)
  assert.equal(s.saves.at(-1)[2], html('two'))
  await s.session.handleRevertCommitted(second)
  assert.equal(s.saves.at(-1)[2], html('one'))
  await s.session.handleRevertCommitted(first)
  assert.equal(s.saves.at(-1)[2], base)
})

test('refreshing during generation keeps the request retryable and ignores the old response', async t => {
  const s = setup(t)
  s.session.inputPrompt.value = '请求途中刷新'
  const old = s.session.handleSend()
  s.dispose()
  const restored = setup(t, s.storage)
  assert.equal(restored.session.canRetry.value, true)
  s.requests[0].resolve({ pageId: 1, modifiedHtml: html('stale'), changed: true })
  await old
  const retry = restored.session.handleRetry()
  restored.requests[0].resolve({ pageId: 1, modifiedHtml: html('retry'), changed: true })
  await retry
  assert.equal(restored.draft.get(1), html('retry'))
})

test('undo stops at the beginning even when a later result matches the initial HTML', async t => {
  const s = setup(t), first = await s.generate(html('one')), second = await s.generate(base)
  await s.session.handleRevert(second)
  await s.session.handleRevert(first)
  let prevented = false
  s.session.onGlobalKeyDown({ key: 'z', ctrlKey: true, preventDefault() { prevented = true } })
  assert.equal(prevented, false)
  assert.equal(s.saves.at(-1)[2], base)
})

test('source patching preserves unrelated elements, scripts, images and special-character identifiers', () => {
  const source = html('<button data-wf-id="a&quot;[]" data-nav="背包">A</button><img src="/api/assets/pet.png"><script>window.app=1</script>')
  const doc = new DOMParser().parseFromString(source, 'text/html')
  const target = utils.captureAiElement(source, doc.querySelector('button'))
  assert.ok(target)
  const result = utils.patchAiElement(source, target, 'REPLACE', '<button data-nav="背包" style="color:blue">New</button>')
  const output = new DOMParser().parseFromString(result, 'text/html')
  assert.equal(output.querySelector('button').getAttribute('data-wf-id'), 'a"[]')
  assert.equal(output.querySelector('img').outerHTML, doc.querySelector('img').outerHTML)
  assert.equal(output.querySelector('script').textContent, 'window.app=1')
  assert.throws(() => utils.patchAiElement(source, target, 'REPLACE', '<div>A</div><div>B</div>'))
})
