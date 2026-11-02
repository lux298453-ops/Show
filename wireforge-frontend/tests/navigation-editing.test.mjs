import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { parseHTML, DOMParser } from 'linkedom'
import ts from 'typescript'

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
function editorSetup() {
  const state=setup(),{document,nav}=state,window={document,HTMLElement:document.defaultView.HTMLElement,Element:document.defaultView.Element},listeners={},messages=[]
  document.addEventListener=(type,fn)=>(listeners[type]??=[]).push(fn)
  window.addEventListener=(type,fn)=>(listeners['window:'+type]??=[]).push(fn)
  window.parent={postMessage:m=>messages.push(m)};window.__wfNavigationEditing=nav
  window.getComputedStyle=el=>new Proxy(el.style,{get:(style,key)=>style[key]||({position:'relative',backgroundColor:'transparent',backgroundImage:'none',boxShadow:'none',fontSize:'11px',color:'#888'}[key])||''})
  window.getSelection=()=>({removeAllRanges(){},rangeCount:0})
  const proto=window.HTMLElement.prototype
  Object.defineProperty(proto,'offsetWidth',{configurable:true,get(){return this.getBoundingClientRect().width}})
  Object.defineProperty(proto,'offsetHeight',{configurable:true,get(){return this.getBoundingClientRect().height}})
  proto.getBoundingClientRect=function(){
    const role=this.getAttribute('data-wf-edit-role'),icon=role==='navigation-icon',item=role==='navigation-item',label=role==='navigation-label'
    const left=(icon?28:label?20:0)+(parseFloat(this.style.left)||0),top=(icon?708:label?740:700)+(parseFloat(this.style.top)||0)
    const width=parseFloat(this.style.width)||(icon?24:item?80:label?40:160),height=parseFloat(this.style.height)||(icon?24:label?14:64)
    return {left,top,width,height,right:left+width,bottom:top+height}
  }
  const context={window,document,parent:window.parent,props:{page:{id:2326},protoHotspot:false},console,DOMParser,getComputedStyle:window.getComputedStyle,
    setTimeout:()=>1,clearTimeout(){},requestAnimationFrame:()=>1,localStorage:{getItem:()=>null,setItem(){}},navigator:{},Element:window.Element}
  vm.createContext(context);vm.runInContext(transformSync(editorFunction+'\nglobalThis.makeEditor=buildEditorPayload',{loader:'ts'}).code,context)
  let script=context.makeEditor(false,false).match(/<script data-wf-inject>([\s\S]*?)<\/script>/)[1]
  script=script.replace('try { setTimeout(function(){ publishLayers();', 'window.__test={resolveTargetElement,publishLayers,snapshotHtml,pushSnapshot,undo,redo,selectElement,applyColor}; try { setTimeout(function(){ publishLayers();')
  vm.runInContext(script,context,{timeout:1500});vm.runInContext('window.__test.publishLayers();window.__test.pushSnapshot()',context,{timeout:1500})
  function event(type,target,extra={}){
    const e={target,button:0,clientX:30,clientY:710,preventDefault(){},stopPropagation(){},stopImmediatePropagation(){},...extra}
    for(const fn of listeners[type]||[]){context.testHandler=fn;context.testEvent=e;vm.runInContext('testHandler(testEvent)',context,{timeout:1500})}
  }
  const api=new Proxy(window.__test,{get:(_,key)=>(...args)=>{
    context.testMethod=key;context.testArgs=args
    return vm.runInContext('window.__test[testMethod](...testArgs)',context,{timeout:1500})
  }})
  return {...state,window,listeners,messages,event,api}
}
const markup=`<nav class="wf-canonical-nav"><div class="wf-nav-item" data-wf-element-id="12" data-nav="目标页"><span class="wf-nav-graphic"><span data-wf-element-id="13"><svg width="24" height="24"><path d="M0 0h24v24"/></svg></span></span><span class="wf-nav-label" data-wf-element-id="14">锁屏</span></div><div class="wf-nav-item"><span class="wf-nav-graphic"><svg width="24" height="24"/></span><span class="wf-nav-label">首页</span></div></nav>`
function setup(html=markup) {
  const {document}=parseHTML('<html><body>'+html+'</body></html>')
  const nav=installNavigationEditing(document,2326);nav.normalize()
  return {document,nav,item:document.querySelector('.wf-nav-item'),icon:document.querySelector('.wf-nav-graphic'),label:document.querySelector('.wf-nav-label')}
}
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
