import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'
import { shallowRef, ref, computed, watch, effect, stop } from 'vue'

const module = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasMotion.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code,
  { module, exports: module.exports })
const { createCanvasMotion, zoomCanvasAt } = module.exports
const inputModule = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasInput.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code,
  { module: inputModule, exports: inputModule.exports })
const script = readFileSync('src/views/PrototypeView.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('view.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['view', 'canvasMotion', 'paintedCanvasView', 'wheelViewportRect', 'viewportOrigin', 'canvasNavigationMode']
const functions = ['paintCanvasTransform', 'canvasFramePoint', 'startCanvasPan', 'onWindowMouseMove', 'onWindowMouseUp', 'onWheel', 'finishWheelZoom', 'zoomAt', 'zoomAtCenter', 'resetZoom100', 'resetCanvasPan', 'onCanvasZoomCommand', 'onCanvasShortcut', 'fitAll', 'fitSelection', 'fitCanvasItems', 'onViewportWheel']
const source = ast.statements.filter(n => ts.isVariableStatement(n)
  ? n.declarationList.declarations.some(d => names.includes(d.name.getText(ast)))
  : ts.isFunctionDeclaration(n) ? functions.includes(n.name?.text)
  : /^(watch\(view,|watch\(canvasContentRef,)/.test(n.getText(ast))).map(n => n.getText(ast)).join('\n')

function setup(batched = false) {
  const frames = new Map(), timers = new Map(), listeners = new Map()
  let sequence = 0, reads = 0, stops = 0
  const context = {
    shallowRef, ref, computed, watch, createCanvasMotion, zoomCanvasAt, fitCanvasBounds: module.exports.fitCanvasBounds,
    canvasWheelIntent: inputModule.exports.canvasWheelIntent,
    canvasTransform: module.exports.canvasTransform,
    scheduleCanvasPaint: batched ? callback => {
      const id = ++sequence; frames.set(id, callback)
      return () => frames.delete(id)
    } : undefined,
    canvasContentRef: ref({ style: {}, classList: { add() {}, remove() {} } }), zoomLabelRef: ref({ textContent: '' }),
    connectionWorldRef: ref({ style: {} }), connectionBadgeWorldRef: ref({ style: {} }),
    viewportRef: ref({ getBoundingClientRect() { reads++; return { left: 256, top: 56 } } }),
    viewportSize() { return { w: 705, h: 864 } },
    viewportHeight: ref(864),
    mode: ref('edit'), selectedBlockKeys: ref(new Set()), selectedNodeKey: ref(null), focusPageId: ref(null),
    blocks: ref([{ key: 'proto-1', pageId: 1, blockType: 'prototype', x: 100, y: 100, w: 375, h: 812 }]), animTimer: null,
    isDragging: ref(false), isBlockDragging: ref(false), isWheelZooming: ref(false), animating: ref(false),
    isSpacePressed: ref(false), selectedElementId: ref(1), selectedConnIds: ref(new Set([1])),
    hoveredTargetBlockId: ref(null),
    pageRefs: ref({ 1: { stopCanvasPan() { stops++ } } }), suppressBlockClick: false,
    dragStart: { x: 0, y: 0 }, dragOrigin: { x: 0, y: 0 }, wheelIdleTimer: null,
    requestAnimationFrame(callback) { const id = ++sequence; frames.set(id, callback); return id },
    cancelAnimationFrame(id) { frames.delete(id) },
    setTimeout(callback, delay) { const id = ++sequence; timers.set(id, { callback, delay }); return id },
    clearTimeout(id) { timers.delete(id) },
    window: { addEventListener(name, callback) { listeners.set(name, callback) }, removeEventListener(name) { listeners.delete(name) } },
  }
  context.isAnyDragging = computed(() => context.isDragging.value || context.isBlockDragging.value)
  vm.createContext(context)
  vm.runInContext(transformSync(source + '\nglobalThis.motion = canvasMotion; globalThis.state = view; globalThis.canvasNavigationMode = canvasNavigationMode; globalThis.setViewportOrigin = value => { viewportOrigin = value }', { loader: 'ts' }).code, context)
  context.state.value = { x: 10, y: 20, k: .75 }
  return {
    context, frames, timers, listeners,
    get reads() { return reads }, get stops() { return stops },
    flush() { const callbacks = [...frames.values()]; frames.clear(); callbacks.forEach(fn => fn(0)) },
    idle() { for (const [id, timer] of timers) { if (timer.delay === 180) { timers.delete(id); timer.callback() } } },
    view() { return { ...context.state.value } },
  }
}

test('pan input paints immediately and commits the workbench once on release', () => {
  const s = setup(), c = s.context
  let updates = 0
  const render = effect(() => { c.state.value; updates++ })
  c.startCanvasPan({ clientX: 500, clientY: 300 })
  for (let i = 1; i <= 12; i++) {
    c.onWindowMouseMove({ clientX: 500 + i * 8, clientY: 300 + i * 4, buttons: 1 }); s.flush()
  }
  assert.equal(updates, 1)
  assert.deepEqual(s.view(), { x: 106, y: 68, k: .75 })
  assert.equal(c.canvasContentRef.value.style.transform, 'translate3d(106px, 68px, 0) scale(0.75)')
  assert.equal(c.connectionWorldRef.value.style.transform, c.canvasContentRef.value.style.transform)
  assert.equal(c.connectionBadgeWorldRef.value.style.transform, c.canvasContentRef.value.style.transform)
  c.onWindowMouseUp()
  assert.equal(updates, 2)
  assert.equal(c.isDragging.value, false)
  assert.equal(s.listeners.size, 0)
  assert.equal(s.stops, 1)
  stop(render)
})

test('a burst leaves no input queued behind RAF and retains its last position on release', () => {
  const s = setup(), c = s.context
  c.startCanvasPan({ clientX: 500, clientY: 300 })
  for (let i = 1; i <= 120; i++) c.onWindowMouseMove({ clientX: 500 + i / 4, clientY: 300 + i / 8, buttons: 1 })
  assert.equal(s.frames.size, 0)
  assert.deepEqual(s.view(), { x: 40, y: 35, k: .75 })
  assert.match(c.canvasContentRef.value.style.transform, /40px, 35px/)
  c.onWindowMouseUp()
  assert.deepEqual(s.view(), { x: 40, y: 35, k: .75 })
  assert.equal(s.frames.size, 0)
  assert.match(c.canvasContentRef.value.style.transform, /40px, 35px/)
})

test('wheel frames retain the cursor anchor and read viewport bounds once per gesture', () => {
  const s = setup(), c = s.context
  const anchor = { x: 600 - 256, y: 400 - 56 }, before = s.view()
  let updates = 0
  const render = effect(() => { c.state.value; updates++ })
  for (let i = 0; i < 12; i++) { c.onWheel({ clientX: 600, clientY: 400, deltaY: -12 }); s.flush() }
  const after = s.view()
  assert.ok(Math.abs((anchor.x - before.x) / before.k - (anchor.x - after.x) / after.k) < 1e-9)
  assert.ok(Math.abs((anchor.y - before.y) / before.k - (anchor.y - after.y) / after.k) < 1e-9)
  assert.equal(updates, 1)
  assert.equal(s.reads, 1)
  assert.equal(c.zoomLabelRef.value.textContent, `${Math.round(after.k * 100)}%`)
  s.idle()
  assert.equal(updates, 2)
  assert.equal(c.isWheelZooming.value, false)
  c.onWheel({ clientX: 600, clientY: 400, deltaY: 12 })
  assert.equal(s.reads, 2)
  c.finishWheelZoom(); stop(render)
})

test('wheel input paints synchronously and moving anchors compose in event order', () => {
  const s = setup(), c = s.context
  let expected = s.view()
  for (const e of [{ clientX: 600, clientY: 400, deltaY: -20 }, { clientX: 650, clientY: 450, deltaY: 30 }]) {
    c.onWheel(e)
    expected = { ...zoomCanvasAt(expected, e.clientX - 256, e.clientY - 56, Math.exp(-e.deltaY * .0012)) }
  }
  assert.equal(s.frames.size, 0)
  assert.deepEqual(s.view(), expected)
  s.idle()
  assert.deepEqual(s.view(), expected)
  assert.equal(s.frames.size, 0)
})

test('focus replacement paints its destination and button zoom uses the current view', () => {
  const s = setup(), c = s.context
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -200 })
  c.state.value = { x: 100, y: 200, k: .5 } // focus/fit/restore destination
  assert.equal(c.canvasContentRef.value.style.transform, 'translate3d(100px, 200px, 0) scale(0.5)')
  s.flush(); s.idle()
  assert.deepEqual(s.view(), { x: 100, y: 200, k: .5 })
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -12 })
  s.flush()
  const expected = { ...zoomCanvasAt(s.view(), 344, 344, 1.18) }
  c.zoomAt(600, 400, 1.18)
  s.flush(); s.idle()
  assert.deepEqual(s.view(), expected)
})

test('wheel to pan, lost mouse button, blur and disposal cannot leave a stale transform', () => {
  const s = setup(), c = s.context
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -12 })
  c.startCanvasPan({ clientX: 600, clientY: 400 })
  assert.equal(c.isWheelZooming.value, false)
  const before = s.view()
  c.onWindowMouseMove({ clientX: 610, clientY: 410, buttons: 1 })
  c.onWindowMouseMove({ clientX: 610, clientY: 410, buttons: 0 })
  assert.equal(c.isDragging.value, false)
  assert.deepEqual(s.view(), { ...before, x: before.x + 10, y: before.y + 10 })
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -12 })
  c.resetCanvasPan()
  assert.equal(s.frames.size, 0)
  assert.equal(c.isWheelZooming.value, false)
  c.motion.panTo(1, 2)
  const last = s.view()
  c.motion.dispose(); c.motion.panTo(500, 600); c.motion.finish()
  assert.deepEqual(s.view(), last)
  assert.equal(s.frames.size, 0)
})

test('reset to 100 percent flushes the pending wheel scale before computing its factor', () => {
  const s = setup(), c = s.context
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -200 })
  c.resetZoom100()
  assert.ok(Math.abs(s.view().k - 1) < 1e-12)
  assert.equal(c.isWheelZooming.value, false)
  assert.equal(s.frames.size, 0)
})

test('zoom limits retain the anchor at both extremes', () => {
  for (const factor of [1e-20, 1e20]) {
    const before = { x: 12, y: 34, k: .75 }, after = zoomCanvasAt(before, 344, 344, factor)
    assert.equal(after.k, factor < 1 ? .04 : 3)
    assert.ok(Math.abs((344 - before.x) / before.k - (344 - after.x) / after.k) < 1e-9)
  }
})

test('two-axis trackpad panning paints synchronously, commits once and retains scale', () => {
  const s = setup(), c = s.context, before = s.view()
  c.canvasNavigationMode.value = 'trackpad'
  let updates = 0
  const render = effect(() => { c.state.value; updates++ })
  for (let i = 0; i < 120; i++) c.onWheel({ clientX: 600, clientY: 400, deltaX: 1.25, deltaY: .5 })
  assert.deepEqual(s.view(), { ...before, x: before.x - 150, y: before.y - 60 })
  assert.equal(updates, 1); assert.equal(s.reads, 1)
  s.idle(); assert.equal(updates, 2); stop(render)
})

test('Shift scroll pans horizontally while Ctrl scroll zooms at the cursor in either mode', () => {
  for (const mode of ['mouse', 'trackpad']) {
    const s = setup(), c = s.context
    c.canvasNavigationMode.value = mode
    c.onWheel({ clientX: 600, clientY: 400, deltaY: 30, shiftKey: true })
    assert.deepEqual(s.view(), { x: -20, y: 20, k: .75 })
    const expected = { ...zoomCanvasAt(s.view(), 344, 344, Math.exp(.12)) }
    c.onWheel({ clientX: 600, clientY: 400, deltaY: -100, ctrlKey: true })
    assert.deepEqual(s.view(), expected)
  }
})

test('zoom presets use the current pending scale and fit selection changes no artboard coordinates', () => {
  const s = setup(), c = s.context
  c.onWheel({ clientX: 600, clientY: 400, deltaY: -200 })
  c.onCanvasZoomCommand('150')
  assert.equal(s.view().k, 1.5)
  const before = JSON.stringify(c.blocks.value)
  c.blocks.value.push({ key: 'proto-2', pageId: 2, blockType: 'prototype', x: 900, y: 100, w: 375, h: 812 })
  c.selectedBlockKeys.value = new Set(['proto-2'])
  c.fitSelection()
  const expected = { ...module.exports.fitCanvasBounds([c.blocks.value[1]], 705, 864) }
  assert.deepEqual(s.view(), expected)
  assert.equal(JSON.stringify(c.blocks.value[0]), JSON.stringify(JSON.parse(before)[0]))
  c.onCanvasShortcut('reset-zoom'); assert.ok(Math.abs(s.view().k - 1) < 1e-12)
  c.onCanvasShortcut('fit-all')
  assert.deepEqual(s.view(), { ...module.exports.fitCanvasBounds(c.blocks.value, 705, 864) })
})

test('native wheel over typing controls and annotation panels does not navigate the canvas', () => {
  const s = setup(), c = s.context, initial = s.view(); let prevented = 0
  c.onViewportWheel({ target: { closest: () => true }, preventDefault() { prevented++ } })
  assert.equal(prevented, 0); assert.deepEqual(s.view(), initial)
  c.onViewportWheel({ clientX: 600, clientY: 400, deltaY: -100, target: { closest: () => false }, preventDefault() { prevented++ } })
  assert.equal(prevented, 1); assert.ok(s.view().k > initial.k)
})

test('fit bounds reserves toolbar and title space and handles empty or invalid selections', () => {
  assert.equal(module.exports.fitCanvasBounds([], 800, 900), null)
  assert.equal(module.exports.fitCanvasBounds([{ x: 0, y: 0, w: 0, h: 500 }], 800, 900), null)
  const b = { x: -900, y: 120, w: 375, h: 812 }, v = module.exports.fitCanvasBounds([b], 800, 900)
  assert.ok((b.y - 36) * v.k + v.y >= 39.999)
  assert.ok((b.y + b.h) * v.k + v.y <= 804.001)
  assert.ok(b.x * v.k + v.x >= 63.999)
})

test('batched motion keeps all logical samples, defers first paint and flushes the last on release', () => {
  const s=setup(true),c=s.context
  c.startCanvasPan({clientX:500,clientY:300})
  for(let i=1;i<=120;i++) c.onWindowMouseMove({clientX:500+i/4,clientY:300+i/8,buttons:1})
  assert.deepEqual(s.view(),{x:40,y:35,k:.75})
  assert.equal(s.frames.size,1)
  assert.match(c.canvasContentRef.value.style.transform,/10px, 20px/)
  c.onWindowMouseUp()
  assert.match(c.canvasContentRef.value.style.transform,/40px, 35px/)
  assert.equal(c.connectionWorldRef.value.style.transform,c.canvasContentRef.value.style.transform)
  assert.equal(c.connectionBadgeWorldRef.value.style.transform,c.canvasContentRef.value.style.transform)
  assert.equal(s.frames.size,0)
  s.flush();assert.deepEqual(s.view(),{x:40,y:35,k:.75})
})

test('batched wheel preserves moving anchors and explicit focus cancels obsolete paint and commit',()=>{
  const s=setup(true),c=s.context;let expected=s.view()
  for(let i=0;i<120;i++){
    const e={clientX:600+i%8,clientY:400+i%5,deltaY:i%2?3:-2}
    c.onWheel(e);expected={...zoomCanvasAt(expected,e.clientX-256,e.clientY-56,Math.exp(-e.deltaY*.0012))}
  }
  assert.deepEqual(s.view(),expected);assert.equal(s.frames.size,1)
  s.flush();assert.equal(c.canvasContentRef.value.style.transform,module.exports.canvasTransform(expected))
  c.onWheel({clientX:600,clientY:400,deltaY:-20});c.onWheel({clientX:600,clientY:400,deltaY:-20})
  c.state.value={x:-99,y:50,k:.5};s.flush();s.idle()
  assert.deepEqual(s.view(),{x:-99,y:50,k:.5})
  assert.equal(c.connectionWorldRef.value.style.transform,'translate3d(-99px, 50px, 0) scale(0.5)')
})

test('batched trackpad, 100 percent, presets and dispose flush/cancel in the right order',()=>{
  const s=setup(true),c=s.context;c.canvasNavigationMode.value='trackpad'
  for(let i=0;i<120;i++)c.onWheel({clientX:600,clientY:400,deltaX:1.25,deltaY:.5})
  s.idle();assert.deepEqual(s.view(),{x:-140,y:-40,k:.75});assert.equal(s.frames.size,0)
  for(let i=0;i<5;i++)c.onWheel({clientX:600,clientY:400,deltaY:-100,ctrlKey:true})
  c.resetZoom100();assert.ok(Math.abs(s.view().k-1)<1e-12)
  c.onCanvasZoomCommand('150');assert.equal(s.view().k,1.5)
  const painted=c.canvasContentRef.value.style.transform
  c.motion.panTo(10,20);c.motion.panTo(40,50);c.motion.dispose();s.flush()
  assert.equal(c.canvasContentRef.value.style.transform,painted)
})

test('a one-sample gesture released before RAF still paints and commits its final position',()=>{
  const s=setup(true),c=s.context
  c.startCanvasPan({clientX:500,clientY:300})
  c.onWindowMouseMove({clientX:530,clientY:315,buttons:1})
  assert.match(c.canvasContentRef.value.style.transform,/10px, 20px/)
  c.onWindowMouseUp()
  assert.deepEqual(s.view(),{x:40,y:35,k:.75})
  assert.match(c.canvasContentRef.value.style.transform,/40px, 35px/)
  assert.equal(s.frames.size,0)
  s.flush();assert.match(c.canvasContentRef.value.style.transform,/40px, 35px/)
})

test('wheel onset uses the measured viewport origin without forcing a new geometry read',()=>{
  const s=setup(true),c=s.context
  c.setViewportOrigin({left:256,top:56})
  let expected={...zoomCanvasAt(s.view(),344,344,Math.exp(.12))}
  c.onWheel({clientX:600,clientY:400,deltaY:-100})
  assert.equal(s.reads,0);assert.deepEqual(s.view(),expected)
  s.idle()
  // A new viewport measurement after a layout change must update the anchor.
  c.setViewportOrigin({left:128,top:80})
  expected={...zoomCanvasAt(s.view(),472,320,Math.exp(-.12))}
  c.onWheel({clientX:600,clientY:400,deltaY:100})
  assert.equal(s.reads,0);assert.deepEqual(s.view(),expected)
  s.idle();assert.equal(s.frames.size,0)
})

test('iframe input maps through the painted camera while RAF is pending and falls back during focus animations',()=>{
  const s=setup(true),c=s.context,block={x:-200,y:100,pageId:1},point={clientX:100,clientY:50}
  c.setViewportOrigin({left:256,top:56})
  const before={clientX:191,clientY:188.5}
  assert.deepEqual({...c.canvasFramePoint(block,point)},before)
  c.motion.panTo(110,120)
  assert.deepEqual({...c.canvasFramePoint(block,point)},before,'pending logical input must not move the visual hit-test camera')
  s.flush()
  assert.deepEqual({...c.canvasFramePoint(block,point)},{clientX:291,clientY:288.5})
  c.animating.value=true;assert.equal(c.canvasFramePoint(block,point),null)
  c.animating.value=false;c.hoveredTargetBlockId.value=1;assert.equal(c.canvasFramePoint(block,point),null)
})

test('paint scheduling waits for RAF and cancellation removes the pending frame',()=>{
  const frames=new Map();let id=0,calls=0
  const mod={exports:{}}
  vm.runInNewContext(transformSync(readFileSync('src/utils/canvasMotion.ts','utf8'),{loader:'ts',format:'cjs'}).code,{
    module:mod,exports:mod.exports,
    requestAnimationFrame:fn=>{frames.set(++id,fn);return id},cancelAnimationFrame:id=>frames.delete(id),
    setTimeout:()=>{throw new Error('Canvas paints must not compete with timer callbacks')},
  })
  mod.exports.scheduleCanvasPaint(()=>calls++)
  assert.equal(calls,0);assert.equal(frames.size,1)
  const [frame,callback]=[...frames.entries()][0]
  frames.delete(frame);callback()
  assert.equal(calls,1);assert.equal(frames.size,0)
  const cancel=mod.exports.scheduleCanvasPaint(()=>calls++);cancel()
  assert.equal(calls,1);assert.equal(frames.size,0)
})
