import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { parseHTML, DOMParser } from 'linkedom'
import ts from 'typescript'
import { createTestPrototypeSecurity } from './fixtures/prototype-security.mjs'

const security = createTestPrototypeSecurity()
const secureDoc = security.createPrototypeDocument('<html><body></body></html>')
security.securePrototypeDocument(secureDoc)
security.window.eval(secureDoc.querySelector('[data-wf-security-runtime]').textContent)

function load(name) {
  const module={exports:{}};const context={module,exports:module.exports,DOMParser};vm.createContext(context)
  vm.runInContext(transformSync(readFileSync('src/utils/'+name+'.ts','utf8'),{loader:'ts',format:'cjs'}).code,context)
  return module.exports
}
const {installNavigationEditing}=load('navigationEditing')
const {createDeletionHistory,snapshotIdentityIndex}=load('deletionHistory')
const sfc=readFileSync('src/components/PageCanvas.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast=ts.createSourceFile('canvas.ts',sfc,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
const editorFunction=ast.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='buildEditorPayload').getText(ast)
function editorSetup(html=markup) {
  const state=setup(html),{document,nav}=state,window={document,HTMLElement:document.defaultView.HTMLElement,Element:document.defaultView.Element},listeners={},messages=[]
  document.addEventListener=(type,fn)=>(listeners[type]??=[]).push(fn)
  window.addEventListener=(type,fn)=>(listeners['window:'+type]??=[]).push(fn)
  document.removeEventListener=(type,fn)=>{listeners[type]=(listeners[type]||[]).filter(listener=>listener!==fn)}
  window.removeEventListener=(type,fn)=>{listeners['window:'+type]=(listeners['window:'+type]||[]).filter(listener=>listener!==fn)}
  window.parent={postMessage:m=>messages.push(m)};window.__wfNavigationEditing=nav
  window.__wfSanitizeFragment=html=>{
    const sanitized=security.window.document.createElement('div')
    sanitized.appendChild(security.window.__wfSanitizeFragment(html))
    const template=document.createElement('template');template.innerHTML=sanitized.innerHTML
    return template.content
  }
  window.getComputedStyle=el=>new Proxy(el.style,{get:(style,key)=>style[key]||({position:'relative',backgroundColor:'transparent',backgroundImage:'none',boxShadow:'none',fontSize:'11px',color:'#888'}[key])||''})
  window.getSelection=()=>({removeAllRanges(){},rangeCount:0})
  const proto=window.HTMLElement.prototype
  Object.defineProperty(proto,'innerText',{configurable:true,get(){return this.textContent},set(value){this.textContent=value}})
  Object.defineProperty(proto,'offsetWidth',{configurable:true,get(){return this.getBoundingClientRect().width}})
  Object.defineProperty(proto,'offsetHeight',{configurable:true,get(){return this.getBoundingClientRect().height}})
  proto.getBoundingClientRect=function(){
    const role=this.getAttribute('data-wf-edit-role'),icon=role==='navigation-icon',item=role==='navigation-item',label=role==='navigation-label'
    const left=(icon?28:label?20:0)+(parseFloat(this.style.left)||0),top=(icon?708:label?740:700)+(parseFloat(this.style.top)||0)
    const width=parseFloat(this.style.width)||(icon?24:item?80:label?40:160),height=parseFloat(this.style.height)||(icon?24:label?14:64)
    return {left,top,width,height,right:left+width,bottom:top+height}
  }
  const frames=new Map();let frameId=0
  const context={window,document,parent:window.parent,props:{page:{id:2326},protoHotspot:false},console,DOMParser,getComputedStyle:window.getComputedStyle,
    setTimeout:()=>1,clearTimeout(){},requestAnimationFrame:fn=>{frames.set(++frameId,fn);return frameId},cancelAnimationFrame:id=>frames.delete(id),localStorage:{getItem:()=>null,setItem(){}},navigator:{},Element:window.Element}
  vm.createContext(context);vm.runInContext(transformSync(editorFunction+'\nglobalThis.makeEditor=buildEditorPayload',{loader:'ts'}).code,context)
  let script=context.makeEditor(false,false).match(/<script data-wf-inject[^>]*>([\s\S]*?)<\/script>/)[1]
  script=script.replace('try { setTimeout(function(){ publishLayers();', 'window.__test={resolveTargetElement,publishLayers,snapshotHtml,pushSnapshot,undo,redo,selectElement,applyColor,reorderLayer,restackSelection,orderedLayerChildren,doExport}; try { setTimeout(function(){ publishLayers();')
  vm.runInContext(script,context,{timeout:1500});vm.runInContext('window.__test.publishLayers();window.__test.pushSnapshot()',context,{timeout:1500})
  function event(type,target,extra={}){
    const e={target,source:window.parent,button:0,clientX:30,clientY:710,preventDefault(){},stopPropagation(){},stopImmediatePropagation(){},...extra}
    for(const fn of listeners[type]||[]){context.testHandler=fn;context.testEvent=e;vm.runInContext('testHandler(testEvent)',context,{timeout:1500})}
  }
  const api=new Proxy(window.__test,{get:(_,key)=>(...args)=>{
    context.testMethod=key;context.testArgs=args
    return vm.runInContext('window.__test[testMethod](...testArgs)',context,{timeout:1500})
  }})
  const flushFrame=()=>{const callbacks=[...frames.values()];frames.clear();for(const callback of callbacks)callback()}
  return {...state,window,listeners,messages,event,api,frames,flushFrame,reinstall:()=>vm.runInContext(script,context,{timeout:1500})}
}
const markup=`<nav class="wf-canonical-nav"><div class="wf-nav-item" data-wf-element-id="12" data-nav="目标页"><span class="wf-nav-graphic"><span data-wf-element-id="13"><svg width="24" height="24"><path d="M0 0h24v24"/></svg></span></span><span class="wf-nav-label" data-wf-element-id="14">锁屏</span></div><div class="wf-nav-item"><span class="wf-nav-graphic"><svg width="24" height="24"/></span><span class="wf-nav-label">首页</span></div></nav>`

test('component insertion, clipboard paste and snapshot restore all sanitize imported HTML',()=>{
  const unsafe='<article data-wf-uid="imported"><a href="/files/evil.html" data-nav="商城">商城</a><img src="/api/assets/pet.png" onerror="alert(1)"><meta http-equiv="refresh" content="0;url=/files/evil.html"><iframe src="/files/evil.html"></iframe><script>alert(1)</script><svg viewBox="0 0 24 24"><use href="#pet"/></svg></article>'
  for(const command of [
    {type:'wf-insert-html',html:unsafe},
    {type:'wf-paste',payload:{html:unsafe,width:160,height:64}},
    {type:'wf-restore-snapshot',html:'WFBG:%23fff|\n'+unsafe},
  ]){
    const s=editorSetup('<main data-wf-uid="original">原内容</main>')
    s.event('window:message',null,{data:command})
    const imported=s.document.querySelector('article')
    assert.ok(imported,command.type)
    assert.equal(imported.querySelector('script,meta,iframe,[onerror]'),null,command.type)
    assert.equal(imported.querySelector('a').hasAttribute('href'),false,command.type)
    assert.equal(imported.querySelector('a').getAttribute('data-nav'),'商城',command.type)
    assert.equal(imported.querySelector('use').getAttribute('href'),'#pet',command.type)
    s.api.selectElement(imported)
    assert.ok(s.messages.some(m=>m.type==='wf-element-selected'),command.type)
    s.api.doExport()
    const saved=s.messages.findLast(m=>m.type==='wf-save')?.html
    assert.ok(saved?.includes('商城'),command.type)
    assert.equal(/onerror|http-equiv="refresh"|evil\.html/.test(saved),false,command.type)
  }
})

test('export and undo snapshots exclude temporary autowire frames while preserving authored modals and popup data',()=>{
  const s=editorSetup('<main data-wf-uid="item">商品</main><div class="wf-autowire-modal"><iframe srcdoc="<body>临时弹窗</body>"></iframe></div><div id="authored-modal" class="wf-modal">设计弹窗</div><script id="wf-autowire-map" type="application/json">{"modalPages":{"2":"记录"}}</script>')
  s.document.body.className='theme wf-edit-mode wf-dragging wf-proto-hotspot wf-interactive'
  assert.equal(s.api.snapshotHtml().includes('wf-autowire-modal'),false)
  s.api.doExport()
  const saved=new DOMParser().parseFromString(s.messages.findLast(m=>m.type==='wf-save').html,'text/html')
  assert.equal(saved.querySelector('.wf-autowire-modal'),null)
  assert.equal(saved.body.className,'theme')
  assert.ok(saved.querySelector('#authored-modal'))
  assert.ok(saved.querySelector('#wf-autowire-map'))
  assert.ok(s.document.querySelector('.wf-autowire-modal'),'export must leave the live document attached')
})
function setup(html=markup) {
  const {document}=parseHTML('<html><body>'+html+'</body></html>')
  const nav=installNavigationEditing(document,2326);nav.normalize()
  return {document,nav,item:document.querySelector('.wf-nav-item'),icon:document.querySelector('.wf-nav-graphic'),label:document.querySelector('.wf-nav-label')}
}
test('double-clicking gradient and plain inserted buttons edits text instead of opening the asset picker',()=>{
  for(const background of ['linear-gradient(135deg, #0284c7, #0369a1)','radial-gradient(circle, #fff, #0284c7)','none']){
    const {document,event,messages}=editorSetup(`<button class="wf-btn wf-inserted-component" data-wf-component="1" style="background-image:${background}">立即提交</button>`)
    const button=document.querySelector('button')
    event('dblclick',button)
    assert.equal(button.getAttribute('data-wf-editing-text'),'true',background)
    assert.equal(messages.some(m=>m.type==='wf-pick-asset'),false,background)
    button.textContent='修改后的按钮'
    button.dispatchEvent(new document.defaultView.Event('blur'))
    assert.equal(button.getAttribute('data-wf-editing-text'),null)
    assert.ok(messages.some(m=>m.type==='wf-text-changed'&&m.text==='修改后的按钮'))
  }
})

test('double-clicking real background images and explicit image slots still opens asset replacement',()=>{
  for(const html of [
    '<div class="wf-inserted-component" style="background-image:url(/assets/card.png)"></div>',
    '<img class="wf-inserted-component" src="/assets/avatar.png">',
    '<div class="wf-inserted-component wf-image-placeholder" style="background-image:linear-gradient(#fff,#ddd)"></div>',
  ]){
    const {document,event,messages}=editorSetup(html),target=document.querySelector('.wf-inserted-component')
    event('dblclick',target)
    assert.ok(messages.some(m=>m.type==='wf-pick-asset'),html)
    assert.equal(target.getAttribute('data-wf-editing-text'),null)
  }
})

test('navigation has stable item/icon/text identities and selects the intended scope',()=>{
  const {document,nav,item,icon,label}=setup(),path=icon.querySelector('path')
  assert.equal(nav.target(path,null),item);assert.equal(nav.part(path),icon)
  assert.equal(nav.target(path,icon),icon);assert.equal(nav.target(label,icon),label)
  assert.equal(nav.describe(icon),'锁屏 · 图标');assert.equal(nav.describe(item),'导航项 · 锁屏')
  const before=document.body.innerHTML;nav.normalize();assert.equal(document.body.innerHTML,before)
  const ids=[...document.querySelectorAll('[data-wf-uid]')].map(n=>n.getAttribute('data-wf-uid'))
  assert.equal(new Set(ids).size,ids.length)
  assert.equal(icon.querySelector('svg').style.width,'100%');assert.equal(icon.style.width,'24px')
})
test('replacing a navigation graphic keeps the owner action, size and source bindings',()=>{
  const {document,nav,item,icon}=setup(),uid=icon.getAttribute('data-wf-uid')
  icon.style.width='18px';assert.equal(nav.replaceIcon(icon,'/icons/home.svg'),true);nav.normalize()
  assert.equal(icon.querySelector('svg'),null);assert.equal(icon.querySelector('img').getAttribute('src'),'/icons/home.svg')
  assert.equal(icon.getAttribute('data-wf-uid'),uid);assert.equal(icon.style.width,'18px')
  assert.equal(item.getAttribute('data-nav'),'目标页');assert.equal(document.querySelectorAll('[data-wf-element-id="13"]').length,1)
  const ids=[...document.querySelectorAll('[data-wf-uid]')].map(n=>n.getAttribute('data-wf-uid'));assert.equal(new Set(ids).size,ids.length)
})
test('deleting an icon or label retains its navigation owner and does not recreate the deleted part',()=>{
  const {document,nav,item,icon,label}=setup()
  nav.preservePartBindings(icon);icon.remove();nav.preservePartBindings(label);label.remove();nav.normalize()
  assert.equal(item.getAttribute('data-nav'),'目标页');assert.equal(item.querySelector('svg,img'),null)
  assert.equal(item.querySelector('.wf-nav-label'),null);assert.equal(item.querySelector('.wf-nav-graphic'),null)
  assert.equal(document.querySelectorAll('[data-wf-element-id="13"]').length,1)
  assert.equal(document.querySelectorAll('[data-wf-element-id="14"]').length,1)
})
test('built-in replacement icons inherit the active item color and remain recolorable',()=>{
  const {nav,item,icon}=setup();item.style.color='#f60'
  nav.replaceIcon(icon,'/api/assets/icon-home');nav.normalize()
  assert.equal(icon.querySelectorAll('svg,img').length,0)
  assert.equal(icon.querySelector('[data-wf-nav-mask]').style.background,'currentColor')
  assert.match(icon.querySelector('[data-wf-nav-mask]').style.getPropertyValue('mask-image'),/icon-home/)
  assert.equal(icon.getAttribute('data-wf-nav-icon-src'),'/api/assets/icon-home')
})
test('icon move and resize stay within their own navigation slot',()=>{
  const {nav,item,icon}=setup();item.getBoundingClientRect=()=>({left:60,top:700,width:60,height:64})
  const origin={left:0,top:0,boxL:78,boxT:708}
  assert.deepEqual({...nav.constrainBox(icon,200,-200,24,24,origin)},{left:18,top:-8,width:24,height:24})
  assert.deepEqual({...nav.constrainBox(icon,0,0,200,200,origin)},{left:-18,top:-8,width:60,height:64})
})

test('inspector resizing keeps a centered navigation icon inside its slot after flex reflow',()=>{
  const s=editorSetup(),{item,icon}=s
  item.getBoundingClientRect=()=>({left:60,top:700,width:60,height:40})
  icon.style.left='10px'
  icon.getBoundingClientRect=()=>{
    const width=parseFloat(icon.style.width)||24,height=parseFloat(icon.style.height)||24
    const left=60+(60-width)/2+(parseFloat(icon.style.left)||0),top=700+(parseFloat(icon.style.top)||0)
    return {left,top,width,height,right:left+width,bottom:top+height}
  }
  s.api.selectElement(icon,true,false)
  s.event('window:message',null,{data:{type:'wf-layout',key:'width',val:100}})
  assert.equal(icon.getBoundingClientRect().width,60)
  assert.equal(icon.getBoundingClientRect().left,60)
  assert.equal(icon.closest('[data-wf-edit-role="navigation-item"]'),item)
  assert.equal(item.getAttribute('data-nav'),'目标页')
})
test('pose switch dock and legacy navigation get the same editable structure without changing actions',()=>{
  const {document,nav}=setup('<div class="wf-pose-dock"><div class="wf-segs"><span class="wf-pose-option" data-nav="待机"><i class="wf-pose-state"><svg width="18" height="18"/></i><b class="wf-pose-label">形象一</b></span></div></div>')
  const entry=document.querySelector('.wf-pose-option');assert.equal(nav.role(entry),'navigation-item')
  assert.equal(entry.getAttribute('data-nav'),'待机');assert.equal(nav.role(entry.querySelector('i')),'navigation-icon');assert.equal(nav.role(entry.querySelector('b')),'navigation-label')
})
test('snapshot identity matching ignores scripts, similar IDs and moved coordinates',()=>{
  const present=snapshotIdentityIndex('WFBG:%23fff|\n<div data-wf-uid="nav-item-10" style="left:900px"></div><script>"nav-item-1"</script>')
  assert.equal(present({deletedRootUid:'nav-item-10'}),true);assert.equal(present({deletedRootUid:'nav-item-1'}),false)
})
test('undoing a move after a deletion does not restore links; undo/redo of the deletion uses the recreated API ID',async()=>{
  const calls=[],r={pageId:1,elementId:12,deletedRootUid:'item',interactionId:40}
  const history=createDeletionHistory({remove:async r=>calls.push(['delete',r.interactionId]),restore:async r=>{r.interactionId=99;calls.push(['restore',99])}})
  await history.deleted(1,[r]);await history.reconcile(1,'<div data-wf-uid="another"/>');assert.equal(calls.length,1)
  await history.reconcile(1,'<div data-wf-uid="item"/>');await history.reconcile(1,'<div data-wf-uid="item" style="left:20px"/>');assert.equal(calls.length,2)
  await history.reconcile(1,'<div/>');assert.deepEqual(calls,[['delete',40],['restore',99],['delete',99]])
})
test('rapid undo is queued behind deletion; failed deletion remains retryable and pages stay independent',async()=>{
  let release;const gate=new Promise(resolve=>release=resolve),calls=[]
  const history=createDeletionHistory({remove:async r=>{if(r.pageId===1)await gate;calls.push('delete'+r.pageId)},restore:async r=>calls.push('restore'+r.pageId)})
  const a=history.deleted(1,[{pageId:1,elementId:12,deletedRootUid:'item'}]),b=history.reconcile(1,'<div data-wf-uid="item"/>')
  await history.deleted(2,[{pageId:2,elementId:12,deletedRootUid:'item'}]);assert.deepEqual(calls,['delete2'])
  release();await Promise.all([a,b]);assert.deepEqual(calls,['delete2','delete1','restore1'])
  let fail=true;const retry=createDeletionHistory({remove:async()=>{if(fail)throw Error('offline')},restore:async()=>{}})
  await assert.rejects(retry.deleted(3,[{pageId:3,elementId:4,deletedRootUid:'item'}]),/offline/)
  fail=false;await retry.reconcile(3,'<div/>')
})

test('actual editor publishes nested navigation layers and drills into a whole icon, never its SVG paths',()=>{
  const {api,window,messages,event,item,icon}=editorSetup()
  const tree=messages.findLast(m=>m.type==='wf-layers')
  assert.ok(tree,'layer tree should be published')
  const text=JSON.stringify(tree);assert.match(text,/导航栏/);assert.match(text,/锁屏 · 图标/);assert.match(text,/锁屏 · 文字/)
  assert.equal(api.resolveTargetElement(icon.querySelector('path'))===item,true)
  api.selectElement(item,true,false);event('dblclick',icon.querySelector('path'))
  assert.equal(window.__wf_getSelectedEl()===icon,true)
  assert.equal(window.document.querySelectorAll('#wf-transform-box .wf-handle').length,4)
  event('dblclick',icon.querySelector('path'));assert.equal(messages.findLast(m=>m.type==='wf-pick-asset').category,'icons')
  event('keydown',icon,{key:'Escape'});assert.equal(window.__wf_getSelectedEl()===item,true)
})
test('actual delete, undo and redo keep the icon identity and report the restored snapshot',()=>{
  const {api,document,window,messages,event,icon,item}=editorSetup(),uid=icon.getAttribute('data-wf-uid')
  api.selectElement(icon,true,false);event('keydown',icon,{key:'Delete'})
  assert.equal(item.querySelector('.wf-nav-graphic'),null);assert.equal(item.getAttribute('data-nav'),'目标页')
  assert.equal(messages.findLast(m=>m.type==='wf-element-deleted').items[0].navigationRole,'navigation-icon')
  api.undo();assert.ok(document.querySelector('.wf-nav-graphic'));assert.equal(document.querySelector('.wf-nav-graphic').getAttribute('data-wf-uid'),uid)
  assert.match(messages.findLast(m=>m.type==='wf-canvas-undo').html,/wf-nav-graphic/)
  api.redo();assert.equal(!!document.querySelector('.wf-nav-item').querySelector('.wf-nav-graphic'),false)
  assert.equal(!!window.__wf_getSelectedEl(),false)
})
test('actual icon resize keeps relative positioning and does not move the navigation owner',()=>{
  const {api,document,event,icon,item}=editorSetup()
  api.selectElement(icon,true,false)
  event('mousedown',document.querySelector('#wf-transform-box [data-dir="se"]'))
  assert.equal(icon.style.position,'relative')
  event('mousemove',icon,{clientX:36,clientY:716,ctrlKey:true})
  event('mouseup',icon,{clientX:36,clientY:716})
  assert.equal(icon.style.width,'30px');assert.equal(item.style.left,'')
})
test('actual icon color editing preserves an outlined SVG instead of filling its paths',()=>{
  const {api,icon}=editorSetup(),svg=icon.querySelector('svg'),path=icon.querySelector('path')
  svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor')
  api.applyColor(icon,'#123456',false)
  assert.equal(svg.getAttribute('fill'),'none');assert.equal(path.getAttribute('fill'),null)
  assert.equal(svg.getAttribute('stroke'),'#123456');assert.equal(icon.style.color,'#123456')
})

test('generated button cards allow selecting and keeping their image, text and SVG children',()=>{
  const {api,document}=editorSetup('<button data-wf-role="card"><div data-wf-role="card"><img data-wf-role="image"><svg><path/></svg></div><span data-wf-role="text">角色名称</span></button>')
  const card=document.querySelector('button'),image=document.querySelector('img'),label=document.querySelector('span'),svg=document.querySelector('svg')
  assert.equal(api.resolveTargetElement(image),card)
  api.selectElement(card,true,false)
  assert.equal(api.resolveTargetElement(image),image)
  api.selectElement(image,true,false)
  assert.equal(api.resolveTargetElement(image),image)
  assert.equal(api.resolveTargetElement(label),label)
  assert.equal(api.resolveTargetElement(svg.querySelector('path')),svg)
})

const overlappingLayers = '<div data-wf-uid="a" style="position:absolute;z-index:9999">A</div><div data-wf-uid="b" style="position:absolute;z-index:10">B</div>'
test('layer drag changes high-z prototype content and publishes the actual stacking order',()=>{
  const {api,document,messages}=editorSetup(overlappingLayers)
  api.reorderLayer('a','b','after')
  assert.ok(Number(document.querySelector('[data-wf-uid="a"]').style.zIndex)<Number(document.querySelector('[data-wf-uid="b"]').style.zIndex))
  assert.deepEqual(Array.from(messages.findLast(m=>m.type==='wf-layers').layers,l=>l.uid),['b','a'])
  assert.equal(document.body.firstElementChild.getAttribute('data-wf-uid'),'a','stacking must not reorder layout nodes')
  api.doExport()
  const saved=messages.findLast(m=>m.type==='wf-save')
  assert.ok(saved);assert.doesNotMatch(saved.html,/z-index:9999/)
})

test('bring to front and send to back persist through the real undo and redo path',()=>{
  const {api,document}=editorSetup(overlappingLayers)
  const uidOrder=()=>[...api.orderedLayerChildren(document.body)].map(el=>el.getAttribute('data-wf-uid'))
  api.selectElement(document.querySelector('[data-wf-uid="b"]'),true,false)
  api.restackSelection('front');assert.deepEqual(uidOrder(),['a','b'])
  api.undo();assert.deepEqual(uidOrder(),['b','a'])
  api.redo();assert.deepEqual(uidOrder(),['a','b'])
  api.selectElement(document.querySelector('[data-wf-uid="b"]'),true,false)
  api.restackSelection('back');assert.deepEqual(uidOrder(),['b','a'])
})

test('layer drag keeps flex layout order and enables z-index on ordinary static children',()=>{
  const {api,document}=editorSetup('<section style="display:flex"><div data-wf-uid="a" style="position:static">A</div><div data-wf-uid="b" style="position:static">B</div></section><article><span data-wf-uid="c" style="position:static">C</span><span data-wf-uid="d" style="position:static">D</span></article>')
  api.reorderLayer('a','b','before')
  assert.deepEqual([...document.querySelector('section').children].map(el=>el.getAttribute('data-wf-uid')),['a','b'])
  assert.equal(document.querySelector('[data-wf-uid="a"]').style.position,'static')
  api.reorderLayer('c','d','before')
  assert.equal(document.querySelector('[data-wf-uid="c"]').style.position,'relative')
  assert.ok(Number(document.querySelector('[data-wf-uid="c"]').style.zIndex)>Number(document.querySelector('[data-wf-uid="d"]').style.zIndex))
})

test('nested component layers follow paint order while editor overlays keep their own z-index',()=>{
  const {api,document,messages}=editorSetup('<section data-wf-component="1" data-wf-uid="component">'+overlappingLayers+'</section><div data-wf-inject style="z-index:1000000"></div>')
  const overlay=document.querySelector('[data-wf-inject]')
  api.reorderLayer('a','b','after')
  const tree=messages.findLast(m=>m.type==='wf-layers').layers
  assert.deepEqual(Array.from(tree[0].children.slice(0,2),l=>l.uid),['b','a'])
  assert.equal(overlay.style.zIndex,'1000000');assert.equal(tree.length,1)
})

test('locked layers and layers in different parents cannot be reordered',()=>{
  const {api,document}=editorSetup('<section><div data-wf-uid="a" data-wf-locked="1">A</div><div data-wf-uid="b">B</div></section><section><div data-wf-uid="c">C</div></section>')
  api.reorderLayer('a','b','before')
  assert.equal(document.querySelector('[data-wf-uid="a"]').style.zIndex,'')
  api.reorderLayer('b','c','before')
  assert.equal(document.querySelector('[data-wf-uid="b"]').parentElement,document.querySelector('section'))
})

const generatedDialog = '<main data-wf-role="page" data-wf-uid="page"><section role="dialog" data-wf-uid="dialog"><header data-wf-uid="header"><h1 data-wf-uid="title" style="z-index:2"><span data-wf-uid="label">新品耀宝</span></h1><div data-wf-role="image" data-wf-uid="art" style="z-index:1"><svg data-wf-uid="vector"><path data-wf-uid="path"/></svg><img data-wf-uid="image" style="z-index:9999"></div></header><div data-wf-role="content" data-wf-uid="content">活动内容</div></section><nav data-wf-uid="nav">导航</nav></main>'

test('generated dialog containers expose independent nested layers without expanding SVG paths',()=>{
  const {messages}=editorSetup(generatedDialog)
  const tree=messages.findLast(m=>m.type==='wf-layers').layers
  const find=(uid,nodes=tree)=>nodes.flatMap(n=>[n,...(n.children||[]).flatMap(function walk(x){return [x,...(x.children||[]).flatMap(walk)]})]).find(n=>n.uid===uid)
  assert.ok(find('dialog')?.children.some(n=>n.uid==='header'))
  assert.ok(find('header')?.children.some(n=>n.uid==='art'))
  assert.ok(find('art')?.children.some(n=>n.uid==='image'))
  assert.ok(find('title')?.children.some(n=>n.uid==='label'))
  assert.equal(find('vector').children.length,0)
  assert.equal(find('path'),undefined)
})

test('front and back on a nested image update its wrapper path inside the dialog and restore through history',()=>{
  const {api,document}=editorSetup(generatedDialog)
  const get=uid=>document.querySelector('[data-wf-uid="'+uid+'"]')
  const order=uid=>Array.from(api.orderedLayerChildren(get(uid)),n=>n.getAttribute('data-wf-uid'))
  const markupBefore=get('page').outerHTML
  api.selectElement(get('image'),true,false);api.restackSelection('front')
  assert.deepEqual(order('header'),['title','art'])
  assert.deepEqual(order('dialog'),['content','header'])
  assert.equal(get('dialog').style.zIndex,'','the dialog must not move across other page frames')
  assert.equal(get('image').parentElement,get('art'))
  api.undo();assert.equal(get('page').outerHTML,markupBefore)
  api.redo();assert.deepEqual(order('header'),['title','art'])
  api.selectElement(get('image'),true,false);api.restackSelection('back')
  assert.deepEqual(order('header'),['art','title'])
  assert.deepEqual(order('dialog'),['header','content'])
})

test('dragging nested generated layers orders their common branches and keeps DOM layout and actions intact',()=>{
  const {api,document,messages}=editorSetup(generatedDialog)
  const get=uid=>document.querySelector('[data-wf-uid="'+uid+'"]')
  get('label').setAttribute('data-nav','收集页')
  const children=[...get('header').children]
  api.reorderLayer('image','label','before')
  assert.ok(Number(get('art').style.zIndex)>Number(get('title').style.zIndex))
  assert.equal(get('image').parentElement,get('art'))
  assert.equal(get('label').parentElement,get('title'))
  assert.deepEqual([...get('header').children],children)
  assert.equal(get('label').getAttribute('data-nav'),'收集页')
  api.doExport();assert.match(messages.findLast(m=>m.type==='wf-save').html,/data-nav="收集页"/)
})

test('layer dragging cannot cross explicit group or dialog boundaries and preserves locked ancestors',()=>{
  const {api,document}=editorSetup(generatedDialog)
  const get=uid=>document.querySelector('[data-wf-uid="'+uid+'"]')
  api.reorderLayer('image','nav','before');assert.equal(get('art').style.zIndex,'1')
  get('art').setAttribute('data-wf-locked','1')
  api.selectElement(get('image'),true,false);api.restackSelection('front')
  assert.equal(get('art').style.zIndex,'1');assert.equal(get('title').style.zIndex,'2')
})

test('restack messages carry stable layer identities after the iframe loses its selection',()=>{
  const {document,window,event}=editorSetup(generatedDialog)
  assert.equal(window.__wf_getSelectedEl(),null)
  event('window:message',null,{data:{type:'wf-restack',edge:'front',uids:['image']}})
  assert.equal(window.__wf_getSelectedEl(),document.querySelector('[data-wf-uid="image"]'))
  assert.ok(Number(document.querySelector('[data-wf-uid="art"]').style.zIndex)>Number(document.querySelector('[data-wf-uid="title"]').style.zIndex))
})

test('right-clicking a row that stayed selected after reload reselects the real canvas before opening the menu',()=>{
  const {document,window,event}=editorSetup(generatedDialog)
  const script=readFileSync('src/components/LayerTree.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const ast=ts.createSourceFile('tree.ts',script,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
  const handler=ast.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='onLayerContextMenu').getText(ast)
  const calls=[],context={props:{selectedLayerUids:['image']},anchorUid:{value:null},isRowSelected:()=>true,
    emit:(name,...args)=>{calls.push(name);if(name==='select-dom-layer')event('window:message',null,{data:{type:'wf-select-uids',uids:args[1]}})},
    mouseEvent:{preventDefault(){},stopPropagation(){},clientX:1,clientY:2}}
  vm.createContext(context)
  vm.runInContext(transformSync(handler+'\nonLayerContextMenu("image",mouseEvent)',{loader:'ts'}).code,context)
  assert.deepEqual(calls,['select-dom-layer','open-layer-menu'])
  assert.equal(window.__wf_getSelectedEl(),document.querySelector('[data-wf-uid="image"]'))
  event('window:message',null,{data:{type:'wf-restack',edge:'front'}})
  assert.ok(Number(document.querySelector('[data-wf-uid="art"]').style.zIndex)>Number(document.querySelector('[data-wf-uid="title"]').style.zIndex))
})

test('disposing and reinstalling the editor removes old input handlers and restores dragging and property edits',()=>{
  const {api,document,window,event,listeners,messages,reinstall}=editorSetup('<p data-wf-uid="label">原文</p>')
  const label=document.querySelector('p'),originalHandlers=listeners.mousedown.length
  for(let i=0;i<3;i++){
    const old=window.__wfEditorLifecycle
    api.selectElement(label,true,false);old.dispose()
    assert.equal(old.active,false);assert.equal(window.__wf_getSelectedEl(),null)
    assert.equal(document.querySelector('#wf-transform-box'),null)
    const count=messages.length;event('window:message',null,{data:{type:'wf-select-uids',uids:['label']}})
    assert.equal(messages.length,count,'disposed editor must not process later commands')
    reinstall();assert.equal(window.__wfEditorLifecycle.active,true)
    assert.equal(listeners.mousedown.length,originalHandlers,'listeners must not accumulate on page switches')
    event('mousedown',label);assert.equal(window.__wf_getSelectedEl(),label)
    event('mousemove',label,{clientX:65,clientY:728,ctrlKey:true});event('mouseup',label)
    assert.equal(label.style.left,String((i+1)*35)+'px')
    event('window:message',null,{data:{type:'wf-update-text',text:'编辑'+i,uids:['label']}})
    assert.equal(label.textContent,'编辑'+i)
  }
})

test('pointer bursts update once per animation frame and release applies the final drag before export',()=>{
  const {api,document,event,frames,flushFrame,messages}=editorSetup('<p data-wf-uid="label" style="left:0px;top:0px">文字</p>')
  const label=document.querySelector('p')
  event('mousedown',label)
  for(let i=1;i<=120;i++)event('mousemove',label,{clientX:30+i,clientY:710,ctrlKey:true})
  assert.equal(frames.size,1);assert.equal(label.style.left,'0px')
  flushFrame();assert.equal(label.style.left,'120px');assert.equal(frames.size,0)
  event('mousemove',label,{clientX:170,clientY:715,ctrlKey:true})
  event('mousemove',label,{clientX:180,clientY:720,ctrlKey:true})
  event('mouseup',label)
  assert.equal(frames.size,0);assert.equal(label.style.left,'150px');assert.equal(label.style.top,'10px')
  api.doExport();assert.match(messages.findLast(m=>m.type==='wf-save').html,/left:150px/)
  flushFrame();assert.equal(label.style.left,'150px','a canceled callback must not move the saved element later')
})

test('disposing an editor cancels pending drag work before a later editor is installed',()=>{
  const {document,window,event,frames,flushFrame,reinstall}=editorSetup('<p style="left:0px">文字</p>')
  const label=document.querySelector('p')
  event('mousedown',label);event('mousemove',label,{clientX:90,clientY:710,ctrlKey:true})
  assert.equal(frames.size,1)
  window.__wfEditorLifecycle.dispose();assert.equal(frames.size,0)
  reinstall();flushFrame();assert.equal(label.style.left,'0px')
  event('mousedown',label);event('mousemove',label,{clientX:60,clientY:710,ctrlKey:true});event('mouseup',label)
  assert.equal(label.style.left,'30px')
})

test('nested element inspector coordinates use the same page coordinates as geometry edits',()=>{
  const {api,document,messages,event}=editorSetup('<div style="left:70px"><span data-wf-uid="label" style="left:25px">文案</span></div>')
  const label=document.querySelector('span')
  Object.defineProperty(label,'offsetLeft',{value:5});Object.defineProperty(label,'offsetTop',{value:8})
  api.selectElement(label,true,false)
  const info=messages.findLast(m=>m.type==='wf-element-selected').info
  assert.equal(info.x,25);assert.equal(info.y,700)
  event('window:message',null,{data:{type:'wf-layout',key:'x',val:45,uids:['label']}})
  assert.equal(label.style.left,'45px')
  assert.equal(messages.findLast(m=>m.type==='wf-element-selected').info.x,45)
})

test('decorative artwork behind its own section can be selected, dragged and opened for replacement',()=>{
  const {api,document,window,event,messages}=editorSetup('<section data-wf-uid="preview"><div style="z-index:-1;pointer-events:none"><img data-wf-uid="art" data-wf-artwork-image="true" style="left:0px;top:0px;width:341px;height:480px" src="/existing-art.png"></div><button data-wf-uid="control">其他装扮</button></section>')
  const section=document.querySelector('section'),img=document.querySelector('img'),control=document.querySelector('button')
  document.elementsFromPoint=()=>[section,img,document.body]
  event('mousedown',section);assert.equal(window.__wf_getSelectedEl(),img)
  event('mousemove',section,{clientX:56,clientY:728,ctrlKey:true});event('mouseup',section)
  assert.equal(img.style.left,'26px');assert.equal(img.style.top,'18px')
  event('dblclick',section)
  assert.equal(messages.findLast(m=>m.type==='wf-pick-asset').src,'/existing-art.png')
  assert.equal(img.classList.contains('wf-current-asset-target'),true)
  event('window:message',null,{data:{type:'wf-replace-asset',src:'/replacement.png'}})
  assert.equal(img.getAttribute('src'),'/replacement.png')
  assert.equal(api.resolveTargetElement(control,{clientX:30,clientY:710}),control,'foreground controls keep their own hit')
  img.parentElement.setAttribute('data-wf-locked','1')
  assert.equal(api.resolveTargetElement(section,{clientX:30,clientY:710}),section)
  img.parentElement.removeAttribute('data-wf-locked');img.setAttribute('data-wf-hidden','1')
  assert.equal(api.resolveTargetElement(section,{clientX:30,clientY:710}),section)
  img.removeAttribute('data-wf-hidden');document.body.classList.add('wf-interactive')
  assert.equal(api.resolveTargetElement(section,{clientX:30,clientY:710}),section,'preview must not use editor hit recovery')
})

test('legacy edited artwork releases unmarked fit rules locally and undo restores its fitted state',()=>{
  const {api,document,window,event,messages}=editorSetup('<style id="legacy-fit">[data-wf-artwork-resolved="true"] > img[data-wf-artwork-image]{inset:0!important;width:100%!important;height:100%!important}</style><style data-wf-artwork-style>img[data-wf-artwork-image]:not([data-wf-layout-edited]){position:absolute!important}</style><div data-wf-artwork-resolved="true"><img data-wf-uid="art" data-wf-artwork-image="true" data-wf-layout-edited="true" style="position:absolute;left:0px;top:0px;width:341px;height:500px"><img data-wf-uid="other" data-wf-artwork-image="true"></div>')
  const get=()=>document.querySelector('[data-wf-uid="art"]'),original=get().outerHTML,computed=window.getComputedStyle
  window.getComputedStyle=el=>{
    const base=computed(el)
    if(el===get()&&!el.hasAttribute('data-wf-artwork-layout'))return new Proxy(base,{get:(s,key)=>({position:'absolute',left:'0px',top:'0px',width:'341px',height:'480px',objectFit:'cover',objectPosition:'50% 0%',transform:'none'}[key])??s[key]})
    return base
  }
  const fitSelector=()=>document.querySelector('#legacy-fit').textContent.split('{')[0]
  event('window:message',null,{data:{type:'wf-layout',key:'width',val:100,uids:['art']}})
  assert.equal(get().style.width,'100px');assert.equal(get().style.height,'480px')
  assert.equal(get().matches(fitSelector()),false,'manual geometry must be excluded from the legacy !important rule')
  assert.equal(document.querySelector('[data-wf-uid="other"]').matches(fitSelector()),true,'unmodified images retain automatic fitting')
  api.undo();assert.equal(get().outerHTML,original);assert.equal(get().matches(fitSelector()),true)
  api.redo();assert.equal(get().style.width,'100px');assert.equal(get().matches(fitSelector()),false)
  event('window:message',null,{data:{type:'wf-layout',key:'height',val:180,uids:['art']}})
  assert.equal(get().style.height,'180px');assert.equal(get().style.width,'100px')
  api.doExport();const saved=messages.findLast(m=>m.type==='wf-save').html
  const restored=new DOMParser().parseFromString(saved,'text/html'),restoredImage=restored.querySelector('[data-wf-uid="art"]')
  assert.equal(restoredImage.style.width,'100px');assert.equal(restoredImage.style.height,'180px')
  assert.equal(restoredImage.matches(restored.querySelector('#legacy-fit').textContent.split('{')[0]),false)
})

test('reactivating a page cancels an unload still waiting for its pending save',async()=>{
  const functions=['hydrateEditorInFrame','unloadEditorInFrame','disposeEditorInFrame'].map(name=>ast.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text===name).getText(ast)).join('\n')
  let release;const pendingSave=new Promise(resolve=>release=resolve),{document}=parseHTML('<html><head><style data-wf-editor></style></head><body></body></html>')
  let disposed=0
  const frame={contentDocument:document,contentWindow:{__wfEditorLifecycle:{active:true,dispose(){disposed++}}}}
  const context={props:{readOnly:false},editorLifecycleVersion:0,hasLoadedEditorState:true,hasLoadedEditor:{value:true},
    htmlFrameRef:{value:frame},flushPendingSave:()=>pendingSave,sendEditMode(){},sendProtoHotspot(){}}
  vm.createContext(context);vm.runInContext(transformSync(functions,{loader:'ts'}).code,context)
  const unloading=vm.runInContext('unloadEditorInFrame()',context)
  vm.runInContext('hydrateEditorInFrame()',context);release();await unloading
  assert.equal(disposed,0);assert.equal(context.hasLoadedEditorState,true)
  assert.ok(document.querySelector('[data-wf-editor]'))
  await vm.runInContext('unloadEditorInFrame()',context)
  assert.equal(disposed,1);assert.equal(context.hasLoadedEditorState,false)
  assert.equal(document.querySelector('[data-wf-editor]'),null)
})
