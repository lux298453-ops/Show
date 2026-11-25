import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'

const script = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['enqueueHistoryCommand', 'handleGlobalUndo', 'handleGlobalRedo', 'undoLatestChange', 'redoLatestChange',
  'undoPageEdit', 'redoPageEdit', 'onSimMessage', 'onEditorNudgeKey']
const source = ast.statements.filter(n => ts.isFunctionDeclaration(n) && names.includes(n.name?.text)
  || ts.isVariableStatement(n) && n.declarationList.declarations.some(d => d.name.getText(ast) === 'historyCommand'))
  .map(n => n.getText(ast)).join('\n')
const ref = value => ({ value })
const copy = value => JSON.parse(JSON.stringify(value))

function setup() {
  const writes = [], commands = [], toasts = []
  const c = {
    Promise, console, Number, Date, isPrototypeFrameMessage: () => true,
    pageEditUndoStack: ref([{ pageId: 1, timestamp: 1, html: 'A' }, { pageId: 1, timestamp: 2, html: 'B', prevHtml: 'A' }]),
    pageEditRedoStack: ref([]), pageDeletionUndoStack: ref([]), pageDeletionRedoStack: ref([]),
    interactionUndoStack: ref([]), interactionRedoStack: ref([]),
    pages: ref([{ id: 1 }, { id: 2 }]), focusPageId: ref(1), selectedNodeId: ref(1), currentFocusPage: ref({ id: 1 }),
    selectedDomLayerUids: ref(['label']), pageEditingConflicts: ref({}),
    mode: ref('edit'), workbenchMode: ref('design'), fineTune: ref(true), activeDrawTool: ref('select'),
    isViewer: ref(false), isVectorMode: ref(false), hasSelectedDomElements: ref(true),
    showFrameFill() {}, focusPage() {}, showToast: text => toasts.push(text), ElMessage: { warning() {} },
    applyHtmlToPage: async (pageId, html) => { writes.push({ pageId, html }) },
    postToFocusFrame: data => commands.push(copy(data)),
  }
  c.clearAllRedoStacks = () => { c.pageEditRedoStack.value = []; c.pageDeletionRedoStack.value = []; c.interactionRedoStack.value = [] }
  vm.createContext(c); vm.runInContext(transformSync(source, { loader: 'ts' }).code, c)
  return { c, writes, commands, toasts, edit(html, pageId = 1) {
    c.onSimMessage({ data: { type: 'wf-snapshot-pushed', pageId, html, timestamp: Date.now() } })
  } }
}

test('editing immediately after undo remains undoable and clears the superseded redo branch', async () => {
  const s = setup()
  assert.equal(await s.c.handleGlobalUndo(), true)
  s.edit('C')
  assert.deepEqual(s.c.pageEditUndoStack.value.map(r => r.html), ['A', 'C'])
  assert.equal(s.c.pageEditRedoStack.value.length, 0)
  assert.equal(await s.c.handleGlobalUndo(), true)
  assert.deepEqual(s.writes.map(r => r.html), ['A', 'A'])
})

test('genuine edits from another page are retained while an undo save is still pending', async () => {
  const s = setup(); let release
  s.c.applyHtmlToPage = (pageId, html) => {
    s.writes.push({ pageId, html })
    return new Promise(resolve => { release = resolve })
  }
  const pending = s.c.handleGlobalUndo(); await Promise.resolve()
  assert.equal(s.writes.length, 1)
  s.edit('Other baseline', 2); s.edit('Other change', 2)
  assert.equal(s.c.pageEditUndoStack.value.at(-1).prevHtml, 'Other baseline')
  release(); await pending
  assert.equal(s.c.pageEditUndoStack.value.at(-1).html, 'Other change')
})

test('rapid undo and redo wait for the preceding restore instead of applying out of order', async () => {
  const s = setup(); let release
  s.c.pageEditUndoStack.value.push({ pageId: 1, timestamp: 3, html: 'C', prevHtml: 'B' })
  s.c.applyHtmlToPage = (pageId, html) => {
    s.writes.push({ pageId, html })
    return s.writes.length === 1 ? new Promise(resolve => { release = resolve }) : Promise.resolve()
  }
  const first = s.c.handleGlobalUndo(), second = s.c.handleGlobalUndo(), third = s.c.handleGlobalRedo()
  await Promise.resolve()
  assert.deepEqual(s.writes.map(r => r.html), ['B'])
  release(); await Promise.all([first, second, third])
  assert.deepEqual(s.writes.map(r => r.html), ['B', 'A', 'B'])
  assert.equal(s.c.pageEditUndoStack.value.at(-1).html, 'B')
  assert.equal(s.c.pageEditRedoStack.value.at(-1).html, 'C')
})

test('a failed command does not leave later history requests blocked', async () => {
  const s = setup(); let first = true
  s.c.pageEditUndoStack.value.push({ pageId: 1, timestamp: 3, html: 'C', prevHtml: 'B' })
  s.c.applyHtmlToPage = async () => { if (first) { first = false; throw new Error('save unavailable') } }
  await assert.rejects(s.c.handleGlobalUndo(), /save unavailable/)
  assert.equal(await s.c.handleGlobalUndo(), true)
})

test('outer inspector buttons forward arrow nudges to the selected layers and retain Shift steps', () => {
  const s = setup(); let prevented = 0
  const e = { key: 'ArrowDown', shiftKey: true, preventDefault() { prevented++ }, target: { closest: () => null } }
  assert.equal(s.c.onEditorNudgeKey(e, false), true)
  assert.deepEqual(s.commands, [{ type: 'wf-nudge', dx: 0, dy: 10, uids: ['label'] }])
  assert.equal(prevented, 1)
})

test('arrow forwarding leaves text inputs, menus, viewers, preview and other tools untouched', () => {
  const scenarios = [
    s => { s.input = true },
    s => { s.e.target.closest = () => ({}) },
    s => { s.c.isViewer.value = true },
    s => { s.c.mode.value = 'preview' },
    s => { s.c.workbenchMode.value = 'interactive' },
    s => { s.c.activeDrawTool.value = 'text' },
    s => { s.c.isVectorMode.value = true },
    s => { s.c.fineTune.value = false },
    s => { s.c.hasSelectedDomElements.value = false },
    s => { s.c.pageEditingConflicts.value[1] = { conflict: true } },
    s => { s.e.ctrlKey = true },
  ]
  for (const change of scenarios) {
    const s = { ...setup(), input: false, e: { key: 'ArrowUp', preventDefault() { throw new Error('unexpected interception') }, target: { closest: () => null } } }
    change(s)
    assert.equal(s.c.onEditorNudgeKey(s.e, s.input), false)
    assert.equal(s.commands.length, 0)
  }
})
