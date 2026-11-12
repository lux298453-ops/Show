import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { computed, effectScope, nextTick, reactive, ref, shallowReactive, watch } from 'vue'
import { DOMParser } from 'linkedom'

const script = readFileSync('src/components/PreviewPageStack.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  .replace(/^import .*$/gm, '')
const pages = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, canvas_height: 812, html_content: `<main>page ${index + 1}</main>`, elements: [] }))
function setup(t) {
  const props = reactive({ page: pages[0], allPages: pages, transition: 'tab' })
  const events = [], animations = [], motionStates = [], overlayEvents = [], cleanups = [], scope = effectScope()
  const context = { computed, nextTick, ref, shallowReactive, watch, clearTimeout, setTimeout,
    defineProps: () => props, defineEmits: () => (...args) => events.push(args), defineExpose: () => {},
    onBeforeUnmount: fn => cleanups.push(fn), tabTargetIds: page => page.peers || [],
    animatePreviewContent: (_, direction) => {
      animations.push(direction)
      motionStates.push({ displayed: context.stack?.displayed, layers: context.stack?.layers })
      return () => {}
    },
    preparePreviewOverlay: frame => frame?.dialog ? { needsBackdrop: true, restore: () => overlayEvents.push(frame) } : null,
  }
  vm.createContext(context)
  scope.run(() => vm.runInContext(transformSync(script + `
    globalThis.stack = { ready: markReady, loading: markLoading, forward, canvas: setCanvas,
      get displayed() { return displayedId.value }, get pending() { return pending.value },
      get layers() { return [...layers.value] },
      get ids() { return [...ids.value] }, get sources() { return [...sources.values()] } };
  `, { loader: 'ts' }).code, context))
  t.after(() => { cleanups.forEach(fn => fn()); scope.stop() })
  const choose = async (id, extra = {}) => { props.page = { ...pages[id - 1], ...extra }; await nextTick(); await nextTick() }
  return { props, events, animations, motionStates, overlayEvents, stack: context.stack, choose }
}

test('dialog navigation retains the source document as a live background and back only dismisses the overlay',async t=>{
  const s=setup(t)
  const base={htmlFrameRef:{state:{scroll:245,draft:'背包当前状态'}}},dialog={htmlFrameRef:{dialog:true}}
  s.stack.canvas(1,base);s.stack.ready(1);await nextTick()
  await s.choose(2,{peers:[]});s.stack.canvas(2,dialog);s.stack.ready(2);await nextTick();await nextTick()
  assert.deepEqual([...s.stack.layers],[1,2])
  assert.equal(s.stack.displayed,2)
  const motionCount=s.animations.length
  await s.choose(1);assert.deepEqual([...s.stack.layers],[1])
  assert.equal(s.animations.length,motionCount,'closing must not animate or replace the background')
  assert.equal(s.overlayEvents.length,1)
  assert.deepEqual(base.htmlFrameRef.state,{scroll:245,draft:'背包当前状态'})
})

test('nested dialogs close in order and returning from a full page reconstructs their original background',async t=>{
  const s=setup(t);s.stack.ready(1);await nextTick()
  for(const id of [2,3]){
    await s.choose(id);s.stack.canvas(id,{htmlFrameRef:{dialog:true}});s.stack.ready(id);await nextTick();await nextTick()
  }
  assert.deepEqual([...s.stack.layers],[1,2,3])
  await s.choose(4);s.stack.ready(4);await nextTick();await nextTick()
  assert.deepEqual([...s.stack.layers],[4])
  s.props.transition='backward';await s.choose(3)
  assert.deepEqual([...s.stack.layers],[1,2,3])
  await s.choose(2);assert.deepEqual([...s.stack.layers],[1,2])
  await s.choose(1);assert.deepEqual([...s.stack.layers],[1])
})

test('opening a dialog keeps its background in cache while a later destination is loading',async t=>{
  const s=setup(t);s.stack.ready(1);await nextTick()
  await s.choose(2);s.stack.canvas(2,{htmlFrameRef:{dialog:true}});s.stack.ready(2);await nextTick();await nextTick()
  for(let id=3;id<=12;id++){
    await s.choose(id,{peers:[8,9,10,11]})
    assert.ok(s.stack.ids.includes(1));assert.ok(s.stack.ids.includes(2))
    assert.deepEqual([...s.stack.layers],[1,2])
  }
})

const overlayModule={exports:{}}
vm.runInNewContext(transformSync(readFileSync('src/utils/previewOverlay.ts','utf8'),{loader:'ts',format:'cjs'}).code,{module:overlayModule,exports:overlayModule.exports})
function overlayFrame(markup){
  const doc=new DOMParser().parseFromString(markup,'text/html')
  doc.defaultView.getComputedStyle=node=>({display:node.style.display||'block',visibility:node.style.visibility||'visible',opacity:node.style.opacity||'1',backgroundColor:node.style.backgroundColor||'transparent',backgroundImage:node.style.backgroundImage||'none'})
  for(const node of doc.querySelectorAll('*')) node.getBoundingClientRect=()=>({x:0,y:0,width:375,height:node.matches('section')?247:833})
  const frame=doc.createElement('iframe');frame.style.backgroundColor='white'
  Object.defineProperties(frame,{contentDocument:{value:doc},clientWidth:{value:375},clientHeight:{value:833}})
  return {frame,doc}
}

test('preview composition hides the duplicate background, retains the real dialog and restores exact inline styles',()=>{
  const {frame,doc}=overlayFrame('<html style="background-color:#123"><head></head><body><main style="background-image:linear-gradient(red,blue)"><header>重新生成的背包</header><div data-wf-role="modal" style="background-color:rgba(0, 0, 0, .5)"><section role="dialog"><input value="保留弹窗输入"><button>取消</button></section></div></main></body></html>')
  const original=doc.documentElement.outerHTML,frameStyle=frame.getAttribute('style'),dialog=doc.querySelector('section'),input=doc.querySelector('input')
  const overlay=overlayModule.exports.preparePreviewOverlay(frame)
  assert.ok(overlay);assert.equal(overlay.needsBackdrop,false)
  assert.equal(doc.querySelector('header').style.visibility,'hidden')
  assert.equal(doc.querySelector('main').style.background,'transparent')
  assert.equal(frame.style.background,'transparent')
  assert.equal(doc.querySelector('section'),dialog);assert.equal(doc.querySelector('input'),input)
  assert.equal(dialog.getAttribute('style'),null)
  overlay.restore();overlay.restore()
  assert.equal(doc.documentElement.outerHTML,original)
  assert.equal(frame.getAttribute('style'),frameStyle)
})

test('ordinary pages with only closed dialogs remain whole pages; a card-only dialog gets one backdrop',()=>{
  for(const attributes of ['hidden','style="display:none"','style="opacity:0"']){
    const {frame,doc}=overlayFrame(`<html><head></head><body><main>背包</main><div ${attributes}><section role="dialog">关闭的弹窗</section></div></body></html>`),original=doc.documentElement.outerHTML
    assert.equal(overlayModule.exports.preparePreviewOverlay(frame),null)
    assert.equal(doc.documentElement.outerHTML,original)
  }
  const {frame}=overlayFrame('<html><head></head><body><main>背景</main><section role="dialog">购买</section></body></html>')
  assert.equal(overlayModule.exports.preparePreviewOverlay(frame).needsBackdrop,true)
})

test('saved blank-area return stays active through repeated openings of the same cached dialog',()=>{
  const {frame,doc}=overlayFrame('<html><head></head><body><div class="wf-page-dismiss" data-wf-inject="backdrop-return" data-action="back" style="position:absolute;inset:0;z-index:1;background:transparent"></div><main class="backdrop">重复背景</main><div class="wf-page-dismiss" id="authored-background">普通设计背景</div><section role="dialog"><button>确认</button></section></body></html>')
  const area=doc.querySelector('[data-wf-inject="backdrop-return"]'),dialog=doc.querySelector('[role="dialog"]')
  const original=doc.documentElement.outerHTML,areaStyle=area.getAttribute('style')
  for(let opening=0;opening<3;opening++){
    const overlay=overlayModule.exports.preparePreviewOverlay(frame)
    assert.ok(overlay)
    assert.equal(doc.querySelector('[data-wf-inject="backdrop-return"]'),area)
    assert.equal(area.getAttribute('style'),areaStyle,'the runtime back target must retain hit testing on every opening')
    assert.equal(area.getAttribute('data-action'),'back')
    assert.equal(doc.querySelector('main').style.visibility,'hidden')
    assert.equal(doc.querySelector('#authored-background').style.visibility,'hidden','only the injected transparent target is exempt')
    assert.equal(doc.querySelector('[role="dialog"]'),dialog)
    overlay.restore()
    assert.equal(doc.documentElement.outerHTML,original)
  }
})

function sheetFrame(attributes = 'class="sheet" data-wf-role="card"', dismiss = '<button data-action="back">收起</button>') {
  const result = overlayFrame(`<html><head></head><body><main><section class="backdrop">重复生成的家园</section><section ${attributes}>${dismiss}<input value="背包当前选择"></section></main></body></html>`)
  const sheet = result.doc.querySelector('main > section:last-child')
  sheet.getBoundingClientRect = () => ({ x: 0, y: 191, top: 191, bottom: 833, left: 0, right: 375, width: 375, height: 642 })
  return { ...result, sheet }
}

test('legacy bottom sheets reuse the origin without removing the normal-flow background spacer',()=>{
  const {frame,doc,sheet}=sheetFrame(),background=doc.querySelector('.backdrop'),input=sheet.querySelector('input')
  const original=doc.documentElement.outerHTML,rect=sheet.getBoundingClientRect()
  const overlay=overlayModule.exports.preparePreviewOverlay(frame)
  assert.ok(overlay);assert.equal(overlay.needsBackdrop,true)
  assert.equal(background.style.visibility,'hidden')
  assert.notEqual(background.style.display,'none','normal-flow sheet must keep its background spacer')
  assert.deepEqual(sheet.getBoundingClientRect(),rect)
  assert.ok(!sheet.style.visibility)
  assert.equal(sheet.querySelector('input'),input)
  assert.ok(sheet.hasAttribute('data-wf-preview-surface'))
  overlay.restore();assert.equal(doc.documentElement.outerHTML,original)
})

test('sheet-looking ordinary pages and hidden drawers are never composed as overlays',()=>{
  for(const kind of ['no-dismiss','no-background','full-page','hidden']){
    const {frame,doc,sheet}=sheetFrame()
    if(kind==='no-dismiss') sheet.querySelector('button').remove()
    if(kind==='no-background') doc.querySelector('.backdrop').className='hero'
    if(kind==='full-page') sheet.getBoundingClientRect=()=>({top:0,bottom:833,left:0,right:375,width:375,height:833})
    if(kind==='hidden') sheet.style.display='none'
    const original=doc.documentElement.outerHTML
    assert.equal(overlayModule.exports.preparePreviewOverlay(frame),null,kind)
    assert.equal(doc.documentElement.outerHTML,original)
  }
})

test('explicit drawers and side panels are recognized; nested dialogs stay part of the drawer',()=>{
  for(const attributes of ['data-wf-role="sheet"','data-wf-role="drawer"','class="drawer"']){
    const {frame,doc,sheet}=sheetFrame(attributes)
    sheet.innerHTML+='<section role="dialog">抽屉中的确认区域</section>'
    if(attributes==='class="drawer"') sheet.getBoundingClientRect=()=>({top:0,bottom:833,left:0,right:280,width:280,height:833})
    const overlay=overlayModule.exports.preparePreviewOverlay(frame)
    assert.ok(overlay)
    assert.deepEqual([...doc.querySelectorAll('[data-wf-preview-surface]')],[sheet])
    overlay.restore()
  }
})

test('keeps the old page while loading and ignores superseded destination readiness', async t => {
  const s = setup(t)
  s.stack.ready(1); await nextTick()
  assert.equal(s.stack.displayed, 1)
  await s.choose(2)
  assert.equal(s.stack.pending, true); assert.equal(s.stack.displayed, 1)
  s.stack.forward(1, 'navigate', ['stale']); assert.equal(s.events.filter(e => e[0] === 'navigate').length, 0)
  await s.choose(3)
  s.stack.ready(2); await nextTick()
  assert.equal(s.stack.displayed, 1)
  s.stack.ready(3); await nextTick(); await nextTick()
  assert.equal(s.stack.displayed, 3); assert.equal(s.stack.pending, false)
  s.stack.forward(3, 'navigate', ['accepted'])
  assert.equal(s.events.at(-1)[1], 'accepted')
})

test('cached tab switches keep node order and drafts, and use content transitions', async t => {
  const s = setup(t)
  s.stack.ready(1); await nextTick()
  await s.choose(2, { html_content: '<main>unsaved draft</main>' })
  s.stack.ready(2); await nextTick(); await nextTick()
  await s.choose(1)
  assert.deepEqual([...s.stack.ids], [1, 2], 'reordering iframes would reload them')
  assert.equal(s.stack.sources.find(p => p.id === 2).html_content, '<main>unsaved draft</main>')
  assert.equal(s.stack.pending, false)
  assert.ok(s.animations.every(type => type === 'tab'))
  await s.choose(1, { html_content: '<main>external replacement</main>' })
  assert.equal(s.stack.pending, true)
  s.stack.ready(1); await nextTick()
  assert.equal(s.stack.pending, false)
})

test('entry effects are prepared before the destination is displayed and never replay on ready messages', async t => {
  const s=setup(t)
  s.stack.ready(1);await nextTick()
  await s.choose(2)
  s.stack.ready(2);await nextTick();await nextTick()
  assert.equal(s.motionStates.length,1)
  assert.equal(s.motionStates[0].displayed,1)
  assert.deepEqual([...s.motionStates[0].layers],[1])
  assert.equal(s.stack.displayed,2)
  s.stack.ready(2);await nextTick();await nextTick()
  assert.equal(s.motionStates.length,1)
})

test('cache remains bounded and protects the visible page during slow navigation', async t => {
  const s = setup(t)
  s.stack.ready(1); await nextTick()
  for (let id = 2; id <= 12; id++) {
    await s.choose(id, { peers: [2, 3, 4, 5] })
    assert.ok(s.stack.ids.length <= 6)
    assert.ok(s.stack.ids.includes(1)); assert.ok(s.stack.ids.includes(id))
  }
})

const motionModule = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/previewMotion.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code,
  { module: motionModule, exports: motionModule.exports })
test('tabs switch in place; ordinary entries fade one host frame without animating document components', () => {
  const doc = new DOMParser().parseFromString('<html><head></head><body><div><div data-wf-chrome="status">status</div><header>tabs</header><main>cards</main><nav>navigation</nav></div></body></html>', 'text/html')
  const calls = []
  for (const node of doc.querySelectorAll('*')) {
    node.getBoundingClientRect = () => ({ height: 100 })
    node.animate = (frames, options) => { calls.push({ node: node.tagName, frames, options }); return { cancel() {} } }
  }
  let cancelled=false
  const frame = { contentDocument: doc, animate: (frames,options)=>{calls.push({node:'FRAME',frames,options});return {cancel(){cancelled=true}}} }
  doc.defaultView.matchMedia = () => ({ matches: false })
  doc.defaultView.getComputedStyle = node => ({ display: node.style.display || 'block', visibility: 'visible', opacity: node.style.opacity || '1' })
  const original=doc.documentElement.outerHTML
  motionModule.exports.animatePreviewContent(frame, 'tab')
  assert.equal(calls.length,0)
  const cancel=motionModule.exports.animatePreviewContent(frame, 'backward')
  assert.deepEqual(calls.map(c => c.node), ['FRAME'])
  assert.deepEqual(Array.from(calls[0].frames,frame=>Object.keys(frame)),[['opacity'],['opacity']])
  assert.equal(calls[0].options.duration,120)
  assert.equal(doc.documentElement.outerHTML,original)
  cancel();assert.equal(cancelled,true)
  calls.length=0
  motionModule.exports.animatePreviewContent(frame,'forward')
  assert.deepEqual(calls.map(c=>c.node),['FRAME'])
  doc.defaultView.matchMedia = () => ({ matches: true }); calls.length = 0
  motionModule.exports.animatePreviewContent(frame, 'forward')
  assert.equal(calls.length, 0)
})

test('standalone dialogs animate their surface without moving the backdrop or hidden dialogs', () => {
  const doc = new DOMParser().parseFromString('<html><body><div class="backdrop"></div><section role="dialog"><div class="wf-modal-card">result</div></section><div role="dialog" hidden>closed</div></body></html>', 'text/html')
  const calls = []
  for (const node of doc.querySelectorAll('*')) {
    node.getBoundingClientRect = () => ({ height: node.hidden ? 0 : 100 })
    node.animate = frames => { calls.push({ node: node.className, frames }); return { cancel() {} } }
  }
  doc.defaultView.matchMedia = () => ({ matches: false })
  doc.defaultView.getComputedStyle = node => ({ display: node.style.display || 'block', visibility: 'visible', opacity: node.style.opacity || '1' })
  motionModule.exports.animatePreviewContent({ contentDocument: doc }, 'forward')
  assert.deepEqual(calls.map(c => c.node), ['wf-modal-card'])
  assert.deepEqual(Array.from(calls[0].frames,frame=>Object.keys(frame)),[['opacity'],['opacity']])
})

test('a full viewport modal wrapper keeps its shade still while its nested dialog surface animates',()=>{
  const {frame,doc}=overlayFrame('<html><head></head><body><div data-wf-role="modal"><section class="confirm-card" role="dialog">购买道具</section></div></body></html>'),calls=[]
  doc.defaultView.matchMedia=()=>({matches:false})
  for(const node of doc.querySelectorAll('*')) node.animate=()=>{calls.push(node);return {cancel(){}}}
  motionModule.exports.animatePreviewContent(frame,'forward')
  assert.deepEqual(calls,[doc.querySelector('.confirm-card')])
})

test('a composed legacy sheet animates only its surface and keeps its background spacer still',()=>{
  const {frame,doc,sheet}=sheetFrame(),calls=[]
  const overlay=overlayModule.exports.preparePreviewOverlay(frame)
  doc.defaultView.matchMedia=()=>({matches:false})
  for(const node of doc.querySelectorAll('*')) node.animate=()=>{calls.push(node);return {cancel(){}}}
  motionModule.exports.animatePreviewContent(frame,'forward')
  assert.deepEqual(calls,[sheet])
  overlay.restore()
})

test('closed modal overlays and invisible feedback retain authored visibility during page transitions', () => {
  const doc = new DOMParser().parseFromString('<html><head></head><body><main>content</main><div role="dialog" style="opacity:0"><section>closed dialog</section></div><div style="opacity:0">feedback</div></body></html>', 'text/html')
  const calls = []
  for (const node of doc.querySelectorAll('*')) {
    node.getBoundingClientRect = () => ({ height: 833 })
    node.animate = () => { calls.push(node.tagName); return { cancel() {} } }
  }
  doc.defaultView.matchMedia = () => ({ matches: false })
  doc.defaultView.getComputedStyle = node => ({ display: 'block', visibility: 'visible', opacity: node.style.opacity || '1' })
  const original=doc.documentElement.outerHTML
  motionModule.exports.animatePreviewContent({ contentDocument: doc,animate:()=>{calls.push('FRAME');return{cancel(){}}} }, 'forward')
  assert.deepEqual(calls, ['FRAME'])
  assert.equal(doc.documentElement.outerHTML,original)
})
