import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'
import { effectScope, nextTick, ref, watch } from 'vue'

const script = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const functions = ['enterDesignMode', 'setWorkbenchMode', 'doCreatePage', 'insertComponentIntoPage',
  'resetComponentDrag', 'onPaletteDragStart', 'onPaletteDragEnd', 'onComponentDragOver',
  'onComponentPointerMove', 'onComponentPointerDown', 'onComponentDragVisibilityChange', 'onDropZoneDrop']
const code = ast.statements.filter(n => ts.isFunctionDeclaration(n) ? functions.includes(n.name?.text)
  : ts.isExpressionStatement(n) && ts.isCallExpression(n.expression) && n.expression.expression.getText(ast) === 'watch'
    && n.expression.arguments[0].getText(ast) === 'activeDrawTool').map(n => n.getText(ast)).join('\n')

function setup(t) {
  const calls = [], page = { id: 2, name: '空白画板', html_content: '<html><body></body></html>' }, scope = effectScope()
  const c = { ref, watch, nextTick, console, Date, Promise, window: {}, document: { hidden: false },
    id: 900001, pages: ref([page]), currentUser: ref(null), isViewer: ref(false), fineTune: ref(false), mode: ref('edit'),
    workbenchMode: ref('interactive'), activeDrawTool: ref('select'), focusPageId: ref(1), selectedNodeId: ref(1),
    activeSelectedElementInfo: ref(null), selectedDomLayerUid: ref(null), selectedDomLayerUids: ref([]),
    activeInteractionPageId: ref(1), liveHotspot: ref(null), flowHandleArmed: ref(false), selectedConnIds: ref(new Set()),
    activeVectorPageId: ref(null), isEditingVector: ref(false), activeEditingPageId: ref(null), pageEditingConflicts: ref({}),
    pageEditUndoStack: ref([]), pendingFramePreset: ref(null),
    isDraggingComponent: ref(false), hoveredDropBlockId: ref(null),
    artboardPointInBlock: () => ({ x: 50, y: 60 }), fitDropInFrame: (x, y) => ({ x, y }),
    showToast: message => calls.push(['toast', message]), ElMessage: { info() {}, error: e => calls.push(['error', e]), warning() {} },
    requestPageEditor: id => calls.push(['editor', id]), recordAuditLog() {}, clearAllRedoStacks() {},
    loadData: async () => {}, focusPage: id => calls.push(['focus', id]),
    projectApi: { createPage: async () => ({ id: 2, name: '空白画板' }) },
    pageRefs: ref({ 2: { insertComponent(...args) { calls.push(['insert', c.workbenchMode.value, ...args]) } } }),
    onSaveHtml() { assert.fail('mounted fixture must use the live iframe') },
  }
  vm.createContext(c)
  scope.run(() => vm.runInContext(transformSync(code, { loader: 'ts' }).code, c))
  t.after(() => scope.stop())
  return { c, calls }
}

test('choosing a creation tool exits interaction mode before drawing, including keyboard tool changes', async t => {
  for (const tool of ['frame', 'rect', 'circle', 'container', 'line', 'pen', 'pencil', 'text']) {
    const { c } = setup(t)
    c.activeDrawTool.value = tool; await nextTick()
    assert.equal(c.workbenchMode.value, 'design', tool)
    assert.equal(c.fineTune.value, true, tool)
    c.activeDrawTool.value = 'select'; await nextTick()
    assert.equal(c.workbenchMode.value, 'design', 'finishing placement must retain editing')
  }
})

test('selection, hand and comment tools retain the chosen interaction mode', async t => {
  const { c } = setup(t)
  for (const tool of ['hand', 'select', 'comment']) {
    c.activeDrawTool.value = tool; await nextTick()
    assert.equal(c.workbenchMode.value, 'interactive')
  }
})

test('successful blank-frame creation enters design mode and focuses the created page', async t => {
  const { c, calls } = setup(t)
  await c.doCreatePage({ name: '空白画板', width: 375, height: 812, x: 100, y: 80 }); await nextTick()
  assert.equal(c.workbenchMode.value, 'design')
  assert.equal(c.fineTune.value, true)
  assert.equal(c.focusPageId.value, 2)
  assert.ok(calls.some(call => call[0] === 'focus' && call[1] === 2))
})

test('adding a component from interaction mode enables design before sending the insertion', t => {
  const { c, calls } = setup(t)
  c.insertComponentIntoPage(2, { html: '<button>按钮</button>', name: '按钮' }, 20, 30)
  assert.equal(c.workbenchMode.value, 'design')
  assert.equal(c.fineTune.value, true)
  const insertion = calls.find(call => call[0] === 'insert')
  assert.equal(insertion?.[1], 'design')
  assert.equal(c.focusPageId.value, 2)
})

test('invalid insertions and failed creation do not change interaction mode', async t => {
  const { c } = setup(t)
  c.insertComponentIntoPage(999, '<button>按钮</button>')
  c.insertComponentIntoPage(2, '')
  assert.equal(c.workbenchMode.value, 'interactive')
  c.projectApi.createPage = async () => { throw new Error('creation failed') }
  await c.doCreatePage({ name: '空白画板', width: 375, height: 812, x: 0, y: 0 })
  assert.equal(c.workbenchMode.value, 'interactive')
})

test('viewers do not enter editing by selecting a creation tool', async t => {
  const { c } = setup(t); c.isViewer.value = true
  c.activeDrawTool.value = 'rect'; await nextTick()
  assert.equal(c.workbenchMode.value, 'interactive')
})

test('cancelled component drags recover when dragend is missing, without cancelling a held drag', t => {
  const { c } = setup(t)
  const start = () => {
    c.window.__wfDraggingComponent = { name: '按钮', html: '<button>按钮</button>' }
    c.onPaletteDragStart(); c.hoveredDropBlockId.value = 2
  }
  start(); c.onComponentPointerMove({ buttons: 1 })
  assert.equal(c.isDraggingComponent.value, true, 'the receiving shield stays during the native drag')
  for (const end of [() => c.onComponentPointerMove({ buttons: 0 }), () => c.onComponentPointerDown(),
    () => c.onPaletteDragEnd(), () => { c.document.hidden = true; c.onComponentDragVisibilityChange() }]) {
    start(); end()
    assert.equal(c.isDraggingComponent.value, false, 'normal input must reach the iframe again')
    assert.equal(c.hoveredDropBlockId.value, null)
    assert.equal(c.window.__wfDraggingComponent, null)
    c.onComponentDragOver({ preventDefault() { assert.fail('a trailing dragover must not re-arm the shield') } })
    assert.equal(c.isDraggingComponent.value, false)
  }
})

test('dropping preserves the component payload before clearing the shield and enables immediate editing', t => {
  const { c, calls } = setup(t)
  c.window.__wfDraggingComponent = { name: '按钮', html: '<button>按钮</button>', width: 140, height: 40 }
  c.onPaletteDragStart()
  c.onDropZoneDrop({ preventDefault() {}, stopPropagation() {}, currentTarget: null },
    { page: c.pages.value[0], pageId: 2 })
  const insertion = calls.find(call => call[0] === 'insert')
  assert.equal(insertion[1], 'design')
  assert.equal(insertion[2], '<button>按钮</button>')
  assert.equal(insertion.at(-2), 140); assert.equal(insertion.at(-1), 40)
  assert.equal(c.isDraggingComponent.value, false)
  assert.equal(c.window.__wfDraggingComponent, null)
})

test('tool and quick-add insertions clear a preceding cancelled drag', t => {
  const { c, calls } = setup(t)
  c.window.__wfDraggingComponent = { html: '<button>旧组件</button>' }; c.onPaletteDragStart()
  c.insertComponentIntoPage(2, { html: '<div>新元素</div>', name: '新元素' }, 10, 20)
  assert.equal(c.isDraggingComponent.value, false)
  assert.equal(c.window.__wfDraggingComponent, null)
  assert.ok(calls.some(call => call[0] === 'insert' && call[2] === '<div>新元素</div>'))
})
