import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'
import { effectScope, nextTick, reactive, ref, shallowRef, watch } from 'vue'

const script = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const functions = ['focusPage', 'persistWorkbenchView', 'restoreWorkbenchView', 'syncWorkbenchLocation',
  'initializeWorkbenchView', 'onRequestedPageChange', 'onUnsavedBeforeUnload', 'loadData']
const variables = ['workbenchStorageKey', 'persistViewTimer', 'didInitialFocus']
const source = ast.statements.filter(n => {
  if (ts.isFunctionDeclaration(n)) return functions.includes(n.name?.text)
  if (ts.isVariableStatement(n)) return n.declarationList.declarations.some(d => variables.includes(d.name.getText(ast)))
  if (!ts.isExpressionStatement(n) || !ts.isCallExpression(n.expression) || n.expression.expression.getText(ast) !== 'watch') return false
  return ['pageReady', '() => route.query.page', 'focusPageId', '[view, focusPageId, selectedNodeKey]'].includes(n.expression.arguments[0].getText(ast))
}).map(n => n.getText(ast)).join('\n')
const copy = value => JSON.parse(JSON.stringify(value))
const storageKey = 'wf_workbench_view_182'
const savedView = { x: -2314.25, y: 47.5, k: .72 }
const saved = (pageId = 2, blockKey = `proto-${pageId}`) => ({ pageId, blockKey, view: savedView, viewport: { w: 1000, h: 800 } })

function setup(t, options = {}) {
  const scope = effectScope(), storage = new Map(), timers = new Map(), fits = [], replacements = [], fills = []
  if (options.saved !== undefined) storage.set(storageKey, typeof options.saved === 'string' ? options.saved : JSON.stringify(options.saved))
  const pages = [1, 2, 3].map(id => ({ id, name: `Page ${id}`, annotations: [] }))
  const blocks = pages.flatMap(page => ['design', 'prototype'].map(type => ({
    key: `${type === 'design' ? 'design' : 'proto'}-${page.id}`, pageId: page.id, page, blockType: type,
    x: page.id * 500, y: 100, w: 375, h: 812,
  })))
  const context = {
    id: 182, ref, shallowRef, watch, nextTick,
    route: reactive({ query: { ...(options.query || {}) } }),
    pages: ref(pages), blocks: ref(blocks), pageReady: ref(false), mode: ref('edit'),
    focusPageId: ref(null), selectedNodeId: ref(null), selectedNodeKey: ref(null), selectedBlockKeys: ref(new Set()),
    activeInteractionPageId: ref(null), previewPageId: ref(null), hoveredElementId: ref(null), hoveredAnnId: ref(null),
    selectedElementId: ref(null), activeSelectedElementInfo: ref(null), selectedDomLayerUid: ref(null), selectedDomLayerUids: ref([]),
    view: shallowRef({ x: 0, y: 0, k: 1 }), viewportRef: ref({}), viewportSize: () => options.viewport || { w: 1000, h: 800 },
    viewportWidth: ref(options.viewport?.w || 1000), viewportHeight: ref(options.viewport?.h || 800),
    openBlockImmediately() {}, flushPageSave: async () => true, showFrameFill: id => fills.push(id),
    fitPageGroup: id => fits.push(id), animating: ref(false), animTimer: null,
    setTimeout(callback) { const id = timers.size + 1; timers.set(id, callback); return id },
    clearTimeout(id) { timers.delete(id) },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    pageRefs: ref({}), currentFocusPage: ref(null), hasUnsavedChanges: ref(false),
    loadingData: ref(false), loadError: ref(''), proto: ref(null), pageOverrides: ref({}), pageAnnOrders: ref({}),
    projectApi: { prototype: async () => ({ pages }) }, loadComments: async () => {}, loadPageOverridesFromStorage() {},
  }
  context.router = { replace: async location => { replacements.push(copy(location)); context.route.query = location.query } }
  vm.createContext(context)
  scope.run(() => vm.runInContext(transformSync(source + '\nglobalThis.setInitialized = value => { didInitialFocus = value };', { loader: 'ts' }).code, context))
  t.after(() => scope.stop())
  return { c: context, storage, fits, replacements, fills, timers,
    read: () => JSON.parse(storage.get(storageKey) || 'null'),
    async boot() { context.pageReady.value = true; await nextTick(); await nextTick(); await nextTick() },
    flushTimers() { const callbacks = [...timers.values()]; timers.clear(); callbacks.forEach(fn => fn()) },
  }
}

test('selecting another page replaces the URL, retains other query fields and does not fit a second time', async t => {
  const s = setup(t, { query: { page: '1', mode: 'inspect' } }); await s.boot()
  s.c.view.value = { ...savedView }
  await s.c.focusPage(2, true); await nextTick(); await nextTick()
  assert.deepEqual(copy(s.c.route.query), { page: '2', mode: 'inspect' })
  assert.deepEqual(s.fits, [1, 2])
  assert.equal(s.c.selectedNodeKey.value, 'proto-2')
  assert.deepEqual(copy(s.c.view.value), savedView)
  assert.deepEqual(s.replacements, [{ query: { page: '2', mode: 'inspect' } }])
})

test('refreshing the selected URL restores its prototype, zoom and pan without fitting', async t => {
  const s = setup(t, { query: { page: '2' }, saved: saved() }); await s.boot()
  assert.equal(s.c.focusPageId.value, 2)
  assert.equal(s.c.selectedNodeKey.value, 'proto-2')
  assert.deepEqual([...s.c.selectedBlockKeys.value], ['proto-2'])
  assert.deepEqual(copy(s.c.view.value), savedView)
  assert.deepEqual(s.fits, [])
})

test('both design and prototype selections survive reload on the same page', async t => {
  for (const blockKey of ['design-2', 'proto-2']) {
    const s = setup(t, { query: { page: '2' }, saved: saved(2, blockKey) }); await s.boot()
    assert.equal(s.c.selectedNodeKey.value, blockKey)
    assert.deepEqual([...s.c.selectedBlockKeys.value], [blockKey])
  }
})

test('same-page artboard changes trigger persistence even though the page ID does not change', async t => {
  const s = setup(t, { saved: saved() }); await s.boot(); s.flushTimers()
  s.c.selectedNodeKey.value = 'design-2'; await nextTick(); s.flushTimers()
  assert.equal(s.read().blockKey, 'design-2')
  assert.equal(s.read().pageId, 2)
})

test('legacy records and stale artboard keys prefer the prototype over the design', async t => {
  for (const blockKey of [undefined, 'deleted-block', 'design-1']) {
    const s = setup(t, { saved: saved(2, blockKey) })
    // Explicitly remove the field to exercise records from older versions.
    if (blockKey === undefined) { const value = s.read(); delete value.blockKey; s.storage.set(storageKey, JSON.stringify(value)) }
    await s.boot(); assert.equal(s.c.selectedNodeKey.value, 'proto-2')
  }
})

test('a new explicit page link overrides an older saved page and fits the requested page', async t => {
  const s = setup(t, { query: { page: '3' }, saved: saved(2) }); await s.boot()
  assert.equal(s.c.focusPageId.value, 3)
  assert.equal(s.c.selectedNodeKey.value, 'proto-3')
  assert.deepEqual(s.fits, [3])
})

test('a bare project URL restores the last page and updates its address', async t => {
  const s = setup(t, { saved: saved(2) }); await s.boot()
  assert.equal(s.c.focusPageId.value, 2)
  assert.equal(s.c.route.query.page, '2')
  assert.deepEqual(s.fits, [])
})

test('deleted pages, invalid transforms and corrupt storage fall back to a real prototype', async t => {
  for (const value of ['{bad json', null, saved(999), { ...saved(), view: { x: 'bad', y: 0, k: 1 } },
    { ...saved(), view: { x: 0, y: 0, k: 0 } }, { ...saved(), view: { x: 0, y: 0, k: 4 } }]) {
    const s = setup(t, { saved: value }); await s.boot()
    assert.equal(s.c.focusPageId.value, 1)
    assert.equal(s.c.selectedNodeKey.value, 'proto-1')
    assert.deepEqual(s.fits, [1])
  }
})

test('viewport resizing preserves the same canvas center and scale', async t => {
  const s = setup(t, { saved: saved(), viewport: { w: 1200, h: 900 } }); await s.boot()
  assert.deepEqual(copy(s.c.view.value), { ...savedView, x: savedView.x + 100, y: savedView.y + 50 })
})

test('an immediate refresh saves the latest view and block without waiting for debounce', async t => {
  const s = setup(t, { saved: saved() }); await s.boot()
  const view = { x: 123, y: -45, k: 1.25 }
  s.c.view.value = view; s.c.selectedNodeKey.value = 'design-2'
  s.c.onUnsavedBeforeUnload({ preventDefault() { assert.fail('no unsaved edits') } })
  assert.deepEqual(s.read(), { pageId: 2, blockKey: 'design-2', view, viewport: { w: 1000, h: 800 } })
})

test('camera persistence uses measured viewport dimensions without forcing layout after a gesture', async t => {
  const s=setup(t,{saved:saved()});await s.boot()
  s.c.viewportSize=()=>assert.fail('persistence must not force synchronous layout')
  s.c.viewportWidth.value=1200;s.c.viewportHeight.value=900
  s.c.persistWorkbenchView()
  assert.deepEqual(s.read().viewport,{w:1200,h:900})
})

test('route changes focus another page once, but replacing the same page leaves its view alone', async t => {
  const s = setup(t, { saved: saved() }); await s.boot()
  await s.c.onRequestedPageChange('2')
  assert.deepEqual(s.fits, [])
  s.c.route.query.page = '3'; await nextTick(); await nextTick(); await nextTick()
  assert.equal(s.c.focusPageId.value, 3)
  assert.deepEqual(s.fits, [3])
  assert.deepEqual(copy(s.c.view.value), savedView)
})

test('data-only reloads preserve selection and camera even if the old URL has not caught up', async t => {
  const s = setup(t, { query: { page: '1' } })
  s.c.focusPageId.value = 2; s.c.selectedNodeKey.value = 'proto-2'; s.c.view.value = { ...savedView }
  await s.c.loadData()
  assert.equal(s.c.focusPageId.value, 2)
  assert.equal(s.c.selectedNodeKey.value, 'proto-2')
  assert.deepEqual(copy(s.c.view.value), savedView)
})

test('unavailable browser storage cannot break opening or moving around the canvas', async t => {
  const s = setup(t)
  s.c.localStorage.getItem = () => { throw new Error('storage blocked') }
  s.c.localStorage.setItem = () => { throw new Error('storage blocked') }
  await s.boot()
  assert.equal(s.c.focusPageId.value, 1)
  assert.doesNotThrow(() => s.c.persistWorkbenchView())
})
