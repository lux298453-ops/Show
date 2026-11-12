import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { transformSync } from 'esbuild'
import { ref } from 'vue'

const script = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['onVectorCommit', 'onVectorCancel', 'onVectorToolbarDone']
const code = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.includes(node.name?.text))
  .map(node => node.getText(ast)).join('\n')

function setup(tool = 'pencil', editing = false) {
  const inserted = [], replaced = [], restored = [], toasts = []
  const c = { activeDrawTool: ref(tool), activeVectorPageId: ref(7), vectorHostKey: ref('prototype-7'),
    isEditingVector: ref(editing), editingVectorUid: ref(editing ? 'original-vector' : ''),
    editingVectorPath: ref(editing ? { points: [] } : null), vectorOverlayRef: ref(null),
    pageRefs: ref({ 7: { insertVectorShapes: (...args) => inserted.push(args),
      replaceVectorOriginal: (...args) => replaced.push(args), restoreVectorOriginal: uid => restored.push(uid) } }),
    showToast: text => toasts.push(text) }
  vm.createContext(c)
  vm.runInContext(transformSync(code, { loader: 'ts' }).code, c)
  return { c, inserted, replaced, restored, toasts }
}

function assertExited(c) {
  assert.equal(c.activeDrawTool.value, 'select')
  assert.equal(c.activeVectorPageId.value, null)
  assert.equal(c.vectorHostKey.value, null)
  assert.equal(c.isEditingVector.value, false)
}

test('pencil Done exits even before drawing or when no overlay is mounted', () => {
  for (const overlay of [null, { hasActivePoints: () => false }]) {
    const { c, inserted } = setup()
    c.vectorOverlayRef.value = overlay
    c.onVectorToolbarDone()
    assertExited(c)
    assert.equal(inserted.length, 0)
  }
})

test('pencil strokes remain independent and continuous until Done without duplicating or deleting them', () => {
  const { c, inserted, replaced, restored } = setup()
  const first = { html: '<svg id="first"></svg>' }, second = { html: '<svg id="second"></svg>' }
  for (const stroke of [first, second]) {
    c.onVectorCommit([stroke])
    assert.equal(c.activeDrawTool.value, 'pencil')
    assert.equal(c.activeVectorPageId.value, 7)
  }
  c.vectorOverlayRef.value = { hasActivePoints: () => false, commitPath: () => assert.fail('saved strokes must not be submitted twice') }
  c.onVectorToolbarDone()
  assertExited(c)
  assert.equal(inserted.length, 2)
  assert.equal(inserted[0][0][0], first)
  assert.equal(inserted[1][0][0], second)
  assert.ok(inserted.every(call => call[2] === false), 'automatic strokes must not select and replace the previous stroke')
  assert.equal(replaced.length, 0)
  assert.equal(restored.length, 0)
})

test('Done commits any remaining pencil points once before clearing the drawing host', () => {
  const { c, inserted } = setup()
  let commits = 0
  c.vectorOverlayRef.value = { hasActivePoints: () => true, commitPath() {
    commits++
    assert.equal(c.activeVectorPageId.value, 7)
    c.onVectorCommit([{ html: '<svg id="pending"></svg>' }])
  } }
  c.onVectorToolbarDone()
  assertExited(c)
  assert.equal(commits, 1)
  assert.equal(inserted.length, 1)
})

test('pen Done still commits a new path and existing vector edits still replace their source', () => {
  for (const editing of [false, true]) {
    const { c, inserted, replaced, restored } = setup('pen', editing)
    let commits = 0
    c.vectorOverlayRef.value = { commitPath() { commits++; c.onVectorCommit([{ html: '<svg id="result"></svg>' }]) } }
    c.onVectorToolbarDone()
    assertExited(c)
    assert.equal(commits, 1)
    assert.equal(inserted.length, editing ? 0 : 1)
    assert.equal(replaced.length, editing ? 1 : 0)
    assert.equal(restored.length, 0, 'Done must apply the edited path instead of restoring its old version')
  }
})
