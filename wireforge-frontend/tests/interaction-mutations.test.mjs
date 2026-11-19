import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'
import { ref, reactive, nextTick } from 'vue'
import { DOMParser } from 'linkedom'

const module = { exports: {} }
const context = { module, exports: module.exports }
vm.createContext(context)
vm.runInContext(transformSync(readFileSync('src/utils/interactionMutations.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code, context)
const { createInteractionMutations } = module.exports
const routingModule = { exports: {} }
const routingContext = { module: routingModule, exports: routingModule.exports, require: () => ({}) }
vm.createContext(routingContext)
vm.runInContext(transformSync(readFileSync('src/utils/canvasRouting.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code, routingContext)
const relation = (id, target = 2) => ({ id, trigger: 'click', action: 'navigate', target_page_id: target,
  params: JSON.stringify({ domUid: `button-${id}`, animation: 'push' }), source: 'user' })
const change = (id, remove = true, target = 2) => ({ pageId: 1, elementId: id, remove, targetPageId: target,
  triggerType: 'click', actionType: 'navigate', params: relation(id).params })
function setup() {
  const pages = reactive([{ id: 1, html_content: '<body>edited image and text</body>', canvas_x: 93, elements: [
    { id: 10, interaction: relation(100) }, { id: 11, interaction: relation(101) },
  ] }, { id: 2, html_content: '<body>other page</body>', elements: [] }])
  const requests = [], pending = new Set()
  let sequence = 0
  const mutations = createInteractionMutations({ pages: () => pages,
    save: changes => new Promise((resolve, reject) => requests.push({ changes, resolve, reject })),
    beginPending: () => { const id = ++sequence; pending.add(id); return () => pending.delete(id) },
  })
  const states = (request, idOffset = 0) => request.changes.map(c => ({ pageId: c.pageId, elementId: c.elementId,
    interaction: { id: c.elementId + idOffset, elementId: c.elementId, triggerType: c.triggerType || 'click',
      actionType: c.remove ? 'none' : c.actionType, targetPageId: c.remove ? null : c.targetPageId, params: c.params, source: 'user' },
  }))
  return { pages, requests, pending, mutations, states }
}

test('delete is visible immediately and keeps every page document, position and object identity', async () => {
  const s = setup(), page = s.pages[0], elements = page.elements, other = s.pages[1]
  const saving = s.mutations.apply([change(10)])
  assert.equal(page.elements[0].interaction.action, 'none')
  assert.equal(page.elements[0].interaction.target_page_id, null)
  assert.equal(page.elements[1].interaction.target_page_id, 2)
  assert.equal(s.pending.size, 1)
  await Promise.resolve()
  assert.equal(s.requests.length, 1)
  page.html_content = '<body>new edit while saving</body>'; page.canvas_x = 222
  s.requests[0].resolve(s.states(s.requests[0])); assert.equal(await saving, true)
  assert.equal(s.pages[0], page); assert.equal(page.elements, elements); assert.equal(s.pages[1], other)
  assert.equal(page.html_content, '<body>new edit while saving</body>'); assert.equal(page.canvas_x, 222)
  assert.equal(s.pending.size, 0)
})

test('multi-delete sends one request and rolls back only its relations on failure', async () => {
  const s = setup(), original = s.pages[0].elements.map(e => ({ ...e.interaction }))
  const saving = s.mutations.apply([change(10), change(11)])
  assert.ok(s.pages[0].elements.every(e => e.interaction.action === 'none'))
  await Promise.resolve(); assert.equal(s.requests.length, 1); assert.equal(s.requests[0].changes.length, 2)
  s.pages[0].html_content = 'pending text edit'
  s.requests[0].reject(new Error('offline')); await assert.rejects(saving, /offline/)
  assert.deepEqual(s.pages[0].elements.map(e => ({ ...e.interaction })), original)
  assert.equal(s.pages[0].html_content, 'pending text edit'); assert.equal(s.pending.size, 0)
})

test('immediate undo queues behind deletion and ignores the older response in the live page', async () => {
  const s = setup()
  const deleting = s.mutations.apply([change(10)]), undoing = s.mutations.apply([change(10, false, 3)])
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 3)
  await Promise.resolve(); assert.equal(s.requests.length, 1)
  s.requests[0].resolve(s.states(s.requests[0], 200))
  assert.equal(await deleting, false); await Promise.resolve()
  assert.equal(s.requests.length, 2)
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 3)
  s.requests[1].resolve(s.states(s.requests[1], 300)); assert.equal(await undoing, true)
  assert.equal(s.pages[0].elements[0].interaction.id, 310)
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 3)
})

test('failed undo returns to the last confirmed deletion, and later independent edits still save', async () => {
  const s = setup()
  const deleting = s.mutations.apply([change(10)]), undoing = s.mutations.apply([change(10, false)])
  await Promise.resolve(); s.requests[0].resolve(s.states(s.requests[0])); await deleting; await Promise.resolve()
  s.requests[1].reject(new Error('restore denied')); await assert.rejects(undoing, /restore denied/)
  assert.equal(s.pages[0].elements[0].interaction.action, 'none')
  assert.equal(s.pages[0].elements[1].interaction.target_page_id, 2)
  const next = s.mutations.apply([change(11)])
  await Promise.resolve(); s.requests[2].resolve(s.states(s.requests[2])); await next
  assert.equal(s.pending.size, 0)
})

test('a response after disposal cannot change a newly loaded view', async () => {
  const s = setup(), saving = s.mutations.apply([change(10)])
  await Promise.resolve(); s.mutations.dispose()
  s.pages[0].elements[0].interaction = relation(888, 9)
  s.requests[0].resolve(s.states(s.requests[0])); assert.equal(await saving, false)
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 9)
})

test('a disabled binding blocks old generated navigation and local popups, including after a fresh preview load', () => {
  for (const attribute of ['data-nav="旧页面"', 'data-modal="old-dialog"']) {
    const doc = new DOMParser().parseFromString(`<html><body class="wf-interactive"><button data-wf-uid="buy" ${attribute}><span>购买</span></button><input type="checkbox"></body></html>`, 'text/html')
    const messages = [], button = doc.querySelector('button'), label = doc.querySelector('span'), control = doc.querySelector('input')
    doc.defaultView.parent = { postMessage: value => messages.push(value) }
    let fallback = 0, controlClicks = 0
    const linked = [{ id: '10', uid: 'buy', action: 'navigate', target: 2 }]
    const disabled = [{ id: '10', uid: 'buy', action: 'none', target: null }]
    const runtime = routingModule.exports.installCanvasRouting(doc, disabled)
    doc.addEventListener('click', () => { fallback++ })
    const click = node => node.dispatchEvent(new doc.defaultView.Event('click', { bubbles: true, cancelable: true }))
    click(label); assert.equal(messages.length, 0); assert.equal(fallback, 0)
    runtime.update(linked); click(label); assert.equal(messages.length, 1); assert.equal(messages[0].page, '2')
    runtime.update(disabled); click(label); assert.equal(messages.length, 1)
    control.addEventListener('click', () => { controlClicks++ }); click(control)
    assert.equal(controlClicks, 1, 'unrelated controls retain their normal behavior')
    assert.equal(doc.querySelector('button'), button); assert.equal(doc.querySelector('span'), label)
  }
})

// Exercise the actual view handlers as well: isolated utility tests alone miss
// accidental loadData calls, history pollution and duplicate click paths.
const view = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', view, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['clearAllRedoStacks', 'interactionHistoryChanges', 'removeInteractionHistory', 'moveInteractionHistory',
  'undoInteraction', 'redoInteraction', 'removeConnection', 'removeConnections']
const handlers = ast.statements.filter(n => ts.isFunctionDeclaration(n) && names.includes(n.name?.text)).map(n => n.getText(ast)).join('\n')
function workbench() {
  const s = setup(), messages = [], audits = []
  const scope = { ...s, ref, Map, Set, Date, pages: ref(s.pages), id: 7, isViewer: ref(false), currentUser: ref(null),
    interactionMutations: s.mutations, interactionUndoStack: ref([]), interactionRedoStack: ref([]),
    pageDeletionRedoStack: ref([]), pageEditRedoStack: ref([]), selectedConnIds: ref(new Set(['conn-10-2'])),
    recordAuditLog: e => audits.push(e), ElMessage: Object.fromEntries(['success', 'warning', 'error'].map(kind => [kind, text => messages.push({ kind, text })])),
    loadData() { throw new Error('A relation edit must not reload any page') },
  }
  vm.createContext(scope); vm.runInContext(transformSync('let redoBranchVersion=0;\n' + handlers, { loader: 'ts' }).code, scope)
  const conn = (id = 10) => ({ id: `conn-${id}-2`, fromId: 1, toId: 2, elementId: id,
    label: '购买', fromPageName: '商城', toPageName: '弹窗' })
  return { ...s, scope, messages, audits, conn }
}

test('view deletion, immediate undo and redo use local deltas with no reload or late success toast', async () => {
  const s = workbench(), deleting = s.scope.removeConnection(s.conn())
  assert.equal(s.scope.interactionUndoStack.value.length, 1)
  assert.equal(s.scope.selectedConnIds.value.size, 0)
  await s.scope.removeConnection(s.conn()); assert.equal(s.scope.interactionUndoStack.value.length, 1)
  const undoing = s.scope.undoInteraction()
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 2)
  await Promise.resolve(); s.requests[0].resolve(s.states(s.requests[0])); await deleting; await Promise.resolve()
  assert.equal(s.messages.length, 0)
  s.requests[1].resolve(s.states(s.requests[1], 500)); await undoing
  assert.equal(s.scope.interactionRedoStack.value.length, 1)
  const redoing = s.scope.redoInteraction(); assert.equal(s.pages[0].elements[0].interaction.action, 'none')
  await Promise.resolve(); s.requests[2].resolve(s.states(s.requests[2])); await redoing
  assert.equal(s.scope.interactionUndoStack.value.length, 1)
  assert.equal(s.audits.length, 1)
})

test('failed deletion restores history and does not remove an unrelated pending command', async () => {
  const s = workbench(), first = s.scope.removeConnection(s.conn(10))
  const second = s.scope.removeConnection(s.conn(11))
  await Promise.resolve(); s.requests[0].reject(new Error('offline')); await first; await Promise.resolve()
  assert.equal(s.scope.interactionUndoStack.value.length, 1)
  assert.equal(s.scope.interactionUndoStack.value[0].elementId, 11)
  assert.equal(s.pages[0].elements[0].interaction.target_page_id, 2)
  assert.equal(s.pages[0].elements[1].interaction.action, 'none')
  s.requests[1].resolve(s.states(s.requests[1])); await second
  assert.equal(s.messages.filter(m => m.kind === 'error').length, 1)
})

test('batch deletion is one undo step and one request, restored as one batch', async () => {
  const s = workbench(), removing = s.scope.removeConnections([s.conn(10), s.conn(11)])
  await Promise.resolve(); assert.equal(s.requests.length, 1); assert.equal(s.requests[0].changes.length, 2)
  s.requests[0].resolve(s.states(s.requests[0])); await removing
  assert.equal(s.scope.interactionUndoStack.value.length, 1)
  const undoing = s.scope.undoInteraction()
  await Promise.resolve(); assert.equal(s.requests[1].changes.length, 2)
  s.requests[1].resolve(s.states(s.requests[1])); await undoing
  assert.ok(s.pages[0].elements.every(e => e.interaction.target_page_id === 2))
})

test('binding-only delete and undo preserve the real PageCanvas source ownership', async () => {
  const source = readFileSync('src/components/PageCanvas.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const canvasAst = ts.createSourceFile('canvas.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const htmlWatcher = canvasAst.statements.find(n => ts.isExpressionStatement(n) && ts.isCallExpression(n.expression)
    && n.expression.expression.getText(canvasAst) === 'watch' && n.expression.arguments[0].getText(canvasAst) === '() => props.page.html_content')
  const s = setup(), sources = []
  const { watch, effectScope } = await import('vue')
  const effect = effectScope(), scope = { watch, props: reactive({ page: s.pages[0], active: true }),
    lastInternalSavedHtml: '', editSourceHtml: { set value(v) { sources.push(v) } }, fitSent: ref(false) }
  vm.createContext(scope); effect.run(() => vm.runInContext(transformSync(htmlWatcher.getText(canvasAst), { loader: 'ts' }).code, scope))
  try {
    const saving = s.mutations.apply([change(10)]); await nextTick()
    s.requests[0].resolve(s.states(s.requests[0])); await saving; await nextTick()
    assert.equal(sources.length, 0, 'binding updates must not send the document through srcdoc again')
  } finally { effect.stop() }
})
