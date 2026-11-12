import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { transformSync } from 'esbuild'

const script = readFileSync('src/components/PageCanvas.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('canvas.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const fn = ast.statements.find(n => ts.isFunctionDeclaration(n) && n.name?.text === 'recomputeLines').getText(ast)

test('inactive annotation lines do not force layout across retained artboards', () => {
  let reads = 0
  const c = { props: { showAnnotations: false, selectedElementId: 1, hoveredAnnId: null, hoveredElementId: null },
    annItems: { value: [{ id: 2, elementId: 1, anchorX: 80, anchorY: 40 }] },
    activeLines: { value: [{ id: 9 }] }, rootRef: { value: { getBoundingClientRect() { reads++; return { left: 100, top: 200, width: 375 } } } },
    stageW: { value: 375 }, panelBodyRef: { value: null }, panelX: { value: 400 },
    isPanelOpen: { value: false }, boxRefs: new Map(), panelHeaderRef: { value: null },
  }
  vm.createContext(c); vm.runInContext(transformSync(fn, { loader: 'ts' }).code, c)
  c.recomputeLines(); assert.equal(reads, 0); assert.equal(c.activeLines.value.length, 0)
  c.props.showAnnotations = true; c.props.selectedElementId = null
  const unchanged = c.activeLines.value
  for (let i = 0; i < 72; i++) c.recomputeLines()
  assert.equal(reads, 0); assert.equal(c.activeLines.value, unchanged)
  c.props.selectedElementId = 1; c.recomputeLines()
  assert.equal(reads, 1); assert.equal(c.activeLines.value.length, 1)
  assert.equal(c.activeLines.value[0].id, 2); assert.match(c.activeLines.value[0].path, /^M 80 40 /)
  c.props.selectedElementId = null; c.recomputeLines()
  assert.equal(reads, 1); assert.equal(c.activeLines.value.length, 0)
})
