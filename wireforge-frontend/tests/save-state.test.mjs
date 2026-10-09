import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { createRequire } from 'node:module'

const directory = await mkdtemp(join(tmpdir(), 'wireforge-save-test-'))
const output = join(directory, 'save-test.cjs')
const result = await build({
  stdin: { contents: "export {default as http} from './src/api/http'; export * from './src/utils/saveState';", resolveDir: resolve('.'), loader: 'ts' },
  bundle: true, platform: 'node', format: 'cjs', write: false,
  define: { 'import.meta.env.VITE_API_BASE_URL': '""' },
})
await writeFile(output, result.outputFiles[0].contents)
const { http, useSaveState, beginSave, finishSave, discardSaveState, markLocalEdit, waitForSaved } = createRequire(import.meta.url)(output)
after(async () => { await unlink(output); await rmdir(directory) })

function config(scope, route, data, method = 'put') {
  return { url: `/projects/${scope}/${route}`, method, data, headers: {} }
}

test('preview waits for local edits and pending saves before continuing', async () => {
  markLocalEdit('740',1)
  let ready=false
  const waiting=waitForSaved('740',{quietMs:0,timeoutMs:1000}).then(()=>{ready=true})
  await new Promise(resolve=>setTimeout(resolve,10));assert.equal(ready,false)
  const ticket=beginSave(config('740','pages/1/html',{html:'edited'}),async()=>{})
  await new Promise(resolve=>setTimeout(resolve,10));assert.equal(ready,false)
  finishSave(ticket);await waiting;assert.equal(ready,true)
})
test('save failure, cancellation and unfinished edits do not silently start preview', async () => {
  markLocalEdit('741',1,'保存失败')
  await assert.rejects(waitForSaved('741'),/保存失败/)
  markLocalEdit('742',1);const controller=new AbortController()
  const waiting=waitForSaved('742',{signal:controller.signal});controller.abort();await assert.rejects(waiting,/取消/)
  markLocalEdit('743',1);await assert.rejects(waitForSaved('743',{timeoutMs:20}),/仍未保存/)
})

test('a late success cannot report saved while a newer edit is still pending or failed', () => {
  const state = useSaveState('701')
  const old = beginSave(config('701', 'pages/1/html', { html: 'old' }), async () => {})
  const latest = beginSave(config('701', 'pages/1/html', { html: 'latest' }), async () => {})
  finishSave(old)
  assert.equal(state.status.value, 'saving')
  finishSave(latest, 'offline')
  assert.equal(state.status.value, 'error')
  assert.equal(state.errorMessage.value, 'offline')
})

test('a superseded HTML failure is not replayed over a newer successful edit', async () => {
  let replays = 0
  const state = useSaveState('702')
  const old = beginSave(config('702', 'pages/1/html', { html: 'old' }), async () => { replays++ })
  finishSave(old, 'offline')
  const latest = beginSave(config('702', 'pages/1/html', { html: 'latest' }), async () => {})
  finishSave(latest)
  await state.retryFailed()
  assert.equal(state.status.value, 'saved')
  assert.equal(replays, 0)
})

test('partial annotation failures survive successful changes to another field', async () => {
  const state = useSaveState('703')
  let replayed
  const failed = beginSave(config('703', 'annotations/1', { text: 'keep me', boxX: 10 }), async next => { replayed = next.data })
  finishSave(failed, 'offline')
  const newer = beginSave(config('703', 'annotations/1', { boxX: 20 }), async () => {})
  finishSave(newer)
  assert.equal(state.status.value, 'error')
  await state.retryFailed()
  assert.deepEqual(replayed, { text: 'keep me' })
})

test('generation, edit locks and destructive actions do not appear as saves', () => {
  for (const [route, method] of [['analyze', 'post'], ['pages/1/edit-lock', 'post'], ['pages/1', 'delete']]) {
    assert.equal(beginSave(config('704', route, {}, method), async () => {}), undefined)
  }
  assert.equal(useSaveState('704').status.value, 'idle')
})

test('POST failures stay visible but cannot be automatically repeated', async () => {
  let replays = 0
  const state = useSaveState('705')
  const ticket = beginSave(config('705', 'pages/1/elements', { label: 'new' }, 'post'), async () => { replays++ })
  finishSave(ticket, 'connection lost')
  await state.retryFailed()
  assert.equal(state.status.value, 'error')
  assert.equal(state.canRetry.value, false)
  assert.equal(state.hasManualFailures.value, true)
  assert.equal(replays, 0)
})

test('a later independent element creation cannot hide an earlier creation failure', () => {
  const state = useSaveState('709')
  const failed = beginSave(config('709', 'pages/1/elements', { label: 'first' }, 'post'), async () => {})
  finishSave(failed, 'offline')
  const second = beginSave(config('709', 'pages/1/elements', { label: 'second' }, 'post'), async () => {})
  finishSave(second)
  assert.equal(state.status.value, 'error')
  assert.equal(state.hasManualFailures.value, true)
})

test('explicitly discarded edits cannot reappear or replay in a newly loaded session', async () => {
  let replays = 0
  const state = useSaveState('710')
  const pending = beginSave(config('710', 'pages/1/html', { html: 'old session' }), async () => { replays++ })
  discardSaveState('710')
  finishSave(pending, 'late response')
  await state.retryFailed()
  assert.equal(state.status.value, 'idle')
  assert.equal(state.hasUnsavedChanges.value, false)
  assert.equal(replays, 0)
})

test('local debounced edits and edit-lock blocks remain unsaved before an HTTP request', () => {
  const state = useSaveState('711')
  markLocalEdit('711', 1)
  assert.equal(state.status.value, 'dirty')
  assert.equal(state.hasUnsavedChanges.value, true)
  markLocalEdit('711', 1, 'locked by another editor')
  assert.equal(state.status.value, 'error')
  assert.equal(state.hasManualFailures.value, true)
  const request = beginSave(config('711', 'pages/1/html', { html: 'new' }), async () => {})
  finishSave(request)
  assert.equal(state.status.value, 'saved')
  assert.equal(state.hasUnsavedChanges.value, false)
})

test('retry cannot overwrite a newer local edit waiting for its debounce', async () => {
  let replays = 0
  const state = useSaveState('712')
  const request = beginSave(config('712', 'pages/1/html', { html: 'old' }), async () => { replays++ })
  finishSave(request, 'offline')
  markLocalEdit('712', 1)
  assert.equal(state.canRetry.value, false)
  await state.retryFailed()
  assert.equal(replays, 0)
  assert.equal(state.hasUnsavedChanges.value, true)
})

test('Axios business failures remain unsaved; retry sends original data through the interceptor', async () => {
  let calls = 0
  const sent = []
  http.defaults.adapter = async cfg => {
    sent.push(JSON.parse(cfg.data))
    return { data: calls++ === 0 ? { code: 1, message: '暂时失败' } : { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config: cfg }
  }
  const state = useSaveState('706')
  await assert.rejects(http.put('/projects/706/pages/1/html', { html: '<p>draft</p>' }), /暂时失败/)
  assert.equal(state.status.value, 'error')
  await state.retryFailed()
  assert.equal(state.status.value, 'saved')
  assert.deepEqual(sent, [{ html: '<p>draft</p>' }, { html: '<p>draft</p>' }])
})

test('network failures are scoped by project and recover without duplicating unrelated writes', async () => {
  let calls = 0
  http.defaults.adapter = async cfg => {
    if (calls++ === 0) throw Object.assign(new Error('Network Error'), { config: cfg })
    return { data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config: cfg }
  }
  const state = useSaveState('707')
  await assert.rejects(http.put('/projects/707/pages/1/position', { canvasX: 12, canvasY: 24 }))
  assert.equal(state.status.value, 'error')
  assert.equal(useSaveState('708').status.value, 'idle')
  await state.retryFailed()
  assert.equal(state.hasUnsavedChanges.value, false)
  assert.equal(calls, 2)
})

test('same-resource writes are sent in order while another page can save independently', async () => {
  let releaseFirst
  const gate = new Promise(resolve => { releaseFirst = resolve })
  const started = []
  http.defaults.adapter = async cfg => {
    const body = JSON.parse(cfg.data)
    started.push(body.html)
    if (body.html === 'first') await gate
    return { data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config: cfg }
  }
  const first = http.put('/projects/713/pages/1/html', { html: 'first' })
  const second = http.put('/projects/713/pages/1/html', { html: 'second' })
  const independent = http.put('/projects/713/pages/2/html', { html: 'another page' })
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(started, ['first', 'another page'])
  assert.equal(useSaveState('713').status.value, 'saving')
  releaseFirst()
  await Promise.all([first, second, independent])
  assert.deepEqual(started, ['first', 'another page', 'second'])
  assert.equal(useSaveState('713').status.value, 'saved')
})
