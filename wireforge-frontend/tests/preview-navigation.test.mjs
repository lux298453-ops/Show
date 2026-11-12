import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { transformSync } from 'esbuild'
import { DOMParser } from 'linkedom'
import { createTestPrototypeSecurity } from './fixtures/prototype-security.mjs'

const security = createTestPrototypeSecurity()

// Exercise the actual injected runtime with a small DOM test double; no browser or project writes.
const sfc = readFileSync('src/components/PageCanvas.vue', 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast = ts.createSourceFile('canvas.ts', sfc, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const functions = ast.statements.filter(n => ts.isFunctionDeclaration(n) && ['injectNavRuntime', 'buildEditorPayload'].includes(n.name?.text)).map(n => n.getText(ast)).join('\n')
const compiled = transformSync(functions + '\nglobalThis.generate=injectNavRuntime;globalThis.editor=buildEditorPayload', { loader: 'ts' }).code
const backgroundModule={exports:{}}
const backgroundContext={module:backgroundModule,exports:backgroundModule.exports};vm.createContext(backgroundContext)
vm.runInContext(transformSync(readFileSync('src/utils/backgroundReturn.ts','utf8'),{loader:'ts',format:'cjs'}).code,backgroundContext)
const navigationModule={exports:{}}
const navigationContext={module:navigationModule,exports:navigationModule.exports};vm.createContext(navigationContext)
vm.runInContext(transformSync(readFileSync('src/utils/navigationEditing.ts','utf8'),{loader:'ts',format:'cjs'}).code,navigationContext)
const routingModule={exports:{}}
const routingContext={module:routingModule,exports:routingModule.exports,require:()=>({interactionDomUid:()=>''})};vm.createContext(routingContext)
vm.runInContext(transformSync(readFileSync('src/utils/canvasRouting.ts','utf8'),{loader:'ts',format:'cjs'}).code,routingContext)
const controlsModule={exports:{}}
const controlsContext={module:controlsModule,exports:controlsModule.exports,DOMParser,require:id=>readFileSync(id.replace('?raw','').replace('../../../','../'),'utf8')};vm.createContext(controlsContext)
vm.runInContext(transformSync(readFileSync('src/utils/prototypeControls.ts','utf8'),{loader:'ts',format:'cjs'}).code,controlsContext)

class Node {
  constructor(tag='div', attrs={}, box={left:0,top:0,width:375,height:833}) {
    this.tagName=tag.toUpperCase();this.attrs={...attrs};this.children=[];this.style={};this.box=box
    const names=new Set((attrs.class||'').split(/\s+/).filter(Boolean))
    this.classNames=names
    this.classList={contains:n=>names.has(n),add:n=>names.add(n),remove:n=>names.delete(n),toggle:(n,on)=>on?names.add(n):names.delete(n)}
    this.isConnected=true;this.innerText=''
  }
  get parentNode(){return this.parentElement}
  get firstChild(){return this.children[0]||null}
  get id(){return this.getAttribute('id')||''}
  set className(value){this.attrs.class=value;this.classNames.clear();value.split(/\s+/).filter(Boolean).forEach(n=>this.classNames.add(n))}
  getAttribute(k){return this.attrs[k]??null}
  setAttribute(k,v){this.attrs[k]=String(v)}
  hasAttribute(k){return k in this.attrs}
  appendChild(n){n.parentElement=this;this.children.push(n);return n}
  insertBefore(n){n.parentElement=this;this.children.unshift(n);return n}
  remove(){if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(n=>n!==this)}
  contains(n){return n===this||this.children.some(c=>c.contains(n))}
  matches(selector){
    if(selector.includes(','))return selector.split(',').some(s=>this.matches(s.trim()))
    if(selector.startsWith('.wf-segs > '))return this.tagName==='SPAN'&&this.parentElement?.classList.contains('wf-segs')
    if(selector.startsWith('#'))return this.id===selector.slice(1)
    if(selector.startsWith('.'))return this.classList.contains(selector.slice(1))
    const attr=selector.match(/^\[([^=\]]+)(?:="([^"]*)")?\]$/)
    if(attr)return this.hasAttribute(attr[1])&&(attr[2]===undefined||this.getAttribute(attr[1])===attr[2])
    return this.tagName===selector.toUpperCase()
  }
  closest(selector){return this.matches(selector)?this:this.parentElement?.closest(selector)||null}
  querySelectorAll(selector){return this.children.flatMap(c=>[...(c.matches(selector)?[c]:[]),...c.querySelectorAll(selector)])}
  querySelector(selector){return this.querySelectorAll(selector)[0]||null}
  getBoundingClientRect(){return {...this.box,right:this.box.left+this.box.width,bottom:this.box.top+this.box.height}}
  addEventListener(){}
}

function setup(elements=[], interactive=true, populate=()=>{}) {
  const listeners={}, messages=[], body=new Node('body'), root=new Node('html');root.appendChild(body)
  const doc={body,documentElement:root,createElement:t=>new Node(t),querySelector:s=>root.querySelector(s),querySelectorAll:s=>root.querySelectorAll(s),
    addEventListener:(type,fn)=>(listeners[type]??=[]).push(fn)}
  const window={scrollX:0,scrollY:0,innerWidth:375,innerHeight:833,addEventListener:(type,fn)=>(listeners['window:'+type]??=[]).push(fn)}
  const scope={DOMParser,props:{page:{id:1,canvas_width:375,canvas_height:833,elements},protoHotspot:false},...backgroundModule.exports,...navigationModule.exports,...routingModule.exports,...controlsModule.exports,...security};vm.createContext(scope);vm.runInContext(compiled,scope)
  populate(body)
  const html=scope.generate('<html><body></body></html>',interactive,false,false)
  const runtime=new DOMParser().parseFromString(html,'text/html').querySelector('body > script[data-wf-inject]').textContent
  const context={document:doc,window,parent:{postMessage:m=>messages.push(m)},requestAnimationFrame:fn=>{fn();return 1},console,setTimeout(){}}
  vm.createContext(context);vm.runInContext(runtime,context)
  function event(type,target){
    const r=target.getBoundingClientRect();const e={target,clientX:r.left+r.width/2,clientY:r.top+r.height/2,preventDefault(){},stopPropagation(){}}
    for(const fn of listeners[type]||[])fn(e)
  }
  const message=data=>{for(const fn of listeners['window:message']||[])fn({data,source:context.parent})}
  return {doc,body,context,window,messages,event,message,editor:scope.editor,generate:scope.generate,html}
}

test('runtime injection removes embedded documents and preserves safe JSON data',()=>{
  const {generate}=setup()
  const nested='<html><head><style data-wf-inject>body{color:red}</style></head><body><button>弹窗</button><script data-wf-inject>window.nested=true</script></body></html>'
  const source=`<!doctype html><html><head><script id="page-data" type="application/json">${JSON.stringify({markup:'</body>',token:'$&'})}</script></head><body><main data-wf-uid="main">商店</main><iframe id="embedded" srcdoc="${nested.replace(/"/g,'&quot;')}"></iframe></body></html>`
  const output=generate(source,false,false,true),doc=new DOMParser().parseFromString(output,'text/html')
  assert.equal(doc.querySelector('#embedded'),null)
  assert.deepEqual(JSON.parse(doc.querySelector('#page-data').textContent),{markup:'</body>',token:'$&'})
  const scripts=[...doc.querySelectorAll('script[data-wf-inject]')]
  assert.equal(scripts.length,5)
  for(const script of scripts) new vm.Script(script.textContent)
  assert.ok(doc.querySelector('body > style[data-wf-inject]'))
  assert.ok(doc.querySelector('body > script[data-wf-editor]'))
  assert.equal(doc.querySelectorAll('[data-wf-uid="main"]').length,1)
})

test('saved autowire popup surfaces are rebuilt from their map, with no stale editor mode',()=>{
  const {generate}=setup()
  const data=JSON.stringify({modalPages:{2:'<html><body>兑换记录</body></html>'}})
  const source=`<html><body class="theme wf-edit-mode wf-dragging wf-proto-hotspot wf-interactive"><main data-wf-uid="item">商品</main><div class="wf-modal wf-autowire-modal wf-show"><iframe srcdoc="<body>旧弹窗</body>"></iframe></div><div class="wf-modal" id="designed-modal">设计内弹窗</div><script id="wf-autowire-map" type="application/json">${data}</script><style data-wf-inject>old runtime</style><div id="wf-transform-box"></div></body></html>`
  const doc=new DOMParser().parseFromString(generate(source,false,false,false),'text/html')
  assert.equal(doc.querySelector('.wf-autowire-modal'),null)
  assert.equal(doc.querySelector('#wf-transform-box'),null)
  assert.equal(doc.body.className,'theme')
  assert.ok(doc.querySelector('#designed-modal'))
  assert.deepEqual(JSON.parse(doc.querySelector('#wf-autowire-map').textContent),JSON.parse(data))
  assert.ok(doc.querySelector('[data-wf-uid="item"]'))
})

test('background return bindings update in the live document without replacing visible content',()=>{
  const back={id:1,type:'background',x:0,y:0,width:375,height:833,interaction:{action:'back'}}
  let card
  const state=setup([back],true,body=>{card=body.appendChild(new Node('article',{id:'card'}))})
  state.message({type:'wf-interactive',on:true,backdropReturns:[{id:2,action:'close',x:5,y:6,w:350,h:800}]})
  assert.equal(state.doc.querySelector('[data-wf-element-id="1"]'),null)
  const area=state.doc.querySelector('[data-wf-element-id="2"]')
  assert.equal(area.getAttribute('data-action'),'close');assert.match(area.style.cssText,/left:5px/)
  state.message({type:'wf-interactive',on:false,backdropReturns:[{id:2,action:'back',x:7,y:6,w:350,h:800}]})
  assert.equal(state.doc.querySelector('[data-wf-element-id="2"]'),area)
  assert.equal(area.getAttribute('data-action'),'back');assert.match(area.style.cssText,/left:7px/)
  state.message({type:'wf-interactive',on:true,backdropReturns:[],disabledBackgroundReturn:true})
  assert.equal(state.doc.querySelector('.wf-page-dismiss'),null)
  assert.equal(state.doc.querySelector('#card'),card)
})

function tabs(body) {
  const owner=body.appendChild(new Node('div',{'data-wf-element-id':'10',class:'wf-tabs'}))
  const row=owner.appendChild(new Node('div',{class:'wf-segs'}))
  const left=row.appendChild(new Node('span',{class:'on'}, {left:120,top:44,width:71,height:36}));left.innerText='扭蛋'
  const right=row.appendChild(new Node('span',{'data-nav':'收集'}, {left:191,top:44,width:71,height:36}));right.innerText='收集'
  return {owner,row,left,right}
}

test('legacy HTML regains its recorded background return; editing cannot trigger navigation',()=>{
  const back={id:1,type:'background',x:0,y:0,width:375,height:833,interaction:{action:'back'}}
  const live=setup([back]);const area=live.doc.querySelector('[data-wf-element-id="1"]')
  assert.ok(area);assert.equal(area.getAttribute('data-action'),'back');assert.match(area.style.cssText,/z-index:1/)
  live.event('click',area);assert.equal(live.messages.filter(m=>m.type==='wf-back').length,1)
  const edit=setup([back],false);edit.event('click',edit.doc.querySelector('[data-wf-element-id="1"]'))
  assert.equal(edit.messages.filter(m=>m.type==='wf-back').length,0)
  assert.match(edit.html,/body:not\(\.wf-interactive\) \.wf-page-dismiss/)
  assert.equal(setup([]).doc.querySelector('[data-wf-element-id="1"]'),null)
})

test('an existing background target is not duplicated; returning within an overlay closes only that overlay',()=>{
  const back={id:1,type:'background',x:0,y:0,width:375,height:833,interaction:{action:'back'}}
  let area,modal
  const state=setup([back],true,body=>{
    modal=body.appendChild(new Node('div',{class:'wf-modal wf-show'}))
    area=modal.appendChild(new Node('div',{'data-wf-element-id':'1','data-action':'back'}))
  })
  assert.equal(state.doc.querySelectorAll('[data-wf-element-id="1"]').length,1)
  state.event('click',area)
  assert.equal(modal.classList.contains('wf-show'),false)
  assert.equal(state.messages.filter(m=>m.type==='wf-back').length,0)
})

test('each tab gets a stable identity and sends its own coordinates and UID exactly once',()=>{
  let parts;const state=setup([],true,body=>{parts=tabs(body)})
  assert.equal(parts.left.getAttribute('data-wf-uid'),'wf-tab-p1-e10-0')
  assert.equal(parts.right.getAttribute('data-wf-uid'),'wf-tab-p1-e10-1')
  state.event('click',parts.right)
  const nav=state.messages.filter(m=>m.type==='wf-nav');assert.equal(nav.length,1)
  assert.equal(nav[0].uid,'wf-tab-p1-e10-1');assert.equal(nav[0].uids[0],nav[0].uid)
  assert.equal(nav[0].x,227);assert.equal(nav[0].y,62)
  state.event('click',parts.left)
  const miss=state.messages.filter(m=>m.type==='wf-miss-click');assert.equal(miss.length,1)
  assert.equal(miss[0].uids[0],'wf-tab-p1-e10-0')
  assert.equal(state.window.__wfPickTabSegment(parts.right),parts.right)
})

test('hotspot selection resolves a segment even when its entire parent is already selected',()=>{
  let parts;const state=setup([],false,body=>{parts=tabs(body)})
  const script=state.editor(false,false).match(/<script data-wf-inject[^>]*>([\s\S]*?)<\/script>/)[1]
  const tree=ts.createSourceFile('editor.js',script,ts.ScriptTarget.Latest,true,ts.ScriptKind.JS)
  let picker
  function visit(n){if(ts.isFunctionDeclaration(n)&&n.name?.text==='pickHotspotEl')picker=n.getText(tree);ts.forEachChild(n,visit)}visit(tree)
  assert.ok(picker)
  Object.assign(state.context,{selectedEl:parts.owner,selectedEls:[],target:parts.right})
  vm.runInContext(picker+';globalThis.picked=pickHotspotEl(target)',state.context)
  assert.equal(state.context.picked,parts.right)
})

test('independent segment bindings override a previous whole-component link',()=>{
  const module={exports:{}}
  const context={module,exports:module.exports};vm.createContext(context)
  vm.runInContext(transformSync(readFileSync('src/utils/interactionHit.ts','utf8'),{loader:'ts',format:'cjs'}).code,context)
  const page={html_content:'wf-tab-p1-e10-0 wf-tab-p1-e10-1 parent',elements:[
    {id:10,x:120,y:44,width:142,height:36,interaction:{action:'navigate',target_page_id:99,params:'{"domUid":"parent"}'}},
    {id:101,x:120,y:44,width:71,height:36,interaction:{action:'navigate',target_page_id:2,params:'{"domUid":"wf-tab-p1-e10-0"}'}},
    {id:102,x:191,y:44,width:71,height:36,interaction:{action:'navigate',target_page_id:3,params:'{"domUid":"wf-tab-p1-e10-1"}'}},
  ]}
  assert.equal(module.exports.resolveNavigateElement(page,227,62,['wf-tab-p1-e10-1','parent']).interaction.target_page_id,3)
  assert.equal(module.exports.resolveNavigateElement(page,156,62,['wf-tab-p1-e10-0','parent']).interaction.target_page_id,2)
})

test('full-screen back returns to the actual previous page and closes native overlays before popping history',()=>{
  const sfc=readFileSync('src/views/PurePreviewView.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const tree=ts.createSourceFile('preview.ts',sfc,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
  const back=tree.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='handleBack').getText(tree)
  const context={activeModal:{value:null},historyStack:{value:[4,7]},currentPageId:{value:9},pages:{value:[{id:1}]},transitionDirection:{value:'forward'},nextTick(){},updatePhoneScale(){},showToast(){}}
  context.closeModal=()=>{context.activeModal.value=null}
  vm.createContext(context);vm.runInContext(transformSync(back,{loader:'ts'}).code,context)
  context.activeModal.value={type:'dialog'};vm.runInContext('handleBack()',context)
  assert.equal(context.currentPageId.value,9);assert.equal(context.historyStack.value.length,2)
  vm.runInContext('handleBack()',context);assert.equal(context.currentPageId.value,7)
  vm.runInContext('handleBack()',context);assert.equal(context.currentPageId.value,4)
  assert.equal(context.transitionDirection.value,'backward')
})

test('full-screen popup and modal relations use the saved destination, while local modal actions stay local',()=>{
  const sfc=readFileSync('src/views/PurePreviewView.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const tree=ts.createSourceFile('preview.ts',sfc,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
  const handler=tree.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='handleElementClick').getText(tree)
  const calls=[]
  const context={currentPage:{value:{}},activeModal:{value:null},isTabNavigation:()=>false,navigateTo:(...args)=>calls.push(args)}
  vm.createContext(context);vm.runInContext(transformSync(handler,{loader:'ts'}).code,context)
  for(const action of ['popup','modal']){
    context.element={id:40,label:'背包',interaction:{action,target_page_id:12}}
    vm.runInContext('handleElementClick(element)',context)
  }
  assert.deepEqual(calls,[[12,false],[12,false]])
  assert.equal(context.activeModal.value,null)
  context.element={id:41,label:'操作菜单',interaction:{action:'modal'}}
  vm.runInContext('handleElementClick(element)',context)
  assert.equal(calls.length,2)
  assert.equal(context.activeModal.value.type,'sheet')
})

test('explicit background behavior overrides inferred return and can be switched off without touching the card',()=>{
  const inferred={id:1,type:'background',interaction:{action:'back'},x:0,y:0,width:375,height:833}
  const setting={id:9,type:'background',style:'{"wfPageAction":"background-return"}',interaction:{action:'none'}}
  let card
  const state=setup([inferred,setting],true,body=>{
    body.appendChild(new Node('div',{class:'wf-page-dismiss','data-wf-element-id':'1','data-action':'back'}))
    card=body.appendChild(new Node('div',{class:'wf-card','data-wf-element-id':'20'}));card.innerText='魔女'
  })
  assert.equal(state.doc.querySelector('.wf-page-dismiss'),null)
  assert.equal(card.innerText,'魔女');assert.ok(card.parentElement)
  assert.equal(backgroundModule.exports.backgroundReturnEnabled({elements:[inferred,setting]}),false)
  const enabled=setup([inferred,{...setting,interaction:{action:'back'}}])
  const area=enabled.doc.querySelector('[data-wf-element-id="9"]');assert.ok(area)
  assert.match(area.style.cssText,/width:375px;height:833px/)
  enabled.event('click',area);assert.equal(enabled.messages.filter(m=>m.type==='wf-back').length,1)
})
