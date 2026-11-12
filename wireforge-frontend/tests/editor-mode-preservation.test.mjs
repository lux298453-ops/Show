import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'
import { computed, nextTick, reactive, ref, watch, effectScope } from 'vue'

// Exercise the real source ownership and watchers, including reactive reads
// inside HTML injection. Testing only the iframe runtime misses srcdoc reloads.
const script = readFileSync('src/components/PageCanvas.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('canvas.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const variables = ['editSourceHtml', 'lastInternalSavedHtml', 'initialIncludeEditor', 'hasLoadedEditorState', 'hasLoadedEditor', 'editorLifecycleVersion', 'navRuntimeHtml']
const sources = ['() => props.page.html_content', '() => props.active', '() => [props.editMode, props.loadEditor, props.protoHotspot]', '() => props.readOnly', '() => props.page?.id', '() => [editSourceHtml.value, props.page.id, props.active, props.frameType] as const', '() => props.page.elements']
const code = ast.statements.filter(n => {
  if (ts.isVariableStatement(n)) return n.declarationList.declarations.some(d => variables.includes(d.name.getText(ast)))
  return ts.isExpressionStatement(n) && ts.isCallExpression(n.expression) && n.expression.expression.getText(ast) === 'watch'
    && sources.includes(n.expression.arguments[0].getText(ast))
}).map(n => n.getText(ast)).join('\n')

function setup(t, initial = {}) {
  const props = reactive({ page: { id: 1, html_content: '<html>original</html>', elements: [] },
    active: true, editMode: true, loadEditor: true, protoHotspot: false, interactive: false, readOnly: false, ...initial })
  const calls = [], scope = effectScope()
  const context = { props, computed, ref, watch, fitSent: ref(false), withBackendFiles: html => html,
    hydrateEditorInFrame: () => calls.push('hydrate'), unloadEditorInFrame: () => calls.push('unload'),
    disposeEditorInFrame: () => calls.push('dispose'), sendEditMode: () => calls.push('edit'),
    sendProtoHotspot: () => calls.push('hotspot'), sendInteractiveMode: () => calls.push('bindings'),
    flushPendingSave: async () => calls.push('flush'),
    // Mirror the injected runtime's dependency on hotspot state and routing.
    injectNavRuntime: (html, interactive, readonly, editor) => JSON.stringify({ html, interactive, readonly, editor,
      pageId: props.page.id, hotspot: props.protoHotspot, elements: props.page.elements }),
  }
  vm.createContext(context)
  scope.run(() => vm.runInContext(transformSync(code + `
    globalThis.html = navRuntimeHtml;
    globalThis.source = editSourceHtml;
    globalThis.saved = value => { lastInternalSavedHtml = value; props.page.html_content = value; };
  `, { loader: 'ts' }).code, context))
  t.after(() => scope.stop())
  return { props, calls, html: context.html, source: context.source, save: context.saved }
}

test('saved and pending DOM edits survive design/hotspot/preview round trips without changing srcdoc', async t => {
  for (const saved of [false, true]) {
    const s = setup(t), initial = s.html.value
    if (saved) { s.save('<html>moved artwork and edited text</html>'); await nextTick() }
    for (let i = 0; i < 3; i++) {
      s.props.editMode = false; s.props.protoHotspot = true; await nextTick()
      assert.equal(s.html.value, initial, 'hotspot mode must retain the live edited document')
      s.props.protoHotspot = false; s.props.interactive = true; await nextTick()
      assert.equal(s.html.value, initial, 'preview mode must retain the live edited document')
      s.props.interactive = false; s.props.editMode = true; await nextTick()
      assert.equal(s.html.value, initial)
    }
    assert.ok(s.calls.includes('hotspot')); assert.ok(s.calls.includes('flush'))
  }
})

test('editing bindings and lazy editor hydration do not replace the document', async t => {
  const s = setup(t, { editMode: false, loadEditor: false }), initial = s.html.value
  s.props.loadEditor = true; await nextTick()
  s.props.page.elements.push({ id: 8, interaction: { action: 'navigate', target_page_id: 2 } }); await nextTick()
  s.props.page.elements[0].interaction.target_page_id = 3; await nextTick()
  assert.equal(s.html.value, initial)
  assert.ok(s.calls.includes('hydrate')); assert.ok(s.calls.includes('bindings'))
})

test('external HTML replacement remains effective immediately after an internal save', async t => {
  const s = setup(t), initial = s.html.value
  s.save('<html>saved DOM</html>'); await nextTick()
  assert.equal(s.html.value, initial)
  s.props.page.html_content = '<html>AI regeneration</html>'; await nextTick()
  assert.equal(JSON.parse(s.html.value).html, '<html>AI regeneration</html>')
  s.props.page.html_content = ''; await nextTick()
  assert.equal(JSON.parse(s.html.value).html, '')
  s.props.page.html_content = '<html>restored</html>'; await nextTick()
  assert.equal(JSON.parse(s.html.value).html, '<html>restored</html>')
  s.props.page = { ...s.props.page, id: 2 }; await nextTick()
  assert.equal(JSON.parse(s.html.value).pageId, 2)
})

test('unopened prototypes defer runtime generation, then open the latest source without mode reloads', async t => {
  const s = setup(t, { active: false, editMode: false, loadEditor: false, frameType: 'prototype' })
  assert.equal(s.html.value, '')
  s.props.page.html_content = '<html>regenerated before opening</html>'; await nextTick()
  assert.equal(s.html.value, '')
  s.props.active = true; await nextTick()
  assert.equal(JSON.parse(s.html.value).html, '<html>regenerated before opening</html>')
  const initial = s.html.value
  s.props.editMode = true; s.props.loadEditor = true; await nextTick()
  assert.equal(s.html.value, initial)
  const design = setup(t, { frameType: 'design' })
  assert.equal(design.html.value, '', 'design-image twins must not prepare unused iframe runtime')
})

test('readonly revokes the editor in place, and authorized editing hydrates it again', async t => {
  const s = setup(t), initial = s.html.value
  s.props.readOnly = true; await nextTick()
  assert.ok(s.calls.includes('dispose'))
  assert.equal(s.html.value, initial)
  s.calls.length = 0; s.props.readOnly = false; await nextTick()
  assert.ok(s.calls.includes('hydrate')); assert.ok(s.calls.includes('edit'))
  assert.equal(s.html.value, initial)
  const viewer = setup(t, { readOnly: true })
  assert.equal(JSON.parse(viewer.html.value).editor, false)
})

test('mode switching handles asynchronous export errors without delaying or undoing a later mode choice', async () => {
  const view = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const viewAst = ts.createSourceFile('view.ts', view, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const fn = viewAst.statements.find(n => ts.isFunctionDeclaration(n) && n.name?.text === 'setWorkbenchMode').getText(viewAst)
  const warnings = []; let reject
  const context = { Promise, Set, Object, console: { warn: (...args) => warnings.push(args) },
    pageRefs: ref({ 1: { flushPendingSave: () => new Promise((_resolve, fail) => { reject = fail }) } }),
    workbenchMode: ref('design'), activeSelectedElementInfo: ref({}), selectedDomLayerUid: ref('old'), selectedDomLayerUids: ref(['old']),
    activeInteractionPageId: ref(1), pages: ref([{ id: 1 }]), selectedNodeId: ref(1), focusPageId: ref(1),
    liveHotspot: ref(null), flowHandleArmed: ref(false), selectedConnIds: ref(new Set()), ElMessage: { info() {} },
  }
  vm.createContext(context); vm.runInContext(transformSync(fn, { loader: 'ts' }).code, context)
  context.setWorkbenchMode('interactive')
  assert.equal(context.workbenchMode.value, 'interactive')
  context.setWorkbenchMode('design'); reject(new Error('export failed')); await Promise.resolve()
  assert.equal(warnings.length, 1)
  assert.equal(context.workbenchMode.value, 'design')
})
